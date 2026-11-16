import 'dart:async';

import 'package:aves/model/db/db.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/analysis_controller.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/events.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/media/media_fetch_service.dart';
import 'package:aves/services/media/media_store_service.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/db.dart';
import '../fake/media_fetch_service.dart';
import '../fake/media_store_service.dart';
import '../fake/storage_service.dart';

void main() {
  late _FaultDb db;
  late _ControlledFetchService fetch;
  late _ControlledMediaStoreService mediaStore;
  setUpAll(() async {
    await setUpAllServices();
    await getIt.unregister<LocalMediaDb>();
    db = _FaultDb();
    getIt.registerSingleton<LocalMediaDb>(db);
    await getIt.unregister<MediaFetchService>();
    fetch = _ControlledFetchService();
    getIt.registerSingleton<MediaFetchService>(fetch);
    await getIt.unregister<MediaStoreService>();
    mediaStore = _ControlledMediaStoreService();
    getIt.registerSingleton<MediaStoreService>(mediaStore);
  });
  setUp(() async {
    await setUpServices();
    db.failInsert = false;
    db.insertCalls = 0;
    fetch.response = null;
    fetch.started = null;
    (mediaFetchService as FakeMediaFetchService).entries = {};
  });
  tearDownAll(tearDownAllServices);

  test('数据库临时写入失败后仍可再次刷新，无需重启图库', () async {
    final source = _LookupProbeSource();
    await _initSource(source);
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'new');
    (mediaFetchService as FakeMediaFetchService).entries = {entry};

    db.failInsert = true;
    await expectLater(source.refreshUris({entry.uri}), throwsStateError);
    expect(source.isReady, isTrue, reason: '瞬时数据库故障不能锁死所有后续媒体变更');
    expect(entry.isDisposed, isTrue, reason: '写入失败的临时媒体对象也必须释放');

    db.failInsert = false;
    (mediaFetchService as FakeMediaFetchService).entries = {entry.copyWith()};
    expect(await source.refreshUris({entry.uri}), isEmpty);
    expect(source.allEntries.single.uri, entry.uri);
    expect(source.isReady, isTrue);
  });

  test('批量变更只读取固定次数的全库快照，避免每个 URI 都遍历全库', () async {
    final entries = List.generate(30, (i) => FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'image$i'));
    (mediaStoreService as FakeMediaStoreService).entries = entries.toSet();
    final source = _LookupProbeSource();
    await _initSource(source);
    (mediaFetchService as FakeMediaFetchService).entries = entries.map((entry) => entry.copyWith()).toSet();
    source.snapshotReads = 0;

    expect(await source.refreshUris(entries.map((entry) => entry.uri).toSet()), isEmpty);
    expect(source.snapshotReads, lessThanOrEqualTo(2), reason: '成本应随图库和变更数相加增长，而不是相乘');
    expect(source.allEntries.length, entries.length);
  });

  test('数据源释放后不再接收设置事件', () async {
    final source = _LookupProbeSource();
    settings.basicLocale = const Locale('fr');
    await Future<void>.delayed(Duration.zero);
    expect(source.localeCallbacks, 1);
    source.dispose();

    settings.basicLocale = const Locale('en');
    await Future<void>.delayed(Duration.zero);
    expect(source.localeCallbacks, 1, reason: '已释放的数据源不能被全局设置流继续持有');
  });

  test('媒体查询尚未返回时退出，不会向已释放的数据源加入文件', () async {
    final source = _LookupProbeSource();
    await _initSource(source);
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'pending');
    fetch.response = Completer<AvesEntry?>();
    fetch.started = Completer<void>();
    final additions = <EntryAddedEvent>[];
    final subscription = source.eventBus.on<EntryAddedEvent>().listen(additions.add);
    addTearDown(subscription.cancel);

    final refresh = source.refreshUris({entry.uri});
    await fetch.started!.future;
    source.dispose();
    fetch.response!.complete(entry);

    expect(await refresh, isEmpty);
    await Future<void>.delayed(Duration.zero);
    expect(source.allEntries, isEmpty);
    expect(additions, isEmpty, reason: '已退出的图库不能收到查询结果写回');
    expect(db.insertCalls, 0);
    expect(entry.isDisposed, isTrue);
  });

  test('初始媒体流尚未结束时退出，会取消订阅并释放未接管条目', () async {
    final source = _LookupProbeSource();
    final listened = Completer<void>();
    final controller = StreamController<AvesEntry>(onListen: listened.complete);
    mediaStore.entryStream = controller;
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'initial-pending');

    await source.init(scope: CollectionSource.fullScope);
    await listened.future;
    controller.add(entry);
    await Future<void>.delayed(Duration.zero);
    source.dispose();
    await controller.close();
    await Future<void>.delayed(Duration.zero);

    expect(entry.isDisposed, isTrue);
    expect(source.allEntries, isEmpty);
    expect(db.insertCalls, 0, reason: '退出后的初始媒体流不能继续写入数据库');
  });

  test('初始媒体流结束但分析仍在途时退出，不会提前销毁条目', () async {
    final source = _DelayedInitialSource();
    final listened = Completer<void>();
    final controller = StreamController<AvesEntry>(onListen: listened.complete);
    mediaStore.entryStream = controller;
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'initial-analysis');

    await source.init(scope: CollectionSource.fullScope);
    await listened.future;
    controller.add(entry);
    await controller.close();
    await source.analysisStarted.future;
    source.dispose();
    expect(entry.isDisposed, isFalse);

    source.releaseAnalysis.complete();
    await Future<void>.delayed(Duration.zero);
    expect(entry.isDisposed, isTrue);
  });

  test('重复 contentId 仍刷新原先的首个条目，索引优化不改变选择语义', () async {
    final first = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'first', contentId: 7);
    final second = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'second', contentId: 7);
    (mediaStoreService as FakeMediaStoreService).entries = {first, second};
    final source = _SelectionProbeSource();
    await _initSource(source);
    fetch.entries = {first.copyWith(path: '${FakeStorageService.primaryPath}Pictures/test/changed.jpg')};

    await source.refreshUris({first.uri});
    expect(source.refreshed, {first});
  });

  test('分析尚未结束时释放，立即解绑全局事件且不再发布状态', () async {
    final source = _DelayedAnalysisSource();
    await _initSource(source);
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/test', 'analyzing');
    fetch.entries = {entry};
    source.releaseAnalysis = Completer<void>();
    final states = <SourceState>[];
    final progress = <ProgressEvent>[];
    source.stateNotifier.addListener(() => states.add(source.state));
    source.progressNotifier.addListener(() => progress.add(source.progressNotifier.value));
    final refresh = source.refreshUris({entry.uri});
    await source.analysisStarted.future;
    source.dispose();
    final statesBeforeRelease = List.of(states);
    final localeCallbacks = source.localeCallbacks;

    settings.basicLocale = const Locale('ja');
    await Future<void>.delayed(Duration.zero);
    expect(source.localeCallbacks, localeCallbacks, reason: '全局订阅必须立即解绑，不能等待外部异步任务返回');
    expect(entry.isDisposed, isFalse, reason: '正在使用的媒体对象应在分析退出后再销毁');
    source.releaseAnalysis!.complete();
    await refresh;

    expect(states, statesBeforeRelease, reason: '退出后到达的异步回调不能发布 ready 状态');
    expect(progress, isEmpty);
    expect(entry.isDisposed, isTrue);
    expect(source.allEntries, isEmpty);
  });

  testWidgets('失败队列有限重试并保留 URI，下一次媒体事件可以恢复', (tester) async {
    final source = _RetryProbeSource();
    addTearDown(source.dispose);
    source.onStoreChanged('content://media/first');
    for (var i = 0; i < 4; i++) {
      await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    }
    expect(source.calls, hasLength(4));
    await tester.pump(const Duration(seconds: 20));
    expect(source.calls, hasLength(4), reason: '持续故障不能产生无限后台重试');

    source.fail = false;
    source.onStoreChanged('content://media/second');
    await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    expect(source.calls.last, {'content://media/first', 'content://media/second'}, reason: '失败批次不能因为队列清空而丢失');
    await tester.pump(const Duration(seconds: 20));
    expect(source.calls, hasLength(5));
  });

  testWidgets('释放数据源会取消尚未执行的变更刷新', (tester) async {
    final source = _RetryProbeSource();
    source.onStoreChanged('content://media/first');
    source.dispose();
    await tester.pump(const Duration(seconds: 20));
    expect(source.calls, isEmpty, reason: '已退出的图库或小组件不能被定时器继续刷新');
  });

  testWidgets('加载结束或恢复监控后会继续处理被暂缓的变更', (tester) async {
    final source = _RetryProbeSource()..fail = false;
    addTearDown(source.dispose);
    source.state = SourceState.loading;
    source.defer = true;
    source.onStoreChanged('content://media/first');
    for (var i = 0; i < 4; i++) {
      await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    }
    source.defer = false;
    source.state = SourceState.ready;
    await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    expect(source.calls, hasLength(5), reason: '长时间初始加载不能让期间的媒体变更永久滞留');

    source.defer = true;
    source.pauseMonitoring();
    source.onStoreChanged('content://media/second');
    for (var i = 0; i < 4; i++) {
      await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    }
    source.defer = false;
    source.resumeMonitoring();
    await tester.pump(ADurations.mediaContentChangeDebounceDelay);
    expect(source.calls, hasLength(10));
    expect(source.calls.last, {'content://media/second'});
  });
}

