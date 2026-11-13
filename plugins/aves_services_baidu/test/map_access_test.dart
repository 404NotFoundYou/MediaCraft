import 'dart:async';

import 'package:aves_map/aves_map.dart';
import 'package:aves_services_platform/aves_services_platform.dart';
import 'package:aves_services_platform/src/sdk.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('deckers.thibault/aves/baidu');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    BaiduMapSdk.resetForTesting();
  });

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('Baidu service adds both Baidu styles and keeps the OpenFreeMap fallback without duplicates', () {
    final serviceStyles = PlatformMobileServices().mapStyles;
    final combined = [...EntryMapStyles.baseStyles, ...serviceStyles];
    final keys = combined.map((style) => style.key).toList();

    expect(
      serviceStyles.map((style) => style.key),
      containsAll(<String>['baiduNormal', 'baiduSatellite', 'openFreeMapBright']),
    );
    expect(keys.toSet(), hasLength(keys.length));
  });

  testWidgets('missing API key keeps style controls visible and never initializes Baidu', (tester) async {
    var initializeCalls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'getConfiguration':
          return <String, Object?>{'hasApiKey': false};
        case 'initialize':
          initializeCalls++;
          return null;
      }
      return null;
    });
    final services = PlatformMobileServices();
    await services.init();

    await tester.pumpWidget(_testApp(services));
    await tester.pumpAndSettle();

    expect(find.text('当前版本未启用百度地图，请选择其他地图样式'), findsOneWidget);
    expect(find.text('style controls'), findsOneWidget);
    expect(find.text('同意并显示地图'), findsNothing);
    expect(initializeCalls, 0);
  });

  testWidgets('configured API key shows privacy consent without initializing the SDK', (tester) async {
    var initializeCalls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'getConfiguration':
          return <String, Object?>{'hasApiKey': true};
        case 'initialize':
          initializeCalls++;
          return null;
      }
      return null;
    });
    final services = PlatformMobileServices();
    await services.init();

    await tester.pumpWidget(_testApp(services));
    await tester.pumpAndSettle();

    expect(find.text('百度地图隐私政策'), findsOneWidget);
    expect(find.text('同意并显示地图'), findsOneWidget);
    expect(find.text('不启用百度地图时，请使用地图样式按钮选择其他地图。'), findsOneWidget);
    expect(find.text('style controls'), findsOneWidget);
    expect(initializeCalls, 0);
  });

  testWidgets('the SDK initializes only after explicit privacy consent', (tester) async {
    final initialization = Completer<void>();
    var initializeCalls = 0;
    Object? initializeArguments;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'getConfiguration':
          return <String, Object?>{'hasApiKey': true};
        case 'initialize':
          initializeCalls++;
          initializeArguments = call.arguments;
          await initialization.future;
          return null;
      }
      return null;
    });
    final services = PlatformMobileServices();
    await services.init();
    await tester.pumpWidget(_testApp(services));
    await tester.pumpAndSettle();
    expect(initializeCalls, 0);

    await tester.tap(find.text('同意并显示地图'));
    await tester.pump();

    expect(initializeCalls, 1);
    expect(initializeArguments, <String, Object?>{'privacyAgreed': true});
    expect(find.text('同意并显示地图'), findsNothing);
    expect((await SharedPreferences.getInstance()).getBool(BaiduMapSdk.consentKey), isTrue);

    BaiduMapSdk.resetForTesting();
    initialization.complete();
    await tester.pump();
  });

  testWidgets('revoked consent is persisted and reopening the map does not initialize the SDK', (tester) async {
    SharedPreferences.setMockInitialValues({BaiduMapSdk.consentKey: true});
    var initializeCalls = 0, revokeCalls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'getConfiguration':
          return <String, Object?>{'hasApiKey': true};
        case 'initialize':
          initializeCalls++;
          return null;
        case 'revokePrivacy':
          revokeCalls++;
          return null;
      }
      return null;
    });
    final services = PlatformMobileServices();
    await services.init();
    await BaiduMapSdk.initialize();
    expect(initializeCalls, 1);

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(body: services.buildPrivacySettings(context)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('撤回百度地图授权'), findsOneWidget);

    await tester.tap(find.text('撤回百度地图授权'));
    await tester.pumpAndSettle();

    expect(revokeCalls, 1);
    expect(BaiduMapSdk.consent.value, isFalse);
    expect((await SharedPreferences.getInstance()).getBool(BaiduMapSdk.consentKey), isNull);

    await tester.pumpWidget(_testApp(services));
    await tester.pumpAndSettle();
    expect(find.text('同意并显示地图'), findsOneWidget);
    expect(initializeCalls, 1);
  });
}

Widget _testApp(PlatformMobileServices services) {
  final controller = AvesMapController();
  final map = services.buildMap<Object>(
    controller: controller,
    clusterListenable: ValueNotifier(0),
    boundsNotifier: ValueNotifier(
      const ZoomedBounds(
        sw: LatLng(30, 120),
        ne: LatLng(31, 121),
        zoom: 10,
        rotation: 0,
      ),
    ),
    style: EntryMapStyles.baiduNormal,
    decoratorBuilder: (_, child) => child ?? const SizedBox(),
    buttonPanelBuilder: (_) => const Text('style controls'),
    markerClusterBuilder: () => {},
    markerWidgetBuilder: (_) => const SizedBox(),
    markerImageReadyChecker: (_) => true,
    dotLocationNotifier: null,
    tracksNotifier: ValueNotifier(const []),
    overlayOpacityNotifier: null,
    overlayEntry: null,
    onUserZoomChange: null,
    onMapTap: null,
    onMarkerTap: null,
    onMarkerLongPress: null,
  );
  return MaterialApp(home: Scaffold(body: map));
}
