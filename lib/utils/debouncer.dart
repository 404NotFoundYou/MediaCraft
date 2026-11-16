import 'dart:async';

import 'package:flutter/foundation.dart';

class Debouncer {
  final Duration delay;

  Timer? _timer;

  new({required this.delay});

  void call(VoidCallback action) {
    cancel();
    _timer = Timer(delay, action);
  }

  void cancel() {
    _timer?.cancel();
    _timer = null;
  }
}
