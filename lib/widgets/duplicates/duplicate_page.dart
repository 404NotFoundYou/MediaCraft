import 'dart:async';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/props.dart';
import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:aves/utils/file_utils.dart';
import 'package:aves/widgets/collection/entry_set_action_delegate.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:aves/widgets/duplicates/duplicate_selection.dart';
import 'package:aves_model/aves_model.dart';
import 'package:material_ui/material_ui.dart';

enum DuplicateScanScope { all, photos, videos }

class DuplicatePage extends StatefulWidget {
  static const routeName = '/duplicates';

  const new({super.key, required this.source, required this.service});

  final CollectionSource source;
  final DuplicateService service;

  @override
  State<DuplicatePage> createState() => _DuplicatePageState();
}

class _DuplicatePageState extends State<DuplicatePage> {
  DuplicateScanScope _scope = DuplicateScanScope.all;
  StreamSubscription<DuplicateScanEvent>? _scanSubscription;
  DuplicateScanProgress? _progress;
  DuplicateScanResult? _result;
  Object? _scanError;
  bool _scanning = false;
  bool _submitting = false;
  DuplicateSelection? _selection;

  @override
  void dispose() {
    _scanSubscription?.cancel();
    if (_scanning) unawaited(widget.service.cancel());
    super.dispose();
  }

  Iterable<AvesEntry> get _scanEntries {
    final entries = widget.source.visibleEntries.where(
      (entry) => !entry.trashed,
    );
    return switch (_scope) {
      DuplicateScanScope.all => entries.where(
        (entry) => entry.isImage || entry.isVideo,
      ),
      DuplicateScanScope.photos => entries.where(
        (entry) => entry.isImage && !entry.isVideo,
      ),
      DuplicateScanScope.videos => entries.where((entry) => entry.isVideo),
    };
  }

  Future<void> _startScan() async {
    if (_submitting) return;
    await _scanSubscription?.cancel();
    setState(() {
      _scanning = true;
      _scanError = null;
      _progress = null;
      _result = null;
      _selection = null;
    });
    _scanSubscription = widget.service
        .scan(_scanEntries)
        .listen(
          (event) {
            if (!mounted) return;
            switch (event) {
              case DuplicateScanProgress():
                setState(() => _progress = event);
              case DuplicateScanCompleted():
                setState(() {
                  _scanning = false;
                  _result = event.result;
                  _selection = DuplicateSelection(event.result.groups);
                });
            }
          },
          onError: (Object error, StackTrace stackTrace) {
            if (!mounted) return;
            setState(() {
              _scanning = false;
              _scanError = error;
            });
          },
          onDone: () {
            if (mounted && _scanning) setState(() => _scanning = false);
          },
        );
  }

  void _cancelScan() => unawaited(widget.service.cancel());

  List<AvesEntry> _entriesFor(DuplicateGroup group) => group.members.map((member) => widget.source.getEntryById(member.entryId)).whereType<AvesEntry>().where((entry) => !entry.trashed).toList(growable: false);

  Set<AvesEntry> get _selectedEntries {
    final ids = _selection?.selectedIds ?? const <int>{};
    return ids.map(widget.source.getEntryById).whereType<AvesEntry>().where((entry) => !entry.trashed).toSet();
  }

  int get _selectedBytes => _selectedEntries.fold(0, (sum, entry) => sum + (entry.sizeBytes ?? 0));

