import 'dart:async';

import 'package:aves/widgets/viewer/overlay/system_ui_restorer.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('状态通知仍为旧的可见值时也显式请求恢复系统栏', () async {
    final visible = ValueNotifier(true);
    var showCalls = 0;
    addTearDown(visible.dispose);

    await restoreViewerSystemUi(
      statusBarVisibleNotifier: visible,
      showSystemUi: () async => showCalls++,
      visibilityTransitionDelay: Duration.zero,
    );

    expect(showCalls, 1, reason: '在途隐藏的可见性通知可能迟到，不能用旧 true 跳过恢复请求');
  });

  test('重复或迟到的隐藏通知不会提前确认系统栏已恢复', () async {
    final visible = ValueNotifier(false);
    final showRequested = Completer<void>();
    var completed = false;
    addTearDown(visible.dispose);

    final restoration = restoreViewerSystemUi(
      statusBarVisibleNotifier: visible,
      showSystemUi: () async => showRequested.complete(),
      visibilityTransitionDelay: Duration.zero,
    ).then((_) => completed = true);
    await showRequested.future;

    visible.notifyListeners();
    visible.notifyListeners();
    await Future<void>.delayed(Duration.zero);
    expect(completed, isFalse, reason: 'false 通知只表示仍隐藏，不能让离场提前 pop');

    visible.value = true;
    visible.notifyListeners();
    await restoration;

    expect(completed, isTrue);
  });
}
