import 'dart:async';

import 'package:aves/model/db/db.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/media/media_store_service.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/db.dart';
import '../fake/media_store_service.dart';
import '../fake/storage_service.dart';

void main() {
  late _FailingDb db;
  late _FailingStore store;
  setUpAll(() async {
    await setUpAllServices();
    await getIt.unregister<LocalMediaDb>();
    await getIt.unregister<MediaStoreService>();
    db = _FailingDb();
    store = _FailingStore();
    getIt.registerSingleton<LocalMediaDb>(db);
    getIt.registerSingleton<MediaStoreService>(store);
  });
  setUp(() async {
    await setUpServices();
    db.failInit = false;
    db.failRead = false;
    store.failStream = false;
    store.entryStream = null;
  });
  tearDownAll(tearDownAllServices);

  test('初始数据库读取失败会显式结束等待，重试后可恢复图库', () async {
    final source = MediaStoreSource();
    addTearDown(source.dispose);
    db.failRead = true;
    await source.init(scope: CollectionSource.fullScope);
    await expectLater(source.waitUntilReady(), throwsStateError);
    expect(source.state, SourceState.failed);
    expect(source.loadError?.error, isA<StateError>());
    expect(source.loadedScope, isNull, reason: '失败的加载不能被后续入口误认为完整图库');

    db.failRead = false;
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'recovered');
    store.entries = {entry};
    await source.init(scope: CollectionSource.fullScope);
    await source.waitUntilReady();
    expect(source.loadError, isNull);
    expect(source.allEntries, {entry});
  });

  test('基础初始化失败不会永久缓存失败的 Future', () async {
    final source = MediaStoreSource();
    addTearDown(source.dispose);
    db.failInit = true;
    final before = db.initCalls;
    await expectLater(source.init(scope: CollectionSource.fullScope), throwsStateError);
    await expectLater(source.waitUntilReady(), throwsStateError);
    expect(source.state, SourceState.failed);

    db.failInit = false;
    await source.init(scope: CollectionSource.fullScope);
    await source.waitUntilReady();
    expect(db.initCalls, before + 2, reason: '重试必须真正重新打开数据库');
    expect(source.isReady, isTrue);
  });

  test('初始 MediaStore 流失败后仍可重新扫描', () async {
    final source = MediaStoreSource();
    addTearDown(source.dispose);
    store.failStream = true;
    await source.init(scope: CollectionSource.fullScope);
    await expectLater(source.waitUntilReady(), throwsStateError);
    expect(source.state, SourceState.failed);

    store.failStream = false;
    await source.init(scope: CollectionSource.fullScope);
    await source.waitUntilReady();
    expect(source.isReady, isTrue);
  });

  test('加载期间释放数据源会明确结束等待，而不是永久挂起', () async {
    final listened = Completer<void>();
    final controller = StreamController<AvesEntry>(onListen: listened.complete);
    store.entryStream = controller;
    final source = MediaStoreSource();
    await source.init(scope: CollectionSource.fullScope);
    await listened.future;
    final waiting = expectLater(source.waitUntilReady(), throwsStateError);
    source.dispose();
    await waiting;
    await controller.close();
    await expectLater(source.waitUntilReady(), throwsStateError);
  });
}

class _FailingDb extends FakeAvesDb {
  bool failInit = false, failRead = false;
  int initCalls = 0;

  @override
  Future<void> init() async {
    initCalls++;
    if (failInit) throw StateError('database open failed');
  }

  @override
  Future<Set<AvesEntry>> loadEntries({int? origin, String? directory}) async {
    if (failRead) throw StateError('database read failed');
    return {};
  }
}

class _FailingStore extends FakeMediaStoreService {
  bool failStream = false;
  StreamController<AvesEntry>? entryStream;

  @override
  Stream<AvesEntry> getEntries(Map<int?, int?> knownEntries, {String? directory}) => failStream ? Stream.error(StateError('media query failed')) : entryStream?.stream ?? super.getEntries(knownEntries, directory: directory);
}
