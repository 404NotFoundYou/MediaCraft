// The source defers inherited teardown until all in-flight media work exits.
// ignore_for_file: must_call_super

import 'dart:async';

import 'package:aves/model/covers.dart';
import 'package:aves/model/dynamic_albums.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/origins.dart';
import 'package:aves/model/favourites.dart';
import 'package:aves/model/filters/covered/stored_album.dart';
import 'package:aves/model/grouping/common.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/analysis_controller.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/vaults/vaults.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/utils/android_file_utils.dart';
import 'package:aves/utils/debouncer.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/foundation.dart';

class MediaStoreSource extends CollectionSource {
  static const _maxChangeRetries = 3;
  final Debouncer _changeDebouncer = Debouncer(delay: ADurations.mediaContentChangeDebounceDelay);
  final Set<String> _changedUris = {};
  int _activeLoads = 0, _changeRetryCount = 0;
  bool _refreshing = false, _disposed = false, _teardownComplete = false;
  Map<String, int>? _lastGenerationByVolume;
  SourceScope _loadedScope, _targetScope;
  bool _canAnalyze = true;
  Future<void>? _essentialLoader;
  AnalysisController? _refreshController;
  final Set<AnalysisController> _loadControllers = {};
  final Set<StreamIterator<AvesEntry>> _entryLoadIterators = {};

  new() {
    stateNotifier.addListener(_onStateChanged);
  }

  void _onStateChanged() {
    if (isReady && !_refreshing && _changedUris.isNotEmpty) {
      _changeRetryCount = 0;
      onStoreChanged(null);
    }
  }

  @override
  void resumeMonitoring() {
    super.resumeMonitoring();
    _changeRetryCount = 0;
    onStoreChanged(null);
  }

  @override
  void dispose() {
    // The source may defer inherited teardown until in-flight database/media work exits.
    if (_disposed) return;
    _disposed = true;
    detachListeners();
    stateNotifier.removeListener(_onStateChanged);
    _changeDebouncer.cancel();
    _changedUris.clear();
    albumGrouping.unregisterSource(this);
    tagGrouping.unregisterSource(this);
    _refreshController?.enableStopSignal();
    _loadControllers.forEach((controller) => controller.enableStopSignal());
    unawaited(_cancelEntryLoads());
    _finishDisposeIfIdle();
  }

  void _beginLoad() => _activeLoads++;

  void _endLoad() {
    _activeLoads--;
    _finishDisposeIfIdle();
  }

  void _finishDisposeIfIdle() {
    if (_disposed && !_teardownComplete && !_refreshing && _activeLoads == 0) {
      _teardownComplete = true;
      super.dispose();
    }
  }

  Future<void> _cancelEntryLoads() async {
    final iterators = _entryLoadIterators.toList();
    await Future.wait(iterators.map((iterator) => iterator.cancel()));
  }

  @override
  set canAnalyze(bool enabled) => _canAnalyze = enabled;

  @override
  SourceScope get loadedScope => _loadedScope;

  @override
  SourceScope get targetScope => _targetScope;

  @override
  Future<void> init({
    required SourceScope scope,
    AnalysisController? analysisController,
    bool loadTopEntriesFirst = false,
  }) async {
    if (_disposed) return;
    _beginLoad();
    try {
      loadError = null;
      state = SourceState.loading;
      _targetScope = scope;
      await reportService.log('$runtimeType init target scope=$scope');
      if (_disposed) return;
      _essentialLoader ??= _loadEssentials().catchError((Object error, StackTrace stack) {
        _essentialLoader = null;
        Error.throwWithStackTrace(error, stack);
      });
      await _essentialLoader;
      if (_disposed) return;
      addDirectories(albums: settings.pinnedFilters.whereType<StoredAlbumFilter>().map((v) => v.album).toSet());
      try {
        _lastGenerationByVolume = await mediaStoreService.getGenerationByVolume();
      } catch (error, stack) {
        await reportService.recordError(error, stack);
      }
      if (_disposed) return;
      final loadController = analysisController ?? AnalysisController();
      _loadControllers.add(loadController);
      _beginLoad();
      unawaited(
        _loadEntries(
              analysisController: loadController,
              loadTopEntriesFirst: loadTopEntriesFirst,
            )
            .catchError((Object error, StackTrace stack) async {
              await _onLoadFailed(error, stack);
            })
            .whenComplete(() {
              _loadControllers.remove(loadController);
              if (analysisController == null) loadController.dispose();
              _endLoad();
            }),
      );
    } catch (error, stack) {
      await _onLoadFailed(error, stack);
      rethrow;
    } finally {
      _endLoad();
    }
  }

