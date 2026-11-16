import 'package:aves/model/source/collection_source.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/identity/buttons/outlined_button.dart';
import 'package:material_ui/material_ui.dart';

class const CollectionLoadError({super.key, required final CollectionSource source}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: .min,
        children: [
          Text(context.l10n.collectionLoadFailure, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          AvesOutlinedButton(
            label: context.l10n.retryButtonLabel,
            onPressed: () async {
              try {
                await source.init(scope: source.targetScope);
              } catch (error, stack) {
                // The source publishes failed state again so this retry remains available.
                debugPrint('Media reload failed: $error\n$stack');
              }
            },
          ),
        ],
      ),
    );
  }
}
