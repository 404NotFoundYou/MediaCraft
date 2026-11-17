import 'dart:math';

import 'package:aves/theme/icons.dart';
import 'package:material_ui/material_ui.dart';

class const MediaCraftBrowseSegments({
  super.key,
  required final Map<int, String> labels,
  required final int selected,
  required final ValueChanged<int> onChanged,
}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(color: colors.surfaceContainer, borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(4),
        child: Row(
          children: labels.entries.map((entry) {
            final isSelected = entry.key == selected;
            return Expanded(
              child: Semantics(
                selected: isSelected,
                button: true,
                child: Material(
                  color: isSelected ? (colors.brightness == Brightness.light ? Colors.white : colors.surfaceContainerHighest) : Colors.transparent,
                  borderRadius: BorderRadius.circular(8),
                  clipBehavior: .antiAlias,
                  child: InkWell(
                    onTap: () => onChanged(entry.key),
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: Text(
                          entry.value,
                          maxLines: 1,
                          overflow: .ellipsis,
                          style: TextStyle(fontSize: 13, color: isSelected ? colors.primary : colors.onSurfaceVariant, fontWeight: isSelected ? .w600 : .w400),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class const MediaCraftBrowseHeader({
  super.key,
  required final String title,
  required final String subtitle,
  required final String searchHint,
  required final VoidCallback onSearch,
  final Widget? trailing,
  final Widget? controls,
}) extends StatelessWidget {
  static double headingHeight(TextScaler scaler) => scaler.scale(28) * 1.25 + scaler.scale(12) * 1.5 + 12;

  static double searchHeight(TextScaler scaler) => max(44, scaler.scale(14) * 1.4 + 20);

  static double controlsHeight(TextScaler scaler) => max(44, scaler.scale(13) * 1.4 + 20);

  static double preferredHeight(TextScaler scaler, {bool hasControls = false}) {
    return headingHeight(scaler) + 12 + searchHeight(scaler) + (hasControls ? controlsHeight(scaler) + 12 : 0) + 16;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final scaler = MediaQuery.textScalerOf(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: .stretch,
        children: [
          SizedBox(
            height: headingHeight(scaler),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: .start,
                    mainAxisAlignment: .center,
                    children: [
                      Text(
                        title,
                        maxLines: 1,
                        overflow: .ellipsis,
                        style: theme.textTheme.headlineMedium?.copyWith(fontSize: 28, fontWeight: .w600, letterSpacing: -.6),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        subtitle,
                        maxLines: 1,
                        overflow: .ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(fontSize: 12, color: colors.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                ?trailing,
              ],
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: searchHeight(scaler),
            child: Material(
              color: colors.surfaceContainerLow,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: colors.outlineVariant),
              ),
              clipBehavior: .antiAlias,
              child: InkWell(
                onTap: onSearch,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 13),
                  child: Row(
                    children: [
                      Icon(AIcons.search, size: 19, color: colors.onSurfaceVariant),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          searchHint,
                          maxLines: 1,
                          overflow: .ellipsis,
                          style: theme.textTheme.bodyMedium?.copyWith(color: colors.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (controls != null) ...[
            const SizedBox(height: 12),
            SizedBox(height: controlsHeight(scaler), child: controls),
          ],
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}
