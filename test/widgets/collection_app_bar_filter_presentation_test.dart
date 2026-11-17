import 'dart:async';

import 'package:aves/app_mode.dart';
import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/selection.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/filters/covered/stored_album.dart';
import 'package:aves/model/filters/favourite.dart';
import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/filters/mime.dart';
import 'package:aves/model/filters/rating.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/theme/colors.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/theme/themes.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/widgets/collection/app_bar.dart';
import 'package:aves/widgets/collection/filter_bar.dart';
import 'package:aves/widgets/common/providers/query_provider.dart';
import 'package:aves/widgets/settings/language/locales.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../common.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  final album = StoredAlbumFilter('/storage/emulated/0/DCIM/Camera', 'Camera');
  final rating = RatingFilter(4);

  test('媒体首页不重复显示已由快捷控件表达的筛选条件', () {
    final filters = <CollectionFilter>{
      MimeFilter.image,
      FavouriteFilter.instance,
    };

    final displayed = collectionAppBarFilterBarFilters(filters, showBrowseHeader: true);

    expect(displayed, isEmpty);
  });

  test('媒体首页仍显示没有其他控件表达的筛选范围', () {
    final filters = <CollectionFilter>{
      MimeFilter.video,
      FavouriteFilter.instance,
      album,
      rating,
    };

    final displayed = collectionAppBarFilterBarFilters(filters, showBrowseHeader: true);

    expect(displayed, {album, rating});
  });

  test('没有首页快捷控件时保留完整真实筛选范围', () {
    final filters = <CollectionFilter>{
      MimeFilter.image,
      FavouriteFilter.instance,
      album,
    };

    final displayed = collectionAppBarFilterBarFilters(filters, showBrowseHeader: false);

    expect(displayed, same(filters), reason: '没有首页快捷控件时不能隐藏真实范围');
  });

  test('点击照片视频或收藏不会让首页 AppBar 新增筛选栏高度', () {
    double filterBarHeight(Set<CollectionFilter> filters) {
      final displayed = collectionAppBarFilterBarFilters(filters, showBrowseHeader: true);
      return displayed.isEmpty ? 0 : FilterBar.preferredHeight;
    }

    expect(filterBarHeight(<CollectionFilter>{}), 0);
    expect(filterBarHeight({MimeFilter.image}), 0);
    expect(filterBarHeight({MimeFilter.video}), 0);
    expect(filterBarHeight({FavouriteFilter.instance}), 0);
    expect(filterBarHeight({album}), FilterBar.preferredHeight);
  });

  testWidgets('首页快捷筛选可以连续切换且控件状态和高度保持同步', (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 852));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final source = await _initSource();
    final collection = CollectionLens(source: source, allowStacks: false);
    final selection = Selection<AvesEntry>(toSelectableItems: null);
    final appMode = ValueNotifier<AppMode>(.main);
    final appBarHeight = ValueNotifier<double>(0);
    final scrollController = ScrollController();
    addTearDown(collection.dispose);
    addTearDown(selection.dispose);
    addTearDown(appMode.dispose);
    addTearDown(appBarHeight.dispose);
    addTearDown(scrollController.dispose);

    Future<void> pumpHarness(double textScale) async {
      final mediaQuery = MediaQueryData(size: const Size(412, 852), textScaler: TextScaler.linear(textScale));
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<Settings>.value(value: settings),
            ChangeNotifierProvider<Selection<AvesEntry>>.value(value: selection),
            ChangeNotifierProvider<CollectionLens>.value(value: collection),
            Provider<CollectionSource>.value(value: source),
            ListenableProvider<ValueNotifier<AppMode>>.value(value: appMode),
            Provider<DurationsData>.value(value: DurationsData.noAnimation()),
            Provider<MediaQueryData>.value(value: mediaQuery),
          ],
          child: MaterialApp(
            locale: const Locale('zh'),
            theme: Themes.lightTheme(MediaCraftUi.forest, false, const KnownLocale(bcp47: 'zh', endonym: '简体中文')),
            localizationsDelegates: const [
              ...AppLocalizations.localizationsDelegates,
              ...GlobalMaterialLocalizations.delegates,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(data: mediaQuery, child: child!),
            home: AvesColorsProvider(
              child: QueryProvider(
                child: Scaffold(
                  body: CustomScrollView(
                    controller: scrollController,
                    slivers: [
                      CollectionAppBar(
                        appBarHeightNotifier: appBarHeight,
                        scrollController: scrollController,
                        collection: collection,
                      ),
                      const SliverToBoxAdapter(child: SizedBox(height: 1200)),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    double actualSliverExtent() => tester.widget<SliverPersistentHeader>(find.byType(SliverPersistentHeader)).delegate.maxExtent;

    await pumpHarness(1);

    final initialHeight = appBarHeight.value;
    FilterChip favouriteChip() => tester.widget<FilterChip>(find.widgetWithText(FilterChip, '收藏夹'));

    expect(favouriteChip().selected, isFalse);
    await tester.tap(find.text('收藏夹'));
    await tester.pumpAndSettle();
    expect(collection.filters, contains(FavouriteFilter.instance));
    expect(favouriteChip().selected, isTrue);
    expect(appBarHeight.value, initialHeight);

    await tester.tap(find.text('收藏夹'));
    await tester.pumpAndSettle();
    expect(collection.filters, isNot(contains(FavouriteFilter.instance)));
    expect(favouriteChip().selected, isFalse);
    expect(appBarHeight.value, initialHeight);

    await tester.tap(find.text('图像'));
    await tester.pumpAndSettle();
    expect(collection.filters, contains(MimeFilter.image));
    expect(collection.filters, isNot(contains(MimeFilter.video)));
    expect(appBarHeight.value, initialHeight);

    await tester.tap(find.text('视频'));
    await tester.pumpAndSettle();
    expect(collection.filters, contains(MimeFilter.video));
    expect(collection.filters, isNot(contains(MimeFilter.image)));
    expect(appBarHeight.value, initialHeight);

    await tester.tap(find.text('全部'));
    await tester.pumpAndSettle();
    expect(collection.filters, isNot(contains(MimeFilter.image)));
    expect(collection.filters, isNot(contains(MimeFilter.video)));
    expect(appBarHeight.value, initialHeight);

    collection.addFilters({MimeFilter.image, FavouriteFilter.instance, album});
    selection.select();
    await tester.pumpAndSettle();
    expect(find.text('图像'), findsOneWidget, reason: '选择态没有首页分段控件，必须显示图像范围');
    expect(find.text('收藏夹'), findsOneWidget, reason: '选择态没有收藏快捷控件，必须显示收藏范围');
    expect(find.text('Camera'), findsOneWidget, reason: '选择态必须继续显示额外相册范围');

    selection.browse();
    appMode.value = .pickFilteredMediaInternal;
    await tester.pumpAndSettle();
    expect(find.text('图像'), findsOneWidget, reason: 'Picker 必须显示调用方限定的图像范围');
    expect(find.text('收藏夹'), findsOneWidget, reason: 'Picker 必须显示调用方限定的收藏范围');
    expect(find.text('Camera'), findsOneWidget, reason: 'Picker 必须显示调用方限定的相册范围');

    await pumpHarness(1.3);
    expect(appBarHeight.value, actualSliverExtent(), reason: '字体缩放后手势命中和滚动条使用的高度必须与真实 Sliver 高度一致');
  });
}

Future<MediaStoreSource> _initSource() async {
  final source = MediaStoreSource();
  final ready = Completer<void>();
  source.stateNotifier.addListener(() {
    if (source.isReady && !ready.isCompleted) ready.complete();
  });
  await source.init(scope: CollectionSource.fullScope);
  await ready.future;
  addTearDown(source.dispose);
  return source;
}
