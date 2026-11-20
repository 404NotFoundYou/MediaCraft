import 'dart:convert';

import 'package:aves/locale/intl.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/ref/mime_types.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/widgets/common/action_mixins/feedback.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/dialogs/aves_dialog.dart';
import 'package:aves/widgets/settings/app_export/items.dart';
import 'package:aves/widgets/settings/app_export/selection_dialog.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class SettingsActionDelegate with FeedbackMixin {
  static const String _exportVersionKey = 'version';
  static const int _exportVersion = 1;

  void onActionSelected(BuildContext context, SettingsAction action) {
    switch (action) {
      case .export:
        _export(context);
      case .import:
        _import(context);
    }
  }

  static Uint8List getExportContent({
    required CollectionSource source,
    required Set<AppExportItem> toExport,
  }) {
    final allMap = Map.fromEntries(
      toExport.map((v) {
        final jsonMap = v.export(source);
        return jsonMap != null ? MapEntry(v.name, jsonMap) : null;
      }).nonNulls,
    );
    allMap[_exportVersionKey] = _exportVersion;
    final jsonContent = const JsonEncoder.withIndent('  ').convert(allMap);
    return utf8.encode(jsonContent);
  }

  Future<void> _export(BuildContext context) async {
    final l10n = context.l10n;
    final toExport = await showAvesDialog<Set<AppExportItem>>(
      context: context,
      builder: (context) => AppExportItemSelectionDialog(
        title: l10n.settingsActionExportDialogTitle,
      ),
    );
    if (toExport == null || toExport.isEmpty) return;

    final date = DateFormat('yyyyMMdd_HHmmss', IntlUtils.rootLocaleName).format(DateTime.now());
    final content = getExportContent(
      source: context.read<CollectionSource>(),
      toExport: toExport,
    );
    const mimeType = MimeTypes.json;
    final success = await storageService.createFile(
      basename: 'mediacraft-settings-$date',
      mimeType: mimeType,
      bytes: content,
    );

    if (success != null) {
      if (success) {
        showFeedback(context, FeedbackType.info, l10n.genericSuccessFeedback);
      } else {
        showFeedback(context, FeedbackType.warn, l10n.genericFailureFeedback);
      }
    }
  }

  static Map<AppExportItem, Object> getImportContent(Uint8List bytes) {
    final json = jsonDecode(utf8.decode(bytes));
    if (json is! Map<String, Object?>) throw const FormatException('Backup must be an object');

    if (!json.containsKey(_exportVersionKey)) {
      // backward compatibility before versioning
      return {AppExportItem.settings: Settings.validatedImport(json)};
    }
    final version = json[_exportVersionKey];
    if (version is! int || version != _exportVersion) throw const FormatException('Unsupported backup version');

    final importable = <AppExportItem, Object>{};
    for (final item in AppExportItem.values) {
      if (!json.containsKey(item.name)) continue;
      final value = json[item.name];
      if (value == null) throw FormatException('Missing backup data for ${item.name}');
      importable[item] = item == AppExportItem.settings ? Settings.validatedImport(value) : value;
    }
    if (importable.isEmpty) throw const FormatException('Backup contains no importable data');
    return importable;
  }

  Future<void> _import(BuildContext context) async {
    final l10n = context.l10n;
    // specifying the JSON MIME type to restrict openable files is correct in theory,
    // but older devices (e.g. SM-P580, API 27) that do not recognize JSON files as such would filter them out
    try {
      final bytes = await storageService.openFile();
      if (bytes.isEmpty || !context.mounted) return;
      final importable = getImportContent(bytes);

      final toImport = await showAvesDialog<Set<AppExportItem>>(
        context: context,
        builder: (context) => AppExportItemSelectionDialog(
          title: l10n.settingsActionImportDialogTitle,
          selectableItems: importable.keys.toSet(),
        ),
      );
      if (toImport == null || toImport.isEmpty || !context.mounted) return;

      final source = context.read<CollectionSource>();
      await Future.forEach<AppExportItem>(toImport, (item) async {
        final jsonObject = importable[item];
        if (jsonObject != null) {
          await item.import(jsonObject, source);
        }
      });
      if (context.mounted) showFeedback(context, FeedbackType.info, l10n.genericSuccessFeedback);
    } catch (error, stack) {
      debugPrint('failed to import app json, error=$error\n$stack');
      if (context.mounted) showFeedback(context, FeedbackType.warn, l10n.genericFailureFeedback);
    }
  }
}