  Future<void> _reviewAndDelete() async {
    final result = _result;
    if (result == null || _selectedEntries.isEmpty || _submitting) return;
    if (!settings.enableBin) {
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('请先启用回收站'),
          content: const Text('重复项清理只会将文件移入 Aves 回收站，不执行永久删除。请在“设置 > 隐私 > 使用回收站”中启用后重试。'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('知道了')),
          ],
        ),
      );
      return;
    }

    final selection = _selection;
    if (selection == null) return;
    final selectionSnapshot = selection.snapshot();
    final expectedMembers = {
      for (final group in result.groups)
        for (final member in group.members) member.entryId: member,
    };
    final involvedIds = {
      ...selectionSnapshot.selectedIds,
      for (final groupId in selectionSnapshot.selected.keys)
        if (selectionSnapshot.selected[groupId]!.isNotEmpty && selectionSnapshot.keepers[groupId] != null) selectionSnapshot.keepers[groupId]!,
    };
    final initialStateIssue = _validateCurrentEntries(involvedIds, expectedMembers);
    if (initialStateIssue != null) {
      await _showOperationError('扫描结果已变化', initialStateIssue);
      return;
    }
    final selectedEntriesSnapshot = selectionSnapshot.selectedIds.map((id) => widget.source.getEntryById(id)!).toSet();
    final selectedBytesSnapshot = selectedEntriesSnapshot.fold(0, (sum, entry) => sum + (entry.sizeBytes ?? 0));
    if (selectedEntriesSnapshot.isEmpty) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('确认清理重复项'),
        content: Text(
          '将把 ${selectedEntriesSnapshot.length} 个完全重复文件（${formatFileSize(settings.avesLocale, selectedBytesSnapshot)}）移入 Aves 回收站。'
          '\n每组至少保留一份。实际处理结果以 Aves 操作报告为准；取消确认不会处理文件，清理后可从 Aves 回收站尝试恢复。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('复核并继续'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _submitting = true);
    var operationFinished = false;
    try {
      operationFinished = await EntrySetActionDelegate().doMove(
        context,
        moveType: MoveType.toBin,
        entries: selectedEntriesSnapshot,
        preserveFileBytes: true,
        beforeMove: () => _verifyBeforeMove(
          result: result,
          selection: selectionSnapshot,
          involvedIds: involvedIds,
          expectedMembers: expectedMembers,
        ),
      );
    } catch (error) {
      if (mounted) await _showOperationError('清理未完成', '复核或系统操作失败：$error');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
    if (operationFinished && mounted) await _startScan();
  }

  Future<bool> _verifyBeforeMove({
    required DuplicateScanResult result,
    required DuplicateSelectionSnapshot selection,
    required Set<int> involvedIds,
    required Map<int, DuplicateMember> expectedMembers,
  }) async {
    final stateIssue = _validateCurrentEntries(involvedIds, expectedMembers);
    if (stateIssue != null) {
      if (mounted) await _showOperationError('扫描结果已变化', stateIssue);
      return false;
    }

    final verificationIssues = <DuplicateIssue>[];
    for (final group in result.groups) {
      final selectedIds = selection.selected[group.id] ?? const <int>{};
      if (selectedIds.isEmpty) continue;
      final keeperId = selection.keepers[group.id];
      if (keeperId == null || selectedIds.contains(keeperId)) {
        verificationIssues.add(DuplicateIssue(entryId: -1, uri: '', code: 'invalid-selection', message: '分组 ${group.id} 没有有效的保留项'));
        continue;
      }
      final membersById = {for (final member in group.members) member.entryId: member};
      final keeperMember = membersById[keeperId];
      final deleteMembers = selectedIds.map((id) => membersById[id]).whereType<DuplicateMember>().toList(growable: false);
      if (keeperMember == null || deleteMembers.length != selectedIds.length) {
        verificationIssues.add(DuplicateIssue(entryId: keeperId, uri: '', code: 'selection-changed', message: '分组 ${group.id} 的保留项或待处理项已变化'));
        continue;
      }
      final verification = await widget.service.verifySelection(group: group, keeper: keeperMember, deleteMembers: deleteMembers);
      if (!verification.valid) {
        if (verification.issues.isEmpty) {
          verificationIssues.add(DuplicateIssue(entryId: -1, uri: '', code: 'verification-failed', message: '分组 ${group.id} 未通过最终字节复核'));
        } else {
          verificationIssues.addAll(verification.issues);
        }
      }
    }
    if (verificationIssues.isNotEmpty) {
      if (mounted) await _showOperationError('复核未通过', verificationIssues.map((issue) => issue.message).join('\n'));
      return false;
    }
    if (!mounted) return false;
    final finalStateIssue = _validateCurrentEntries(involvedIds, expectedMembers);
    if (finalStateIssue != null) {
      await _showOperationError('扫描结果已变化', finalStateIssue);
      return false;
    }
    return true;
  }

  String? _validateCurrentEntries(Set<int> entryIds, Map<int, DuplicateMember> expectedMembers) {
    for (final id in entryIds) {
      final expected = expectedMembers[id];
      final current = widget.source.getEntryById(id);
      if (expected == null || current == null) return '媒体 $id 已不在本次扫描结果中，请重新扫描。';
      if (current.trashed) return '媒体 ${current.uri} 已进入回收站，请重新扫描。';
      if (current.uri != expected.uri) return '媒体 $id 的位置已经变化，请重新扫描。';
    }
    return null;
  }

  Future<void> _showOperationError(String title, String message) => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: SingleChildScrollView(child: Text(message)),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('知道了'))],
    ),
  );

  @override
  Widget build(BuildContext context) {
    return AvesScaffold(
      appBar: AppBar(title: const Text('重复项清理')),
      body: SafeArea(
        child: Column(
          children: [
            _ScanControls(
              scope: _scope,
              scanning: _scanning,
              submitting: _submitting,
              progress: _progress,
              onScopeChanged: (scope) => setState(() => _scope = scope),
              onScan: _startScan,
              onCancel: _cancelScan,
            ),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
      bottomNavigationBar: _selectedEntries.isEmpty
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: SizedBox(
                  width: MediaQuery.sizeOf(context).width - 24,
                  height: 52,
                  child: FilledButton.icon(
                    onPressed: _submitting ? null : _reviewAndDelete,
                    style: FilledButton.styleFrom(
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    icon: _submitting ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.delete_outline),
                    label: Text('复核 ${_selectedEntries.length} 项·${formatFileSize(settings.avesLocale, _selectedBytes)}'),
                  ),
                ),
              ),
            ),
    );
  }

  Widget _buildBody() {
    if (_scanError != null) {
      return _Message(icon: Icons.error_outline, text: '扫描失败：$_scanError');
    }
    if (_scanning) return const SizedBox.shrink();
    final result = _result;
    if (result == null) {
      return const _Message(
        icon: Icons.content_copy,
        text: '选择范围后开始扫描，扫描不会自动删除任何文件。',
      );
    }
    if (result.cancelled) {
      return const _Message(
        icon: Icons.cancel_outlined,
        text: '扫描已取消，没有处理任何文件。',
      );
    }
    if (result.groups.isEmpty && result.issues.isEmpty) {
      return const _Message(
        icon: Icons.check_circle_outline,
        text: '未发现完全重复的照片或视频。',
      );
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
      children: [
        if (result.issues.isNotEmpty)
          Card(
            child: ExpansionTile(
              leading: const Icon(Icons.warning_amber),
              title: Text('${result.issues.length} 项读取问题'),
              children: result.issues
                  .map(
                    (issue) => ListTile(
                      dense: true,
                      title: Text(issue.message),
                      subtitle: issue.uri.isEmpty ? null : Text(issue.uri),
                    ),
                  )
                  .toList(),
            ),
          ),
        for (final group in result.groups)
          _DuplicateGroupCard(
            group: group,
            entries: _entriesFor(group),
            keeperId: _selection?.keepers[group.id],
            selectedIds: _selection?.selected[group.id] ?? const {},
            enabled: !_submitting,
            onKeeperChanged: (id) => setState(() {
              _selection?.keep(group.id, id);
            }),
            onSelectionChanged: (id, selected) => setState(() {
              _selection?.select(group.id, id, selected);
            }),
          ),
      ],
    );
  }
}

