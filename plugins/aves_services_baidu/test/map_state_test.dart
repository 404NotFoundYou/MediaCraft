import 'package:aves_map/aves_map.dart';
import 'package:aves_services_platform/src/map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_baidu_mapapi_map/flutter_baidu_mapapi_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart' as mui show VisualDensity;
import 'package:provider/provider.dart';

void main() {
  testWidgets('a ready marker does not replace existing markers or an unchanged track', (tester) async {
    const mapChannel = MethodChannel('flutter_bmfmap/map_23');
    const coordinates = MethodChannel('deckers.thibault/aves/baidu');
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(coordinates, (call) async => (call.arguments as Map)['points']);
    addTearDown(() => messenger.setMockMethodCallHandler(coordinates, null));

    final commands = <String>[];
    var failNextMarker = false;
    messenger.setMockMethodCallHandler(mapChannel, (call) async {
      final method = call.method.split('/').last;
      if (method == 'addMarker') {
        final markerId = ((call.arguments as Map)['customMap'] as Map)['markerId'];
        commands.add('addMarker:$markerId');
        if (failNextMarker) {
          failNextMarker = false;
          return false;
        }
      } else if (method != 'flutterReady') {
        commands.add(method);
      }
      return true;
    });
    addTearDown(() {
      messenger.setMockMethodCallHandler(mapChannel, null);
      mapChannel.setMethodCallHandler(null);
    });

    const first = MarkerKey('first', null), second = MarkerKey('second', null);
    const points = [LatLng(30, 120), LatLng(31, 121)];
    final clusters = ChangeNotifier();
    final controller = AvesMapController();
    final bounds = ValueNotifier(const ZoomedBounds(sw: LatLng(29, 119), ne: LatLng(32, 123), zoom: 10, rotation: 0));
    final tracks = ValueNotifier<List<GeoTrack>>([const GeoTrack(points: points, color: Colors.red)]);
    addTearDown(() {
      clusters.dispose();
      controller.dispose();
      bounds.dispose();
      tracks.dispose();
    });
    await tester.pumpWidget(
      Provider.value(
        value: const MapThemeData(
          interactive: true,
          showCoordinateFilter: false,
          navigationButton: MapNavigationButton.none,
          scale: AlwaysStoppedAnimation(1.0),
          visualDensity: mui.VisualDensity.standard,
          mapHeight: null,
          attributionPadding: EdgeInsets.zero,
        ),
        child: MaterialApp(
          home: EntryBaiduMap<String>(
            controller: controller,
            clusterListenable: clusters,
            boundsNotifier: bounds,
            style: const EntryMapStyle(key: 'baidu'),
            decoratorBuilder: (_, map) => map!,
            buttonPanelBuilder: (_) => const SizedBox(),
            markerClusterBuilder: () => {
              first: GeoEntry(markerId: 'first', latitude: 30, longitude: 120),
              second: GeoEntry(markerId: 'second', latitude: 31, longitude: 122),
            },
            markerWidgetBuilder: (key) => SizedBox(key: key),
            markerImageReadyChecker: (_) => false,
            dotLocationNotifier: null,
            tracksNotifier: tracks,
          ),
        ),
      ),
    );
    await tester.pump();
    tester.widget<BMFMapWidget>(find.byType(BMFMapWidget)).onBMFMapCreated(BMFMapController.withId(23));
    await tester.pump();
    expect(commands, ['addPolyline']);

    void bitmapReady(MarkerKey<String> key) {
      final generator = tester.widget<MarkerGeneratorWidget<MarkerKey<String>>>(find.byType(MarkerGeneratorWidget<MarkerKey<String>>));
      generator.onRendered(key, Uint8List.fromList([1]));
    }

    bitmapReady(first);
    await tester.pump();
    expect(commands, ['addPolyline', 'addMarker:first']);
    commands.clear();

    failNextMarker = true;
    bitmapReady(second);
    await tester.pump();
    expect(commands, ['addMarker:second'], reason: 'One newly available bitmap must not re-send unrelated native overlays.');
    commands.clear();

    tracks.value = [GeoTrack(points: List.of(points), color: Colors.red)];
    await tester.pump();
    expect(commands, ['cleanAllMarkers', 'addMarker:first', 'addMarker:second'], reason: 'After a partial marker failure, recover marker state without rebuilding an equal track.');

    commands.clear();
    tracks.value = [const GeoTrack(points: points, color: Colors.blue)];
    await tester.pump();
    expect(commands, ['removeOverlay', 'addPolyline'], reason: 'A track change must not resend marker bitmaps.');
    await tester.pumpWidget(const SizedBox());
    commands.clear();
    // The SDK schedules a map-loaded fallback even if the widget was disposed.
    await tester.pump(const Duration(seconds: 3));
    expect(commands, isEmpty, reason: 'An ended map session must ignore late SDK callbacks.');
  }, variant: const TargetPlatformVariant({TargetPlatform.windows}));

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
