import 'dart:async';
import 'dart:ui' as ui;

import 'package:aves/image_providers/thumbnail_provider.dart';
import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:aves/services/media/media_fetch_service.dart';
import 'package:aves/widgets/duplicates/duplicate_page.dart';
import 'package:collection/collection.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../common.dart';
import '../../fake/media_fetch_service.dart';
import '../../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await setUpAllServices();
    await getIt.unregister<MediaFetchService>();
    getIt.registerSingleton<MediaFetchService>(_PendingThumbnailService());
  });
  setUp(() async {
    await setUpServices();
    settings.enableBin = true;
  });
  tearDownAll(tearDownAllServices);

  testWidgets('大量重复组按需加载，滚动后仍保留勾选及保留项', (tester) async {
    final fixture = _DuplicatePageFixture();
    final groups = [fixture.group];
    for (var i = 1; i < 120; i++) {
      final keeper = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'keeper$i', id: 2 * i + 1);
      final duplicate = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'duplicate$i', id: 2 * i + 2);
      fixture.source.entries.addAll([keeper, duplicate]);
      groups.add(
        DuplicateGroup(id: 'group$i', sizeBytes: 42, digest: 'digest$i', members: [DuplicateMember.fromEntry(keeper), DuplicateMember.fromEntry(duplicate)]),
      );
    }
    fixture.service.result = DuplicateScanResult(groups: groups, issues: const [], cancelled: false);
    await fixture.pump(tester, moveAction: (context, {required entries, required beforeMove}) async => false);
    expect(fixture.source.entryLookups, lessThan(20), reason: '首屏不应该查询尚未滚入视口的所有重复组');

    final scrollable = find.byType(Scrollable);
    final duplicateCheckbox = find.byWidgetPredicate((widget) => widget is Checkbox && widget.semanticLabel == '清理 ${fixture.deleteEntry.bestTitle}');
    await tester.scrollUntilVisible(duplicateCheckbox, 100, scrollable: scrollable);
    await Scrollable.ensureVisible(tester.element(duplicateCheckbox), alignment: .5);
    await tester.pumpAndSettle();
    await tester.tap(duplicateCheckbox);
    await tester.pumpAndSettle();
    final scrollPosition = tester.state<ScrollableState>(scrollable).position;
    final firstGroupOffset = scrollPosition.pixels;
    scrollPosition.jumpTo(firstGroupOffset + 1500);
    await tester.pumpAndSettle();
    expect(duplicateCheckbox, findsNothing);
    expect(find.text('已选 1 项'), findsOneWidget);

    scrollPosition.jumpTo(firstGroupOffset);
    await tester.pumpAndSettle();
    final keeperCheckbox = find.byWidgetPredicate((widget) => widget is Checkbox && widget.semanticLabel == '清理 ${fixture.keeperEntry.bestTitle}');
    expect(tester.widget<Checkbox>(keeperCheckbox).onChanged, isNull, reason: '保留项始终不能被勾选清理');
    expect(tester.widget<Checkbox>(duplicateCheckbox).value, isTrue, reason: '离开视口后重建卡片不能丢失勾选');
  });

  testWidgets('取消确认不会进入移动流程', (tester) async {
    final fixture = _DuplicatePageFixture();
    var moveCalls = 0;
    await fixture.pump(
      tester,
      moveAction: (context, {required entries, required beforeMove}) async {
        moveCalls++;
        return true;
      },
    );

    await fixture.selectDuplicate(tester);
    await fixture.openReview(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();

    expect(moveCalls, 0);
    expect(fixture.service.verificationCalls, 0);
  });

  testWidgets('最终复核失败时不移动任何文件', (tester) async {
    final fixture = _DuplicatePageFixture(
      verification: const DuplicateVerification(
        valid: false,
        issues: [DuplicateIssue(entryId: 2, uri: 'content://2', code: 'changed', message: '媒体内容已变化')],
      ),
    );
    var moved = false;
    await fixture.pump(
      tester,
      moveAction: (context, {required entries, required beforeMove}) async {
        if (!await beforeMove()) return false;
        moved = true;
        return true;
      },
    );

    await fixture.selectDuplicate(tester);
    await fixture.openReview(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.text('复核并继续'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(fixture.service.verificationCalls, 1);
    expect(moved, isFalse);
    expect(find.text('复核未通过'), findsOneWidget);
  });

  testWidgets('复核成功后移动所选文件并重新扫描', (tester) async {
    final fixture = _DuplicatePageFixture();
    Set<AvesEntry>? movedEntries;
    await fixture.pump(
      tester,
      moveAction: (context, {required entries, required beforeMove}) async {
        if (!await beforeMove()) return false;
        movedEntries = entries;
        return true;
      },
    );

    await fixture.selectDuplicate(tester);
    await fixture.openReview(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.text('复核并继续'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
    for (var i = 0; i < 10 && fixture.service.scanCalls < 2; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(fixture.service.verificationCalls, 1);
    expect(movedEntries, {fixture.deleteEntry});
    expect(fixture.service.scanCalls, 2);
  });
}

class _DuplicatePageFixture {
  new({
    this.verification = const DuplicateVerification(valid: true, issues: []),
  }) {
    source.entries = {keeperEntry, deleteEntry};
    service.result = DuplicateScanResult(groups: [group], issues: const [], cancelled: false);
    service.verification = verification;
  }

  final DuplicateVerification verification;
  final keeperEntry = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'keeper', id: 1, contentId: 1);
  final deleteEntry = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'delete', id: 2, contentId: 2);
  final source = _FakeCollectionSource();
  final service = _FakeDuplicateService();

  DuplicateGroup get group => DuplicateGroup(
    id: 'same-bytes',
    sizeBytes: 42,
    digest: 'digest',
    members: [DuplicateMember.fromEntry(keeperEntry), DuplicateMember.fromEntry(deleteEntry)],
  );

  Future<void> pump(WidgetTester tester, {required DuplicateMoveAction moveAction}) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<Settings>.value(value: settings),
          Provider<CollectionSource>.value(value: source),
        ],
        child: MaterialApp(
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
          home: DuplicatePage(source: source, service: service, moveAction: moveAction),
        ),
      ),
    );
    await tester.tap(find.text('开始扫描'));
    await tester.pumpAndSettle();
  }

  Future<void> selectDuplicate(WidgetTester tester) async {
    await tester.ensureVisible(find.byType(Checkbox).last);
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Checkbox).last);
    await tester.pumpAndSettle();
  }

  Future<void> openReview(WidgetTester tester) async {
    final label = find.byWidgetPredicate((widget) => widget is Text && (widget.data?.startsWith('复核 ') ?? false));
    expect(label, findsOneWidget);
    await tester.tap(label);
    await tester.pumpAndSettle();
  }
}

