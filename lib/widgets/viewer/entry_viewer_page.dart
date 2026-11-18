import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/theme/colors.dart';
import 'package:aves/theme/media_viewer_theme.dart';
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
    final viewerTheme = mediaViewerTheme(context);
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
            child: AvesColorsProvider(
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
        ),
        backgroundColor: Navigator.canPop(context) ? Colors.transparent : EntryViewerPage.getBackground(context),
        resizeToAvoidBottomInset: false,
      ),
    );
  }
}
