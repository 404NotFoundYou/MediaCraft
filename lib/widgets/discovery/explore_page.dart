import 'package:aves/app_mode.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/filters/covered/location.dart';
import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/filters/mime.dart';
import 'package:aves/model/filters/type.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/widgets/collection/collection_page.dart';
import 'package:aves/widgets/common/basic/draggable_scrollbar/notifications.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/identity/mediacraft_logo.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:aves/widgets/filter_grids/countries_page.dart';
import 'package:aves/widgets/filter_grids/places_page.dart';
import 'package:aves/widgets/filter_grids/tags_page.dart';
import 'package:aves/widgets/map/map_page.dart';
import 'package:aves/widgets/navigation/drawer/app_drawer.dart';
import 'package:aves/widgets/navigation/nav_bar/nav_bar.dart';
import 'package:aves/widgets/navigation/nav_item.dart';
import 'package:aves/widgets/navigation/tv_rail.dart';
import 'package:aves/widgets/search/collection_search_page_route.dart';
import 'package:collection/collection.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class ExplorePage extends StatelessWidget {
  static const routeName = '/explore';

  const new({super.key});

  @override
  Widget build(BuildContext context) {
    final source = context.read<CollectionSource>();
    final body = StreamBuilder<Object>(
      stream: source.eventBus.on<Object>(),
      builder: (context, _) => _buildContent(context, source),
    );
    return _buildPage(context, body);
  }

  Widget _buildContent(BuildContext context, CollectionSource source) {
    String text(String zh, String en) => Localizations.localeOf(context).languageCode == 'zh' ? zh : en;
    final entries = source.visibleEntries;
    final locatedEntries = entries.where((entry) => entry.hasGps).toList();
    final heroEntry = source.sortedEntriesByDate.where((entry) => entry.hasGps).firstOrNull;
    final typeCards = <(String, IconData, CollectionFilter)>[
      (text('照片', 'Photos'), AIcons.image, MimeFilter.image),
      (text('视频', 'Videos'), AIcons.video, MimeFilter.video),
      (text('动态照片', 'Motion photos'), AIcons.motionPhoto, TypeFilter.motionPhoto),
      (text('全景', 'Panoramas'), AIcons.panorama, TypeFilter.panorama),
      ('RAW', AIcons.raw, TypeFilter.raw),
      (text('动画', 'Animated'), AIcons.animated, TypeFilter.animated),
      (text('慢动作', 'Slow motion'), AIcons.slowMotion, TypeFilter.slowMotion),
      (text('球面视频', 'Spherical videos'), AIcons.sphericalVideo, TypeFilter.sphericalVideo),
    ];

    return ColoredBox(
      color: _background(context),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(22, 12, 22, 28),
        children: [
          const _BrandHeader(),
          const SizedBox(height: 26),
          Text(
            text('探索', 'Explore'),
            style: Theme.of(context).textTheme.displaySmall?.copyWith(
              color: _ink(context),
              fontWeight: FontWeight.w800,
              letterSpacing: 0,
            ),
          ),
          const SizedBox(height: 5),
          Text(
            text('用不同视角，重访你的世界。', 'Revisit your world from a different angle.'),
            style: TextStyle(color: _muted(context)),
          ),
          const SizedBox(height: 20),
          _MapHero(
            entry: heroEntry,
            count: locatedEntries.length,
            title: text('照片里的足迹', 'Your photo map'),
            subtitle: locatedEntries.isEmpty ? text('还没有可显示的位置媒体', 'No located media is available yet') : text('${locatedEntries.length} 项带位置媒体', '${locatedEntries.length} located items'),
            emptyLabel: text('有位置信息的媒体会显示在这里', 'Located media will appear here'),
            onTap: () => _openMap(context, source),
          ),
          const SizedBox(height: 26),
          _SectionTitle(text('按地点浏览', 'Browse by place')),
          const SizedBox(height: 10),
          _WhiteGroup(
            children: [
              _ExploreTile(
                icon: AIcons.country,
                title: text('国家与地区', 'Countries and regions'),
                subtitle: text('继续下钻省、州和媒体集合', 'Drill down to states and media collections'),
                onTap: () => const AvesNavItem(route: CountryListPage.routeName).goTo(context, topLevel: false),
              ),
              _ExploreTile(
                icon: AIcons.place,
                title: text('地点', 'Places'),
                subtitle: text('查看已识别地点中的媒体', 'Open media grouped by recognized places'),
                onTap: () => const AvesNavItem(route: PlaceListPage.routeName).goTo(context, topLevel: false),
              ),
              _ExploreTile(
                icon: AIcons.tag,
                title: text('标签与分组', 'Tags and groups'),
                subtitle: text('用标签和多级分组组织内容', 'Organize media with tags and nested groups'),
                onTap: () => const AvesNavItem(route: TagListPage.routeName).goTo(context, topLevel: false),
              ),
            ],
          ),
          const SizedBox(height: 26),
          _SectionTitle(text('媒体类型', 'Media types')),
          const SizedBox(height: 10),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
              childAspectRatio: 1.65,
            ),
            itemCount: typeCards.length,
            itemBuilder: (context, index) {
              final (label, icon, filter) = typeCards[index];
              return _TypeCard(
                label: label,
                icon: icon,
                count: entries.where(filter.test).length,
                onTap: () => _openCollection(context, source, filter),
              );
            },
          ),
          const SizedBox(height: 26),
          _SectionTitle(text('组合筛选', 'Combined filters')),
          const SizedBox(height: 10),
          _SearchCard(
            title: text('高级搜索与筛选', 'Advanced search and filters'),
            subtitle: text(
              '组合日期、相册、地点、评分、元数据和内容凭证',
              'Combine dates, albums, places, ratings, metadata and credentials',
            ),
            onTap: () => Navigator.maybeOf(context)?.push(CollectionSearchPageRoute(context: context)),
          ),
        ],
      ),
    );
  }

  Widget _buildPage(BuildContext context, Widget body) {
    final canNavigate = context.select<ValueNotifier<AppMode>, bool>((v) => v.value.canNavigate);
    if (settings.useTvLayout) {
      return AvesScaffold(
        body: Row(
          children: [
            TvRail(controller: context.read<TvRailController>()),
            Expanded(child: SafeArea(child: body)),
          ],
        ),
      );
    }
    return AvesScaffold(
      drawer: canNavigate ? const AppDrawer() : null,
      backgroundColor: _background(context),
      body: SafeArea(bottom: false, child: body),
      bottomNavigationBar: canNavigate && settings.enableBottomNavigationBar ? const AppBottomNavBar(events: Stream<DraggableScrollbarEvent>.empty()) : null,
    );
  }

  void _openMap(BuildContext context, CollectionSource source) {
    Navigator.maybeOf(context)?.push(
      MaterialPageRoute(
        settings: const RouteSettings(name: MapPage.routeName),
        builder: (_) => MapPage(
          collection: CollectionLens(source: source, filters: {LocationFilter.located}),
        ),
      ),
    );
  }

  void _openCollection(BuildContext context, CollectionSource source, CollectionFilter filter) {
    Navigator.maybeOf(context)?.push(
      MaterialPageRoute(
        settings: const RouteSettings(name: CollectionPage.routeName),
        builder: (_) => CollectionPage(source: source, filters: {filter}),
      ),
    );
  }
}