  Future<void> _onLoadFailed(Object error, StackTrace stack) async {
    if (_disposed) return;
    _loadedScope = null;
    loadError = AsyncError(error, stack);
    state = SourceState.failed;
    await reportService.recordError(error, stack);
  }

  Future<void> _loadEssentials() async {
    final stopwatch = Stopwatch()..start();
    state = SourceState.loading;
    await localMediaDb.init();
    if (_disposed) return;
    await vaults.init();
    if (_disposed) return;
    await favourites.init();
    if (_disposed) return;
    albumGrouping.init();
    albumGrouping.setGroups(settings.albumGroups);
    albumGrouping.registerSource(this);
    tagGrouping.init();
    tagGrouping.setGroups(settings.tagGroups);
    tagGrouping.registerSource(this);
    await covers.init();
    if (_disposed) return;
    await dynamicAlbums.init();
    if (_disposed) return;

    final deviceOffset = DateTime.now().timeZoneOffset.inMilliseconds;
    final catalogOffset = settings.catalogTimeZoneOffsetMillis;
    if (deviceOffset != catalogOffset) {
      unawaited(reportService.log('Time zone offset change: $catalogOffset -> $deviceOffset. Clear catalog metadata to get correct date/times.'));
      await localMediaDb.clearDatesWithoutTimeZone();
      if (_disposed) return;
      await localMediaDb.clearCatalogMetadataWithoutTimeZone();
      if (_disposed) return;
      settings.catalogTimeZoneOffsetMillis = deviceOffset;
    }
    await loadDates();
    if (_disposed) return;
    debugPrint('$runtimeType load essentials complete in ${stopwatch.elapsed.inMilliseconds}ms');
  }

  Future<void> _loadEntries({
    AnalysisController? analysisController,
    required bool loadTopEntriesFirst,
  }) async {
    if (_disposed) return;
    unawaited(reportService.log('$runtimeType load (known) start'));
    final stopwatch = Stopwatch()..start();
    state = SourceState.loading;
    clearEntries();

    final scopeAlbumFilters = _targetScope?.whereType<StoredAlbumFilter>();
    final scopeDirectory = scopeAlbumFilters != null && scopeAlbumFilters.length == 1 ? scopeAlbumFilters.first.album : null;

    final Set<AvesEntry> topEntries = {};
    if (loadTopEntriesFirst) {
      final topIds = settings.topEntryIds?.toSet();
      if (topIds != null) {
        debugPrint('$runtimeType load ${stopwatch.elapsed} load ${topIds.length} top entries');
        topEntries.addAll(await localMediaDb.loadEntriesById(topIds));
        if (_disposed) {
          topEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());
          return;
        }
        addEntries(topEntries);
      }
    }

