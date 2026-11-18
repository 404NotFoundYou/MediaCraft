import 'package:aves/theme/themes.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/settings/language/locales.dart';
import 'package:material_ui/material_ui.dart';

ThemeData mediaViewerTheme(BuildContext context) {
  final baseTheme = Theme.of(context);
  final accentColor = baseTheme.brightness == Brightness.light ? baseTheme.colorScheme.primary : MediaCraftUi.forest;
  final theme = Themes.lightTheme(
    accentColor,
    true,
    context.knownLocale ?? KnownLocale.english,
  );

  return theme.copyWith(
    scaffoldBackgroundColor: MediaCraftUi.paper,
    canvasColor: MediaCraftUi.paper,
    cardColor: MediaCraftUi.paperElevated,
    cardTheme: theme.cardTheme.copyWith(color: MediaCraftUi.paperElevated),
    popupMenuTheme: theme.popupMenuTheme.copyWith(
      color: MediaCraftUi.paperElevated,
      iconColor: MediaCraftUi.ink,
    ),
    iconTheme: theme.iconTheme.copyWith(color: MediaCraftUi.ink),
  );
}
