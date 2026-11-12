import 'dart:async';

import 'package:aves/model/entry/extensions/keys.dart';
import 'package:aves/model/filters/trash.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/services/common/image_op_events.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  test('移入回收站事件刷新回收站镜头并从普通镜头移除条目', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'duplicate');
    (mediaStoreService as FakeMediaStoreService).entries = {entry};
    final source = await _initSource();
    final regularLens = CollectionLens(source: source, allowStacks: false);
    final trashLens = CollectionLens(source: source, filters: {TrashFilter.instance}, allowStacks: false);
    addTearDown(regularLens.dispose);
    addTearDown(trashLens.dispose);

    expect(regularLens.sortedEntries, [entry]);
    expect(trashLens.sortedEntries, isEmpty);

    await source.updateAfterMove(
      todoEntries: {entry},
      moveType: MoveType.toBin,
      destinationAlbums: const {},
      movedOps: {
        MoveOpEvent(
          success: true,
          skipped: false,
          uri: entry.uri,
          newFields: const {
            EntryFields.trashed: true,
            EntryFields.trashPath: '/app/trash/duplicate.jpg',
          },
          deleted: false,
        ),
      },
    );
    await Future<void>.delayed(Duration.zero);

    expect(regularLens.sortedEntries, isEmpty);
    expect(trashLens.sortedEntries, [entry]);
  });
}

Future<MediaStoreSource> _initSource() async {
  final source = MediaStoreSource();
  final ready = Completer<void>();
  source.stateNotifier.addListener(() {
    if (source.isReady && !ready.isCompleted) ready.complete();
  });
  await source.init(scope: CollectionSource.fullScope);
  await ready.future;
  addTearDown(source.dispose);
  return source;
}
