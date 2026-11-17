import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/filters/mime.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/widgets/map/scroller.dart';
import 'package:aves/widgets/common/providers/media_query_data_provider.dart';
import 'package:aves/widgets/common/search/route.dart';
import 'package:aves/widgets/search/collection_search_delegate.dart';
import 'package:aves/widgets/settings/settings_definition.dart';
import 'package:aves/widgets/settings/settings_mobile_page.dart';
import 'package:aves/widgets/settings/settings_search_delegate.dart';
import 'package:aves/theme/colors.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/widgets/viewer/controls/notifications.dart';
import 'package:event_bus/event_bus.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  testWidgets('地图信息延迟更新在页面销毁后不会访问已释放状态', (tester) async {
    final first = FakeMediaStoreService.newImage('/DCIM/Camera', 'first');
    final second = FakeMediaStoreService.newImage('/DCIM/Camera', 'second');
    final regionCollection = ValueNotifier<CollectionLens?>(null);
    final dotEntry = ValueNotifier<AvesEntry?>(first);
    final selectedIndex = ValueNotifier<int?>(0);
    addTearDown(regionCollection.dispose);
    addTearDown(dotEntry.dispose);
    addTearDown(selectedIndex.dispose);
    addTearDown(first.dispose);
    addTearDown(second.dispose);

    await tester.pumpWidget(
      _LocalizedApp(
        child: MapEntryScroller(
          regionCollectionNotifier: regionCollection,
          dotEntryNotifier: dotEntry,
          selectedIndexNotifier: selectedIndex,
          onTap: (_) {},
        ),
      ),
    );
    dotEntry.value = second;
    await tester.pumpWidget(const _LocalizedApp(child: SizedBox()));
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
  });

  testWidgets('设置搜索查询与尺寸变化复用同一份条目加载结果', (tester) async {
    final section = _CountingSection();
    final delegate = SettingsSearchDelegate(
      searchFieldLabel: '搜索设置',
      searchFieldStyle: const TextStyle(),
      sections: [section],
    )..query = '测试';
    addTearDown(delegate.dispose);

    Future<void> pump({required Size size, Locale locale = const Locale('zh')}) async {
      await tester.pumpWidget(
        _LocalizedApp(
          locale: locale,
          child: MediaQuery(
            data: MediaQueryData(size: size),
            child: Builder(builder: delegate.buildSuggestions),
          ),
        ),
      );
      await tester.pump();
    }

    await pump(size: const Size(360, 720));
    expect(section.loadCount, 1);
    expect(find.text('测试设置'), findsOneWidget);

    delegate.query = '测试设置';
    await pump(size: const Size(720, 360));
    expect(section.loadCount, 1, reason: '查询和窗口尺寸变化只应过滤、重排已有条目');
    expect(find.text('测试设置'), findsOneWidget);

    await pump(size: const Size(720, 360), locale: const Locale('en'));
    expect(section.loadCount, 2, reason: '语言变化时需要重新获取可能按语言或设备条件生成的条目');
  });

  testWidgets('快速重复选择搜索条件只关闭搜索页一次', (tester) async {
    final source = _SearchSource();
    final collection = _SearchCollection(source);
    final navigatorKey = GlobalKey<NavigatorState>();
    await tester.pumpWidget(_LocalizedApp(navigatorKey: navigatorKey, child: const Text('根页面')));

    navigatorKey.currentState!.push(MaterialPageRoute(builder: (_) => const Scaffold(body: Text('父页面'))));
    await tester.pumpAndSettle();

    final delegate = CollectionSearchDelegate(
      searchFieldLabel: '搜索媒体',
      searchFieldStyle: const TextStyle(),
      source: source,
      parentCollection: collection,
    );
    navigatorKey.currentState!.push(SearchPageRoute(delegate: delegate));
    await tester.pumpAndSettle();
    delegate.currentBody = SearchBody.suggestions;
    await tester.pumpAndSettle();

    final notificationContext = tester.element(find.byType(ListView).last);
    SelectFilterNotification(MimeFilter.image).dispatch(notificationContext);
    SelectFilterNotification(MimeFilter.video).dispatch(notificationContext);
    await tester.pump(const Duration(milliseconds: 150));
    await tester.pumpAndSettle();

    expect(collection.addFiltersCalls, 1);
    expect(find.text('父页面'), findsOneWidget, reason: '第二次点击不应留下另一个延迟pop并关闭父页面');
    expect(find.text('根页面'), findsNothing);
  });

  testWidgets('延迟关闭期间被弹层覆盖不会误退弹层或锁死搜索', (tester) async {
    final source = _SearchSource();
    final collection = _SearchCollection(source);
    final navigatorKey = GlobalKey<NavigatorState>();
    await tester.pumpWidget(_LocalizedApp(navigatorKey: navigatorKey, child: const Text('根页面')));

    navigatorKey.currentState!.push(MaterialPageRoute(builder: (_) => const Scaffold(body: Text('父页面'))));
    await tester.pumpAndSettle();
    final delegate = CollectionSearchDelegate(
      searchFieldLabel: '搜索媒体',
      searchFieldStyle: const TextStyle(),
      source: source,
      parentCollection: collection,
    );
    navigatorKey.currentState!.push(SearchPageRoute(delegate: delegate));
    await tester.pumpAndSettle();
    delegate.currentBody = SearchBody.suggestions;
    await tester.pumpAndSettle();

    SelectFilterNotification(MimeFilter.image).dispatch(tester.element(find.byType(ListView).last));
    navigatorKey.currentState!.push(MaterialPageRoute(builder: (_) => const Scaffold(body: Text('临时弹层'))));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));
    expect(find.text('临时弹层'), findsOneWidget, reason: '延迟关闭不得pop后来覆盖搜索页的路由');

    navigatorKey.currentState!.pop();
    await tester.pumpAndSettle();
    SelectFilterNotification(MimeFilter.video).dispatch(tester.element(find.byType(ListView).last));
    await tester.pump(const Duration(milliseconds: 150));
    await tester.pumpAndSettle();

    expect(collection.addFiltersCalls, 2, reason: '未执行的首次关闭必须释放选择门禁');
    expect(find.text('父页面'), findsOneWidget);
    expect(find.text('根页面'), findsNothing);
  });

  testWidgets('设置首页旋转和字体变化后保留滚动位置', (tester) async {
    Future<void> pump(MediaQueryData mediaQuery) async {
      await tester.pumpWidget(
        _SettingsApp(
          mediaQuery: mediaQuery,
        ),
      );
      await tester.pumpAndSettle();
    }

    await pump(const MediaQueryData(size: Size(360, 500)));
    await tester.drag(find.byType(ListView).first, const Offset(0, -320));
    await tester.pumpAndSettle();
    final before = tester.state<ScrollableState>(find.byType(Scrollable).first).position.pixels;
    expect(before, greaterThan(0));

    await pump(const MediaQueryData(size: Size(500, 360), textScaler: TextScaler.linear(1.5)));
    final after = tester.state<ScrollableState>(find.byType(Scrollable).first).position.pixels;
    expect(after, greaterThan(0), reason: '尺寸或字体变化不应通过替换Future把设置列表重建到顶部');
  });
}

