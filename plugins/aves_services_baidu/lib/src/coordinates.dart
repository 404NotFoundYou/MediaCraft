import 'dart:math';

import 'package:flutter/services.dart';
import 'package:flutter_baidu_mapapi_base/flutter_baidu_mapapi_base.dart';
import 'package:latlong2/latlong.dart' as ll;

// All application data stays in WGS84. Only the map adapter sees BD09 coordinates.
class BaiduCoordinates {
  static const _channel = MethodChannel('deckers.thibault/aves/baidu');
  final Map<ll.LatLng, BMFCoordinate> _converted = {};

  Future<BMFCoordinate> toMap(ll.LatLng location) async => (await toMapMany([location])).single;

  Future<List<BMFCoordinate>> toMapMany(List<ll.LatLng> locations) async {
    final missing = locations.where((location) => !_converted.containsKey(location)).toSet().toList();
    if (missing.isNotEmpty) {
      for (final location in missing) {
        _validate(location.latitude, location.longitude);
      }
      final values = await _channel.invokeListMethod<Object?>('toMapCoordinates', {
        'points': missing.map((location) => [location.latitude, location.longitude]).toList(),
      });
      if (values == null || values.length != missing.length) throw StateError('百度地图坐标转换失败');
      for (var i = 0; i < missing.length; i++) {
        final value = (values[i] as List).cast<num>();
        if (value.length != 2) throw StateError('百度地图坐标转换失败');
        final latitude = value[0].toDouble(), longitude = value[1].toDouble();
        _validate(latitude, longitude);
        _converted[missing[i]] = BMFCoordinate(latitude, longitude);
      }
    }
    return locations.map((location) => _converted[location]!).toList();
  }

  // The SDK exposes only GPS -> BD09. Solve against that same forward converter;
  // preserve the strict solution when possible. CoordinateConverter casts its
  // inputs to float32, so arbitrary camera points may be between reachable cells.
  // Otherwise accept only the closest result within that native float32 spacing.
  // This prevents a BD09 map selection from being persisted as an EXIF GPS value.
  Future<ll.LatLng> fromMap(BMFCoordinate location) async {
    _validate(location.latitude, location.longitude);
    var latitude = location.latitude, longitude = location.longitude;
    ll.LatLng? bestGuess;
    var bestError = double.infinity;
    for (var i = 0; i < 10; i++) {
      final guess = ll.LatLng(latitude, longitude);
      final mapped = await toMap(guess);
      final latitudeError = mapped.latitude - location.latitude;
      final longitudeError = mapped.longitude - location.longitude;
      if (latitudeError.abs() < 0.000001 && longitudeError.abs() < 0.000001) return guess;
      final error = max(latitudeError.abs() / _nativePrecision(latitude), longitudeError.abs() / _nativePrecision(longitude));
      if (error < bestError) {
        bestGuess = guess;
        bestError = error;
      }
      latitude -= latitudeError;
      longitude -= longitudeError;
      _validate(latitude, longitude);
    }
    if (bestGuess != null && bestError <= 1) return bestGuess;
    throw StateError('无法准确转换地图坐标，请切换到其他地图样式后选择位置');
  }

  static double _nativePrecision(double coordinate) {
    final value = ByteData(4)..setFloat32(0, coordinate);
    final exponent = ((value.getUint32(0) >> 23) & 0xff) - 127;
    return max(0.000001, pow(2, exponent - 23).toDouble());
  }

  static void _validate(double latitude, double longitude) {
    if (!latitude.isFinite || !longitude.isFinite || latitude.abs() > 90 || longitude.abs() > 180) {
      throw ArgumentError('地图坐标超出有效范围');
    }
  }
}
