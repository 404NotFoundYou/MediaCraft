import 'dart:async';
import 'dart:io';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/analysis_controller.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/widgets/common/action_mixins/entry_storage.dart';
import 'package:aves/widgets/common/action_mixins/feedback.dart';
import 'package:aves/widgets/common/action_mixins/size_aware.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../common.dart';
import '../../../fake/media_store_service.dart';
import '../../../fake/storage_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  testWidgets('操作完成回调失败会结束等待并传播错误', (tester) async {
    final harness = _FeedbackHarness();
    late Future<void> operation;
    await tester.pumpWidget(
      ChangeNotifierProvider<Settings>.value(
        value: settings,
        child: MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () {
                operation = harness.showOpReport<int>(
                  context: context,
                  opStream: Stream.value(1).asBroadcastStream(),
                  onDone: (_) async => throw StateError('database sync failed'),
                );
              },
              child: const Text('start'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('start'));
    final expectation = expectLater(operation, throwsA(isA<StateError>()));
    await tester.pumpAndSettle();
    await expectation;
    expect(find.byType(ReportOverlay<int>), findsNothing);
  });

  testWidgets('平台流错误会传播而不是被静默吞掉', (tester) async {
    final harness = _FeedbackHarness();
    late Future<void> operation;
    await tester.pumpWidget(
      ChangeNotifierProvider<Settings>.value(
        value: settings,
        child: MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () {
                operation = harness.showOpReport<int>(
                  context: context,
                  opStream: Stream<int>.error(StateError('platform failed')).asBroadcastStream(),
                );
              },
              child: const Text('start'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('start'));
    final expectation = expectLater(operation, throwsA(isA<StateError>()));
    await tester.pumpAndSettle();
    await expectation;
  });

  test('业务同步失败后仍恢复媒体监控', () async {
    final source = _OperationSource();

    await expectLater(
      runWithMonitoringPaused<void>(source, () async => throw StateError('database sync failed')),
      throwsA(isA<StateError>()),
    );

    expect(source.canRefresh, isTrue);
    expect(source.pauseCalls, 1);
    expect(source.resumeCalls, 1);
  });

  test('多目标复制按各目标文件集合校验空间', () async {
    final storage = storageService as FakeStorageService;
    storage.freeSpaceByPath
      ..clear()
      ..addAll({
        FakeStorageService.primaryPath: 60,
        FakeStorageService.removablePath: 60,
      });
    final primaryEntry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/source', 'primary');
    final removableEntry = FakeMediaStoreService.newImage('${FakeStorageService.removablePath}Pictures/source', 'removable');

    final result = await _SizeHarness().checkFreeSpaceForMoves(
      _UnusedBuildContext(),
      {
        '${FakeStorageService.primaryPath}Pictures/target': {primaryEntry},
        '${FakeStorageService.removablePath}Pictures/target': {removableEntry},
      },
      MoveType.copy,
    );

    expect(result, isTrue, reason: '每个卷只需容纳分配给自己的 42 B 文件，而不是全部 84 B');
  });

  test('导出条目延迟出现时重试后返回真实条目', () async {
    final entry = FakeMediaStoreService.newImage('${FakeStorageService.primaryPath}Pictures/export', 'exported');
    final source = _DelayedExportSource(entry);

    final result = await refreshExportedEntries(
      source,
      {entry.uri},
      attempts: 3,
      retryDelay: Duration.zero,
    );

    expect(source.refreshCalls, 2);
    expect(result, {entry});
  });

  test('导出条目耗尽重试仍未出现时显式失败', () async {
    final source = _DelayedExportSource(null);

    await expectLater(
      refreshExportedEntries(
        source,
        {'content://missing'},
        attempts: 2,
        retryDelay: Duration.zero,
      ),
      throwsA(isA<StateError>()),
    );
    expect(source.refreshCalls, 2);
  });

  test('媒体源失败时导出刷新立即传播原始错误', () async {
    final failure = AsyncError(StateError('media source failed'), StackTrace.current);
    final source = _DelayedExportSource(null, loadFailure: failure);

    await expectLater(
      refreshExportedEntries(
        source,
        {'content://missing'},
        attempts: 3,
        retryDelay: Duration.zero,
      ),
      throwsA(isA<StateError>()),
    );
    expect(source.refreshCalls, 0);
  });

  test('文件名冲突检查只使用异步目录流', () async {
    final directory = _AsyncOnlyDirectory([_FakeEntity('/target/existing.jpg')]);

    final conflict = await hasFileNameConflict(directory, {'existing.jpg'});

    expect(conflict, isTrue);
    expect(directory.listCalls, 1);
    expect(directory.listSyncCalls, 0, reason: '慢速目录不能在 UI isolate 上同步枚举');
  });
}

class _FeedbackHarness with FeedbackMixin;

class _SizeHarness with SizeAwareMixin;

class _OperationSource extends Fake implements CollectionSource {
  bool refreshing = true;
  int pauseCalls = 0, resumeCalls = 0;

  @override
  bool get canRefresh => refreshing;

  @override
  void pauseMonitoring() {
    pauseCalls++;
    refreshing = false;
  }

  @override
  void resumeMonitoring() {
    resumeCalls++;
    refreshing = true;
  }
}

class _DelayedExportSource(
  final AvesEntry? entry, {
  final AsyncError? loadFailure,
}) extends Fake implements CollectionSource {
  int refreshCalls = 0;
  Set<AvesEntry> entries = {};

  @override
  AsyncError? get loadError => loadFailure;

  @override
  Set<AvesEntry> get allEntries => entries;

  @override
  Future<Set<String>> refreshUris(Set<String> changedUris, {AnalysisController? analysisController}) async {
    refreshCalls++;
    if (refreshCalls == 1) return changedUris;
    final exportedEntry = entry;
    if (exportedEntry != null) entries = {exportedEntry};
    return {};
  }
}

class _UnusedBuildContext extends Fake implements BuildContext;

class _AsyncOnlyDirectory(final List<FileSystemEntity> entities) extends Fake implements Directory {
  int listCalls = 0, listSyncCalls = 0;

  @override
  Future<bool> exists() async => true;

  @override
  Stream<FileSystemEntity> list({bool recursive = false, bool followLinks = true}) {
    listCalls++;
    return Stream.fromIterable(entities);
  }

  @override
  List<FileSystemEntity> listSync({bool recursive = false, bool followLinks = true}) {
    listSyncCalls++;
    throw StateError('synchronous directory listing is forbidden');
  }
}

class _FakeEntity(@override final String path) extends Fake implements FileSystemEntity;
