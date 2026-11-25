import 'dart:convert';

import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/favourites.dart';
import 'package:flutter/material.dart';

class DuplicateFilters {
  const new({this.album, this.directory = '', this.dates, this.minMiB, this.maxMiB, this.onlyNew = false});
  final String? album;
  final String directory;
  final DateTimeRange? dates;
  final double? minMiB, maxMiB;
  final bool onlyNew;

  bool accepts(AvesEntry entry) {
    if (album != null && entry.directory != album) return false;
    final folder = directory.trim().replaceFirst(RegExp(r'/+$'), '');
    if (folder.isNotEmpty && entry.directory != folder && !(entry.directory?.startsWith('$folder/') ?? false)) return false;
    final date = entry.bestDate;
    if (dates case final range?) {
      final end = range.end;
      if (date == null || date.isBefore(range.start) || !date.isBefore(DateTime(end.year, end.month, end.day + 1))) return false;
    }
    final bytes = entry.sizeBytes;
    return (minMiB == null || bytes != null && bytes >= minMiB! * 1048576) && (maxMiB == null || bytes != null && bytes <= maxMiB! * 1048576);
  }
}

enum DuplicateKeeper {
  first('扫描排序首项'),
  resolution('最高分辨率'),
  largest('最大文件'),
  newest('最新文件'),
  favourite('收藏优先'),
  directory('指定目录优先');

  new(this.label);
  final String label;

  int? choose(List<AvesEntry> entries, String folder) {
    num rank(AvesEntry e) => switch (this) {
      first => 0,
      resolution => e.width * e.height,
      largest => e.sizeBytes ?? -1,
      newest => e.bestDate?.millisecondsSinceEpoch ?? -1,
      favourite => e.isFavourite ? 1 : 0,
      directory => folder.isNotEmpty && (e.directory == folder || (e.directory?.startsWith('$folder/') ?? false)) ? 1 : 0,
    };
    return entries.isEmpty ? null : entries.reduce((a, b) => rank(b) > rank(a) ? b : a).id;
  }
}

String duplicateReport(DuplicateScanResult result, {required bool csv}) {
  final rows = [
    for (final group in result.groups)
      for (final member in group.members)
        {'group': group.id, 'kind': group.fragment ? 'clip' : group.kind, 'score': group.similarityScore, 'id': member.entryId, 'uri': member.uri, 'path': member.path, 'bytes': member.sizeBytes, 'error': null},
    for (final issue in result.issues) {'group': null, 'kind': 'issue', 'score': null, 'id': issue.entryId, 'uri': issue.uri, 'path': null, 'bytes': null, 'error': '${issue.code} ${issue.message}'},
  ];
  if (!csv) return const JsonEncoder.withIndent('  ').convert({'cancelled': result.cancelled, 'rows': rows});
  String cell(Object? value) {
    var text = value?.toString() ?? '';
    if (value is String && RegExp(r'^\s*[=+@\-\t\r]').hasMatch(text)) text = "'$text";
    return '"${text.replaceAll('"', '""')}"';
  }

  return ['group,kind,score,id,uri,path,bytes,error', for (final row in rows) row.values.map(cell).join(',')].join('\r\n');
}

Future<DuplicateFilters?> chooseDuplicateFilters(BuildContext context, DuplicateFilters current, Iterable<String> albums) async {
  final form = GlobalKey<FormState>();
  String? album = current.album;
  var folder = current.directory, onlyNew = current.onlyNew;
  var dates = current.dates;
  var minSize = current.minMiB?.toString() ?? '', maxSize = current.maxMiB?.toString() ?? '';
  final choices = {...albums, ?album}.toList()..sort();
  return showDialog<DuplicateFilters>(
    context: context,
    builder: (context) => StatefulBuilder(
      builder: (context, refresh) {
        TextFormField sizeField(bool minimum) => TextFormField(
          initialValue: minimum ? minSize : maxSize,
          decoration: InputDecoration(labelText: minimum ? '最小大小（MiB）' : '最大大小（MiB）'),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          onChanged: (text) => minimum ? minSize = text : maxSize = text,
          validator: (text) {
            if (text == null || text.trim().isEmpty) return null;
            final value = double.tryParse(text.trim());
            if (value == null || !value.isFinite || value < 0) return '请输入非负数';
            if (!minimum && value < (double.tryParse(minSize) ?? 0)) return '不能小于最小值';
            return null;
          },
        );
        return AlertDialog(
          title: const Text('筛选扫描范围'),
          content: SingleChildScrollView(
            child: Form(
              key: form,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DropdownButton<String>(
                    isExpanded: true,
                    hint: const Text('全部相册'),
                    value: album,
                    items: [
                      const DropdownMenuItem(child: Text('全部相册')),
                      for (final dir in choices)
                        DropdownMenuItem(
                          value: dir,
                          child: Text(dir, overflow: TextOverflow.ellipsis),
                        ),
                    ],
                    onChanged: (v) => refresh(() => album = v),
                  ),
                  TextFormField(
                    initialValue: folder,
                    decoration: const InputDecoration(labelText: '目录路径（含子目录）'),
                    onChanged: (v) => folder = v,
                  ),
                  sizeField(true),
                  sizeField(false),
                  TextButton(
                    onPressed: () async {
                      final range = await showDateRangePicker(context: context, firstDate: DateTime(1800), lastDate: DateTime(2200), initialDateRange: dates);
                      if (range != null && context.mounted) refresh(() => dates = range);
                    },
                    child: Text(dates == null ? '选择拍摄日期范围' : '${dates!.start.toLocal().toString().split(' ').first} — ${dates!.end.toLocal().toString().split(' ').first}'),
                  ),
                  CheckboxListTile(contentPadding: EdgeInsets.zero, title: const Text('仅显示含新增媒体的分组'), value: onlyNew, onChanged: (v) => refresh(() => onlyNew = v ?? false)),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, const DuplicateFilters()), child: const Text('重置')),
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
            FilledButton(
              onPressed: () {
                if (form.currentState!.validate()) Navigator.pop(context, DuplicateFilters(album: album, directory: folder.trim(), dates: dates, minMiB: double.tryParse(minSize), maxMiB: double.tryParse(maxSize), onlyNew: onlyNew));
              },
              child: const Text('应用'),
            ),
          ],
        );
      },
    ),
  );
}
