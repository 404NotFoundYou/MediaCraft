import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('deckers.thibault/aves/duplicates');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(setUpAllServices);
  tearDownAll(tearDownAllServices);
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('相似扫描使用独立通道并返回相似分组', () async {
    String? calledMethod;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'scanSimilar') {
        expect(call.arguments, isNot(contains('cachedFingerprints')));
        calledMethod = call.method;
        return 'job';
      }
      expect(call.method, 'scanStatus');
      return <String, Object?>{
        'groups': [
          {
            'id': 'image-1-0',
            'sizeBytes': 42,
            'digest': 'image-1',
            'kind': 'image',
            'highConfidence': true,
            'similarityScore': .95,
            'members': [
              {
                'entryId': 1,
                'uri': 'content://media/1',
                'sizeBytes': 42,
                'dateModifiedMillis': null,
                'mimeType': 'image/jpeg',
                'width': 100,
                'height': 100,
                'isVideo': false,
              },
              {
                'entryId': 2,
                'uri': 'content://media/2',
                'sizeBytes': 42,
                'dateModifiedMillis': null,
                'mimeType': 'image/jpeg',
                'width': 100,
                'height': 100,
                'isVideo': false,
              },
            ],
          },
        ],
        'issues': <Object?>[],
        // Older history reports can include a cache payload. Displaying a report ignores it.
        'fingerprints': ['legacy cache'],
        'cancelled': false,
      };
    });

    final first = FakeMediaStoreService.newImage('Pictures', 'first', id: 1)..sizeBytes = 42;
    final second = FakeMediaStoreService.newImage('Pictures', 'second', id: 2)..sizeBytes = 42;
    final event = await DuplicateService().scanSimilar([first, second]).single as DuplicateScanCompleted;

    expect(calledMethod, 'scanSimilar');
    expect(event.result.groups.single.kind, 'image');
    expect(event.result.groups.single.highConfidence, isTrue);
    expect(event.result.groups.single.similarityScore, .95);
  });
}
