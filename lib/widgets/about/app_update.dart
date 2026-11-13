import 'package:aves/model/device.dart';
import 'package:aves/services/app_update_service.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/aves_app.dart';
import 'package:aves/widgets/common/action_mixins/feedback.dart';
import 'package:aves/widgets/common/identity/buttons/outlined_button.dart';
import 'package:material_ui/material_ui.dart';

class const AppUpdateChecker({super.key}) extends StatefulWidget {
  @override
  State<AppUpdateChecker> createState() => _AppUpdateCheckerState();
}

class _AppUpdateCheckerState extends State<AppUpdateChecker> with FeedbackMixin {
  bool _isChecking = false;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Center(
        child: AvesOutlinedButton(
          icon: _isChecking ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(AIcons.refresh),
          label: _isChecking ? '正在检查更新…' : '检查更新',
          onPressed: _isChecking ? null : _check,
        ),
      ),
    );
  }

  Future<void> _check() async {
    setState(() => _isChecking = true);
    try {
      final release = await AppUpdateService.checkForUpdate(currentVersion: device.packageVersion);
      if (!mounted) return;
      if (release == null) {
        showFeedback(context, FeedbackType.info, '当前已是最新版本（${device.packageVersion}）');
      } else {
        showFeedback(
          context,
          FeedbackType.info,
          '发现新版本 ${release.version}',
          SnackBarAction(
            label: '查看',
            onPressed: () => AvesApp.launchUrl(release.url.toString()),
          ),
        );
      }
    } catch (error, stack) {
      debugPrint('failed to check for updates: $error\n$stack');
      if (mounted) showFeedback(context, FeedbackType.warn, '检查更新失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _isChecking = false);
    }
  }
}