Future<void> _initSource(MediaStoreSource source) async {
  addTearDown(source.dispose);
  final ready = Completer<void>();
  source.stateNotifier.addListener(() {
    if (source.isReady && !ready.isCompleted) ready.complete();
  });
  await source.init(scope: CollectionSource.fullScope);
  await ready.future;
}

class _FaultDb extends FakeAvesDb {
  bool failInsert = false;
  int insertCalls = 0;

  @override
  Future<void> insertEntries(Set<AvesEntry> entries) async {
    insertCalls++;
    if (failInsert) throw StateError('database write failed');
  }
}

class _ControlledFetchService extends FakeMediaFetchService {
  Completer<AvesEntry?>? response;
  Completer<void>? started;

  @override
  Future<AvesEntry?> getEntry(String uri, String? mimeType, {bool allowUnsized = false}) async {
    final pending = response;
    if (pending != null) {
      if (started != null && !started!.isCompleted) started!.complete();
      return await pending.future;
    }
    return super.getEntry(uri, mimeType, allowUnsized: allowUnsized);
  }
}

class _ControlledMediaStoreService extends FakeMediaStoreService {
  StreamController<AvesEntry>? entryStream;

  @override
  void reset() {
    super.reset();
    entryStream = null;
  }

  @override
  Stream<AvesEntry> getEntries(Map<int?, int?> knownEntries, {String? directory}) => entryStream?.stream ?? super.getEntries(knownEntries, directory: directory);
}

