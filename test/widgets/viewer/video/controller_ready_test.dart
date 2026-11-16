import 'dart:async';

import 'package:aves/model/settings/settings.dart';
import 'package:aves_video/aves_video.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../common.dart';
import '../../../fake/media_store_service.dart';

void main() {
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  test('untilReady completes normally when playback becomes ready', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'video');
    final controller = _TestVideoController(entry);
    final ready = controller.untilReady;

    controller.emit(.playing);

    await ready;
    await controller.dispose();
    entry.dispose();
  });

  test('untilReady fails immediately when the controller is already in error', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'video');
    final controller = _TestVideoController(entry)..emit(.error);

    await expectLater(controller.untilReady, throwsStateError);
    await controller.dispose();
    entry.dispose();
  });

  test('untilReady fails when playback reports an error', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'video');
    final controller = _TestVideoController(entry);
    final ready = controller.untilReady;

    controller.emit(.error);

    await expectLater(ready, throwsStateError);
    await controller.dispose();
    entry.dispose();
  });

  test('untilReady forwards stream errors', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'video');
    final controller = _TestVideoController(entry);
    final ready = controller.untilReady;

    controller.emitError(StateError('decoder failed'));

    await expectLater(ready, throwsStateError);
    await controller.dispose();
    entry.dispose();
  });

  test('untilReady fails instead of hanging when the controller is disposed', () async {
    final entry = FakeMediaStoreService.newImage('Pictures/test', 'video');
    final controller = _TestVideoController(entry);
    final ready = controller.untilReady;
    final readyResult = expectLater(ready, throwsStateError);

    await controller.dispose();

    await readyResult;
    entry.dispose();
  });
}

class _TestVideoController extends AvesVideoController {
  final StreamController<PlaybackStatus> _statuses = StreamController.broadcast();
  PlaybackStatus _status = .idle;

  new(super.entry) : super(playbackStateHandler: _NoopPlaybackStateHandler(), settings: settings);

  void emit(PlaybackStatus status) {
    _status = status;
    _statuses.add(status);
  }

  void emitError(Object error) => _statuses.addError(error);

  @override
  PlaybackStatus get status => _status;

  @override
  Stream<PlaybackStatus> get statusStream => _statuses.stream;

  @override
  bool get isReady => _status == .playing;

  @override
  int get duration => 0;

  @override
  int get currentPosition => 0;

  @override
  void onVisualChanged() {}

  @override
  Future<void> dispose() async {
    await _statuses.close();
    await super.dispose();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _NoopPlaybackStateHandler implements PlaybackStateHandler {
  @override
  Future<int?> getResumeTime({required int entryId, required BuildContext context}) async => null;

  @override
  Future<void> saveResumeTime({required int entryId, required int position, required double progress}) async {}
}
