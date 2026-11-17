import 'package:aves/model/entry/entry.dart';
import 'package:aves/l10n/l10n.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/widgets/editor/control_panel.dart';
import 'package:aves/widgets/editor/transform/controller.dart';
import 'package:aves_magnifier/aves_magnifier.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../common.dart';
import '../../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  testWidgets('活动编辑工具会阻止退出，取消工具后恢复退出', (tester) async {
    final fixture = _ControlPanelFixture();
    addTearDown(fixture.dispose);
    await fixture.pump(tester);

    expect(_popScope(tester).canPop, isTrue);

    fixture.actionNotifier.value = .transform;
    await tester.pumpAndSettle();

    expect(_popScope(tester).canPop, isFalse, reason: '返回操作应先退出当前编辑工具，不能直接离开编辑器');

    fixture.actionNotifier.value = null;
    await tester.pumpAndSettle();

    expect(_popScope(tester).canPop, isTrue);
  });
}

PopScope _popScope(WidgetTester tester) => tester.widget(find.byWidgetPredicate((widget) => widget is PopScope)) as PopScope;

class _ControlPanelFixture {
  final AvesEntry entry = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'editor');
  final ValueNotifier<EditorAction?> actionNotifier = ValueNotifier(null);
  final AvesMagnifierController magnifierController = AvesMagnifierController();
  late final TransformController transformController = TransformController(entry.displaySize);

  Future<void> pump(WidgetTester tester) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<DurationsData>.value(value: DurationsData()),
          Provider<AvesMagnifierController>.value(value: magnifierController),
          Provider<TransformController>.value(value: transformController),
        ],
        child: MaterialApp(
          locale: const Locale('zh'),
          localizationsDelegates: const [
            ...AppLocalizations.localizationsDelegates,
            ...GlobalMaterialLocalizations.delegates,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: EditorControlPanel(entry: entry, actionNotifier: actionNotifier),
            ),
          ),
        ),
      ),
    );
  }

  void dispose() {
    actionNotifier.dispose();
    magnifierController.dispose();
    transformController.dispose();
    entry.dispose();
  }
}
