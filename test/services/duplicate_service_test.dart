import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/services/duplicate_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('deckers.thibault/aves/duplicates');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('unknown media size is reported instead of silently skipped', () async {
    final entry = FakeMediaStoreService.newImage('DCIM', 'unknown-size')..sizeBytes = null;
    List<dynamic>? platformMembers;
    messenger.setMockMethodCallHandler(channel, (call) async {
      platformMembers = (call.arguments as Map)['members'] as List;
      return <String, Object?>{
        'groups': <Object?>[],
        'issues': <Object?>[],
        'cancelled': false,
      };
    });

    final event = await DuplicateService().scan([entry]).single as DuplicateScanCompleted;

    expect(platformMembers, isEmpty);
    expect(event.result.groups, isEmpty);
    expect(event.result.issues.single.code, 'invalid-size');
    expect(event.result.issues.single.entryId, entry.id);
  });

  test('empty verification is rejected without calling the platform', () async {
    var platformCalls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      platformCalls++;
      return null;
    });
    const keeper = DuplicateMember(entryId: 1, uri: 'content://test/1', sizeBytes: 3, dateModifiedMillis: null);
    const group = DuplicateGroup(id: 'group', sizeBytes: 3, digest: 'digest', members: [keeper]);

    final result = await DuplicateService().verifySelection(group: group, keeper: keeper, deleteMembers: const []);

    expect(result.valid, isFalse);
    expect(result.issues.single.code, 'invalid-selection');
    expect(platformCalls, 0);
  });

  test('keeper repeated as delete member is rejected without calling the platform', () async {
    var platformCalls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      platformCalls++;
      return null;
    });
    const keeper = DuplicateMember(entryId: 1, uri: 'content://test/1', sizeBytes: 3, dateModifiedMillis: null);
    const group = DuplicateGroup(id: 'group', sizeBytes: 3, digest: 'digest', members: [keeper]);

    final result = await DuplicateService().verifySelection(group: group, keeper: keeper, deleteMembers: const [keeper]);

    expect(result.valid, isFalse);
    expect(result.issues.single.code, 'invalid-selection');
    expect(platformCalls, 0);
  });

  test('platform scan error is surfaced and never replaced by an empty result', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'read-failed', message: 'cannot read media');
    });
    final entry = FakeMediaStoreService.newImage('DCIM', 'read-error');

    await expectLater(
      DuplicateService().scan([entry]),
      emitsError(isA<PlatformException>().having((error) => error.code, 'code', 'read-failed')),
    );
  });
}
