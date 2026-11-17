import 'package:aves/model/settings/enums/accessibility_animations.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/common/action_mixins/feedback.dart';
import 'package:aves/widgets/common/bars/app_bar_title.dart';
import 'package:aves/widgets/common/basic/font_size_icon_theme.dart';
import 'package:aves/widgets/common/basic/insets.dart';
import 'package:aves/widgets/common/basic/popup/menu_row.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/settings/settings_action_delegate.dart';
import 'package:aves/widgets/settings/settings_definition.dart';
import 'package:aves/widgets/settings/settings_page.dart';
import 'package:aves/widgets/settings/settings_search_page_route.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_staggered_animations/flutter_staggered_animations.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class const SettingsMobilePage({super.key}) extends StatefulWidget {
  @override
  State<SettingsMobilePage> createState() => _SettingsMobilePageState();
}

class _SettingsMobilePageState extends State<SettingsMobilePage> with FeedbackMixin {
  @override
  Widget build(BuildContext context) {
    final animations = context.select<Settings, AccessibilityAnimations>((v) => v.accessibilityAnimations);
    return AvesScaffold(
      appBar: AppBar(
        title: InteractiveAppBarTitle(
          onTap: () => _goToSearch(context),
          child: Text(context.l10n.settingsPageTitle),
        ),
        actions: [
          IconButton(
            icon: const Icon(AIcons.search),
            onPressed: () => _goToSearch(context),
            tooltip: MaterialLocalizations.of(context).searchFieldLabel,
          ),
          PopupMenuButton<SettingsAction>(
            itemBuilder: (context) {
              return [
                PopupMenuItem(
                  value: SettingsAction.export,
                  child: MenuRow(text: context.l10n.settingsActionExport, icon: const Icon(AIcons.fileExport)),
                ),
                PopupMenuItem(
                  value: SettingsAction.import,
                  child: MenuRow(text: context.l10n.settingsActionImport, icon: const Icon(AIcons.fileImport)),
                ),
              ];
            },
            onSelected: (action) async {
              // wait for the popup menu to hide before proceeding with the action
              await Future.pause(animations.popUpAnimationDelay * timeDilation);
              SettingsActionDelegate().onActionSelected(context, action);
            },
            popUpAnimationStyle: animations.popUpAnimationStyle,
          ),
        ].map((v) => FontSizeIconTheme(child: v)).toList(),
      ),
      body: GestureAreaProtectorStack(
        child: SafeArea(
          bottom: false,
          child: AnimationLimiter(
            child: SettingsListView(
              children: [
                _SettingsSearchTile(onTap: () => _goToSearch(context)),
                const SizedBox(height: 8),
                ...SettingsPage.sections.map(
                  (section) => _SettingsSectionTile(section: section),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _goToSearch(BuildContext context) {
    Navigator.maybeOf(context)?.push(
      SettingsSearchPageRoute(context: context),
    );
  }
}

class _SettingsSearchTile extends StatelessWidget {
  const new({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: const Icon(AIcons.search),
        title: Text(MaterialLocalizations.of(context).searchFieldLabel),
        subtitle: Text(context.l10n.settingsPageTitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _SettingsSectionTile extends StatefulWidget {
  const new({required this.section});
  final SettingsSection section;

  @override
  State<_SettingsSectionTile> createState() => _SettingsSectionTileState();
}

class _SettingsSectionTileState extends State<_SettingsSectionTile> {
  late Future<List<SettingsTile>> _tiles;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _tiles = widget.section.tiles(context);
  }

  void _retry() => setState(() => _tiles = widget.section.tiles(context));

  @override
  Widget build(BuildContext context) {
    final section = widget.section;
    return FutureBuilder<List<SettingsTile>>(
      future: _tiles,
      builder: (context, snapshot) {
        final tiles = snapshot.data;
        final summary = tiles?.take(3).map((tile) => tile.title(context)).join(' · ');
        return Card(
          key: Key('section-${section.key}'),
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            minTileHeight: 76,
            leading: section.icon(context),
            title: Text(section.title(context)),
            subtitle: snapshot.hasError
                ? Text('${context.l10n.genericFailureFeedback} · ${context.l10n.retryButtonLabel}', style: TextStyle(color: Theme.of(context).colorScheme.error))
                : summary?.isNotEmpty == true
                ? Text(summary!, maxLines: 2, overflow: .ellipsis)
                : null,
            trailing: snapshot.hasError
                ? IconButton(onPressed: _retry, icon: const Icon(Icons.refresh), tooltip: context.l10n.retryButtonLabel)
                : tiles == null
                ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.chevron_right),
            onTap: snapshot.hasError
                ? _retry
                : tiles == null
                ? null
                : () => Navigator.maybeOf(context)?.push(
                    MaterialPageRoute(
                      settings: RouteSettings(name: '${SettingsPage.routeName}/${section.key}'),
                      builder: (context) => _SettingsSectionPage(section: section),
                    ),
                  ),
          ),
        );
      },
    );
  }
}

class _SettingsSectionPage extends StatefulWidget {
  const new({required this.section});
  final SettingsSection section;

  @override
  State<_SettingsSectionPage> createState() => _SettingsSectionPageState();
}

class _SettingsSectionPageState extends State<_SettingsSectionPage> {
  late Future<List<SettingsTile>> _tiles;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _tiles = widget.section.tiles(context);
  }

  @override
  Widget build(BuildContext context) {
    return AvesScaffold(
      appBar: AppBar(title: Text(widget.section.title(context))),
      body: SafeArea(
        bottom: false,
        child: FutureBuilder<List<SettingsTile>>(
          future: _tiles,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: .min,
                    children: [
                      Icon(Icons.error_outline, color: Theme.of(context).colorScheme.error),
                      const SizedBox(height: 12),
                      Text('${context.l10n.genericFailureFeedback}\n${snapshot.error}', textAlign: .center),
                      const SizedBox(height: 16),
                      FilledButton(onPressed: () => setState(() => _tiles = widget.section.tiles(context)), child: Text(context.l10n.retryButtonLabel)),
                    ],
                  ),
                ),
              );
            }
            final tiles = snapshot.data;
            if (tiles == null) return const Center(child: CircularProgressIndicator());
            return SettingsListView(children: tiles.map((tile) => tile.build(context)).toList());
          },
        ),
      ),
    );
  }
}
