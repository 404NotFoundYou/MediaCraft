import 'dart:async';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/keys.dart';
import 'package:aves/model/filters/trash.dart';
import 'package:aves/model/metadata/catalog.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/ref/bursts.dart';
import 'package:aves/ref/mime_types.dart';
import 'package:aves/services/common/image_op_events.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  CollectionLens lensFor(List<AvesEntry> entries) {
    final source = MediaStoreSource();
    final lens = CollectionLens(source: source, fixedSort: true, fixedSelection: entries);
    addTearDown(() {
      lens.dispose();
      source.dispose();
      for (final entry in entries) {
        entry.dispose();
      }
    });
    return lens;
  }

  test('burst stacks retain the original main slot and never merge across albums', () {
    settings.collectionBurstPatterns = {BurstPatterns.samsung};
    final second = FakeMediaStoreService.newImage('/album', '20260101_123456_002');
    final outside = FakeMediaStoreService.newImage('/other', '20260101_123456_002');
    final first = FakeMediaStoreService.newImage('/album', '20260101_123456_001');
    final tail = FakeMediaStoreService.newImage('/album', 'tail');
    final lens = lensFor([second, outside, first, tail]);

    expect(lens.sortedEntries.map((entry) => entry.id), [outside.id, first.id, tail.id]);
    final stack = lens.sortedEntries[1];
    expect(stack.stackedEntries, [first, second]);

    lens.setLiveQuery('20260101_123456_002');
    expect(lens.sortedEntries, [second, outside], reason: 'Search must still find an individual image inside a burst.');
    expect(stack.isDisposed, isTrue);
    expect(first.isDisposed, isFalse);
  });

  test('RAW stacks use the first developed image in the same album and keep all RAW members', () {
    final primary = FakeMediaStoreService.newImage('/album', 'frame');
    AvesEntry media(String extension, String mimeType) {
      final jpeg = FakeMediaStoreService.newImage('/album', 'frame');
      final entry = jpeg.copyWith(path: '/album/frame.$extension', sourceMimeType: mimeType);
      jpeg.dispose();
      return entry;
    }

    final alternative = media('heic', MimeTypes.heic);
    final other = FakeMediaStoreService.newImage('/other', 'frame');
    final dng = media('dng', MimeTypes.dng);
    final nef = media('nef', MimeTypes.nef);
    final lens = lensFor([dng, other, primary, alternative, nef]);

    expect(lens.sortedEntries.map((entry) => entry.id), [other.id, primary.id, alternative.id]);
    expect(lens.sortedEntries[1].stackedEntries, [primary, dng, nef], reason: 'A second RAW must not vanish when both match the same developed image.');
  });

  test('live title searches skip identical input but follow renames and new catalog titles', () {
    final photo = FakeMediaStoreService.newImage('/album', 'Street');
    final garden = FakeMediaStoreService.newImage('/album', 'garden');
    final lens = lensFor([photo, garden]);
    var refreshes = 0;
    lens.addListener(() => refreshes++);

    lens.setLiveQuery('street');
    lens.setLiveQuery('street');
    expect(lens.sortedEntries, [photo]);
    expect(refreshes, 1);
    lens.setLiveQuery('/^Str/');
    expect(lens.sortedEntries, [photo]);
    lens.setLiveQuery('-street');
    expect(lens.sortedEntries, [garden]);
    lens.setLiveQuery('id=${photo.contentId}');
    expect(lens.sortedEntries, [photo]);

    photo.path = '/album/Canal.jpg';
    lens.setLiveQuery('canal');
    expect(lens.sortedEntries, [photo], reason: 'Previously cached title text must not survive a rename.');
    photo.catalogMetadata = CatalogMetadata(id: photo.id, xmpTitle: 'Harbour');
    lens.refresh();
    expect(lens.sortedEntries, isEmpty);
    lens.setLiveQuery('harbour');
    expect(lens.sortedEntries, [photo]);
  });

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
