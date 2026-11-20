import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/utils/android_file_utils.dart';
import 'package:aves_model/aves_model.dart';
import 'package:collection/collection.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter/foundation.dart';

final Favourites favourites = Favourites._private();

class Favourites with ChangeNotifier {
  Set<FavouriteRow> _rows = {};
  Set<int> _entryIds = {};

  new _private() {
    if (kFlutterMemoryAllocationsEnabled) ChangeNotifier.maybeDispatchObjectCreation(this);
  }

  Future<void> init() async {
    _rows = await localMediaDb.loadAllFavourites();
    _entryIds = _rows.map((row) => row.entryId).toSet();
  }

  int get count => _rows.length;

  Set<int> get all => Set.unmodifiable(_entryIds);

  bool isFavourite(AvesEntry entry) => _entryIds.contains(entry.id);

  FavouriteRow _entryToRow(AvesEntry entry) => FavouriteRow(entryId: entry.id);

  Future<void> add(Set<AvesEntry> entries) async {
    final newRows = entries.map(_entryToRow).toSet();

    await localMediaDb.addFavourites(newRows);
    _rows.addAll(newRows);
    _entryIds.addAll(newRows.map((row) => row.entryId));

    notifyListeners();
  }

  Future<void> removeEntries(Set<AvesEntry> entries) => removeIds(entries.map((entry) => entry.id).toSet());

  Future<void> removeIds(Set<int> entryIds) async {
    final removedRows = _rows.where((row) => entryIds.contains(row.entryId)).toSet();

    await localMediaDb.removeFavourites(removedRows);
    removedRows.forEach(_rows.remove);
    _entryIds.removeAll(entryIds);

    notifyListeners();
  }

  Future<void> clear() async {
    await localMediaDb.clearFavourites();
    _rows.clear();
    _entryIds.clear();

    notifyListeners();
  }

  // import/export

  Map<String, List<String>>? export(CollectionSource source) {
    final visibleEntries = source.visibleEntries;
    final ids = all;
    final paths = visibleEntries.where((entry) => ids.contains(entry.id)).map((entry) => entry.path).nonNulls.toSet();
    final byVolume = groupBy<String, StorageVolume?>(paths, androidFileUtils.getStorageVolume);
    final jsonMap = Map.fromEntries(
      byVolume.entries.map((kv) {
        final volume = kv.key?.path;
        if (volume == null) return null;
        final rootLength = volume.length;
        final relativePaths = kv.value.map((v) => v.substring(rootLength)).toList();
        return MapEntry(volume, relativePaths);
      }).nonNulls,
    );
    return jsonMap.isNotEmpty ? jsonMap : null;
  }

  Future<void> import(Object jsonMap, CollectionSource source) async {
    if (jsonMap is! Map<String, Object?>) throw const FormatException('Favourites backup must be an object');

    final visibleEntries = source.visibleEntries;
    final foundEntries = <AvesEntry>{};
    final missedPaths = <String>{};
    for (final MapEntry(key: volume, value: relativePaths) in jsonMap.entries) {
      if (relativePaths is! List || !relativePaths.every((path) => path is String)) {
        throw const FormatException('Invalid favourite paths');
      }
      for (final relativePath in relativePaths.cast<String>()) {
        final path = pContext.join(volume, relativePath);
        final entry = visibleEntries.firstWhereOrNull((entry) => entry.path == path);
        if (entry != null) {
          foundEntries.add(entry);
        } else {
          missedPaths.add(path);
        }
      }
    }
    if (foundEntries.isNotEmpty) await add(foundEntries);
    if (missedPaths.isNotEmpty) debugPrint('failed to import favourites with ${missedPaths.length} missed paths');
  }
}

@immutable
class FavouriteRow extends Equatable {
  final int entryId;

  @override
  List<Object?> get props => [entryId];

  const new({
    required this.entryId,
  });

  factory fromMap(Map map) {
    return FavouriteRow(
      entryId: map['id'] as int,
    );
  }

  Map<String, Object?> toMap() => {
    'id': entryId,
  };

  Map<String, Object?> toDbMap() => toMap();
}
