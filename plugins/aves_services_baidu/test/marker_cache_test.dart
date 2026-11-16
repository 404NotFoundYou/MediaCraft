import 'dart:typed_data';

import 'package:aves_map/aves_map.dart';
import 'package:aves_services_platform/src/map.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget testHost(Widget child) => MaterialApp(
    home: MediaQuery(
      data: const MediaQueryData(size: Size.zero),
      child: child,
    ),
  );

  Future<void> pumpUntilRendered(WidgetTester tester, List<Uint8List> rendered) async {
    for (var i = 0; i < 20 && rendered.isEmpty; i++) {
      await tester.pump(const Duration(milliseconds: 1));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 1)));
    }
  }

  testWidgets('marker generator releases render nodes that are no longer requested', (tester) async {
    const generatorKey = Key('generator');

    Widget build(List<Key> markerKeys) {
      return testHost(
        MarkerGeneratorWidget<Key>(
          key: generatorKey,
          markers: markerKeys.map((key) => SizedBox(key: key, width: 10, height: 10)).toList(),
          isReadyToRender: (_) => false,
          onRendered: (_, _) {},
        ),
      );
    }

    Finder renderNodes() => find.descendant(
      of: find.byKey(generatorKey),
      matching: find.byType(RepaintBoundary),
    );

    await tester.pumpWidget(build(const [Key('a'), Key('b')]));
    expect(renderNodes(), findsNWidgets(2));

    await tester.pumpWidget(build(const [Key('b')]));
    expect(renderNodes(), findsOneWidget, reason: 'Offscreen media must not leave render nodes retained for the lifetime of the map.');
  });

  testWidgets('marker rendering retries after its first frame has no size', (tester) async {
    const markerKey = Key('marker');
    final rendered = <Uint8List>[];
    final markerSize = ValueNotifier<double>(0);
    addTearDown(markerSize.dispose);

    final host = testHost(
      MarkerGeneratorWidget<Key>(
        markers: [
          ValueListenableBuilder<double>(
            key: markerKey,
            valueListenable: markerSize,
            builder: (_, size, _) => SizedBox(width: size, height: size),
          ),
        ],
        isReadyToRender: (_) => true,
        onRendered: (_, bitmap) => rendered.add(bitmap),
      ),
    );

    await tester.pumpWidget(host);
    await tester.pump();
    expect(rendered, isEmpty);

    markerSize.value = 10;
    await pumpUntilRendered(tester, rendered);

    expect(rendered, hasLength(1), reason: 'Rendering must retry after the child gains size without requiring a parent map update.');
  });

  testWidgets('removing and re-adding the same marker key ignores the obsolete item', (tester) async {
    const markerKey = Key('marker');
    final rendered = <Uint8List>[];

    Widget build(List<Widget> markers) => testHost(
      MarkerGeneratorWidget<Key>(
        markers: markers,
        isReadyToRender: (_) => true,
        onRendered: (_, bitmap) => rendered.add(bitmap),
      ),
    );

    await tester.pumpWidget(build(const [SizedBox(key: markerKey)]));
    await tester.pumpWidget(build(const []));
    await tester.pumpWidget(build(const [SizedBox(key: markerKey, width: 10, height: 10)]));
    await pumpUntilRendered(tester, rendered);

    expect(rendered, hasLength(1), reason: 'Only the newly inserted item may publish a bitmap for the reused key.');
  });

  test('marker bitmap cache retains only markers in the current map region', () {
    final bitmaps = <String, Uint8List>{
      'old': Uint8List(1),
      'current': Uint8List(1),
    };

    pruneMarkerBitmaps(bitmaps, const {'current'});

    expect(bitmaps.keys, ['current'], reason: 'PNG data for markers outside the current region must be released.');
  });
}
