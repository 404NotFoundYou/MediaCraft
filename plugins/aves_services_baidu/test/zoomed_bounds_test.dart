import 'package:aves_map/aves_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

void main() {
  test('native WGS84 camera center is used when saving a Baidu map selection', () {
    const nativeCenter = LatLng(31.230416, 121.473701);
    const bounds = ZoomedBounds(
      sw: LatLng(31.1, 121.2),
      ne: LatLng(31.4, 121.8),
      zoom: 12,
      rotation: 0,
      center: nativeCenter,
    );

    expect(bounds.projectedCenter, nativeCenter);
  });

  test('bounds without a native center keep the legacy Mercator center', () {
    const bounds = ZoomedBounds(
      sw: LatLng(10, 100),
      ne: LatLng(50, 120),
      zoom: 8,
      rotation: 0,
    );

    expect(bounds.projectedCenter.latitude, closeTo(32.1467014004801, 1e-10));
    expect(bounds.projectedCenter.longitude, closeTo(110, 1e-10));
  });

  test('camera-only copies preserve the native center while new bounds clear it', () {
    const center = LatLng(31.230416, 121.473701);
    const bounds = ZoomedBounds(
      sw: LatLng(31.1, 121.2),
      ne: LatLng(31.4, 121.8),
      zoom: 12,
      rotation: 3,
      center: center,
    );

    final cameraUpdate = bounds.copyWith(zoom: 13, rotation: 10);
    final southwestUpdate = bounds.copyWith(sw: const LatLng(31, 121));
    final northeastUpdate = bounds.copyWith(ne: const LatLng(31.5, 122));

    expect(cameraUpdate.center, center);
    expect(cameraUpdate.zoom, 13);
    expect(cameraUpdate.rotation, 10);
    expect(southwestUpdate.center, isNull);
    expect(northeastUpdate.center, isNull);
  });
}
