import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/common/basic/font_size_icon_theme.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/identity/aves_filter_chip.dart';
import 'package:aves/widgets/common/identity/empty.dart';
import 'package:aves_model/aves_model.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class const HiddenItemsPage({super.key}) extends StatelessWidget {
  static const routeName = '/settings/hidden_items';

  static const List<String> settingKeys = [
    SettingKeys.hiddenFiltersKey,
    SettingKeys.deactivatedHiddenFiltersKey,
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AvesScaffold(
      appBar: AppBar(
        automaticallyImplyLeading: !settings.useTvLayout,
        title: Text(l10n.settingsHiddenItemsPageTitle),
      ),
      body: SafeArea(
        child: Selector<Settings, Set<CollectionFilter>>(
          selector: (context, s) => settings.hiddenFilters.toSet(),
          builder: (context, activatedHiddenFilters, child) {
            return Selector<Settings, Set<CollectionFilter>>(
              selector: (context, s) => settings.deactivatedHiddenFilters.toSet(),
              builder: (context, deactivatedHiddenFilters, child) {
                final allHiddenFilters = {
                  ...activatedHiddenFilters,
                  ...deactivatedHiddenFilters,
                };
                if (allHiddenFilters.isEmpty) {
                  return Column(
                    crossAxisAlignment: .start,
                    children: [
                      _Banner(
                        bannerText: context.l10n.settingsHiddenFiltersBanner,
                        activeCount: 0,
                        totalCount: 0,
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.all(8),
                          child: EmptyContent(
                            icon: AIcons.hide,
                            text: context.l10n.settingsHiddenFiltersEmpty,
                          ),
                        ),
                      ),
                    ],
                  );
                }

                final filterList = allHiddenFilters.toList()..sort();
                return ListView(
                  padding: const EdgeInsets.only(bottom: 16),
                  children: [
                    _Banner(
                      bannerText: context.l10n.settingsHiddenFiltersBanner,
                      activeCount: activatedHiddenFilters.length,
                      totalCount: allHiddenFilters.length,
                    ),
                    ...filterList.map((filter) {
                      void onRemove(CollectionFilter filter) => settings.changeFilterVisibility({filter}, true);
                      final active = activatedHiddenFilters.contains(filter);
                      return Card(
                        margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                          child: Row(
                            children: [
                              Expanded(
                                child: LayoutBuilder(
                                  builder: (context, constraints) => AvesFilterChip(
                                    filter: filter,
                                    maxWidth: constraints.maxWidth,
                                    onTap: onRemove,
                                    onRemove: onRemove,
                                    onLongPress: (context, filter, tapPosition) {
                                      AvesFilterChip.showDefaultLongPressMenu(context, filter, tapPosition, canNavigate: false);
                                    },
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Switch(
                                    value: active,
                                    onChanged: (v) => settings.activateHiddenFilter(filter, v),
                                  ),
                                  Text(
                                    active ? '隐藏中' : '已暂停',
                                    style: Theme.of(context).textTheme.labelSmall,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    }),
                  ],
                );
              },
            );
          },
        ),
      ),
    );
  }
}

class const _Banner({
  required final String bannerText,
  required final int activeCount,
  required final int totalCount,
}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const FontSizeIconTheme(child: Icon(AIcons.info)),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(bannerText),
                  if (totalCount > 0) ...[
                    const SizedBox(height: 8),
                    Text(
                      '$activeCount/$totalCount 条规则正在隐藏内容',
                      style: Theme.of(context).textTheme.labelMedium,
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
