import 'dart:async';
import 'dart:convert';
import 'dart:ui' as ui;

import 'package:aves/image_providers/thumbnail_provider.dart';
import 'package:aves/services/common/service_policy.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/media/media_fetch_service.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('eviction ends the old thumbnail request without poisoning a new resolve of the same key', () async {
    final cache = PaintingBinding.instance.imageCache;
    final fetch = _ScheduledThumbnails();
    getIt.registerSingleton<MediaFetchService>(fetch);
    final policy = platformServicePolicy..debugConcurrentTaskMaxOverride = 1;
    final gate = Completer<void>();
    final blocker = policy.call(() => gate.future, key: gate);
    addTearDown(() async {
      if (!gate.isCompleted) gate.complete();
      await blocker;
      policy.debugConcurrentTaskMaxOverride = null;
      cache.clear();
      cache.clearLiveImages();
      await getIt.unregister<MediaFetchService>();
    });

    ThumbnailProvider provider(String name) => ThumbnailProvider(
      ThumbnailProviderKey(uri: '$name.png', mimeType: 'image/png', pageId: null, rotationDegrees: 0, isFlipped: false, dateModifiedMillis: 0, videoThumbnailMethods: null),
    );

    final first = provider('old'), retained = provider('retained');
    final oldStream = first.resolve(ImageConfiguration.empty);
    final cancelled = Completer<Object>();
    final cancelledListener = ImageStreamListener((info, _) => info.dispose(), onError: (error, _) => cancelled.complete(error));
    oldStream.addListener(cancelledListener);
    first.pause();
    final retainedStream = retained.resolve(ImageConfiguration.empty);
    retained.pause();

    final fillers = List.generate(127, (i) {
      final future = policy.call(() async => i, key: i, priority: ServiceCallPriority.getRegion);
      expect(fetch.cancelRegion(i), isTrue);
      return future;
    });
    expect(cache.statusForKey(first.key).pending, isFalse, reason: 'Returning immediately must not reuse a cancelled pending image.');
    final reloadedStream = first.resolve(ImageConfiguration.empty);
    expect(reloadedStream.completer, isNot(same(oldStream.completer)));
    expect(retained.resolve(ImageConfiguration.empty).completer, same(retainedStream.completer));

    Future<ImageInfo> load(ImageStream stream) {
      final image = Completer<ImageInfo>();
      final listener = ImageStreamListener((info, _) => image.complete(info), onError: image.completeError);
      stream.addListener(listener);
      return image.future.whenComplete(() => stream.removeListener(listener));
    }

    final loaded = [load(reloadedStream), load(retainedStream)];
    for (var i = 0; i < fillers.length; i++) {
      unawaited(policy.resume<int>(i));
    }
    gate.complete();
    expect(await cancelled.future, isA<CancelledException>());
    oldStream.removeListener(cancelledListener);
    await Future.wait(fillers);
    for (final image in await Future.wait(loaded)) {
      expect(image.image.width, 1);
      image.dispose();
    }
    expect(fetch.loads, [first.key, retained.key], reason: 'Only the resumed and freshly requested decodes should run.');
  });
}

class _ScheduledThumbnails extends PlatformMediaFetchService {
  final loads = <ThumbnailProviderKey>[];

  @override
  Future<ui.Codec> getThumbnail({
    required bool decoded,
    required ThumbnailProviderKey request,
    ImageDecoderCallback? decode,
    Object? taskKey,
    int? priority,
  }) => platformServicePolicy.call(
    () {
      loads.add(request);
      return ui.instantiateImageCodec(base64Decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAANSURBVBhXY/jPwPAfAAUAAf+mXJtdAAAAAElFTkSuQmCC'));
    },
    key: taskKey,
    priority: ServiceCallPriority.getFastThumbnail,
  );
}
