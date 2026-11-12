import 'package:aves/model/entry/entry.dart';
import 'package:aves/widgets/collection/entry_set_action_delegate.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  testWidgets('移动入口向实际移动链路传递文件字节保护标志', (tester) async {
    final delegate = _RecordingMoveDelegate();
    final entry = _TestEntry();
    late BuildContext context;
    await tester.pumpWidget(
      Builder(
        builder: (value) {
          context = value;
          return const SizedBox();
        },
      ),
    );

    var callbackInvoked = false;
    final completed = await delegate.doMove(
      context,
      moveType: MoveType.fromBin,
      entries: {entry},
      preserveFileBytes: true,
      beforeMove: () async {
        callbackInvoked = true;
        return true;
      },
    );

    expect(completed, isTrue);
    expect(delegate.preserveFileBytes, isTrue);
    expect(delegate.beforeMoveWasProvided, isTrue);
    expect(callbackInvoked, isTrue);
  });
}

class _TestEntry extends AvesEntry {
  new()
    : super(
        id: 1,
        uri: 'content://media/1',
        path: null,
        contentId: 1,
        pageId: null,
        sourceMimeType: 'image/jpeg',
        width: 1,
        height: 1,
        sourceRotationDegrees: 0,
        sizeBytes: 42,
        sourceTitle: 'duplicate',
        dateAddedSecs: 1,
        dateModifiedMillis: 1,
        sourceDateTakenMillis: null,
        durationMillis: null,
        trashed: true,
        origin: 0,
      );

  @override
  String? get directory => '/storage/emulated/0/Pictures';
}

class _RecordingMoveDelegate extends EntrySetActionDelegate {
  bool? preserveFileBytes;
  bool beforeMoveWasProvided = false;

  @override
  Future<bool> doQuickMove(
    BuildContext context, {
    required MoveType moveType,
    required Map<String, Set<AvesEntry>> entriesByDestination,
    bool hideShowAction = false,
    bool preserveFileBytes = false,
    Future<bool> Function()? beforeMove,
    VoidCallback? onSuccess,
  }) async {
    this.preserveFileBytes = preserveFileBytes;
    beforeMoveWasProvided = beforeMove != null;
    if (beforeMove != null && !await beforeMove()) return false;
    return true;
  }
}
