import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:aves_services_platform/src/coordinates.dart';
import 'package:flutter/services.dart';
import 'package:flutter_baidu_mapapi_base/flutter_baidu_mapapi_base.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart' as ll;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('deckers.thibault/aves/baidu');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<Map<String, dynamic>> measuredGrids;

  setUpAll(() async {
    final localFixture = File('test/fixtures/native_coordinate_grid.json');
    final fixture = localFixture.existsSync() ? localFixture : File('plugins/aves_services_baidu/test/fixtures/native_coordinate_grid.json');
    final json = await fixture.readAsString();
    measuredGrids = (jsonDecode(json) as List).cast<Map<String, dynamic>>();
  });

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('a BD09 map selection round-trips to the original WGS84 photo coordinate', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'toMapCoordinates');
      final points = ((call.arguments as Map)['points'] as List).cast<List>();
      return points.map((point) {
        final latitude = (point[0] as num).toDouble();
        final longitude = (point[1] as num).toDouble();
        return <double>[
          latitude + .006 + math.sin(longitude) * .00001,
          longitude + .0065 + math.cos(latitude) * .00001,
        ];
      }).toList();
    });
    final coordinates = BaiduCoordinates();
    const photoGps = ll.LatLng(31.230416, 121.473701);

    final mapPoint = await coordinates.toMap(photoGps);
    final valueForPhoto = await coordinates.fromMap(mapPoint);

    expect(mapPoint.latitude, isNot(closeTo(photoGps.latitude, .001)));
    expect(mapPoint.longitude, isNot(closeTo(photoGps.longitude, .001)));
    expect(valueForPhoto.latitude, closeTo(photoGps.latitude, .000001));
    expect(valueForPhoto.longitude, closeTo(photoGps.longitude, .000001));
  });

  test('batch conversion deduplicates requests and reuses cached WGS84 conversions', () async {
    var calls = 0;
    List<Object?>? requestedPoints;
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls++;
      requestedPoints = ((call.arguments as Map)['points'] as List).cast<Object?>();
      return requestedPoints!.map((value) {
        final point = (value as List).cast<num>();
        return <double>[point[0] + .006, point[1] + .0065];
      }).toList();
    });
    final coordinates = BaiduCoordinates();
    const first = ll.LatLng(39.9042, 116.4074);
    const second = ll.LatLng(22.5431, 114.0579);

    final converted = await coordinates.toMapMany(const [first, second, first]);
    final cached = await coordinates.toMapMany(const [second, first]);

    expect(calls, 1);
    expect(requestedPoints, const [
      [39.9042, 116.4074],
      [22.5431, 114.0579],
    ]);
    expect(converted, hasLength(3));
    expect(converted[0].latitude, converted[2].latitude);
    expect(cached[0].latitude, converted[1].latitude);
    expect(cached[1].longitude, converted[0].longitude);
  });

  test('camera points between native float32 cells remain usable without saving BD09 as GPS', () async {
    double nativeValue(double value) => Float32List.fromList([value]).single;
    messenger.setMockMethodCallHandler(channel, (call) async {
      final points = ((call.arguments as Map)['points'] as List).cast<List>();
      return points.map((point) => [nativeValue((point[0] as num).toDouble()) + .006, nativeValue((point[1] as num).toDouble()) + .0065]).toList();
    });
    final expectedLatitude = nativeValue(39.908722) + .0000017;
    final expectedLongitude = nativeValue(116.397389) + .0000033;
    final cameraPoint = BMFCoordinate(expectedLatitude + .006, expectedLongitude + .0065);
    final coordinates = BaiduCoordinates();

    final gps = await coordinates.fromMap(cameraPoint);
    final restoredCameraPoint = await coordinates.toMap(gps);

    expect(gps.latitude, closeTo(expectedLatitude, .000003815));
    expect(gps.longitude, closeTo(expectedLongitude, .00000763));
    expect(restoredCameraPoint.latitude, closeTo(cameraPoint.latitude, .000003815));
    expect(restoredCameraPoint.longitude, closeTo(cameraPoint.longitude, .00000763));
    expect(gps.latitude, isNot(closeTo(cameraPoint.latitude, .001)), reason: 'The BD09 offset must not enter a photo GPS value.');
  });

  // Captured from SDK 8.2.0 on Honor Android 12. These camera points never
  // reach the old fixed 1e-6 bound, even after ten native conversion rounds.
  for (final index in [0, 1]) {
    test('native SDK grid $index accepts a camera point at SDK precision and returns WGS84', () async {
      double nativeValue(num value) => Float32List.fromList([value.toDouble()]).single;
      final fixture = measuredGrids[index];
      final samples = (fixture['samples'] as List).cast<Map<String, dynamic>>();
      messenger.setMockMethodCallHandler(channel, (call) async {
        final points = ((call.arguments as Map)['points'] as List).cast<List>();
        return points.map((point) {
          final sample = samples.firstWhere((sample) {
            final source = (sample['source'] as List).cast<num>();
            return nativeValue(source[0]) == nativeValue(point[0] as num) && nativeValue(source[1]) == nativeValue(point[1] as num);
          });
          return sample['mapped'];
        }).toList();
      });
      final target = (fixture['target'] as List).cast<num>();
      final cameraPoint = BMFCoordinate(target[0].toDouble(), target[1].toDouble());
      final coordinates = BaiduCoordinates();

      final gps = await coordinates.fromMap(cameraPoint);
      final mapped = await coordinates.toMap(gps);

      expect(mapped.latitude, closeTo(cameraPoint.latitude, index == 0 ? .000003815 : .000001908));
      expect(mapped.longitude, closeTo(cameraPoint.longitude, .00000763));
      expect(gps.latitude, isNot(closeTo(cameraPoint.latitude, .001)), reason: 'A valid camera selection must be converted back to WGS84 before media editing.');
    });
  }

  test('null converter response fails instead of persisting an unconverted coordinate', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => null);

    await expectLater(BaiduCoordinates().toMap(const ll.LatLng(30, 120)), throwsStateError);
  });

  test('converter response count must match the requested WGS84 batch', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => <Object?>[]);

    await expectLater(BaiduCoordinates().toMap(const ll.LatLng(30, 120)), throwsStateError);
  });

  test('invalid converter coordinates are rejected before they can enter the cache', () async {
    var calls = 0;
    messenger.setMockMethodCallHandler(channel, (_) async {
      calls++;
      return <Object?>[
        <double>[double.nan, 120],
      ];
    });
    final coordinates = BaiduCoordinates();

    await expectLater(coordinates.toMap(const ll.LatLng(30, 120)), throwsArgumentError);
    await expectLater(coordinates.toMap(const ll.LatLng(30, 120)), throwsArgumentError);
    expect(calls, 2, reason: 'An invalid BD09 result must never be cached.');
  });

  test('invalid WGS84 input is rejected without invoking the native converter', () async {
    var calls = 0;
    messenger.setMockMethodCallHandler(channel, (_) async {
      calls++;
      return <Object?>[];
    });

    await expectLater(BaiduCoordinates().toMap(const ll.LatLng(double.infinity, 120)), throwsArgumentError);
    expect(calls, 0);
  });

  test('a forward converter that cannot reproduce the selected map point fails loudly', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      final points = ((call.arguments as Map)['points'] as List).cast<List>();
      return points.map((point) {
        final latitude = (point[0] as num).toDouble();
        final longitude = (point[1] as num).toDouble();
        return <double>[30.001 + 3 * (latitude - 30), 120.001 + 3 * (longitude - 120)];
      }).toList();
    });

    await expectLater(
      BaiduCoordinates().fromMap(BMFCoordinate(30, 120)),
      throwsA(isA<StateError>().having((error) => error.message, 'message', contains('无法准确转换'))),
    );
  });
}
