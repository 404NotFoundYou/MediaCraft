import 'dart:async';
import 'dart:convert';

import 'package:aves/services/common/services.dart';
import 'package:aves/widgets/duplicates/duplicate_options.dart';
import 'package:aves/widgets/duplicates/duplicate_frames.dart';

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

enum DuplicateScanMode { exact, similar }

typedef DuplicateMoveAction = Future<bool> Function(
  BuildContext context, {
  required Set<AvesEntry> entries,
  required Future<bool> Function() beforeMove,
});

class DuplicatePage extends StatefulWidget {
  static const routeName = '/duplicates';

  const new({super.key, required this.source, required this.service, this.moveAction = _moveToBin});

  final CollectionSource source;
  final DuplicateService service;
  final DuplicateMoveAction moveAction;

  static Future<bool> _moveToBin(
    BuildContext context, {
    required Set<AvesEntry> entries,
    required Future<bool> Function() beforeMove,
  }) => EntrySetActionDelegate().doMove(
    context,
    moveType: MoveType.toBin,
    entries: entries,
    preserveFileBytes: true,
    beforeMove: beforeMove,
  );

  @override
  State<DuplicatePage> createState() => _DuplicatePageState();
}

class _DuplicatePageState extends State<DuplicatePage> {
  DuplicateScanMode _mode = DuplicateScanMode.exact;
  DuplicateScanScope _scope = DuplicateScanScope.all;
  StreamSubscription<DuplicateScanEvent>? _scanSubscription;
  DuplicateScanProgress? _progress;
  DuplicateScanResult? _result;
  Object? _scanError;
  bool _scanning = false;
  bool _submitting = false;
  DuplicateSelection? _selection;

