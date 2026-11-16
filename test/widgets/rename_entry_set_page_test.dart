import 'dart:async';

import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/metadata/metadata_fetch_service.dart';
import 'package:aves/widgets/common/identity/buttons/outlined_button.dart';
import 'package:aves/widgets/dialogs/entry_editors/rename_entry_set_page.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../common.dart';
import '../fake/media_store_service.dart';
import '../fake/metadata_fetch_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _ControlledMetadataFetchService metadata;
  setUpAll(() async {
    await setUpAllServices();
    await getIt.unregister<MetadataFetchService>();
    metadata = _ControlledMetadataFetchService();
    getIt.registerSingleton<MetadataFetchService>(metadata);
  });
  setUp(() async {
    await setUpServices();
    metadata.getFieldsHandler = null;
  });
  tearDownAll(tearDownAllServices);

  Future<void> pumpPage(WidgetTester tester, String pattern, {int entryCount = 1}) async {
    settings.entryRenamingPattern = pattern;
    final entries = List.generate(entryCount, (index) => FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'photo$index'));
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<Settings>.value(value: settings),
          Provider<MediaQueryData>.value(value: const MediaQueryData(size: Size(400, 800))),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          home: RenameEntrySetPage(entries: entries),
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets('未知命名字段会显示错误并阻止批量重命名', (tester) async {
    await pumpPage(tester, '<nam>');

    expect(find.text('Unknown naming field: <nam>'), findsOneWidget);
    expect(tester.widget<AvesOutlinedButton>(find.byType(AvesOutlinedButton)).onPressed, isNull);
  });

  testWidgets('合法命名模式在预览完成后允许提交', (tester) async {
    await pumpPage(tester, '<name>_<counter,padding=2>');
    await tester.pump();

    expect(find.text('photo0_01'), findsOneWidget);
    expect(tester.widget<AvesOutlinedButton>(find.byType(AvesOutlinedButton)).onPressed, isNotNull);
  });

  testWidgets('连续修改模板会忽略旧预览，并立即处理后续预览错误', (tester) async {
    final firstPreview = Completer<Map<String, Object?>>();
    var callCount = 0;
    metadata.getFieldsHandler = (entry, fields) {
      callCount++;
      return callCount == 1 ? firstPreview.future : Future.error(StateError('later preview failed'));
    };
    await pumpPage(tester, '<hash,md5>', entryCount: 2);

    await tester.enterText(find.byType(TextField).first, '<name>');
    await tester.pump();
    firstPreview.complete(const {'hashMd5': 'obsolete'});
    await tester.pump();
    await tester.pump();

    expect(find.text('photo0'), findsNWidgets(2));
    expect(find.textContaining('later preview failed'), findsNothing);
    expect(tester.widget<AvesOutlinedButton>(find.byType(AvesOutlinedButton)).onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });
}

class _ControlledMetadataFetchService extends FakeMetadataFetchService {
  Future<Map<String, Object?>> Function(AvesEntry entry, Set<MetadataField> fields)? getFieldsHandler;

  @override
  Future<Map<String, Object?>> getFields(AvesEntry entry, Set<MetadataField> fields) {
    return getFieldsHandler?.call(entry, fields) ?? SynchronousFuture(const {'hashMd5': 'hash'});
  }
}
