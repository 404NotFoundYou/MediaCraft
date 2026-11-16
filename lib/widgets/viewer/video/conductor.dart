import 'dart:async';
import 'dart:math';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/props.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/device_service.dart';
import 'package:aves/services/media/media_session_service.dart';
import 'package:aves/services/window_service.dart';
import 'package:aves/widgets/viewer/video/db_playback_state_handler.dart';
import 'package:aves_video/aves_video.dart';
import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:leak_tracker/leak_tracker.dart';

class VideoConductor {
  final CollectionLens? collection;
  final AvesVideoControllerFactory _videoControllerFactory;
  final MediaSessionService _mediaSessionService;
  final WindowService _windowService;
  final DeviceService _deviceService;
  final List<AvesVideoController> _controllers = [];
  final Map<AvesVideoController, StreamSubscription> _statusSubscriptions = {};
  final Map<AvesVideoController, StreamSubscription> _eventSubscriptions = {};
  final Set<Future<void>> _statusOperations = {};
  final Set<Future<void>> _eventOperations = {};
  final Set<Future<AvesVideoController>> _controllerOperations = {};
  final PlaybackStateHandler _playbackStateHandler = DatabasePlaybackStateHandler();
  bool _disposed = false;
  int _statusGeneration = 0;
  Future<void> _statusTail = Future.value();
  final Duration statusDrainTimeout;

  final ValueNotifier<AvesVideoController?> playingVideoControllerNotifier = ValueNotifier(null);

  static const _defaultMaxControllerCount = 3;

  new({
    this.collection,
    AvesVideoControllerFactory? controllerFactory,
    MediaSessionService? mediaSession,
    WindowService? window,
    DeviceService? device,
    this.statusDrainTimeout = const Duration(seconds: 2),
  }) : _videoControllerFactory = controllerFactory ?? videoControllerFactory,
       _mediaSessionService = mediaSession ?? mediaSessionService,
       _windowService = window ?? windowService,
       _deviceService = device ?? deviceService {
    if (kFlutterMemoryAllocationsEnabled) {
      LeakTracking.dispatchObjectCreated(
        library: 'aves',
        className: '$VideoConductor',
        object: this,
      );
    }
  }