    debugPrint('$runtimeType load ${stopwatch.elapsed} fetch known entries');
    final knownEntries = await localMediaDb.loadEntries(origin: EntryOrigins.mediaStoreContent, directory: scopeDirectory);
    if (_disposed) {
      knownEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());
      return;
    }
    final knownLiveEntries = knownEntries.where((entry) => !entry.trashed).toSet();
    final isLargeCollection = knownEntries.length > 100000;
    if (isLargeCollection && settings.isErrorReportingAllowed) {
      // disable error reporting for large collections, to prevent noisy OOM reports
      settings.isErrorReportingAllowed = false;
    }
    unawaited(reportService.setCustomKey('is_large_collection', isLargeCollection));
    unawaited(reportService.log('$runtimeType found ${knownEntries.length} known entries'));

    debugPrint('$runtimeType load ${stopwatch.elapsed} check known entries and paths');
    final knownPathByContentId = <int, String?>{
      for (final entry in knownLiveEntries)
        if (entry.contentId case final int id) id: entry.path,
    };
    final (:removed, :moved) = await mediaStoreService.checkKnownEntries(knownPathByContentId).onError<Object>((error, stack) {
      knownEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());
      Error.throwWithStackTrace(error, stack);
    });
    final removedContentIds = removed.toSet();
    try {
      if (_disposed) return;
      if (topEntries.isNotEmpty && removedContentIds.isNotEmpty) {
        final removedTopEntries = topEntries.where((entry) => removedContentIds.contains(entry.contentId));
        await removeEntries(removedTopEntries.map((entry) => entry.uri).toSet(), includeTrash: false);
      }
      if (_disposed) return;
    } finally {
      if (_disposed) knownEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());
    }
    final removedEntries = knownEntries.where((entry) => removedContentIds.contains(entry.contentId)).toSet();
    knownEntries.removeAll(removedEntries);
    removedEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());

    // show known entries
    debugPrint('$runtimeType load ${stopwatch.elapsed} add known entries');
    // add entries without notifying, so that the collection is not refreshed
    // with items that may be hidden right away because of their metadata
    addEntries(knownEntries, notify: false);
    // but use album notification without waiting for cataloguing
    // so that it is more reactive when picking an album in view mode
    notifyAlbumsChanged();

    await _loadVaultEntries(scopeDirectory);
    if (_disposed) return;

    debugPrint('$runtimeType load ${stopwatch.elapsed} load metadata');
    if (scopeDirectory != null) {
      final ids = knownLiveEntries.map((entry) => entry.id).toSet();
      await loadCatalogMetadata(ids: ids);
      if (_disposed) return;
      await loadAddresses(ids: ids);
      if (_disposed) return;
    } else {
      await loadCatalogMetadata();
      if (_disposed) return;
      await loadAddresses();
      if (_disposed) return;

      // trash
      await loadTrashDetails();
      if (_disposed) return;
      unawaited(
        deleteExpiredTrash().then(
          (deletedUris) {
            if (!_disposed && deletedUris.isNotEmpty) {
              debugPrint('evicted ${deletedUris.length} expired items from the trash');
              removeEntries(deletedUris, includeTrash: true);
            }
          },
          onError: (error) => debugPrint('failed to evict expired trash error=$error'),
        ),
      );
    }
    updateDerivedFilters();

    // clean up obsolete entries
    if (removedEntries.isNotEmpty) {
      debugPrint('$runtimeType load ${stopwatch.elapsed} remove obsolete entries');
      await localMediaDb.removeIds(removedEntries.map((entry) => entry.id).toSet());
      if (_disposed) return;
    }

    _loadedScope = _targetScope;
    unawaited(reportService.log('$runtimeType load (known) done in ${stopwatch.elapsed.inSeconds}s for ${knownEntries.length} known, ${removedEntries.length} removed'));

    if (_canAnalyze) {
      // it can discover new entries only if it can analyze them
      await _loadNewEntries(
        analysisController: analysisController,
        directory: scopeDirectory,
        knownLiveEntries: knownLiveEntries,
        knownDateByContentId: {
          for (final entry in knownLiveEntries) entry.contentId: entry.dateModifiedMillis,
          // Some apps move media without updating its modification date.
          for (final id in moved) id: null,
        },
      );
      if (_disposed) return;
    } else {
      state = SourceState.ready;
    }
  }

  Future<void> _loadNewEntries({
    required AnalysisController? analysisController,
    required String? directory,
    required Set<AvesEntry> knownLiveEntries,
    required Map<int?, int?> knownDateByContentId,
  }) async {
    unawaited(reportService.log('$runtimeType load (new) start'));
    final stopwatch = Stopwatch()..start();

    // items to add to the collection
    final newEntries = <AvesEntry>{};

    // recover untracked trash items
    debugPrint('$runtimeType load ${stopwatch.elapsed} recover untracked entries');
    if (directory == null) {
      final recoveredEntries = await recoverUntrackedTrashItems();
      if (_disposed) {
        recoveredEntries.where((entry) => !entry.isDisposed).forEach((entry) => entry.dispose());
        return;
      }
      // set media store as origin of recovered items, because they would be managed by it if restored
      recoveredEntries.forEach((entry) => entry.origin = EntryOrigins.mediaStoreContent);
      newEntries.addAll(recoveredEntries);
    }

    // fetch new & modified entries
    debugPrint('$runtimeType load ${stopwatch.elapsed} fetch new entries');
    final knownContentIds = knownDateByContentId.keys.toSet();
    final knownEntryByContentId = Map.fromEntries(knownLiveEntries.map((entry) => MapEntry(entry.contentId, entry)));
    final iterator = StreamIterator<AvesEntry>(mediaStoreService.getEntries(knownDateByContentId, directory: directory));
    _entryLoadIterators.add(iterator);
    try {
      while (await iterator.moveNext()) {
        final entry = iterator.current;
        if (_disposed) {
          entry.dispose();
          continue;
        }
        final contentId = entry.contentId;
        final existingEntry = knownContentIds.contains(contentId) ? knownEntryByContentId[contentId] : null;
        entry.id = existingEntry?.id ?? localMediaDb.nextId;
        newEntries.add(entry);
        setProgress(done: newEntries.length, total: 0);
      }
      if (_disposed) return;
      if (newEntries.isNotEmpty) {
        debugPrint('$runtimeType load ${stopwatch.elapsed} save ${newEntries.length} new entries');
        await localMediaDb.insertEntries(newEntries);
        if (_disposed) return;
        final duplicates = await localMediaDb.searchLiveDuplicates(EntryOrigins.mediaStoreContent, newEntries);
        if (_disposed) return;
        if (duplicates.isNotEmpty) {
          debugPrint('Loading entries yielded duplicates=${duplicates.join(', ')}');
          await localMediaDb.removeIds(duplicates.map((v) => v.id).toSet());
          if (_disposed) return;
          for (final duplicate in duplicates) {
            final duplicateId = duplicate.id;
            newEntries.removeWhere((v) => duplicateId == v.id);
          }
        }
        await Future.forEach(newEntries.where((v) => v.trashed), (entry) async {
          final trashDetails = entry.trashDetails;
          if (trashDetails != null) {
            await localMediaDb.updateTrash(entry.id, trashDetails);
          } else {
            unawaited(reportService.recordError(Exception('Adding trashed entry but trash details are missing for entry=$entry')));
          }
        });
        if (_disposed) return;
        addEntries(newEntries);
        invalidateAlbumFilterSummary();
        updateDirectories();
      }
      debugPrint('$runtimeType load ${stopwatch.elapsed} analyze');
      Set<AvesEntry>? analysisEntries;
      final analysisIds = analysisController?.entryIds;
      if (analysisIds != null) {
        analysisEntries = allEntries.where((entry) => analysisIds.contains(entry.id)).toSet();
      }
      await analyze(analysisController, entries: analysisEntries);
      if (_disposed) return;
      notifyAlbumsChanged();
      unawaited(reportService.log('$runtimeType load (new) done in ${stopwatch.elapsed.inSeconds}s for ${newEntries.length} new entries'));
    } finally {
      _entryLoadIterators.remove(iterator);
      for (final entry in newEntries) {
        if (!identical(getEntryById(entry.id), entry) && !entry.isDisposed) entry.dispose();
      }
      await iterator.cancel();
    }
  }

  // returns URIs to retry later. They could be URIs that are:
  // 1) currently being processed during bulk move/deletion
  // 2) registered in the Media Store but still being processed by their owner in a temporary location
  // For example, when taking a picture with a Galaxy S10e default camera app, querying the Media Store
  // sometimes yields an entry with its temporary path: `/data/sec/camera/!@#$%^..._temp.jpg`
  @override
  Future<Set<String>> refreshUris(Set<String> changedUris, {AnalysisController? analysisController}) async {
    if (_disposed) return {};
    if (!canRefresh || _essentialLoader == null || !isReady || _refreshing) return changedUris;
    _refreshing = true;
    final controller = analysisController ?? AnalysisController();
    _refreshController = controller;
    state = SourceState.loading;
    try {
      return await _refreshUris(changedUris, analysisController: controller);
    } finally {
      _refreshController = null;
      if (analysisController == null) controller.dispose();
      if (!_disposed) state = SourceState.ready;
      _refreshing = false;
      if (_disposed) {
        _finishDisposeIfIdle();
      } else {
        _onStateChanged();
      }
    }
  }

  Future<Set<String>> _refreshUris(Set<String> changedUris, {AnalysisController? analysisController}) async {
    final changedUriDetails = changedUris.length < 5 ? ' (${changedUris.join(', ')})' : '';
    unawaited(reportService.log('$runtimeType refresh start for ${changedUris.length} uris$changedUriDetails'));
    final changedUriByContentId = Map.fromEntries(
      changedUris.map((uri) {
        final pathSegments = Uri.parse(uri).pathSegments;
        // e.g. URI `content://media/` has no path segment
        if (pathSegments.isEmpty) return null;
        final idString = pathSegments.last;
        final contentId = int.tryParse(idString);
        if (contentId == null) return null;
        return MapEntry(contentId, uri);
      }).nonNulls,
    );

    // clean up obsolete entries
    if (changedUriByContentId.isNotEmpty) {
      final obsoleteContentIds = await mediaStoreService.checkObsoleteContentIds(changedUriByContentId.keys.toList());
      if (_disposed) return {};
      final obsoleteUris = obsoleteContentIds.map(changedUriByContentId.remove).nonNulls.toSet();
      await removeEntries(obsoleteUris, includeTrash: false);
      if (_disposed) return {};
    }

    // fetch new entries
    final tempUris = <String>{};
    final newEntries = <AvesEntry>{}, entriesToRefresh = <AvesEntry>{};
    try {
      final existingDirectories = <String>{};
      final existingEntryByContentId = <int, AvesEntry>{};
      if (changedUriByContentId.isNotEmpty) {
        for (final entry in allEntries) {
          final id = entry.contentId;
          if (id != null && changedUriByContentId.containsKey(id)) {
            existingEntryByContentId.putIfAbsent(id, () => entry);
            if (existingEntryByContentId.length == changedUriByContentId.length) break;
          }
        }
      }
      for (final kv in changedUriByContentId.entries) {
        final contentId = kv.key;
        final uri = kv.value;
        final sourceEntry = await mediaFetchService.getEntry(uri, null);
        if (_disposed) {
          sourceEntry?.dispose();
          return {};
        }
        if (sourceEntry != null) {
          var isSourceEntryUsed = false;
          final existingEntry = existingEntryByContentId[contentId];
          // compare paths because some apps move files without updating their `last modified date`
          if (existingEntry == null || (sourceEntry.dateModifiedMillis ?? 0) > (existingEntry.dateModifiedMillis ?? 0) || sourceEntry.path != existingEntry.path) {
            final newPath = sourceEntry.path;
            final volume = newPath != null ? androidFileUtils.getStorageVolume(newPath) : null;
            if (volume != null) {
              if (existingEntry != null) {
                entriesToRefresh.add(existingEntry);
              } else if (_canAnalyze) {
                // it can discover new entries only if it can analyze them
                sourceEntry.id = localMediaDb.nextId;
                newEntries.add(sourceEntry);
                isSourceEntryUsed = true;
              }
              final existingDirectory = existingEntry?.directory;
              if (existingDirectory != null) {
                existingDirectories.add(existingDirectory);
              }
            } else {
              debugPrint('$runtimeType refreshUris entry=$sourceEntry is not located on a known storage volume. Will retry soon...');
              tempUris.add(uri);
            }
          }
          if (!isSourceEntryUsed) {
            sourceEntry.dispose();
          }
        }
      }

      await _refreshVaultEntries(
        changedUris: changedUris.where(vaults.isVaultEntryUri).toSet(),
        newEntries: newEntries,
        entriesToRefresh: entriesToRefresh,
        existingDirectories: existingDirectories,
      );
      if (_disposed) return {};

      invalidateAlbumFilterSummary(directories: existingDirectories);

      if (newEntries.isNotEmpty) {
        await localMediaDb.insertEntries(newEntries);
        if (_disposed) return {};

        // TODO TLAD find duplication cause
        final duplicates = await localMediaDb.searchLiveDuplicates(EntryOrigins.mediaStoreContent, newEntries);
        if (_disposed) return {};
        if (duplicates.isNotEmpty) {
          debugPrint('$runtimeType refreshUris duplicates=${duplicates.join(', ')}');
          // post-error cleanup
          await localMediaDb.removeIds(duplicates.map((v) => v.id).toSet());
          if (_disposed) return {};
          for (final duplicate in duplicates) {
            final duplicateId = duplicate.id;
            newEntries.removeWhere((v) => duplicateId == v.id);
            tempUris.add(duplicate.uri);
          }
        }

        addEntries(newEntries);
        await analyze(analysisController, entries: newEntries);
        if (_disposed) return {};
      }

      if (entriesToRefresh.isNotEmpty) {
        await refreshEntries(entriesToRefresh, EntryDataType.values.toSet());
        if (_disposed) return {};
      }

      unawaited(reportService.log('$runtimeType refresh end for ${changedUris.length} uris$changedUriDetails'));

      if (tempUris.isNotEmpty) {
        final tempUriDetails = tempUris.length < 5 ? ' (${tempUris.join(', ')})' : '';
        unawaited(reportService.log('$runtimeType refresh will retry ${tempUris.length} uris$tempUriDetails'));
      }

      return tempUris;
    } finally {
      for (final entry in newEntries) {
        if (!identical(getEntryById(entry.id), entry) && !entry.isDisposed) entry.dispose();
      }
    }
  }

  void onStoreChanged(String? uri) {
    if (_disposed) return;
    if (uri != null) {
      _changedUris.add(uri);
      _changeRetryCount = 0;
    }
    if (_changedUris.isNotEmpty) {
      _changeDebouncer(() async {
        final todo = _changedUris.toSet();
        _changedUris.clear();
        try {
          final tempUris = await refreshUris(todo);
          if (_disposed) return;
          _changedUris.addAll(tempUris);
        } catch (error, stack) {
          if (_disposed) return;
          _changedUris.addAll(todo);
          await reportService.recordError(error, stack);
        }
        if (_disposed) return;
        if (_changedUris.isEmpty) {
          _changeRetryCount = 0;
        } else if (_changeRetryCount < _maxChangeRetries) {
          _changeRetryCount++;
          onStoreChanged(null);
        } else {
          await reportService.log('$runtimeType deferred ${_changedUris.length} uris after $_maxChangeRetries retries; waiting for the next media change');
        }
      });
    }
  }

  @override
  Future<void> checkForChanges() async {
    if (_disposed || _essentialLoader == null) return;
    final sinceGeneration = _lastGenerationByVolume;
    try {
      final generation = await mediaStoreService.getGenerationByVolume();
      if (_disposed || generation == null) return;
      // Read the next baseline before querying so changes made during the query are not skipped.
      final changedUris = await mediaStoreService.getChangedUris({for (final volume in generation.keys) volume: sinceGeneration?[volume]});
      if (_disposed) return;
      _changedUris.addAll(changedUris);
      if (identical(_lastGenerationByVolume, sinceGeneration)) _lastGenerationByVolume = generation;
      onStoreChanged(null);
    } catch (error, stack) {
      await reportService.recordError(error, stack);
    }
  }

  // vault

  Future<void> _loadVaultEntries(String? directory) async {
    addEntries(await localMediaDb.loadEntries(origin: EntryOrigins.vault, directory: directory));
  }

  Future<void> _refreshVaultEntries({
    required Set<String> changedUris,
    required Set<AvesEntry> newEntries,
    required Set<AvesEntry> entriesToRefresh,
    required Set<String> existingDirectories,
  }) async {
    if (changedUris.isEmpty) return;
    final existingEntryByUri = <String, AvesEntry>{};
    for (final entry in allEntries) {
      existingEntryByUri.putIfAbsent(entry.uri, () => entry);
    }
    for (final uri in changedUris) {
      final existingEntry = existingEntryByUri[uri];
      if (existingEntry != null) {
        entriesToRefresh.add(existingEntry);
        final existingDirectory = existingEntry.directory;
        if (existingDirectory != null) {
          existingDirectories.add(existingDirectory);
        }
      } else {
        final sourceEntry = await mediaFetchService.getEntry(uri, null, allowUnsized: true);
        if (_disposed) {
          sourceEntry?.dispose();
          return;
        }
        if (sourceEntry != null) {
          try {
            newEntries.add(
              sourceEntry.copyWith(
                id: localMediaDb.nextId,
                origin: EntryOrigins.vault,
              ),
            );
          } finally {
            sourceEntry.dispose();
          }
        }
      }
    }
  }
}