class _ScanControls extends StatelessWidget {
  const new({
    required this.scope,
    required this.scanning,
    required this.submitting,
    required this.progress,
    required this.onScopeChanged,
    required this.onScan,
    required this.onCancel,
  });
  final DuplicateScanScope scope;
  final bool scanning;
  final bool submitting;
  final DuplicateScanProgress? progress;
  final ValueChanged<DuplicateScanScope> onScopeChanged;
  final VoidCallback onScan;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final p = progress;
    final stageLabel = switch (p?.stage) {
      'hashing' => '读取文件',
      'verifying' => '核对内容',
      'completed' => '扫描完成',
      _ => '准备扫描',
    };
    final busy = scanning || submitting;
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('仅扫描当前已授权且可访问的本机照片和视频。读取失败的项目会单独列出，不会参与清理。'),
              const SizedBox(height: 12),
              SegmentedButton<DuplicateScanScope>(
                segments: const [
                  ButtonSegment(
                    value: DuplicateScanScope.all,
                    label: Text('全部'),
                    icon: Icon(Icons.perm_media_outlined),
                  ),
                  ButtonSegment(
                    value: DuplicateScanScope.photos,
                    label: Text('照片'),
                    icon: Icon(Icons.photo_outlined),
                  ),
                  ButtonSegment(
                    value: DuplicateScanScope.videos,
                    label: Text('视频'),
                    icon: Icon(Icons.videocam_outlined),
                  ),
                ],
                selected: {scope},
                onSelectionChanged: busy ? null : (values) => onScopeChanged(values.single),
              ),
              const SizedBox(height: 10),
              if (scanning) ...[
                LinearProgressIndicator(
                  value: p == null || p.total == 0 ? null : p.processed / p.total,
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        p == null ? '准备扫描…' : '$stageLabel  ${p.processed}/${p.total}',
                      ),
                    ),
                    TextButton(onPressed: onCancel, child: const Text('取消')),
                  ],
                ),
              ] else
                FilledButton.icon(
                  onPressed: submitting ? null : onScan,
                  icon: const Icon(Icons.search),
                  label: const Text('开始扫描'),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DuplicateGroupCard extends StatelessWidget {
  const new({
    required this.group,
    required this.entries,
    required this.keeperId,
    required this.selectedIds,
    required this.enabled,
    required this.onKeeperChanged,
    required this.onSelectionChanged,
  });
  final DuplicateGroup group;
  final List<AvesEntry> entries;
  final int? keeperId;
  final Set<int> selectedIds;
  final bool enabled;
  final ValueChanged<int> onKeeperChanged;
  final void Function(int id, bool selected) onSelectionChanged;

  @override
  Widget build(BuildContext context) {
    if (entries.length < 2) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.refresh),
          title: Text('此组媒体已变化'),
          subtitle: Text('部分文件已移走或不可访问，请重新扫描后再处理。'),
        ),
      );
    }
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '完全重复·${entries.length} 份',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(
              '每份 ${formatFileSize(settings.avesLocale, group.sizeBytes)}，请保留一份并手动勾选其余项。',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 8),
            RadioGroup<int>(
              groupValue: keeperId,
              onChanged: (id) {
                if (enabled && id != null) onKeeperChanged(id);
              },
              child: Column(
                children: [
                  for (final entry in entries)
                    _DuplicateEntryTile(
                      entry: entry,
                      keeper: entry.id == keeperId,
                      selected: selectedIds.contains(entry.id),
                      enabled: enabled,
                      onSelected: (value) => onSelectionChanged(entry.id, value),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DuplicateEntryTile extends StatelessWidget {
  const new({
    required this.entry,
    required this.keeper,
    required this.selected,
    required this.enabled,
    required this.onSelected,
  });
  final AvesEntry entry;
  final bool keeper;
  final bool selected;
  final bool enabled;
  final ValueChanged<bool> onSelected;

  @override
  Widget build(BuildContext context) {
    const extent = 64.0;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: ThumbnailImage(
              entry: entry,
              extent: extent,
              devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.fileNameWithoutExtension != null ? '${entry.fileNameWithoutExtension}${entry.extension ?? ''}' : (entry.sourceTitle ?? entry.uri),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  entry.path ?? entry.uri,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                RadioListTile<int>(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  value: entry.id,
                  enabled: enabled,
                  title: Text('保留这一份', semanticsLabel: '保留 ${entry.bestTitle}'),
                ),
              ],
            ),
          ),
          Checkbox(
            semanticLabel: '清理 ${entry.bestTitle}',
            value: keeper ? false : selected,
            onChanged: keeper || !enabled ? null : (value) => onSelected(value ?? false),
          ),
        ],
      ),
    );
  }
}

class _Message extends StatelessWidget {
  const new({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48),
          const SizedBox(height: 12),
          Text(text, textAlign: TextAlign.center),
        ],
      ),
    ),
  );
}
