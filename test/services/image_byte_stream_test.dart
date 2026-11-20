import 'dart:async';

import 'package:aves/ref/mime_types.dart';
import 'package:aves/services/media/media_fetch_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('a cancelled image consumer stops the native stream before the image is decoded', () async {
    const channel = MethodChannel('deckers.thibault/aves/media_byte_stream');
    const codec = StandardMethodCodec();
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final listening = Completer<String>();
    final cancelled = Completer<String>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method.startsWith('listen#')) listening.complete(call.method.split('#').last);
      if (call.method.startsWith('cancel#')) cancelled.complete(call.method.split('#').last);
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    final stop = StateError('image is no longer needed');
    final future = PlatformMediaFetchService().getFullImage(
      decoded: false,
      request: ImageRequest(
        'file:///gallery/test.jpg',
        MimeTypes.jpeg,
        rotationDegrees: 0,
        isFlipped: false,
        isAnimated: false,
        pageId: null,
        sizeBytes: 2,
        videoThumbnailMethods: null,
        onBytesReceived: (received, _) {
          expect(received, 1, reason: 'The length header must not count as image data');
          throw stop;
        },
      ),
      decode: (_, {getTargetSize}) async => throw StateError('must not decode an abandoned image'),
    );
    final failed = expectLater(future, throwsA(same(stop)));
    final id = await listening.future;
    final events = '${channel.name}#$id';
    await messenger.handlePlatformMessage(events, codec.encodeSuccessEnvelope(3), null);
    await messenger.handlePlatformMessage(events, codec.encodeSuccessEnvelope(Uint8List.fromList([1])), null);
    await failed;
    expect(await cancelled.future, id);
  });
}
