import 'dart:async';
import 'dart:math';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/origins.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/ref/mime_types.dart';
import 'package:aves/services/device_service.dart';
import 'package:aves/services/media/media_session_service.dart';
import 'package:aves/services/window_service.dart';
import 'package:aves/widgets/viewer/video/conductor.dart';
import 'package:aves/widgets/viewer/visual/controller_mixin.dart';
import 'package:aves_model/aves_model.dart';
import 'package:aves_video/aves_video.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../common.dart';
import '../../../fake/media_store_service.dart';

void main() {
  setUpAll(setUpAllServices);
  setUp(() async {
    await setUpServices();
    settings.keepScreenOn = KeepScreenOn.videoPlayback;
  });
  tearDownAll(tearDownAllServices);

  test('closing the viewer waits for an in-flight status update before releasing screen-on', () async {
    final controller = _FakeVideoController(FakeMediaStoreService.newImage('Pictures/test', 'video'));
    final session = _DelayedMediaSessionService();
    final window = _RecordingWindowService();
    final conductor = VideoConductor(
      controllerFactory: _FakeVideoControllerFactory(controller),
      mediaSession: session,
      window: window,
    );
    await conductor.getOrCreateController(controller.entry);

    controller.emit(.playing);
    await session.updateStarted.future;
    final disposing = conductor.dispose();
    session.releaseUpdate.complete();
    await disposing;

    expect(window.keepScreenOnCalls, [false], reason: 'A late playing update must not turn the screen back on after viewer disposal.');
    controller.entry.dispose();
  });

  test('closing during controller preparation does not build a late native controller', () async {
    final controller = _FakeVideoController(FakeMediaStoreService.newImage('Pictures/test', 'video'));
    final factory = _FakeVideoControllerFactory(controller);
    final device = _DelayedDeviceService();
    final conductor = VideoConductor(
      controllerFactory: factory,
      mediaSession: _DelayedMediaSessionService()..releaseUpdate.complete(),
      window: _RecordingWindowService(),
      device: device,
    );

    final creation = conductor.getOrCreateController(controller.entry);
    await device.collectionStarted.future;
    final creationResult = expectLater(creation, throwsStateError);
    final disposing = conductor.dispose();
    device.releaseCollection.complete();
    await creationResult;
    await disposing;

    expect(factory.buildCount, 0, reason: 'Disposal during GC must cancel controller creation before allocating the player.');
    await controller.dispose();
    controller.entry.dispose();
  });

  test('closing the viewer has a bounded wait when media session update never returns', () async {
    final controller = _FakeVideoController(FakeMediaStoreService.newImage('Pictures/test', 'video'));
    final session = _DelayedMediaSessionService();
    final window = _RecordingWindowService();
    final conductor = VideoConductor(
      controllerFactory: _FakeVideoControllerFactory(controller),
      mediaSession: session,
      window: window,
      statusDrainTimeout: const Duration(milliseconds: 1),
    );
    await conductor.getOrCreateController(controller.entry);
    controller.emit(.playing);
    await session.updateStarted.future;

    await conductor.dispose().timeout(const Duration(seconds: 1));

    expect(window.keepScreenOnCalls, [false]);
    session.releaseUpdate.complete();
    await Future<void>.delayed(Duration.zero);
    controller.entry.dispose();
  });

  test('a late screen-on completion restores screen-off after disposal', () async {
    final controller = _FakeVideoController(FakeMediaStoreService.newImage('Pictures/test', 'video'));
    final window = _DelayedPlayingWindowService();
    final conductor = VideoConductor(
      controllerFactory: _FakeVideoControllerFactory(controller),
      mediaSession: _ImmediateMediaSessionService(),
      window: window,
      statusDrainTimeout: const Duration(milliseconds: 1),
    );
    await conductor.getOrCreateController(controller.entry);
    controller.emit(.playing);
    await window.playingStarted.future;

    await conductor.dispose().timeout(const Duration(seconds: 1));
    window.releasePlaying.complete();
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);

    expect(window.appliedValues, [false, true, false]);
    controller.entry.dispose();
  });

  test('media session status updates are serialized and finish with the latest state', () async {
    final controller = _FakeVideoController(FakeMediaStoreService.newImage('Pictures/test', 'video'));
    final session = _SerialMediaSessionService();
    final conductor = VideoConductor(
      controllerFactory: _FakeVideoControllerFactory(controller),
      mediaSession: session,
      window: _RecordingWindowService(),
    );
    await conductor.getOrCreateController(controller.entry);

    controller.emit(.playing);
    await session.firstStarted.future;
    controller.emit(.paused);
    await Future<void>.delayed(Duration.zero);
    expect(session.statuses, <PlaybackStatus>[.playing], reason: 'The paused update must wait for the in-flight native update.');

    session.releaseFirst.complete();
    await session.secondCompleted.future;

    expect(session.maxConcurrentUpdates, 1);
    expect(session.statuses, <PlaybackStatus>[.playing, .paused]);
    await conductor.dispose();
    controller.entry.dispose();
  });

  testWidgets('leaving the viewer during controller preparation does not update disposed state', (tester) async {
    final entry = _videoEntry();
    final controller = _FakeVideoController(entry);
    final factory = _FakeVideoControllerFactory(controller);
    final device = _DelayedDeviceService();
    final conductor = VideoConductor(
      controllerFactory: factory,
      mediaSession: _DelayedMediaSessionService()..releaseUpdate.complete(),
      window: _RecordingWindowService(),
      device: device,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Provider<VideoConductor>.value(
          value: conductor,
          child: _ControllerHarness(entry),
        ),
      ),
    );
    await tester.pump();
    expect(device.collectionStarted.isCompleted, isTrue);
    await tester.pumpWidget(const SizedBox());
    device.releaseCollection.complete();
    await tester.pump();
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(factory.buildCount, 1, reason: 'The conductor may retain a prepared controller, but the old widget must not call setState or autoplay it.');
    await tester.runAsync(conductor.dispose);
    entry.dispose();
  });
}

