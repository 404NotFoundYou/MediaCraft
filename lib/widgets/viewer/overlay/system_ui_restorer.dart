import 'dart:async';

import 'package:flutter/foundation.dart';

Future<void> restoreViewerSystemUi({
  required ValueNotifier<bool> statusBarVisibleNotifier,
  required Future<void> Function() showSystemUi,
  Duration visibilityTransitionDelay = const Duration(milliseconds: 50),
}) async {
  final waitForVisibility = !statusBarVisibleNotifier.value;
  final ready = Completer<void>();
  void onStatusBarVisibleChanged() async {
    await Future<void>.delayed(visibilityTransitionDelay);
    if (!statusBarVisibleNotifier.value || ready.isCompleted) return;
    ready.complete();
  }

  if (waitForVisibility) {
    statusBarVisibleNotifier.addListener(onStatusBarVisibleChanged);
  }
  try {
    await showSystemUi();
    if (waitForVisibility) {
      // as of Flutter v3.47.5, a white/black flash appears on page transition
      // from a viewer with a transparent background and no system UI
      // to a regular page with system UI (tested on Android 15)
      // so we wait until the system UI is ready
      await ready.future;
    }
  } finally {
    if (waitForVisibility) {
      statusBarVisibleNotifier.removeListener(onStatusBarVisibleChanged);
    }
  }
}
