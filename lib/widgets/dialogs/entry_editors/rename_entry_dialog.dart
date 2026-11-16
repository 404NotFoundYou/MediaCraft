import 'dart:io';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/naming_pattern.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/dialogs/aves_dialog.dart';
import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

class RenameEntryDialog extends StatefulWidget {
  static const routeName = '/dialog/rename_entry';

  final AvesEntry entry;

  const new({
    super.key,
    required this.entry,
  });

  @override
  State<RenameEntryDialog> createState() => _RenameEntryDialogState();
}

class _RenameEntryDialogState extends State<RenameEntryDialog> {
  final TextEditingController _nameController = TextEditingController();
  final ValueNotifier<bool> _isValidNotifier = ValueNotifier(false);
  final ValueNotifier<String?> _validationErrorNotifier = ValueNotifier(null);
  int _validationGeneration = 0;

  AvesEntry get entry => widget.entry;

  @override
  void initState() {
    super.initState();
    _nameController.text = entry.fileNameWithoutExtension ?? entry.sourceTitle ?? '';
    _validate();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _isValidNotifier.dispose();
    _validationErrorNotifier.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AvesDialog(
      content: Row(
        crossAxisAlignment: .end,
        children: [
          Expanded(
            child: ValueListenableBuilder<String?>(
              valueListenable: _validationErrorNotifier,
              builder: (context, error, child) => TextField(
                controller: _nameController,
                decoration: InputDecoration(
                  labelText: context.l10n.renameEntryDialogLabel,
                  errorText: error,
                ),
                autofocus: true,
                maxLines: null,
                onChanged: (_) => _validate(),
                onSubmitted: (_) => _submit(context),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 4, bottom: 12),
            child: Text(
              '${Unicode.FSI}${entry.extension}${Unicode.PDI}',
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      ),
      actions: [
        const CancelButton(),
        ValueListenableBuilder<bool>(
          valueListenable: _isValidNotifier,
          builder: (context, isValid, child) {
            return TextButton(
              onPressed: isValid ? () => _submit(context) : null,
              child: Text(context.l10n.applyButtonLabel),
            );
          },
        ),
      ],
    );
  }

  String _buildEntryPath(String name) {
    if (name.isEmpty) return '';
    return pContext.join(entry.directory!, '$name${entry.extension}');
  }

  String get newName => _nameController.text.trimLeft().replaceAll('\n', '');

  Future<void> _validate() async {
    final generation = ++_validationGeneration;
    final _newName = newName;
    final validationError = NamingPattern.validateGeneratedName(_newName);
    if (validationError != null) {
      _validationErrorNotifier.value = validationError;
      _isValidNotifier.value = false;
      return;
    }
    final path = _buildEntryPath(_newName);
    final exists = _newName.isNotEmpty && await FileSystemEntity.type(path) != FileSystemEntityType.notFound;
    if (!mounted || generation != _validationGeneration) return;
    _validationErrorNotifier.value = exists ? 'A file with this name already exists' : null;
    _isValidNotifier.value = _newName.isNotEmpty && !exists;
  }

  void _submit(BuildContext context) {
    if (_isValidNotifier.value) {
      Navigator.maybeOf(context)?.pop<String>(newName);
    }
  }
}
