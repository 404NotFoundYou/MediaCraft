import 'package:aves/model/device.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/theme/styles.dart';
import 'package:aves/widgets/about/policy_page.dart';
import 'package:aves/widgets/common/basic/link_chip.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/identity/aves_logo.dart';
import 'package:material_ui/material_ui.dart';

class AppReference extends StatelessWidget {
  static const avesGithub = 'https://github.com/deckerst/aves';
  static const avesFaq = '$avesGithub/wiki/FAQ';
  static const mediaCraftGithub = 'https://github.com/404NotFoundYou/MediaCraft';
  static const liquidGlassWidgetsGithub = 'https://github.com/sdegenaar/liquid_glass_widgets';

  const new({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        children: [
          _buildAvesLine(context),
          const SizedBox(height: 16),
          Wrap(
            alignment: .center,
            spacing: 16,
            crossAxisAlignment: .center,
            children: AppReference.buildLinks(context),
          ),
        ],
      ),
    );
  }

  Widget _buildAvesLine(BuildContext context) {
    final textScaler = MediaQuery.textScalerOf(context);
    final appName = context.l10n.appName;
    const fontSize = 20.0;
    final appTitleStyle = AStyles.appNameStyle(
      context,
      appName,
    ).copyWith(fontSize: fontSize, fontWeight: .normal);

    return Row(
      mainAxisSize: .min,
      spacing: 8,
      children: [
        AvesLogo(size: textScaler.scale(fontSize) * 1.3),
        Text(appName, style: appTitleStyle),
        Text(device.packageVersion, style: appTitleStyle),
      ],
    );
  }

  static List<Widget> buildLinks(BuildContext context) {
    final l10n = context.l10n;
    return [
      const LinkChip(
        leading: Icon(AIcons.github, size: 24),
        text: 'GitHub',
        urlString: AppReference.mediaCraftGithub,
      ),
      LinkChip(
        leading: const Icon(AIcons.legal, size: 22),
        text: l10n.aboutLinkLicense,
        urlString: '${AppReference.mediaCraftGithub}/blob/main/LICENSE',
      ),
      LinkChip(
        leading: const Icon(AIcons.privacy, size: 22),
        text: l10n.aboutLinkPolicy,
        onTap: () => _goToPolicyPage(context),
      ),
    ];
  }

  static List<Widget> buildSourceLinks() {
    return const [
      LinkChip(
        leading: Icon(AIcons.github, size: 24),
        text: 'Aves',
        urlString: avesGithub,
      ),
      LinkChip(
        leading: Icon(AIcons.legal, size: 22),
        text: 'Aves BSD-3-Clause',
        urlString: '$avesGithub/blob/develop/LICENSE',
      ),
      LinkChip(
        leading: Icon(AIcons.github, size: 24),
        text: 'liquid_glass_widgets',
        urlString: liquidGlassWidgetsGithub,
      ),
      LinkChip(
        leading: Icon(AIcons.legal, size: 22),
        text: 'liquid_glass_widgets MIT',
        urlString: '$liquidGlassWidgetsGithub/blob/main/LICENSE',
      ),
    ];
  }

  static void _goToPolicyPage(BuildContext context) {
    Navigator.maybeOf(context)?.push(
      MaterialPageRoute(
        settings: const RouteSettings(name: PolicyPage.routeName),
        builder: (context) => const PolicyPage(),
      ),
    );
  }
}
