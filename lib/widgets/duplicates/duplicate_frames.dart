import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

Future<void> showDuplicateFrames(BuildContext context, DuplicateGroup group, DuplicateService service) {
  final reference = group.members.first;
  final rows = [
    for (final member in group.members.skip(1))
      for (final frame in group.frames[member.entryId] ?? []) (member: member, frame: frame),
  ];
  final cache = <(int, int), Future<Uint8List?>>{};
  Widget thumb(DuplicateMember member, int time) => Column(
    children: [
      FutureBuilder<Uint8List?>(
        future: cache.putIfAbsent((member.entryId, time), () => service.preview(member, time)),
        builder: (context, state) {
          final bytes = state.data;
          return SizedBox(
            width: 80,
            height: 58,
            child: bytes != null ? Image.memory(bytes, fit: BoxFit.contain) : Center(child: Text(state.connectionState == ConnectionState.done ? '读取失败' : '读取中')),
          );
        },
      ),
      Text('${(time / 1000).toStringAsFixed(2)} 秒'),
    ],
  );
  return showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(group.fragment ? '片段对照' : '采样帧对照'),
      content: SizedBox(
        width: 390,
        height: 400,
        child: ListView.builder(
          itemCount: rows.length,
          itemBuilder: (context, index) {
            final row = rows[index];
            return Semantics(
              label: '参照项 ${reference.entryId}，对照项 ${row.member.entryId}',
              child: Column(
                children: [
                  if (index == 0 || rows[index - 1].member != row.member) Text('${reference.path ?? reference.uri}\n对照 ${row.member.path ?? row.member.uri}', maxLines: 4, overflow: TextOverflow.ellipsis),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(child: thumb(reference, row.frame.reference)),
                      Text(row.frame.matched ? '匹配' : '有差异'),
                      Flexible(child: thumb(row.member, row.frame.member)),
                    ],
                  ),
                ],
              ),
            );
          },
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('关闭'))],
    ),
  );
}