class _ControllerHarness extends StatefulWidget {
  final AvesEntry entry;

  const new(this.entry);

  @override
  State<_ControllerHarness> createState() => _ControllerHarnessState();
}

class _ControllerHarnessState extends State<_ControllerHarness> with EntryViewControllerMixin<_ControllerHarness> {
  @override
  bool get isViewingImage => true;

  @override
  late final ValueNotifier<AvesEntry?> entryNotifier = ValueNotifier(widget.entry);

  @override
  void initState() {
    super.initState();
    unawaited(initEntryControllers(widget.entry));
  }

  @override
  void dispose() {
    cleanEntryControllers(widget.entry);
    entryNotifier.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => const SizedBox();
}

class _FakeVideoControllerFactory extends Fake implements AvesVideoControllerFactory {
  final AvesVideoController controller;
  int buildCount = 0;

  new(this.controller);

  @override
  AvesVideoController buildController(AvesEntryBase entry, {required PlaybackStateHandler playbackStateHandler, required VideoSettings settings}) {
    buildCount++;
    return controller;
  }
}

class _FakeVideoController extends Fake implements AvesVideoController {
  final StreamController<PlaybackStatus> _status = StreamController.broadcast();

  @override
  final AvesEntry entry;

  PlaybackStatus _current = .paused;

  new(this.entry);

  void emit(PlaybackStatus status) {
    _current = status;
    _status.add(status);
  }

  @override
  Stream<PlaybackStatus> get statusStream => _status.stream;

  @override
  Stream<VideoEvent> get eventStream => const Stream.empty();

  @override
  bool get isPlaying => _current == .playing;

  @override
  PlaybackStatus get status => _current;

  @override
  Future<void> dispose() => _status.close();
}

class _DelayedMediaSessionService extends Fake implements MediaSessionService {
  final Completer<void> updateStarted = Completer();
  final Completer<void> releaseUpdate = Completer();

  @override
  Future<void> update({
    required AvesEntry entry,
    required AvesVideoController controller,
    required bool canSkipToNext,
    required bool canSkipToPrevious,
  }) async {
    updateStarted.complete();
    await releaseUpdate.future;
  }
}

class _RecordingWindowService extends Fake implements WindowService {
  final List<bool> keepScreenOnCalls = [];

  @override
  Future<void> keepScreenOn(bool on) async => keepScreenOnCalls.add(on);
}

class _DelayedPlayingWindowService extends Fake implements WindowService {
  final Completer<void> playingStarted = Completer();
  final Completer<void> releasePlaying = Completer();
  final List<bool> appliedValues = [];

  @override
  Future<void> keepScreenOn(bool on) async {
    if (on) {
      playingStarted.complete();
      await releasePlaying.future;
    }
    appliedValues.add(on);
  }
}

class _ImmediateMediaSessionService extends Fake implements MediaSessionService {
  @override
  Future<void> update({
    required AvesEntry entry,
    required AvesVideoController controller,
    required bool canSkipToNext,
    required bool canSkipToPrevious,
  }) async {}
}

class _SerialMediaSessionService extends Fake implements MediaSessionService {
  final Completer<void> firstStarted = Completer();
  final Completer<void> releaseFirst = Completer();
  final Completer<void> secondCompleted = Completer();
  final List<PlaybackStatus> statuses = [];
  int _activeUpdates = 0, maxConcurrentUpdates = 0;

  @override
  Future<void> update({
    required AvesEntry entry,
    required AvesVideoController controller,
    required bool canSkipToNext,
    required bool canSkipToPrevious,
  }) async {
    _activeUpdates++;
    maxConcurrentUpdates = max(maxConcurrentUpdates, _activeUpdates);
    statuses.add(controller.status);
    try {
      if (statuses.length == 1) {
        firstStarted.complete();
        await releaseFirst.future;
      } else {
        secondCompleted.complete();
      }
    } finally {
      _activeUpdates--;
    }
  }
}

class _DelayedDeviceService extends Fake implements DeviceService {
  final Completer<void> collectionStarted = Completer();
  final Completer<void> releaseCollection = Completer();

  @override
  Future<void> requestGarbageCollection() async {
    collectionStarted.complete();
    await releaseCollection.future;
  }
}

AvesEntry _videoEntry() => AvesEntry(
  id: 1,
  uri: 'content://media/external/video/media/1',
  path: '/storage/emulated/0/Movies/video.mp4',
  contentId: 1,
  pageId: null,
  sourceMimeType: MimeTypes.mp4,
  width: 1920,
  height: 1080,
  sourceRotationDegrees: 0,
  sizeBytes: 1024,
  sourceTitle: 'video',
  dateAddedSecs: 0,
  dateModifiedMillis: 0,
  sourceDateTakenMillis: 0,
  durationMillis: 180000,
  trashed: false,
  origin: EntryOrigins.mediaStoreContent,
);