  bool get isDisposed => _disposed;

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _statusGeneration++;
    if (kFlutterMemoryAllocationsEnabled) {
      LeakTracking.dispatchObjectDisposed(object: this);
    }
    await Future.wait(_statusSubscriptions.values.map((sub) => sub.cancel()));
    await Future.wait(_eventSubscriptions.values.map((sub) => sub.cancel()));
    _statusSubscriptions.clear();
    _eventSubscriptions.clear();
    try {
      await Future.wait(_controllerOperations.toList());
    } catch (error, stack) {
      await reportService.recordError(error, stack);
    }
    await Future.wait(_statusSubscriptions.values.map((sub) => sub.cancel()));
    await Future.wait(_eventSubscriptions.values.map((sub) => sub.cancel()));
    _statusSubscriptions.clear();
    _eventSubscriptions.clear();
    try {
      await Future.wait(_statusOperations.toList()).timeout(statusDrainTimeout);
    } on TimeoutException {
      unawaited(reportService.log('$runtimeType timed out waiting for ${_statusOperations.length} video status operations'));
    }
    await Future.wait(_eventOperations.toList());
    await Future.forEach<AvesVideoController>(List.of(_controllers), (controller) => controller.dispose());
    playingVideoControllerNotifier.dispose();
    _controllers.clear();
    if (settings.keepScreenOn == .videoPlayback) {
      await _windowService.keepScreenOn(false);
    }
  }

  Future<AvesVideoController> getOrCreateController(AvesEntry entry, {int? maxControllerCount}) async {
    if (_disposed) throw StateError('Video conductor is disposed');
    late Future<AvesVideoController> operation;
    operation = _getOrCreateController(entry, maxControllerCount: maxControllerCount).whenComplete(() => _controllerOperations.remove(operation));
    _controllerOperations.add(operation);
    return operation;
  }

  Future<AvesVideoController> _getOrCreateController(AvesEntry entry, {int? maxControllerCount}) async {
    var controller = getController(entry);
    if (controller != null) {
      _controllers.remove(controller);
    } else {
      maxControllerCount = max(_defaultMaxControllerCount, maxControllerCount ?? 0);
      while (_controllers.length >= maxControllerCount) {
        await _disposeController(_controllers.removeLast());
        if (_disposed) throw StateError('Video conductor is disposed');
      }
      await _deviceService.requestGarbageCollection();
      if (_disposed) throw StateError('Video conductor is disposed');
      controller = _videoControllerFactory.buildController(
        entry,
        playbackStateHandler: _playbackStateHandler,
        settings: settings,
      );
      _statusSubscriptions[controller] = controller.statusStream.listen((event) => _handleControllerStatusChanged(entry, controller!, event));
      _eventSubscriptions[controller] = controller.eventStream.listen((event) => _handleControllerEvent(entry, controller!, event));
    }
    _controllers.insert(0, controller);
    return controller;
  }

  AvesVideoController? getPlayingController() => _controllers.firstWhereOrNull((c) => c.isPlaying);

  AvesVideoController? getController(AvesEntry entry) {
    return _controllers.firstWhereOrNull((c) => c.entry.uri == entry.uri && c.entry.pageId == entry.pageId);
  }

  void _handleControllerStatusChanged(AvesEntry entry, AvesVideoController controller, PlaybackStatus status) {
    if (_disposed) return;
    final generation = ++_statusGeneration;
    late Future<void> operation;
    operation = _statusTail
        .then((_) async {
          if (!_isCurrentStatus(generation)) return;
          try {
            await _onControllerStatusChanged(entry, controller, status, generation);
          } catch (error, stack) {
            await reportService.recordError(error, stack);
          }
        })
        .whenComplete(() => _statusOperations.remove(operation));
    _statusTail = operation;
    _statusOperations.add(operation);
    unawaited(operation);
  }

  bool _isCurrentStatus(int generation) => !_disposed && generation == _statusGeneration;

  Future<void> _onControllerStatusChanged(AvesEntry entry, AvesVideoController controller, PlaybackStatus status, int generation) async {
    bool canSkipToNext = false, canSkipToPrevious = false;
    final entries = collection?.sortedEntries;
    if (entries != null) {
      final currentIndex = entries.indexOf(entry);
      if (currentIndex != -1) {
        bool isVideo(AvesEntry entry) => entry.isVideo;
        canSkipToPrevious = entries.take(currentIndex).lastWhereOrNull(isVideo) != null;
        canSkipToNext = entries.skip(currentIndex + 1).firstWhereOrNull(isVideo) != null;
      }
    }

    await _mediaSessionService.update(
      entry: entry,
      controller: controller,
      canSkipToNext: canSkipToNext,
      canSkipToPrevious: canSkipToPrevious,
    );
    if (!_isCurrentStatus(generation)) return;
    if (settings.keepScreenOn == .videoPlayback) {
      await _windowService.keepScreenOn(status == .playing);
      if (!_isCurrentStatus(generation)) {
        if (_disposed && status == .playing) await _windowService.keepScreenOn(false);
        return;
      }
    }

    playingVideoControllerNotifier.value = getPlayingController();
  }

  void _handleControllerEvent(AvesEntry entry, AvesVideoController controller, VideoEvent event) {
    if (_disposed) return;
    late Future<void> operation;
    operation = () async {
      try {
        await _onControllerEvent(entry, controller, event);
      } catch (error, stack) {
        await reportService.recordError(error, stack);
      } finally {
        _eventOperations.remove(operation);
      }
    }();
    _eventOperations.add(operation);
    unawaited(operation);
  }

  Future<void> _onControllerEvent(AvesEntry entry, AvesVideoController controller, VideoEvent event) async {
    if (event is LagEvent) {
      debugPrint('Video lag detected: disposing video controllers, keeping only the one for entry=$entry');
      final otherControllers = List.of(_controllers)..remove(controller);
      _controllers.removeWhere(otherControllers.contains);
      await Future.forEach<AvesVideoController>(otherControllers, _disposeController);
    }
  }

  Future<void> _applyToAll(Future Function(AvesVideoController controller) action) {
    // local copy to prevent concurrent modification
    return Future.forEach<AvesVideoController>(List.of(_controllers), action);
  }

  Future<void> pauseAll() => _applyToAll((controller) => controller.pause());

  Future<void> muteAll(bool muted) => _applyToAll((controller) => controller.mute(muted));

  Future<void> _disposeController(AvesVideoController controller) async {
    await _statusSubscriptions.remove(controller)?.cancel();
    await _eventSubscriptions.remove(controller)?.cancel();
    await controller.dispose();
  }
}