  DuplicateFilters _filters = const DuplicateFilters();
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_restoreLatest());
  }

  Future<void> _restoreLatest() async {
    final generation = _generation;
    try {
      final history = await widget.service.history();
      if (mounted && generation == _generation && history.isNotEmpty) await _openJob(history.first);
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _scanError = error);
    }
  }

  @override
  void dispose() {
    _scanSubscription?.cancel();
    _generation++;
    super.dispose();
  }

  Iterable<AvesEntry> get _scanEntries {
    final entries = widget.source.visibleEntries.where(
      (entry) => !entry.trashed && _filters.accepts(entry),
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
    if (_scanning || _submitting) return;
    final scan = _mode == DuplicateScanMode.exact ? widget.service.scan(_scanEntries, onlyNew: _filters.onlyNew) : widget.service.scanSimilar(_scanEntries, onlyNew: _filters.onlyNew);
    await _listen(scan);
  }

  Future<void> _openJob(Map job) async {
    _mode = job['mode'] == 'scan' ? DuplicateScanMode.exact : DuplicateScanMode.similar;
    await _listen(widget.service.watch(job['id'] as String));
  }

  Future<void> _listen(Stream<DuplicateScanEvent> scan) async {
    final generation = ++_generation;
    await _scanSubscription?.cancel();
    if (!mounted || generation != _generation) return;
    setState(() {
      _scanning = true;
      _scanError = null;
      _progress = null;
      _result = null;
      _selection = null;
    });
    _scanSubscription = scan.listen(
      (event) {
        if (!mounted) return;
        switch (event) {
          case DuplicateScanProgress():
            setState(() => _progress = event);
          case DuplicateScanCompleted():
            setState(() {
              _scanning = false;
              _result = _visibleResult(event.result);
              _selection = DuplicateSelection(_result!.groups);
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

  Future<void> _cancelScan() async {
    try {
      await widget.service.cancel();
    } catch (error) {
      if (mounted) await _showOperationError('取消失败', '$error');
    }
  }

  DuplicateScanResult _visibleResult(DuplicateScanResult result) {
    final visible = {
      for (final entry in widget.source.visibleEntries)
        if (!entry.trashed) entry.id: entry.uri,
    };
    return DuplicateScanResult(cancelled: result.cancelled, groups: result.groups.where((g) => g.members.every((m) => visible[m.entryId] == m.uri)).toList(), issues: result.issues.where((i) => visible[i.entryId] == i.uri).toList());
  }

  Future<void> _menu(String action) async {
    try {
      if (action == 'history') {
        final jobs = await widget.service.history();
        if (!mounted) return;
        final chosen = await showDialog<Map>(
          context: context,
          builder: (context) => SimpleDialog(
            title: const Text('最近五次扫描'),
            children: [
              if (jobs.isEmpty) const ListTile(title: Text('暂无扫描记录')),
              for (final job in jobs)
                SimpleDialogOption(onPressed: () => Navigator.pop(context, job), child: Text('${job['mode'] == 'scan' ? '完全重复' : '视觉相似'} · ${DateTime.fromMillisecondsSinceEpoch(job['created'] as int).toString().split('.').first}')),
            ],
          ),
        );
        if (chosen != null && mounted) await _openJob(chosen);
      } else if (action == 'filters') {
        final value = await chooseDuplicateFilters(context, _filters, widget.source.visibleEntries.map((e) => e.directory).nonNulls);
        if (mounted && value != null) {
          setState(() {
            _generation++;
            _filters = value;
            _result = null;
            _selection = null;
            _scanError = null;
          });
        }
      } else if (action == 'json' || action == 'csv') {
        final report = duplicateReport(_visibleResult(_result!), csv: action == 'csv');
        final saved = await storageService.createFile(basename: 'MediaCraft-${DateTime.now().millisecondsSinceEpoch}.$action', mimeType: action == 'csv' ? 'text/csv' : 'application/json', bytes: utf8.encode(report));
        if (mounted && saved != null) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(saved ? '已导出扫描报告' : '导出失败')));
      } else {
        final policy = DuplicateKeeper.values.byName(action);
        var folder = '';
        if (policy == DuplicateKeeper.directory) {
          final folders = widget.source.visibleEntries.map((v) => v.directory).nonNulls.toSet().toList()..sort();
          final selected = await showDialog<String>(
            context: context,
            builder: (context) => SimpleDialog(
              title: const Text('优先保留目录'),
              children: [for (final path in folders) SimpleDialogOption(onPressed: () => Navigator.pop(context, path), child: Text(path))],
            ),
          );
          if (selected == null || !mounted) return;
          folder = selected;
        }
        setState(() {
          for (final group in _result!.groups.where((g) => !g.fragment)) {
            final keeper = policy.choose(_entriesFor(group), folder);
            if (keeper != null) _selection?.keep(group.id, keeper);
          }
        });
      }
    } catch (error) {
      if (mounted) await _showOperationError('操作未完成', '$error');
    }
  }

  void _changeMode(DuplicateScanMode mode) {
    if (_mode == mode) return;
    _generation++;
    setState(() {
      _mode = mode;
      _progress = null;
      _result = null;
      _selection = null;
      _scanError = null;
    });
  }

  List<AvesEntry> _entriesFor(DuplicateGroup group) =>
      group.members.map((member) => widget.source.getEntryById(member.entryId)).whereType<AvesEntry>().where((entry) => !entry.trashed && widget.source.visibleEntries.contains(entry)).toList(growable: false);

  Set<AvesEntry> get _selectedEntries {
    final ids = _selection?.selectedIds ?? const <int>{};
    return ids.map(widget.source.getEntryById).whereType<AvesEntry>().where((entry) => !entry.trashed && widget.source.visibleEntries.contains(entry)).toSet();
  }

  Future<void> _reviewAndDelete() async {
    final result = _result;
    if (result == null || _selectedEntries.isEmpty || _submitting) return;
    if (!settings.enableBin) {
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('请先启用回收站'),
          content: const Text('重复项清理只会将文件移入回收站，不执行永久删除。请在“设置 > 隐私 > 使用回收站”中启用后重试。'),
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
          '将把 ${selectedEntriesSnapshot.length} 个${_mode == DuplicateScanMode.exact ? '完全重复文件' : '相似媒体'}（${formatFileSize(settings.avesLocale, selectedBytesSnapshot)}）移入回收站。'
          '\n每组至少保留一份。实际处理结果以操作报告为准；取消确认不会处理文件，清理后可从回收站尝试恢复。',
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
      operationFinished = await widget.moveAction(
        context,
        entries: selectedEntriesSnapshot,
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
      final verification = _mode == DuplicateScanMode.exact
          ? await widget.service.verifySelection(group: group, keeper: keeperMember, deleteMembers: deleteMembers)
          : await widget.service.verifySimilarSelection(group: group, keeper: keeperMember, deleteMembers: deleteMembers);
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
      if (!widget.source.visibleEntries.contains(current)) return '媒体已隐藏或不再可访问，请重新扫描。';
      if (_mode == DuplicateScanMode.similar &&
          (current.width != expected.width ||
              current.height != expected.height ||
              current.rotationDegrees != expected.rotationDegrees ||
              current.isFlipped != expected.isFlipped ||
              current.pageId != expected.pageId ||
              current.mimeType != expected.mimeType ||
              ((current.durationMillis ?? 0) > 0 && current.durationMillis != expected.durationMillis))) {
        return '媒体的画面信息已变化，请重新扫描。';
      }
      if (current.trashed) return '媒体 ${current.uri} 已进入回收站，请重新扫描。';
      if (current.uri != expected.uri) return '媒体 $id 的位置已经变化，请重新扫描。';
      if (current.sizeBytes != expected.sizeBytes) return '媒体 ${current.uri} 的大小已经变化，请重新扫描。';
      if (current.dateModifiedMillis != expected.dateModifiedMillis) return '媒体 ${current.uri} 的修改时间已经变化，请重新扫描。';
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
    final showActions = !_scanning && _scanError == null && _result?.cancelled == false && _result!.groups.isNotEmpty;
    final selectedEntries = showActions ? _selectedEntries : const <AvesEntry>{};
    final selectedSize = showActions ? formatFileSize(settings.avesLocale, selectedEntries.fold(0, (sum, entry) => sum + (entry.sizeBytes ?? 0))) : '';
    return AvesScaffold(
      appBar: AppBar(
        title: const Text('重复媒体清理'),
        actions: [
          IconButton(tooltip: '筛选扫描范围', onPressed: _scanning || _submitting ? null : () => _menu('filters'), icon: const Icon(Icons.filter_alt_outlined)),
          PopupMenuButton<String>(
            enabled: !_scanning && !_submitting,
            tooltip: '扫描记录与选项',
            onSelected: _menu,
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'history', child: Text('扫描历史')),
              if (_result?.cancelled == false) ...[const PopupMenuItem(value: 'json', child: Text('导出 JSON')), const PopupMenuItem(value: 'csv', child: Text('导出 CSV'))],
              if (showActions)
                for (final policy in DuplicateKeeper.values) PopupMenuItem(value: policy.name, child: Text('保留策略 · ${policy.label}')),
            ],
          ),
        ],
      ),
      body: SafeArea(
        bottom: false,
        child: _buildBody(),
      ),
      bottomNavigationBar: !showActions
          ? null
          : DecoratedBox(
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surface,
                border: Border(top: BorderSide(color: Theme.of(context).colorScheme.outlineVariant)),
              ),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
                  child: Column(
                    mainAxisSize: .min,
                    crossAxisAlignment: .stretch,
                    children: [
                      Wrap(
                        alignment: .spaceBetween,
                        spacing: 12,
                        runSpacing: 4,
                        children: [
                          Text('已选 ${selectedEntries.length} 项', style: Theme.of(context).textTheme.bodySmall),
                          Text(selectedSize, style: Theme.of(context).textTheme.titleSmall),
                        ],
                      ),
                      const SizedBox(height: 10),
                      FilledButton.icon(
                        onPressed: _submitting || selectedEntries.isEmpty ? null : _reviewAndDelete,
                        style: FilledButton.styleFrom(
                          minimumSize: const Size.fromHeight(48),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        icon: _submitting ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.delete_outline),
                        label: Text('复核 ${selectedEntries.length} 项·$selectedSize', maxLines: 2, overflow: .ellipsis),
                      ),
                    ],
                  ),
                ),
              ),
            ),
    );
  }

  Widget _buildBody() {
    final controls = _ScanControls(
      mode: _mode,
      scope: _scope,
      scanning: _scanning,
      submitting: _submitting,
      progress: _progress,
      onModeChanged: _changeMode,
      onScopeChanged: (scope) => setState(() {
        _generation++;
        _scope = scope;
        _result = null;
        _selection = null;
      }),
      onScan: _startScan,
      onCancel: _cancelScan,
    );
    Widget withControls(Widget content) => ListView(children: [controls, content]);
    if (_scanError != null) {
      return withControls(_Message(icon: Icons.error_outline, text: '扫描失败：$_scanError'));
    }
    if (_scanning) return withControls(_Message(icon: Icons.fact_check_outlined, text: _mode == DuplicateScanMode.exact ? '正在核对文件内容。你可以随时取消，扫描本身不会删除文件。' : '正在分析图片和视频指纹。你可以随时取消，扫描本身不会删除文件。'));
    final result = _result;
    if (result == null) {
      return withControls(
        _Message(
          icon: Icons.content_copy,
          text: _mode == DuplicateScanMode.exact ? '相同的内容，留下一份就好。\n\n扫描后逐组复核，每组默认保留首份，待清理项由你手动勾选。只有最终复核通过后才会移入回收站。' : '画面高度相似的媒体会归入候选组。\n\n相似结果需要逐组复核，待清理项由你手动勾选。只有最终复核通过后才会移入回收站。',
        ),
      );
    }
    if (result.cancelled) {
      return withControls(
        const _Message(
          icon: Icons.cancel_outlined,
          text: '扫描已取消，没有处理任何文件。',
        ),
      );
    }
    if (result.groups.isEmpty && result.issues.isEmpty) {
      return withControls(
        _Message(
          icon: Icons.check_circle_outline,
          text: _mode == DuplicateScanMode.exact ? '未发现完全重复的照片或视频。' : '未发现相似的照片或视频。',
        ),
      );
    }
    final headers = <Widget>[
      controls,
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
        child: Column(
          crossAxisAlignment: .start,
          children: [
            Text(_mode == DuplicateScanMode.exact ? '${result.groups.length} 组完全重复媒体' : '${result.groups.length} 组相似媒体', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text(_mode == DuplicateScanMode.exact ? '绿色行是保留项，右侧复选框是待清理项。保留项始终不会被勾选。' : '相似度用于辅助判断，绿色行是保留项，右侧复选框是待清理项。保留项始终不会被勾选。'),
          ],
        ),
      ),
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
    ];
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 20),
      itemCount: headers.length + result.groups.length,
      itemBuilder: (context, index) {
        if (index < headers.length) return headers[index];
        final group = result.groups[index - headers.length];
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: _DuplicateGroupCard(
            group: group,
            similar: _mode == DuplicateScanMode.similar,
            entries: _entriesFor(group),
            keeperId: _selection?.keepers[group.id],
            selectedIds: _selection?.selected[group.id] ?? const {},
            enabled: !_submitting && !group.fragment,
            onFrames: () => showDuplicateFrames(context, group, widget.service),
            onKeeperChanged: (id) => setState(() {
              _selection?.keep(group.id, id);
            }),
            onSelectionChanged: (id, selected) => setState(() {
              _selection?.select(group.id, id, selected);
            }),
          ),
        );
      },
    );
  }
}

