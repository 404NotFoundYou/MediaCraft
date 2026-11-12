import 'package:aves/model/duplicates/duplicate_models.dart';

class DuplicateSelection {
  new(Iterable<DuplicateGroup> groups)
    : _membersByGroup = {
        for (final group in groups) group.id: group.members.map((member) => member.entryId).toSet(),
      },
      keepers = {
        for (final group in groups)
          if (group.members.isNotEmpty) group.id: group.members.first.entryId,
      },
      selected = {for (final group in groups) group.id: <int>{}};

  final Map<String, Set<int>> _membersByGroup;
  final Map<String, int> keepers;
  final Map<String, Set<int>> selected;

  Set<int> get selectedIds => selected.values.expand((ids) => ids).toSet();

  DuplicateSelectionSnapshot snapshot() => DuplicateSelectionSnapshot(
    keepers: Map.unmodifiable(keepers),
    selected: Map.unmodifiable({for (final entry in selected.entries) entry.key: Set<int>.unmodifiable(entry.value)}),
  );

  void keep(String groupId, int entryId) {
    if (!(_membersByGroup[groupId]?.contains(entryId) ?? false)) return;
    keepers[groupId] = entryId;
    selected[groupId]?.remove(entryId);
  }

  void select(String groupId, int entryId, bool value) {
    if (!(_membersByGroup[groupId]?.contains(entryId) ?? false) || keepers[groupId] == entryId) return;
    if (value) {
      selected[groupId]?.add(entryId);
    } else {
      selected[groupId]?.remove(entryId);
    }
  }

  bool isValidGroup(String groupId) {
    final members = _membersByGroup[groupId];
    final keeper = keepers[groupId];
    final deleteIds = selected[groupId];
    return members != null && keeper != null && members.contains(keeper) && deleteIds != null && deleteIds.every(members.contains) && !deleteIds.contains(keeper);
  }
}

class DuplicateSelectionSnapshot {
  const new({required this.keepers, required this.selected});

  final Map<String, int> keepers;
  final Map<String, Set<int>> selected;

  Set<int> get selectedIds => selected.values.expand((ids) => ids).toSet();
}
