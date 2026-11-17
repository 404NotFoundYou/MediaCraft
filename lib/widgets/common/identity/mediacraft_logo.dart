import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/colors.dart';
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

    return CircleAvatar(
      backgroundColor: theme.dividerColor,
      radius: size / 2,
      child: CircleAvatar(
        backgroundColor: const Color(0xfff7f8f4),
        radius: size / 2 - AvesBorder.curvedBorderWidth(context),
        child: child,
      ),
    );
  }
}

class MediaCraftLogoPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final unit = size.shortestSide / 100;
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 12 * unit
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = AColors.brandTeal;
    final mPath = Path()
      ..moveTo(18 * unit, 70 * unit)
      ..lineTo(18 * unit, 42 * unit)
      ..cubicTo(18 * unit, 32 * unit, 31 * unit, 32 * unit, 31 * unit, 42 * unit)
      ..lineTo(31 * unit, 70 * unit)
      ..moveTo(31 * unit, 42 * unit)
      ..cubicTo(31 * unit, 32 * unit, 44 * unit, 32 * unit, 44 * unit, 42 * unit)
      ..lineTo(44 * unit, 70 * unit);
    final cPath = Path()
      ..moveTo(82 * unit, 41 * unit)
      ..cubicTo(76 * unit, 33 * unit, 61 * unit, 34 * unit, 56 * unit, 47 * unit)
      ..cubicTo(50 * unit, 62 * unit, 60 * unit, 73 * unit, 72 * unit, 73 * unit)
      ..cubicTo(77 * unit, 73 * unit, 81 * unit, 70 * unit, 84 * unit, 66 * unit);
    canvas
      ..drawPath(mPath, stroke)
      ..drawPath(cPath, stroke)
      ..drawCircle(
        Offset(70 * unit, 53 * unit),
        4.5 * unit,
        Paint()
          ..style = PaintingStyle.fill
          ..color = AColors.brandAmber,
      );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