class _LocalizedApp extends StatelessWidget {
  const new({required this.child, this.locale = const Locale('zh'), this.navigatorKey});

  final Widget child;
  final Locale locale;
  final GlobalKey<NavigatorState>? navigatorKey;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<Settings>.value(value: settings),
        Provider<DurationsData>.value(value: DurationsData.noAnimation()),
      ],
      child: AvesColorsProvider(
        child: MaterialApp(
          navigatorKey: navigatorKey,
          locale: locale,
          localizationsDelegates: const [
            ...AppLocalizations.localizationsDelegates,
            ...GlobalMaterialLocalizations.delegates,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQueryDataProvider(child: child!),
          home: Scaffold(body: child),
        ),
      ),
    );
  }
}

class _SettingsApp extends StatelessWidget {
  const new({required this.mediaQuery});

  final MediaQueryData mediaQuery;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<Settings>.value(value: settings),
        Provider<DurationsData>.value(value: DurationsData.noAnimation()),
        Provider<MediaQueryData>.value(value: mediaQuery),
      ],
      child: AvesColorsProvider(
        child: MaterialApp(
          locale: const Locale('zh'),
          localizationsDelegates: const [
            ...AppLocalizations.localizationsDelegates,
            ...GlobalMaterialLocalizations.delegates,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: MediaQuery(
            data: mediaQuery,
            child: const SettingsMobilePage(),
          ),
        ),
      ),
    );
  }
}

class _CountingSection extends SettingsSection {
  int loadCount = 0;

  @override
  String get key => 'test';

  @override
  Widget icon(BuildContext context) => const Icon(Icons.settings);

  @override
  String title(BuildContext context) => '测试分类';

  @override
  Future<List<SettingsTile>> tiles(BuildContext context) async {
    loadCount++;
    return [_TestTile()];
  }
}

class _TestTile extends SettingsTile {
  @override
  List<String> get settingKeys => const [];

  @override
  String title(BuildContext context) => '测试设置';

  @override
  Widget build(BuildContext context) => const ListTile(title: Text('测试设置'));
}

class _SearchSource extends Fake implements CollectionSource {
  @override
  final EventBus eventBus = EventBus();

  @override
  Set<AvesEntry> get visibleEntries => {};

  @override
  List<String> get rawAlbums => [];

  @override
  List<String> get sortedCountries => [];

  @override
  List<String> get sortedStates => [];

  @override
  List<String> get sortedPlaces => [];

  @override
  List<String> get sortedTags => [];

  @override
  String getStoredAlbumDisplayName(BuildContext? context, String album) => album;
}

class _SearchCollection extends Fake implements CollectionLens {
  new(this.source);

  @override
  final CollectionSource source;

  @override
  final Set<CollectionFilter> filters = {};

  int addFiltersCalls = 0;

  @override
  void addFilters(Set<CollectionFilter> newFilters) {
    addFiltersCalls++;
    filters.addAll(newFilters);
  }
}
