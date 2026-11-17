import 'dart:async';

import 'package:aves/widgets/about/about_page.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:material_ui/material_ui.dart';

enum MediaCraftHelpTopic { automaticExport, hardwareAcceleration }

Future<void> showMediaCraftHelp(BuildContext context, MediaCraftHelpTopic topic) {
  final l10n = context.l10n;
  final chinese = Localizations.localeOf(context).languageCode == 'zh';
  final title = switch (topic) {
    .automaticExport => l10n.settingsAutoExportSettings,
    .hardwareAcceleration => l10n.settingsVideoEnableHardwareAcceleration,
  };
  final content = switch (topic) {
    .automaticExport => chinese ? '请检查保存目录是否仍然存在、目录访问授权是否有效，以及设备是否有可用空间。\n\n可在隐私设置中检查存储访问，并重新选择自动导出的目标目录。取消目录选择不会开启自动导出。\n\n若问题持续，请通过问题反馈提供设备信息与失败情况。' : 'Check that the destination folder still exists, directory access is granted, and the device has available storage.\n\nReview storage access in privacy settings and select the export folder again. Cancelling folder selection does not enable automatic export.\n\nIf it still fails, report the device details and what happened.',
    .hardwareAcceleration => chinese ? '此设置控制视频如何使用设备的解码能力，不会修改视频文件。播放效果取决于视频格式、设备与当前播放器。\n\n遇到黑屏、花屏或卡顿时，可切换可用选项，并用同一段视频比较播放结果。若问题持续，可在反馈中提供设备、视频格式与所选模式。' : 'This setting controls how video playback uses the device decoder; it does not modify the video file. Results depend on the format, device, and current player.\n\nFor blank frames, artifacts, or stuttering, compare the available options using the same video. If it persists, report the device, video format, and selected mode.',
  };
  return showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(title),
      content: SingleChildScrollView(child: Text(content)),
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(dialogContext).pop();
            unawaited(
              Navigator.maybeOf(context)?.push(
                MaterialPageRoute(
                  settings: const RouteSettings(name: AboutPage.routeName),
                  builder: (_) => const AboutPage(),
                ),
              ),
            );
          },
          child: Text(l10n.aboutBugReportButton),
        ),
        FilledButton(onPressed: () => Navigator.of(dialogContext).pop(), child: Text(MaterialLocalizations.of(context).closeButtonLabel)),
      ],
    ),
  );
}
