import 'package:aves/app_mode.dart';
import 'package:aves/model/filters/trash.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/utils/file_utils.dart';
import 'package:aves/widgets/about/about_page.dart';
import 'package:aves/widgets/collection/collection_page.dart';
import 'package:aves/widgets/common/basic/draggable_scrollbar/notifications.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/identity/mediacraft_logo.dart';
import 'package:aves/widgets/duplicates/duplicate_page.dart';
import 'package:aves/widgets/explorer/page.dart';
import 'package:aves/widgets/navigation/drawer/app_drawer.dart';
import 'package:aves/widgets/navigation/nav_bar/nav_bar.dart';
import 'package:aves/widgets/navigation/nav_item.dart';
import 'package:aves/widgets/navigation/tv_rail.dart';
import 'package:aves/widgets/settings/settings_page.dart';
import 'package:aves/widgets/stats/stats_page.dart';
import 'package:collection/collection.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class ToolsPage extends StatelessWidget {
  static const routeName = '/tools';

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
    final trashedEntries = source.trashedEntries;
    final trashSize = trashedEntries.map((entry) => entry.sizeBytes).whereType<int>().sum;
    final trashDetail = settings.enableBin
        ? text(
            '${trashedEntries.length} 项 · ${formatFileSize(settings.avesLocale, trashSize, round: 0)}',
            '${trashedEntries.length} items · ${formatFileSize(settings.avesLocale, trashSize, round: 0)}',
          )
        : text('当前未启用，可在隐私设置中开启', 'Disabled; enable it in privacy settings');

    return ColoredBox(
      color: _background(context),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(22, 12, 22, 28),
        children: [
          const _BrandHeader(),
          const SizedBox(height: 26),
          Text(
            text('工具', 'Tools'),
            style: Theme.of(context).textTheme.displaySmall?.copyWith(
              color: _ink(context),
              fontWeight: FontWeight.w800,
              letterSpacing: 0,
            ),
          ),
          const SizedBox(height: 5),
          Text(
            text('让媒体库，保持轻盈有序。', 'Keep your media library light and organized.'),
            style: TextStyle(color: _muted(context)),
          ),
          const SizedBox(height: 20),
          _DuplicateHero(
            eyebrow: 'STORAGE CARE',
            title: text('相同的文件，\n留下一份就好。', 'Keep one copy\nof identical files.'),
            subtitle: text(
              '核对完全重复的照片与视频，确认后移入回收站。',
              'Review exact duplicate photos and videos before moving them to the bin.',
            ),
            buttonLabel: text('重复媒体清理', 'Clean duplicate media'),
            onTap: () => const AvesNavItem(route: DuplicatePage.routeName).goTo(context, topLevel: false),
          ),
          const SizedBox(height: 26),
          _SectionTitle(text('浏览与整理', 'Browse and organize')),
          const SizedBox(height: 10),
          _WhiteGroup(
            children: [
              _ToolTile(
                icon: AIcons.explorer,
                title: text('文件夹浏览', 'Folder browser'),
                subtitle: text('沿真实存储路径查看媒体', 'Browse media along real storage paths'),
                onTap: () => const AvesNavItem(route: ExplorerPage.routeName).goTo(context, topLevel: false),
              ),
              _ToolTile(
                icon: AIcons.stats,
                title: text('媒体统计', 'Media statistics'),
                subtitle: text('数量、容量与内容分布', 'Counts, storage and content distribution'),
                onTap: () => _openStats(context, source),
              ),
              _ToolTile(
                icon: AIcons.bin,
                title: text('回收站', 'Bin'),
                subtitle: trashDetail,
                enabled: settings.enableBin,
                onTap: () => _openBin(context, source),
              ),
            ],
          ),
          const SizedBox(height: 26),
          _SectionTitle(text('个性化与维护', 'Personalize and maintain')),
          const SizedBox(height: 10),
          _WhiteGroup(
            children: [
              _ToolTile(
                icon: AIcons.settings,
                title: text('设置', 'Settings'),
                subtitle: text('导航、查看器、视频、隐私和显示', 'Navigation, viewer, video, privacy and display'),
                onTap: () => const AvesNavItem(route: SettingsPage.routeName).goTo(context, topLevel: false),
              ),
              _ToolTile(
                icon: AIcons.fileExport,
                title: text('数据与个性化维护', 'Data and personalization'),
                subtitle: text('导入或导出动态相册、收藏和偏好', 'Import or export dynamic albums, favorites and settings'),
                onTap: () => const AvesNavItem(route: SettingsPage.routeName).goTo(context, topLevel: false),
              ),
              _ToolTile(
                icon: AIcons.info,
                title: text('关于与存储占用', 'About and storage usage'),
                subtitle: text('更新、缓存、许可和项目来源', 'Updates, cache, licenses and project sources'),
                onTap: () => const AvesNavItem(route: AboutPage.routeName).goTo(context, topLevel: false),
              ),
            ],
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

  void _openStats(BuildContext context, CollectionSource source) {
    Navigator.maybeOf(context)?.push(
      MaterialPageRoute(
        settings: const RouteSettings(name: StatsPage.routeName),
        builder: (_) => StatsPage(entries: source.visibleEntries, source: source),
      ),
    );
  }

  void _openBin(BuildContext context, CollectionSource source) {
    Navigator.maybeOf(context)?.push(
      MaterialPageRoute(
        settings: const RouteSettings(name: CollectionPage.routeName),
        builder: (_) => CollectionPage(source: source, filters: {TrashFilter.instance}),
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

class _DuplicateHero extends StatelessWidget {
  const new({required this.eyebrow, required this.title, required this.subtitle, required this.buttonLabel, required this.onTap});
  final String eyebrow;
  final String title;
  final String subtitle;
  final String buttonLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final strong = _strong(context);
    final strongForeground = _strongForeground(context);
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(color: _soft(context), borderRadius: BorderRadius.circular(MediaCraftUi.radiusLarge)),
      child: Stack(
        children: [
          PositionedDirectional(top: -8, end: -6, child: Icon(Icons.copy_all_outlined, size: 58, color: _accent(context).withValues(alpha: .34))),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                eyebrow,
                style: TextStyle(color: _accent(context), fontSize: 10, fontWeight: FontWeight.w700, letterSpacing: 1.5),
              ),
              const SizedBox(height: 14),
              Text(
                title,
                style: TextStyle(color: _ink(context), fontSize: 24, height: 1.3, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: 250,
                child: Text(subtitle, style: TextStyle(color: _muted(context), height: 1.6)),
              ),
              const SizedBox(height: 18),
              FilledButton.icon(
                onPressed: onTap,
                style: FilledButton.styleFrom(
                  backgroundColor: strong,
                  foregroundColor: strongForeground,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MediaCraftUi.radiusMedium)),
                ),
                icon: const Icon(Icons.arrow_forward, size: 18),
                label: Text(buttonLabel),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ToolTile extends StatelessWidget {
  const new({required this.icon, required this.title, required this.subtitle, required this.onTap, this.enabled = true});
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 3),
      enabled: enabled,
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
      onTap: enabled ? onTap : null,
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
