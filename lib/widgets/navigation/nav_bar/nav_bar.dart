import 'dart:math';

import 'package:aves/app_mode.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/widgets/collection/collection_page.dart';
import 'package:aves/widgets/common/basic/draggable_scrollbar/notifications.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/extensions/media_query.dart';
import 'package:aves/widgets/navigation/nav_bar/floating.dart';
import 'package:aves/widgets/navigation/nav_item.dart';
import 'package:collection/collection.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class const AppBottomNavBar({
  super.key,
  required final Stream<DraggableScrollbarEvent> events,
  // collection loaded in the `CollectionPage`, if any
  final CollectionLens? currentCollection,
}) extends StatefulWidget {
  static const double barHeight = 68;

  static EdgeInsets getMargin(double safeBottomPadding) {
    return EdgeInsets.only(left: 12, top: 8, right: 12, bottom: max(8, safeBottomPadding + 4));
  }

  static double getHeightWithMargin(double safeBottomPadding) {
    return barHeight + getMargin(safeBottomPadding).vertical;
  }

  @override
  State<AppBottomNavBar> createState() => _AppBottomNavBarState();
}

class _AppBottomNavBarState extends State<AppBottomNavBar> {
  String? _lastRoute;

  @override
  void initState() {
    super.initState();
    _registerWidget(widget);
  }

  @override
  void didUpdateWidget(covariant AppBottomNavBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    _unregisterWidget(oldWidget);
    _registerWidget(widget);
  }

  @override
  void dispose() {
    _unregisterWidget(widget);
    super.dispose();
  }

  void _registerWidget(AppBottomNavBar widget) {
    widget.currentCollection?.filterChangeNotifier.addListener(_onCollectionFilterChanged);
  }

  void _unregisterWidget(AppBottomNavBar widget) {
    widget.currentCollection?.filterChangeNotifier.removeListener(_onCollectionFilterChanged);
  }

  @override
  Widget build(BuildContext context) {
    final items = context.select<Settings, List<AvesNavItem>>((v) => v.bottomNavigationActions);
    if (items.length < 2) return const SizedBox();

    final safeBottomPadding = context.select<MediaQueryData, double>((mq) => mq.safeBottomPadding);
    Widget child = FloatingNavBar(
      scrollController: PrimaryScrollController.of(context),
      events: widget.events,
      childHeight: AppBottomNavBar.getHeightWithMargin(safeBottomPadding),
      child: SafeArea(
        bottom: false,
        child: MediaQuery.removePadding(
          context: context,
          // `bottom` is computed beforehand and used in multiple places,
          // so we remove it from context to prevent additional padding
          removeBottom: true,
          child: Padding(
            padding: AppBottomNavBar.getMargin(safeBottomPadding),
            child: _buildNavigationBar(context, items),
          ),
        ),
      ),
    );

    final animate = context.select<Settings, bool>((v) => v.animate);
    if (animate) {
      child = Hero(
        tag: 'nav-bar',
        flightShuttleBuilder: (flightContext, animation, flightDirection, fromHeroContext, toHeroContext) {
          return MediaQuery.removeViewInsets(
            context: context,
            removeBottom: true,
            child: toHeroContext.widget,
          );
        },
        child: child,
      );
    }

    return child;
  }

  void _onCollectionFilterChanged() => setState(() {});

  Widget _buildNavigationBar(BuildContext context, List<AvesNavItem> items) {
    final colorScheme = Theme.of(context).colorScheme;
    final selectedIndex = _getCurrentIndex(context, items);
    return Material(
      color: Colors.transparent,
      child: Container(
        height: AppBottomNavBar.barHeight,
        padding: const EdgeInsets.all(6),
        decoration: ShapeDecoration(
          color: colorScheme.surfaceContainerHigh.withValues(alpha: .96),
          shadows: [
            BoxShadow(
              color: Colors.black.withValues(alpha: colorScheme.brightness == Brightness.dark ? .24 : .10),
              blurRadius: 18,
              offset: const Offset(0, 6),
            ),
          ],
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
            side: BorderSide(color: colorScheme.outlineVariant.withValues(alpha: .72)),
          ),
        ),
        child: Row(
          children: [
            for (var index = 0; index < items.length; index++)
              Expanded(
                child: _buildNavigationDestination(
                  context,
                  item: items[index],
                  selected: index == selectedIndex,
                  onPressed: () => _goTo(context, items, index),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavigationDestination(
    BuildContext context, {
    required AvesNavItem item,
    required bool selected,
    required VoidCallback onPressed,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    final foreground = selected ? colorScheme.primary : colorScheme.onSurfaceVariant;
    return Semantics(
      button: true,
      selected: selected,
      label: item.getText(context),
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(17),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          margin: const EdgeInsets.symmetric(horizontal: 2),
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
          decoration: BoxDecoration(
            color: selected ? colorScheme.primaryContainer.withValues(alpha: .72) : Colors.transparent,
            borderRadius: BorderRadius.circular(17),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              IconTheme(
                data: IconThemeData(color: foreground, size: 22),
                child: item.getIcon(context),
              ),
              const SizedBox(height: 2),
              Text(
                item.getText(context),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: foreground,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  int _getCurrentIndex(BuildContext context, List<AvesNavItem> items) {
    // current route may be null during navigation
    final currentRoute = context.currentRouteName ?? _lastRoute;
    _lastRoute = currentRoute;

    final currentItem = items.firstWhereOrNull((item) {
      final itemRoute = item.route;
      if (currentRoute != itemRoute) return false;

      switch (itemRoute) {
        case CollectionPage.routeName:
          final currentFilters = widget.currentCollection?.filters ?? {};
          return const SetEquality().equals(currentFilters, item.filters ?? {});
        default:
          return true;
      }
    });
    final currentIndex = currentItem != null ? items.indexOf(currentItem) : 0;
    return currentIndex;
  }

  void _goTo(BuildContext context, List<AvesNavItem> items, int index) {
    final item = items[index];
    item.goTo(context, topLevel: null);
  }
}

class const NavBarPaddingSliver({super.key}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final canNavigate = context.select<ValueNotifier<AppMode>, bool>((v) => v.value.canNavigate);
    final enableBottomNavigationBar = context.select<Settings, bool>((v) => v.enableBottomNavigationBar);
    final safeBottomPadding = context.select<MediaQueryData, double>((mq) => mq.safeBottomPadding);
    final showBottomNavigationBar = canNavigate && enableBottomNavigationBar;
    final height = showBottomNavigationBar ? AppBottomNavBar.getHeightWithMargin(safeBottomPadding) : safeBottomPadding;
    return SliverToBoxAdapter(
      child: SizedBox(height: height),
    );
  }
}