class _ScanControls extends StatelessWidget {
  const new({
    required this.mode,
    required this.scope,
    required this.scanning,
    required this.submitting,
    required this.progress,
    required this.onModeChanged,
    required this.onScopeChanged,
    required this.onScan,
    required this.onCancel,
  });
  final DuplicateScanMode mode;
  final DuplicateScanScope scope;
  final bool scanning;
  final bool submitting;
  final DuplicateScanProgress? progress;
  final ValueChanged<DuplicateScanMode> onModeChanged;
  final ValueChanged<DuplicateScanScope> onScopeChanged;
  final VoidCallback onScan;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final p = progress;
    final stageLabel = switch (p?.stage) {
      'queued' => '等待后台扫描',
      'similar-exact' => '排除字节完全相同的文件',
      'similar-grouping' => '比对媒体指纹',
      'hashing' => '读取文件',
      'verifying' => '核对内容',
      'similar-fingerprinting' => '生成媒体指纹',
      'similar-completed' => '相似分组完成',
      'completed' => '扫描完成',
      _ => '准备扫描',
    };
    final busy = scanning || submitting;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
      child: Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('扫描范围', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text('仅扫描已授权、可访问的本机媒体。读取失败的项目会单独列出。', style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 12),
              SegmentedButton<DuplicateScanMode>(
                segments: const [
                  ButtonSegment(
                    value: DuplicateScanMode.exact,
                    label: Text('完全重复'),
                    icon: Icon(Icons.copy_outlined),
                  ),
                  ButtonSegment(
                    value: DuplicateScanMode.similar,
                    label: Text('视觉相似'),
                    icon: Icon(Icons.auto_awesome_motion_outlined),
                  ),
                ],
                selected: {mode},
                showSelectedIcon: false,
                onSelectionChanged: busy ? null : (values) => onModeChanged(values.single),
              ),
              const SizedBox(height: 10),
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
                showSelectedIcon: false,
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
                        p == null ? '准备扫描…' : (p.total == 0 ? '$stageLabel  ${p.processed}' : '$stageLabel  ${p.processed}/${p.total}'),
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
    required this.similar,
    required this.onFrames,
    required this.entries,
    required this.keeperId,
    required this.selectedIds,
    required this.enabled,
    required this.onKeeperChanged,
    required this.onSelectionChanged,
  });
  final DuplicateGroup group;
  final bool similar;
  final VoidCallback onFrames;
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
      clipBehavior: .antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              similar ? '${group.highConfidence ? '高度相似' : '可能相似'}·${entries.length} 份' : '完全重复·${entries.length} 份',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(
              similar ? _similarSummary(group) : '每份 ${formatFileSize(settings.avesLocale, group.sizeBytes)}，请保留一份并手动勾选其余项。',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 8),
            if (group.frames.values.any((frames) => frames.isNotEmpty)) TextButton.icon(onPressed: onFrames, icon: const Icon(Icons.movie_outlined), label: const Text('查看采样帧对照')),
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

  String _similarSummary(DuplicateGroup group) {
    if (group.fragment) return '检测到相似片段，可对照采样时间查看长短版本。此组仅供查看。';
    final score = group.similarityScore == null ? '未知' : '${(group.similarityScore! * 100).round()}%';
    if (group.kind == 'video' && group.matchedFrames != null && group.totalFrames != null) {
      return '$score 相似，${group.matchedFrames}/${group.totalFrames} 个采样帧匹配，请手动确认后处理。';
    }
    return '$score 相似，请手动确认后处理。';
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
    return Material(
      color: keeper ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: .6) : Colors.transparent,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: .antiAlias,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
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
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    entry.path ?? entry.uri,
                    maxLines: 2,
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