class _BrandHeader extends StatelessWidget {
  const new();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const MediaCraftLogo(size: 34),
        const SizedBox(width: 9),
        Text(
          'MediaCraft',
          style: TextStyle(color: _ink(context), fontWeight: FontWeight.w800, fontSize: 18),
        ),
        const Spacer(),
        if (!settings.useTvLayout)
          Builder(
            builder: (context) => IconButton(
              tooltip: MaterialLocalizations.of(context).openAppDrawerTooltip,
              onPressed: () => Scaffold.of(context).openDrawer(),
              icon: Icon(Icons.menu, color: _accent(context)),
            ),
          ),
      ],
    );
  }
}

class _MapHero extends StatelessWidget {
  const new({required this.entry, required this.count, required this.title, required this.subtitle, required this.emptyLabel, required this.onTap});

  final AvesEntry? entry;
  final int count;
  final String title;
  final String subtitle;
  final String emptyLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _soft(context),
      borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: SizedBox(
          height: 210,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (entry != null)
                ThumbnailImage(
                  entry: entry!,
                  extent: 420,
                  devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
                  fit: BoxFit.cover,
                )
              else
                Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(AIcons.map, color: _accent(context), size: 46),
                      const SizedBox(height: 8),
                      Text(emptyLabel, style: TextStyle(color: _muted(context))),
                    ],
                  ),
                ),
              Positioned(
                left: 14,
                right: 14,
                bottom: 14,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: entry == null ? _card(context) : _heroOverlay(context),
                    borderRadius: BorderRadius.circular(MediaCraftUi.radiusMedium),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                title,
                                style: TextStyle(color: entry == null ? _ink(context) : _heroForeground(context), fontWeight: FontWeight.w800, fontSize: 17),
                              ),
                              const SizedBox(height: 2),
                              Text(subtitle, style: TextStyle(color: entry == null ? _muted(context) : _heroForeground(context).withValues(alpha: .82))),
                            ],
                          ),
                        ),
                        if (count > 0) Icon(AIcons.next, color: _heroForeground(context)),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ExploreTile extends StatelessWidget {
  const new({required this.icon, required this.title, required this.subtitle, required this.onTap});
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 3),
      leading: Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(color: _soft(context), borderRadius: const BorderRadius.all(Radius.circular(MediaCraftUi.radiusMedium))),
        child: Icon(icon, color: _accent(context), size: 21),
      ),
      title: Text(
        title,
        style: TextStyle(color: _ink(context), fontWeight: FontWeight.w700),
      ),
      subtitle: Text(subtitle, style: TextStyle(color: _muted(context))),
      trailing: Icon(AIcons.next, color: _muted(context)),
      onTap: onTap,
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const new(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text,
    style: TextStyle(color: _ink(context), fontWeight: FontWeight.w800, fontSize: 16),
  );
}

