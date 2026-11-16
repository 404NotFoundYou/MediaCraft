import 'package:aves_services_platform/src/map.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a partial track replacement records only native operations that succeeded', () async {
    final active = ['old'];
    var failSecondAddition = true;

    Future<void> replace() => replaceTrackedOverlays<String>(
      active: active,
      replacements: const ['first', 'second'],
      remove: (_) async {},
      add: (overlay) async {
        if (overlay == 'second' && failSecondAddition) throw StateError('native add failed');
      },
    );

    await expectLater(replace(), throwsStateError);
    expect(active, ['first'], reason: 'An overlay must become active only after the native map accepted it.');

    failSecondAddition = false;
    await replace();
    expect(active, ['first', 'second'], reason: 'The next synchronization must recover after a partial native failure.');
  });

  test('a failed track removal remains recorded so the next synchronization can retry it', () async {
    final active = ['first', 'second'];
    var failSecondRemoval = true;

    Future<void> replace() => replaceTrackedOverlays<String>(
      active: active,
      replacements: const ['replacement'],
      remove: (overlay) async {
        if (overlay == 'second' && failSecondRemoval) throw StateError('native remove failed');
      },
      add: (_) async {},
    );

    await expectLater(replace(), throwsStateError);
    expect(active, ['second'], reason: 'Successful removals must be committed while the failed native removal remains tracked for retry.');

    failSecondRemoval = false;
    await replace();
    expect(active, ['replacement']);
  });
}
