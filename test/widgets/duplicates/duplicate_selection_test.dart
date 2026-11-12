import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/widgets/duplicates/duplicate_selection.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const group = DuplicateGroup(
    id: 'same-bytes',
    sizeBytes: 42,
    digest: 'digest',
    members: [
      DuplicateMember(
        entryId: 1,
        uri: 'content://1',
        sizeBytes: 42,
        dateModifiedMillis: 1,
      ),
      DuplicateMember(
        entryId: 2,
        uri: 'content://2',
        sizeBytes: 42,
        dateModifiedMillis: 1,
      ),
    ],
  );

  test('扫描结果默认不选择任何待清理文件', () {
    final selection = DuplicateSelection([group]);

    expect(selection.keepers[group.id], 1);
    expect(selection.selectedIds, isEmpty);
  });

  test('保留项不能同时进入待清理集合', () {
    final selection = DuplicateSelection([group]);

    selection.select(group.id, 1, true);
    expect(selection.selectedIds, isEmpty);

    selection.select(group.id, 2, true);
    selection.keep(group.id, 2);
    expect(selection.selectedIds, isEmpty);
    expect(selection.isValidGroup(group.id), isTrue);
  });

  test('保留项缺失时分组计划无效', () {
    final selection = DuplicateSelection([group]);

    selection.keepers[group.id] = 99;
    selection.select(group.id, 2, true);

    expect(selection.isValidGroup(group.id), isFalse);
  });

  test('提交快照不受后续选区修改影响', () {
    final selection = DuplicateSelection([group]);
    selection.select(group.id, 2, true);
    final snapshot = selection.snapshot();

    selection.keep(group.id, 2);

    expect(snapshot.keepers[group.id], 1);
    expect(snapshot.selectedIds, {2});
    expect(selection.selectedIds, isEmpty);
  });
}