class _SelectionProbeSource extends MediaStoreSource {
  Set<AvesEntry>? refreshed;

  @override
  Future<void> refreshEntries(Set<AvesEntry> entries, Set<EntryDataType> dataTypes) async {
    refreshed = entries;
  }
}

class _DelayedInitialSource extends _LookupProbeSource {
  final analysisStarted = Completer<void>();
  final releaseAnalysis = Completer<void>();

  @override
  Future<void> analyze(AnalysisController? analysisController, {Set<AvesEntry>? entries}) async {
    analysisStarted.complete();
    await releaseAnalysis.future;
  }
}

class _DelayedAnalysisSource extends _LookupProbeSource {
  final analysisStarted = Completer<void>();
  Completer<void>? releaseAnalysis;

  @override
  Future<void> analyze(AnalysisController? analysisController, {Set<AvesEntry>? entries}) async {
    final pending = releaseAnalysis;
    if (pending == null) return super.analyze(analysisController, entries: entries);
    analysisStarted.complete();
    await pending.future;
    state = SourceState.ready;
    setProgress(done: 1, total: 1);
  }
}

class _LookupProbeSource extends MediaStoreSource {
  int snapshotReads = 0, localeCallbacks = 0;

  @override
  Set<AvesEntry> get allEntries {
    snapshotReads++;
    return super.allEntries;
  }

  @override
  void invalidateStoredAlbumDisplayNames() {
    localeCallbacks++;
    super.invalidateStoredAlbumDisplayNames();
  }
}

class _RetryProbeSource extends MediaStoreSource {
  final List<Set<String>> calls = [];
  bool fail = true, defer = false;

  @override
  Future<Set<String>> refreshUris(Set<String> changedUris, {AnalysisController? analysisController}) async {
    calls.add(Set.of(changedUris));
    if (fail) throw StateError('temporary failure');
    if (defer) return changedUris;
    return {};
  }
}
