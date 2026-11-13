import 'package:aves/theme/themes.dart';
import 'package:material_ui/material_ui.dart';

class const AvesFloatingBar({
  super.key,
  required final EdgeInsets margin,
  required final Widget Function(BuildContext context, Color backgroundColor, Widget? child) builder,
  final Widget? child,
}) extends StatefulWidget {
  static const borderRadius = BorderRadius.all(Radius.circular(24));

  @override
  State<AvesFloatingBar> createState() => _AvesFloatingBarState();
}

class _AvesFloatingBarState extends State<AvesFloatingBar> {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final backgroundColor = theme.appBarTheme.backgroundColor ?? Themes.firstLayerColor(context);
    return Container(
      foregroundDecoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: AvesFloatingBar.borderRadius,
      ),
      margin: widget.margin,
      child: widget.builder(
        context,
        backgroundColor,
        widget.child,
      ),
    );
  }
}
