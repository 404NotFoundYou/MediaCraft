import 'package:aves/app_mode.dart';
import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/settings/defaults.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/filters/mime.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/widgets/collection/collection_page.dart';
import 'package:aves/widgets/discovery/explore_page.dart';
import 'package:aves/widgets/discovery/tools_page.dart';
import 'package:aves/widgets/filter_grids/albums_page.dart';
import 'package:aves/widgets/navigation/nav_bar/nav_bar.dart';
import 'package:aves/widgets/navigation/nav_item.dart';
import 'package:aves/widgets/navigation/tv_rail.dart';
import 'package:aves/widgets/settings/settings_page.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../common.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  test('新安装默认导航表达媒体浏览、组织、探索和工具四个职责', () {
    expect(
      SettingsDefaults.bottomNavigationActions,
      const [
        AvesNavItem(route: CollectionPage.routeName),
        AvesNavItem(route: AlbumListPage.routeName),
        AvesNavItem(route: ExplorePage.routeName),
        AvesNavItem(route: ToolsPage.routeName),
      ],
    );
  });

  test('已有底部导航配置优先于新默认布局', () {
    const userNavigation = [
      AvesNavItem(route: CollectionPage.routeName),
      AvesNavItem(route: SettingsPage.routeName),
    ];

    settings.bottomNavigationActions = userNavigation;

    expect(settings.bottomNavigationActions, userNavigation);
  });

  testWidgets('系统媒体选择模式不提供工具维护入口', (tester) async {
    final appMode = ValueNotifier<AppMode>(.pickMultipleMediaExternal);
    addTearDown(appMode.dispose);
    settings.bottomNavigationActions = SettingsDefaults.bottomNavigationActions;

    await tester.pumpWidget(_NavigationHarness(appMode: appMode));

    expect(find.text('媒体'), findsOneWidget);
    expect(find.text('相册'), findsOneWidget);
    expect(find.text('探索'), findsOneWidget);
    expect(find.text('工具'), findsNothing);

    appMode.value = .main;
    await tester.pump();

    expect(find.text('工具'), findsOneWidget);
  });

  testWidgets('TV 布局的系统媒体选择模式同样不提供工具维护入口', (tester) async {
    final appMode = ValueNotifier<AppMode>(.pickMultipleMediaExternal);
    addTearDown(appMode.dispose);
    settings.drawerTypeBookmarks = [];
    settings.drawerAlbumBookmarks = [];
    settings.drawerPageBookmarks = [ExplorePage.routeName, ToolsPage.routeName];

    await tester.pumpWidget(_TvRailHarness(appMode: appMode));

    expect(find.text('探索'), findsOneWidget);
    expect(find.text('工具'), findsNothing);

    appMode.value = .main;
    await tester.pump();

    expect(find.text('工具'), findsOneWidget);
  });

  testWidgets('大字体下四个底部入口仍可见，导航文字不会溢出', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final appMode = ValueNotifier<AppMode>(.main);
    addTearDown(appMode.dispose);
    settings.bottomNavigationActions = SettingsDefaults.bottomNavigationActions;

    await tester.pumpWidget(_NavigationHarness(appMode: appMode, textScaler: const TextScaler.linear(2)));

    expect(tester.takeException(), isNull);
    for (final label in ['媒体', '相册', '探索', '工具']) {
      expect(find.text(label).hitTestable(), findsOneWidget);
    }
  });

  testWidgets('工具路由在选择模式下回到选择图库并保持AppMode', (tester) async {
    late BuildContext context;
    final appMode = ValueNotifier<AppMode>(.main);
    addTearDown(appMode.dispose);
    final source = _FakeCollectionSource();
    final fixedFilters = <CollectionFilter>{MimeFilter.image};
    final collection = _FakeCollectionLens(fixedFilters);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<Settings>.value(value: settings),
          ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
          Provider<CollectionSource>.value(value: source),
          ListenableProvider<CollectionLens?>.value(value: collection),
        ],
        child: MaterialApp(
          home: Builder(
            builder: (buildContext) {
              context = buildContext;
              return const SizedBox();
            },
          ),
        ),
      ),
    );

    final exploreRoute = const AvesNavItem(route: ExplorePage.routeName).routeBuilder(context, topLevel: true) as MaterialPageRoute;
    final mainToolsRoute = const AvesNavItem(route: ToolsPage.routeName).routeBuilder(context, topLevel: true) as MaterialPageRoute;

    expect(exploreRoute.settings.name, ExplorePage.routeName);
    expect(exploreRoute.builder(context), isA<ExplorePage>());
    expect(mainToolsRoute.settings.name, ToolsPage.routeName);
    expect(mainToolsRoute.builder(context), isA<ToolsPage>());

    appMode.value = .pickFilteredMediaInternal;
    await tester.pump();
    final pickerToolsRoute = const AvesNavItem(route: ToolsPage.routeName).routeBuilder(context, topLevel: true) as MaterialPageRoute;
    final pickerPage = pickerToolsRoute.builder(context);

    expect(pickerToolsRoute.settings.name, CollectionPage.routeName);
    expect(pickerPage, isA<CollectionPage>());
    expect(pickerPage, isNot(isA<ToolsPage>()));
    expect((pickerPage as CollectionPage).filters, fixedFilters);
    expect(appMode.value, AppMode.pickFilteredMediaInternal);
  });
}

class _FakeCollectionSource extends Fake implements CollectionSource;

class _FakeCollectionLens extends Fake with ChangeNotifier implements CollectionLens {
  new(this.filters);

  @override
  final Set<CollectionFilter> filters;
}

class _NavigationHarness extends StatelessWidget {
  const new({required this.appMode, this.textScaler = TextScaler.noScaling});

  final ValueNotifier<AppMode> appMode;
  final TextScaler textScaler;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<Settings>.value(value: settings),
        ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
        Provider<MediaQueryData>.value(value: const MediaQueryData()),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: textScaler),
          child: child!,
        ),
        localizationsDelegates: const [
          ...AppLocalizations.localizationsDelegates,
          ...GlobalMaterialLocalizations.delegates,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(
          body: SizedBox(),
          bottomNavigationBar: AppBottomNavBar(
            events: Stream.empty(),
          ),
        ),
      ),
    );
  }
}

class _TvRailHarness extends StatelessWidget {
  const new({required this.appMode});

  final ValueNotifier<AppMode> appMode;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<Settings>.value(value: settings),
        ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
        Provider<CollectionSource>.value(value: _FakeCollectionSource()),
      ],
      child: MaterialApp(
        locale: const Locale('zh'),
        localizationsDelegates: const [
          ...AppLocalizations.localizationsDelegates,
          ...GlobalMaterialLocalizations.delegates,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: TvRail(controller: TvRailController()),
        ),
      ),
    );
  }
}