class _FakeCollectionSource extends Fake implements CollectionSource {
  late Set<AvesEntry> entries;
  int entryLookups = 0;

  @override
  Set<AvesEntry> get visibleEntries => entries;

  @override
  AvesEntry? getEntryById(int id) {
    entryLookups++;
    return entries.where((entry) => entry.id == id).firstOrNull;
  }
}

class _PendingThumbnailService extends FakeMediaFetchService {
  final _thumbnail = Completer<ui.Codec>();

  @override
  Future<T>? resumeLoading<T>(Object taskKey) => null;

  @override
  bool cancelThumbnail(Object taskKey) => false;

  @override
  Future<ui.Codec> getThumbnail({required bool decoded, required ThumbnailProviderKey request, ImageDecoderCallback? decode, Object? taskKey, int? priority}) => _thumbnail.future;
}

class _FakeDuplicateService extends DuplicateService {
  late DuplicateScanResult result;
  late DuplicateVerification verification;
  int scanCalls = 0;
  int verificationCalls = 0;

  @override
  Stream<DuplicateScanEvent> scan(Iterable<AvesEntry> entries) {
    scanCalls++;
    return Stream.value(DuplicateScanCompleted(result));
  }

  @override
  Future<DuplicateVerification> verifySelection({
    required DuplicateGroup group,
    required DuplicateMember keeper,
    required Iterable<DuplicateMember> deleteMembers,
  }) async {
    verificationCalls++;
    return verification;
  }
}
