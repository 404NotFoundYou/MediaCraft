import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:aves/widgets/duplicates/duplicate_page.dart';
import 'package:collection/collection.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../common.dart';
import '../../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(() async {
    await setUpServices();
    settings.enableBin = true;
  });
  tearDownAll(tearDownAllServices);

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

  @override
  Set<AvesEntry> get visibleEntries => entries;

  @override
  AvesEntry? getEntryById(int id) => entries.where((entry) => entry.id == id).firstOrNull;
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
