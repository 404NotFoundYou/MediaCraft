import 'dart:async';

import 'package:aves/widgets/viewer/overlay/visibility_coordinator.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('快速显隐只保留尚未开始的最新目标', () async {
    final coordinator = ViewerOverlayVisibilityCoordinator();
    final firstStarted = Completer<void>();
    final releaseFirst = Completer<void>();
    final appliedTargets = <int>[];
    var startedTransitions = 0;

    unawaited(
      coordinator.schedule((isCurrent) async {
        startedTransitions++;
        firstStarted.complete();
        await releaseFirst.future;
        if (isCurrent()) appliedTargets.add(0);
      }),
    );
    await firstStarted.future;

    for (var target = 1; target <= 10; target++) {
      unawaited(
        coordinator.schedule((isCurrent) async {
          startedTransitions++;
          if (isCurrent()) appliedTargets.add(target);
        }),
      );
    }

    releaseFirst.complete();
    await coordinator.pending;

    expect(startedTransitions, 2, reason: '执行中的操作可以收尾，但尚未开始的旧目标必须被最新目标覆盖');
    expect(appliedTargets, [10], reason: '停止操作后应立即收敛到最后一次用户选择');
  });

  test('关闭协调器会取消旧目标并拒绝新的隐藏请求', () async {
    final coordinator = ViewerOverlayVisibilityCoordinator();
    final firstStarted = Completer<void>();
    final releaseFirst = Completer<void>();
    var pendingStarted = false;

    unawaited(
      coordinator.schedule((isCurrent) async {
        firstStarted.complete();
        await releaseFirst.future;
        expect(isCurrent(), isFalse);
      }),
    );
    await firstStarted.future;
    unawaited(coordinator.schedule((isCurrent) async => pendingStarted = true));
    final closed = coordinator.close();
    var startedAfterClose = false;
    unawaited(coordinator.schedule((isCurrent) async => startedAfterClose = true));

    releaseFirst.complete();
    await closed;

    expect(pendingStarted, isFalse, reason: '退出清理之后不能再执行旧的系统栏或动画请求');
    expect(startedAfterClose, isFalse, reason: '离场开始后新的隐藏请求必须被拒绝');
  });

  test('关闭会等待活动隐藏完成，再允许离场恢复系统栏', () async {
    final coordinator = ViewerOverlayVisibilityCoordinator();
    final hideStarted = Completer<void>();
    final releaseHide = Completer<void>();
    final events = <String>[];

    unawaited(
      coordinator.schedule((isCurrent) async {
        events.add('hide-start');
        hideStarted.complete();
        await releaseHide.future;
        events.add('hide-end');
      }),
    );
    await hideStarted.future;

    var closeCompleted = false;
    final leave = coordinator.close().then((_) {
      closeCompleted = true;
      events.add('show');
    });
    await Future<void>.delayed(Duration.zero);

    expect(closeCompleted, isFalse, reason: '系统栏隐藏仍在平台执行时，离场恢复不能抢先完成');
    expect(events, ['hide-start']);

    releaseHide.complete();
    await leave;

    expect(events, ['hide-start', 'hide-end', 'show'], reason: '下一页面的系统栏恢复必须是最后一个平台副作用');
  });
}
