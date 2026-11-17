import 'dart:async';

typedef ViewerOverlayTransition = Future<void> Function(bool Function() isCurrent);

class ViewerOverlayVisibilityCoordinator {
  int _generation = 0;
  bool _running = false;
  bool _closed = false;
  ({int generation, ViewerOverlayTransition transition})? _pendingTransition;
  Completer<void> _idleCompleter = Completer<void>()..complete();

  Future<void> get pending => _idleCompleter.future;

  Future<void> schedule(ViewerOverlayTransition transition) {
    if (_closed) return pending;
    _pendingTransition = (generation: ++_generation, transition: transition);
    if (!_running) {
      _running = true;
      _idleCompleter = Completer<void>();
      unawaited(_drain());
    }
    return pending;
  }

  Future<void> cancelPending() {
    _generation++;
    _pendingTransition = null;
    return pending;
  }

  Future<void> close() {
    _closed = true;
    return cancelPending();
  }

  Future<void> _drain() async {
    try {
      while (_pendingTransition != null) {
        final current = _pendingTransition!;
        _pendingTransition = null;
        await current.transition(() => current.generation == _generation);
      }
      _idleCompleter.complete();
    } catch (error, stackTrace) {
      _pendingTransition = null;
      _idleCompleter.completeError(error, stackTrace);
    } finally {
      _running = false;
    }
  }
}
