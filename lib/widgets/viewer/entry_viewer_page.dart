import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/theme/ui_tokens.dart';
import 'package:aves/widgets/aves_app.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/viewer/controls/controller.dart';
import 'package:aves/widgets/viewer/entry_viewer_stack.dart';
import 'package:aves/widgets/viewer/providers.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class const EntryViewerPage({
  super.key,
  // provided collection should be a new instance specifically created
  // for the `EntryViewerPage` widget, so it can be safely disposed here
  final CollectionLens? collection,
  required final AvesEntry initialEntry,
}) extends StatefulWidget {
  static const routeName = '/viewer';

  @override
  State<EntryViewerPage> createState() => _EntryViewerPageState();

  static const viewerSurface = MediaCraftUi.viewerSurface;

  static Color getBackground(BuildContext context) => viewerSurface;
}

class _EntryViewerPageState extends State<EntryViewerPage> {
  final ViewerController _viewerController = ViewerController();

  @override
  void dispose() {
    _viewerController.dispose();
    // provided collection should be a new instance specifically created
    // for the `EntryViewerPage` widget, so it can be safely disposed here
    widget.collection?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final collection = widget.collection;
    final viewerTheme = _viewerTheme(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: AvesApp.themeSystemOverlayStyle(viewerTheme),
      child: AvesScaffold(
        body: MultiProvider(
          providers: [
            ViewStateConductorProvider(),
            VideoConductorProvider(collection: collection),
            MultiPageConductorProvider(),
          ],
          child: Theme(
            data: viewerTheme,
            child: DefaultTextStyle.merge(
              style: const TextStyle(color: MediaCraftUi.viewerForeground),
              child: EntryViewerStack(
                collection: collection,
                initialEntry: widget.initialEntry,
                viewerController: _viewerController,
              ),
            ),
          ),
        ),
        backgroundColor: Navigator.canPop(context) ? Colors.transparent : EntryViewerPage.getBackground(context),
        resizeToAvoidBottomInset: false,
      ),
    );
  }

  ThemeData _viewerTheme(BuildContext context) {
    final baseTheme = Theme.of(context);
    final baseColors = baseTheme.colorScheme;
    const viewerSurfaceElevated = Color(0xff252c29);
    final viewerColors = baseColors.copyWith(
      brightness: Brightness.dark,
      surface: EntryViewerPage.viewerSurface,
      surfaceContainer: viewerSurfaceElevated,
      surfaceContainerHigh: const Color(0xff303835),
      onSurface: MediaCraftUi.viewerForeground,
      onSurfaceVariant: MediaCraftUi.viewerForegroundMuted,
      outline: MediaCraftUi.viewerOutline,
      outlineVariant: MediaCraftUi.viewerOutline,
    );
    return baseTheme.copyWith(
      brightness: Brightness.dark,
      colorScheme: viewerColors,
      canvasColor: EntryViewerPage.viewerSurface,
      scaffoldBackgroundColor: EntryViewerPage.viewerSurface,
      cardColor: viewerSurfaceElevated,
      appBarTheme: baseTheme.appBarTheme.copyWith(systemOverlayStyle: AvesApp.systemUIStyleForBrightness(Brightness.dark, EntryViewerPage.viewerSurface)),
      iconTheme: baseTheme.iconTheme.copyWith(color: MediaCraftUi.viewerForeground),
      textTheme: baseTheme.textTheme.apply(
        bodyColor: MediaCraftUi.viewerForeground,
        displayColor: MediaCraftUi.viewerForeground,
      ),
      popupMenuTheme: baseTheme.popupMenuTheme.copyWith(
        color: viewerSurfaceElevated,
        iconColor: MediaCraftUi.viewerForeground,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(MediaCraftUi.radiusMedium)),
        ),
      ),
    );
  }
}
