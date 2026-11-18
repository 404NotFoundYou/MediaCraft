import 'package:aves/app_mode.dart';
import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/colors.dart';
import 'package:aves/theme/media_viewer_theme.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/widgets/aves_app.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/viewer/info/info_page.dart';
import 'package:aves/widgets/viewer/overlay/bottom/video/video.dart';
import 'package:aves/widgets/viewer/overlay/bottom/viewer_buttons.dart';
import 'package:aves/widgets/viewer/video/conductor.dart';
import 'package:aves_video/aves_video.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(() async {
    await setUpServices();
    settings
      ..showOverlayThumbnailPreview = false
      ..videoControlActions = [];
  });
  tearDownAll(tearDownAllServices);

  for (final outerBrightness in Brightness.values) {
    testWidgets('媒体页在${outerBrightness.name}外层下仍使用明确浅色主题', (tester) async {
      await _pumpLightBoundary(
        tester,
        outerBrightness: outerBrightness,
        child: const _ThemeProbe(),
      );

      final probeContext = tester.element(find.byKey(const Key('media-theme-probe')));
      final theme = Theme.of(probeContext);
      final neutralProbe = tester.widget<ColoredBox>(find.byKey(const Key('neutral-probe')));
      final systemUiStyle = AvesApp.themeSystemOverlayStyle(theme);

      expect(theme.brightness, Brightness.light, reason: '媒体查看与编辑界面必须独立于应用外层明暗模式');
      expect(theme.scaffoldBackgroundColor, MediaCraftUi.paper);
      expect(theme.cardTheme.color, MediaCraftUi.paperElevated);
      expect(theme.popupMenuTheme.color, MediaCraftUi.paperElevated);
      expect(theme.colorScheme.onSurface, MediaCraftUi.ink, reason: '浅色面板必须使用深色前景，避免白字落在浅底');
      expect(theme.iconTheme.color, MediaCraftUi.ink);
      expect(systemUiStyle.statusBarIconBrightness, Brightness.dark);
      expect(neutralProbe.color, MediaCraftUi.inkMuted, reason: '语义颜色需要在浅色边界内重新计算，不能沿用深色祖先的白色 neutral');
    });
  }

  testWidgets('照片与视频操作栏使用统一不透明浅色面板', (tester) async {
    final image = FakeMediaStoreService.newImage('/storage/emulated/0/Pictures', 'light-viewer-image');
    final video = FakeMediaStoreService.newImage('/storage/emulated/0/Movies', 'light-viewer-video');
    addTearDown(image.dispose);
    addTearDown(video.dispose);

    await _pumpLightBoundary(
      tester,
      outerBrightness: Brightness.dark,
      child: Column(
        children: [
          SizedBox(
            width: 720,
            child: ViewerButtons(
              mainEntry: image,
              pageEntry: image,
              collection: null,
              scale: kAlwaysCompleteAnimation,
            ),
          ),
          SizedBox(
            width: 720,
            child: VideoControlOverlay(
              entry: video,
              controller: null,
              scale: kAlwaysCompleteAnimation,
              onActionSelected: (_) {},
            ),
          ),
        ],
      ),
    );

    final photoContext = tester.element(find.byType(ViewerButtonRowContent));
    final videoContext = tester.element(find.byType(VideoControlOverlay));
    expect(Theme.of(photoContext).brightness, Brightness.light, reason: '照片操作栏不能再次强制深色主题');
    expect(Theme.of(videoContext).brightness, Brightness.light, reason: '视频操作栏不能再次强制深色主题');
    expect(Theme.of(photoContext).colorScheme.onSurface, MediaCraftUi.ink);
    expect(Theme.of(videoContext).colorScheme.onSurface, MediaCraftUi.ink);

    expect(_panelColors(tester, find.byType(ViewerButtons)), contains(MediaCraftUi.paperElevated));
    expect(_panelColors(tester, find.byType(VideoControlOverlay)), isNotEmpty);
    expect(
      _panelColors(tester, find.byType(VideoControlOverlay)).every((color) => color == MediaCraftUi.paperElevated),
      isTrue,
      reason: '视频主面板和进度组件应使用统一实底，不能叠加半透明深色背景',
    );
  });

  testWidgets('详情页空状态继承浅色页面与浅色语义颜色', (tester) async {
    final entryNotifier = ValueNotifier<AvesEntry?>(null);
    final isScrollingNotifier = ValueNotifier(false);
    final pageInViewNotifier = ValueNotifier(1.0);
    addTearDown(entryNotifier.dispose);
    addTearDown(isScrollingNotifier.dispose);
    addTearDown(pageInViewNotifier.dispose);

    await _pumpLightBoundary(
      tester,
      outerBrightness: Brightness.dark,
      child: InfoPage(
        collection: null,
        entryNotifier: entryNotifier,
        isScrollingNotifier: isScrollingNotifier,
        pageInViewNotifier: pageInViewNotifier,
      ),
    );

    final scaffoldContext = tester.element(find.byType(AvesScaffold));
    expect(Theme.of(scaffoldContext).brightness, Brightness.light);
    expect(Theme.of(scaffoldContext).scaffoldBackgroundColor, MediaCraftUi.paper, reason: '详情页不能继续显示查看器的深色整页背景');
    expect(scaffoldContext.read<AvesColorsData>().neutral, MediaCraftUi.inkMuted);
  });
}

Future<void> _pumpLightBoundary(
  WidgetTester tester, {
  required Brightness outerBrightness,
  required Widget child,
}) async {
  final appMode = ValueNotifier<AppMode>(.main);
  final videoConductor = _FakeVideoConductor();
  addTearDown(appMode.dispose);

  await tester.binding.setSurfaceSize(const Size(800, 1000));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<Settings>.value(value: settings),
        ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
        Provider<VideoConductor>.value(value: videoConductor),
        Provider<MediaQueryData>.value(value: const MediaQueryData(size: Size(800, 1000))),
      ],
      child: MaterialApp(
        theme: ThemeData(brightness: outerBrightness),
        locale: const Locale('zh'),
        localizationsDelegates: const [
          ...AppLocalizations.localizationsDelegates,
          ...GlobalMaterialLocalizations.delegates,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            final viewerTheme = mediaViewerTheme(context);
            return Theme(
              data: viewerTheme,
              child: AvesColorsProvider(
                child: Scaffold(
                  body: Center(child: child),
                ),
              ),
            );
          },
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Set<Color> _panelColors(WidgetTester tester, Finder ancestor) {
  return tester
      .widgetList<DecoratedBox>(find.descendant(of: ancestor, matching: find.byType(DecoratedBox)))
      .map((widget) => widget.decoration)
      .whereType<BoxDecoration>()
      .map((decoration) => decoration.color)
      .nonNulls
      .where((color) => color == MediaCraftUi.viewerPanel || color == MediaCraftUi.viewerPanelSoft)
      .toSet();
}

class _ThemeProbe extends StatelessWidget {
  const new();

  @override
  Widget build(BuildContext context) {
    return Column(
      key: const Key('media-theme-probe'),
      mainAxisSize: MainAxisSize.min,
      children: [
        ColoredBox(
          key: const Key('neutral-probe'),
          color: context.watch<AvesColorsData>().neutral,
          child: const SizedBox(width: 10, height: 10),
        ),
      ],
    );
  }
}

class _FakeVideoConductor extends Fake implements VideoConductor {
  @override
  AvesVideoController? getController(AvesEntry entry) => null;
}