class _WhiteGroup extends StatelessWidget {
  const new({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: _card(context),
        border: Border.all(color: _outline(context)),
        borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
      ),
      child: Column(
        children: children.mapIndexed((index, child) => index == children.length - 1 ? child : Column(children: [child, const Divider(height: 1, indent: 66)])).toList(),
      ),
    );
  }
}

class _TypeCard extends StatelessWidget {
  const new({required this.label, required this.icon, required this.count, required this.onTap});
  final String label;
  final IconData icon;
  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _card(context),
      shape: RoundedRectangleBorder(
        side: BorderSide(color: _outline(context)),
        borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
        child: Padding(
          padding: const EdgeInsets.all(13),
          child: Row(
            children: [
              Icon(icon, color: _accent(context), size: 25),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: _ink(context), fontWeight: FontWeight.w700),
                    ),
                    Text('$count', style: TextStyle(color: _muted(context))),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SearchCard extends StatelessWidget {
  const new({required this.title, required this.subtitle, required this.onTap});
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _strong(context),
      borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge),
        child: Padding(
          padding: const EdgeInsets.all(17),
          child: Row(
            children: [
              Icon(AIcons.search, color: _strongForeground(context), size: 28),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(color: _strongForeground(context), fontWeight: FontWeight.w800, fontSize: 16),
                    ),
                    const SizedBox(height: 3),
                    Text(subtitle, style: TextStyle(color: _strongForeground(context).withValues(alpha: .78))),
                  ],
                ),
              ),
              Icon(AIcons.next, color: _strongForeground(context)),
            ],
          ),
        ),
      ),
    );
  }
}

bool _isDark(BuildContext context) => Theme.of(context).colorScheme.brightness == Brightness.dark;
Color _background(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.surface : MediaCraftUi.paper;
Color _card(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.surfaceContainer : MediaCraftUi.paperElevated;
Color _soft(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.surfaceContainerHigh : MediaCraftUi.sage;
Color _ink(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.onSurface : MediaCraftUi.ink;
Color _muted(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.onSurfaceVariant : MediaCraftUi.inkMuted;
Color _accent(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.primary : MediaCraftUi.forest;
Color _outline(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.outlineVariant : MediaCraftUi.outline;
Color _strong(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.primaryContainer : MediaCraftUi.forest;
Color _strongForeground(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.onPrimaryContainer : Colors.white;
Color _heroOverlay(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: .94) : MediaCraftUi.forestDark.withValues(alpha: .88);
Color _heroForeground(BuildContext context) => _isDark(context) ? Theme.of(context).colorScheme.onSurface : Colors.white;
