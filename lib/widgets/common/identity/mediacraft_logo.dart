import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/colors.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/fx/borders.dart';
import 'package:aves/widgets/common/fx/colors.dart';
import 'package:aves_model/aves_model.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class const MediaCraftLogo({
  super.key,
  required final double size,
}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget child = CustomPaint(
      size: Size.square(size * .72),
      painter: MediaCraftLogoPainter(),
    );
    if (context.select<Settings, bool>((v) => v.themeColorMode == AvesThemeColorMode.monochrome)) {
      final tint = Color.lerp(theme.colorScheme.primary, Colors.white, .5)!;
      child = ColorFiltered(
        colorFilter: ColorFilter.mode(tint, BlendMode.modulate),
        child: ColorFiltered(
          colorFilter: MatrixColorFilters.greyscale,
          child: child,
        ),
      );
    }

    return Transform.flip(
      flipX: context.isRtl,
      child: CircleAvatar(
        backgroundColor: theme.dividerColor,
        radius: size / 2,
        child: CircleAvatar(
          backgroundColor: Colors.white,
          radius: size / 2 - AvesBorder.curvedBorderWidth(context),
          child: child,
        ),
      ),
    );
  }
}

class MediaCraftLogoPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final unit = size.shortestSide / 100;
    final paint = Paint()..style = PaintingStyle.fill;
    const cells = [
      (Rect.fromLTWH(40.4, 12, 19.2, 38.8), AColors.brandTeal),
      (Rect.fromLTWH(49.2, 40.4, 38.8, 19.2), AColors.brandCoral),
      (Rect.fromLTWH(40.4, 49.2, 19.2, 38.8), AColors.brandAmber),
      (Rect.fromLTWH(12, 40.4, 38.8, 19.2), Color(0xff6b7cff)),
    ];
    for (final (rect, color) in cells) {
      paint.color = color;
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(rect.left * unit, rect.top * unit, rect.width * unit, rect.height * unit),
          Radius.circular(9.6 * unit),
        ),
        paint,
      );
    }
    paint.color = const Color(0xfff4f8f7);
    canvas.drawCircle(Offset(50 * unit, 50 * unit), 6.5 * unit, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
