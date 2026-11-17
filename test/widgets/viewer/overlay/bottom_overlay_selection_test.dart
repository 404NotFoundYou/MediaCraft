import 'package:aves/app_mode.dart';
import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/selection.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/widgets/viewer/overlay/bottom/bottom.dart';
import 'package:aves/widgets/viewer/overlay/bottom/selection_button.dart';
import 'package:aves/widgets/viewer/overlay/bottom/viewer_buttons.dart';
import 'package:aves/widgets/viewer/video/conductor.dart';
import 'package:aves_video/aves_video.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../common.dart';
import '../../../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(() async {
    await setUpServices();
    settings.showOverlayThumbnailPreview = false;
  });
  tearDownAll(tearDownAllServices);

  testWidgets('浏览与选择模式切换会立即替换查看器底部操作条', (tester) async {
    final entry = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'viewer');
    final selection = Selection<AvesEntry>(toSelectableItems: null)..select();
    final appMode = ValueNotifier<AppMode>(.main);
    final animationController = AnimationController(vsync: const TestVSync(), value: 1);
    final videoConductor = _FakeVideoConductor();
    addTearDown(() {
      animationController.dispose();
      appMode.dispose();
      selection.dispose();
      entry.dispose();
    });

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<Settings>.value(value: settings),
          ChangeNotifierProvider<Selection<AvesEntry>>.value(value: selection),
          ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
          Provider<DurationsData>.value(value: DurationsData.noAnimation()),
          Provider<MediaQueryData>.value(value: const MediaQueryData()),
          Provider<VideoConductor>.value(value: videoConductor),
        ],
        child: MaterialApp(
          locale: const Locale('zh'),
          localizationsDelegates: const [
            ...AppLocalizations.localizationsDelegates,
            ...GlobalMaterialLocalizations.delegates,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ViewerBottomOverlay(
              entries: [entry],
              index: 0,
              collection: null,
              animationController: animationController,
              availableSize: const Size(600, 800),
              multiPageController: null,
            ),
          ),
        ),
      ),
    );

    expect(find.byType(SelectionButton), findsOneWidget);
    expect(find.byType(ViewerButtons), findsNothing);

    selection.browse();
    await tester.pump();

    expect(find.byType(SelectionButton), findsNothing, reason: '结束选择后不能保留旧的选择操作条');
    expect(find.byType(ViewerButtons), findsOneWidget);
  });
}

class _FakeVideoConductor extends Fake implements VideoConductor {
  @override
  AvesVideoController? getController(AvesEntry entry) => null;
}
