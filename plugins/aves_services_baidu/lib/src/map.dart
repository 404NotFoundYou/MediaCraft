import 'dart:async';
import 'dart:typed_data';

import 'package:aves_map/aves_map.dart';
import 'package:aves_services_platform/src/coordinates.dart';
import 'package:flutter/foundation.dart' show listEquals;
import 'package:flutter/material.dart';
import 'package:flutter_baidu_mapapi_base/flutter_baidu_mapapi_base.dart';
import 'package:flutter_baidu_mapapi_map/flutter_baidu_mapapi_map.dart';
import 'package:latlong2/latlong.dart' as ll;
import 'package:provider/provider.dart';

typedef _BaiduMapSession = ({BMFMapController controller, int generation});
typedef _MarkerVisual = ({ll.LatLng position, Uint8List bitmap, String? markerId});

@visibleForTesting
Future<void> replaceTrackedOverlays<T>({
  required List<T> active,
  required List<T> replacements,
  required Future<void> Function(T overlay) remove,
  required Future<void> Function(T overlay) add,
}) async {
  for (final overlay in List<T>.of(active)) {
    await remove(overlay);
    active.remove(overlay);
  }
  for (final overlay in replacements) {
    await add(overlay);
    active.add(overlay);
  }
}

@visibleForTesting
void pruneMarkerBitmaps<K>(Map<K, Uint8List> bitmaps, Set<K> currentKeys) {
  bitmaps.removeWhere((key, _) => !currentKeys.contains(key));
}

class _BaiduMapSessionEnded implements Exception {
  const new();
}

class EntryBaiduMap<T> extends StatefulWidget {
  final AvesMapController controller;
  final Listenable clusterListenable;
  final ValueNotifier<ZoomedBounds> boundsNotifier;
  final double? minZoom;
  final double? maxZoom;
  final EntryMapStyle style;
  final TransitionBuilder decoratorBuilder;
  final WidgetBuilder buttonPanelBuilder;
  final MarkerClusterBuilder<T> markerClusterBuilder;
  final MarkerWidgetBuilder<T> markerWidgetBuilder;
  final MarkerImageReadyChecker<T> markerImageReadyChecker;
  final ValueNotifier<ll.LatLng?>? dotLocationNotifier;
  final ValueNotifier<List<GeoTrack>> tracksNotifier;
  final ValueNotifier<double>? overlayOpacityNotifier;
  final MapOverlay? overlayEntry;
  final UserZoomChangeCallback? onUserZoomChange;
  final MapTapCallback? onMapTap;
  final MarkerTapCallback<T>? onMarkerTap;
  final MarkerLongPressCallback<T>? onMarkerLongPress;

  const new({
    super.key,
    required this.controller,
    required this.clusterListenable,
    required this.boundsNotifier,
    this.minZoom,
    this.maxZoom,
    required this.style,
    required this.decoratorBuilder,
    required this.buttonPanelBuilder,
    required this.markerClusterBuilder,
    required this.markerWidgetBuilder,
    required this.markerImageReadyChecker,
    required this.dotLocationNotifier,
    required this.tracksNotifier,
    this.overlayOpacityNotifier,
    this.overlayEntry,
    this.onUserZoomChange,
    this.onMapTap,
    this.onMarkerTap,
    this.onMarkerLongPress,
  });

  @override
  State<EntryBaiduMap<T>> createState() => _EntryBaiduMapState<T>();
}

class _EntryBaiduMapState<T> extends State<EntryBaiduMap<T>> {
  final BaiduCoordinates coordinates = BaiduCoordinates();
  final List<StreamSubscription<Object?>> _subscriptions = [];
  final Map<MarkerKey<T>, Uint8List> _markerBitmaps = {};
  final Map<LocalKey, ({_MarkerVisual visual, BMFMarker marker})> _activeMarkers = {};
  final List<BMFPolyline> _trackOverlays = [];

  List<GeoTrack>? _syncedTracks;
  bool _markersNeedReset = false;
  BMFMapController? _serviceMapController;
  int _sessionGeneration = 0;
  Map<MarkerKey<T>, GeoEntry<T>> _geoEntryByMarkerKey = {};
  Uint8List? _dotMarkerBitmap;
  late Future<BMFCoordinate> _initialCenter;
  bool _overlaySyncRequested = false;
  bool _overlaySyncing = false;
  String? _mapError, _overlayError;
  int _boundsRequest = 0;

  ZoomedBounds get bounds => widget.boundsNotifier.value;

  @override
  void initState() {
    super.initState();
    _initialCenter = coordinates.toMap(bounds.projectedCenter);
    _registerWidget(widget);
  }

  @override
  void didUpdateWidget(covariant EntryBaiduMap<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    _unregisterWidget(oldWidget);
    _registerWidget(widget);
    if (widget.style != oldWidget.style) {
      _runCurrent(_updateMapStyle);
    }
    _scheduleOverlaySync();
  }

  @override
  void dispose() {
    _serviceMapController = null;
    _sessionGeneration++;
    _boundsRequest++;
    _overlaySyncRequested = false;
    _unregisterWidget(widget);
    super.dispose();
  }

  void _registerWidget(EntryBaiduMap<T> widget) {
    _subscriptions.add(widget.controller.moveCommands.listen((event) => _runCurrent((session) => _moveTo(session, event.latLng))));
    _subscriptions.add(widget.controller.zoomCommands.listen((event) => _runCurrent((session) => _zoomBy(session, event.delta))));
    _subscriptions.add(widget.controller.rotationResetCommands.listen((_) => _runCurrent(_resetRotation)));
    widget.clusterListenable.addListener(_updateMarkers);
    widget.dotLocationNotifier?.addListener(_scheduleOverlaySync);
    widget.tracksNotifier.addListener(_scheduleOverlaySync);
  }

  void _unregisterWidget(EntryBaiduMap<T> widget) {
    widget.clusterListenable.removeListener(_updateMarkers);
    widget.dotLocationNotifier?.removeListener(_scheduleOverlaySync);
    widget.tracksNotifier.removeListener(_scheduleOverlaySync);
    _subscriptions
      ..forEach((subscription) => subscription.cancel())
      ..clear();
  }

  void _scheduleOverlaySync() {
    if (!mounted || _currentSession == null) return;
    _overlaySyncRequested = true;
    if (!_overlaySyncing) unawaited(_drainOverlaySync());
  }

  @override
  Widget build(BuildContext context) {
    final error = _mapError ?? _overlayError;
    return Stack(
      children: [
        MarkerGeneratorWidget<Key>(
          markers: const [DotMarker(key: Key('dot'))],
          isReadyToRender: (key) => true,
          onRendered: (key, bitmap) {
            _dotMarkerBitmap = bitmap;
            _scheduleOverlaySync();
          },
        ),
        MarkerGeneratorWidget<MarkerKey<T>>(
          markers: _geoEntryByMarkerKey.keys.map(widget.markerWidgetBuilder).toList(),
          isReadyToRender: widget.markerImageReadyChecker,
          onRendered: (key, bitmap) {
            if (!_geoEntryByMarkerKey.containsKey(key)) return;
            _markerBitmaps[key] = bitmap;
            _scheduleOverlaySync();
          },
        ),
        widget.decoratorBuilder(context, _buildMap()),
        widget.buttonPanelBuilder(context),
        if (error != null || widget.overlayEntry != null)
          Positioned(
            left: 8,
            right: 8,
            bottom: 8,
            child: Material(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  error ?? '百度地图暂不支持 GeoTIFF 叠加层，请切换到其他地图样式。',
                  style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildMap() {
    final interactive = context.select<MapThemeData, bool>((value) => value.interactive);
    return FutureBuilder<BMFCoordinate>(
      future: _initialCenter,
      builder: (context, snapshot) {
        final center = snapshot.data;
        if (snapshot.hasError) {
          return Center(child: Text('百度地图坐标转换失败：${snapshot.error}'));
        }
        if (center == null) return const SizedBox();

        return BMFMapWidget(
          mapOptions: _buildMapOptions(interactive, center: center),
          onBMFMapCreated: _onMapCreated,
        );
      },
    );
  }

  BMFMapOptions _buildMapOptions(bool interactive, {BMFCoordinate? center}) {
    return BMFMapOptions(
      mapType: widget.style.key == 'baiduSatellite' ? BMFMapType.Satellite : BMFMapType.Standard,
      center: center,
      zoomLevel: bounds.zoom.round().clamp(4, 21),
      minZoomLevel: widget.minZoom?.round().clamp(4, 21),
      maxZoomLevel: widget.maxZoom?.round().clamp(4, 21),
      rotation: -bounds.rotation,
      gesturesEnabled: interactive,
      zoomEnabled: interactive,
      zoomEnabledWithTap: interactive,
      zoomEnabledWithDoubleClick: interactive,
      scrollEnabled: interactive,
      overlookEnabled: false,
      rotateEnabled: interactive,
      showMapScaleBar: false,
      showZoomControl: false,
    );
  }

  void _onMapCreated(BMFMapController controller) {
    if (!mounted) return;
    _serviceMapController = controller;
    final session = (controller: controller, generation: ++_sessionGeneration);
    _activeMarkers.clear();
    _markersNeedReset = false;
    _trackOverlays.clear();
    _syncedTracks = null;
    _mapError = _overlayError = null;
    controller.setMapDidLoadCallback(
      callback: () {
        if (!_isSessionActive(session)) return;
        _updateMarkers();
        _runSafely(session, () => _updateBoundsAndNotifyIdle(session));
        _scheduleOverlaySync();
      },
    );
    controller.setMapStatusDidChangedCallback(callback: () => _runSafely(session, () => _updateBoundsAndNotifyIdle(session)));
    controller.setMapOnClickedMapBlankCallback(
      callback: (coordinate) => _runSafely(session, () => _dispatchMapTap(session, coordinate)),
    );
    controller.setMapOnLongClickCallback(
      callback: (coordinate) => _runSafely(session, () => _dispatchMapLongPress(session, coordinate)),
    );
    controller.setMapClickedMarkerCallback(callback: (marker) => _dispatchMarkerTap(session, marker));
    controller.setMapRenderValidDataCallback(
      callback: (isValid, errorCode, _) {
        if (_isSessionActive(session) && !isValid) {
          debugPrint('Baidu map rendering failed with errorCode=$errorCode');
          _showMapError();
        }
      },
    );
    // 4.0.2 may emit mapDidLoad before Dart registers its callback on a cold
    // start. Seed markers from the already known bounds without relying on it.
    _updateMarkers();
  }

  void _updateMarkers() {
    if (!mounted) return;
    final markers = widget.markerClusterBuilder();
    pruneMarkerBitmaps(_markerBitmaps, markers.keys.toSet());
    setState(() => _geoEntryByMarkerKey = markers);
    _scheduleOverlaySync();
  }

  Future<void> _drainOverlaySync() async {
    _overlaySyncing = true;
    try {
      while (_overlaySyncRequested && mounted) {
        _overlaySyncRequested = false;
        final session = _currentSession;
        if (session == null) break;
        try {
          await Future.wait([_syncMarkers(session), _syncTracks(session)]);
          if (_isSessionActive(session) && _overlayError != null) {
            setState(() => _overlayError = null);
          }
        } catch (error, stack) {
          if (!_isSessionActive(session) || error is _BaiduMapSessionEnded) break;
          debugPrint('Failed to synchronize Baidu map overlays: $error\n$stack');
          if (_overlayError == null) {
            setState(() => _overlayError = '百度地图标记或轨迹暂时无法显示');
          }
        }
      }
    } finally {
      _overlaySyncing = false;
      if (_overlaySyncRequested && mounted) _scheduleOverlaySync();
    }
  }

  Future<void> _syncMarkers(_BaiduMapSession session) async {
    final controller = session.controller;
    const dotKey = ValueKey('dot');
    final desired = <LocalKey, _MarkerVisual>{};
    for (final MapEntry(:key, value: entry) in _geoEntryByMarkerKey.entries) {
      final bitmap = _markerBitmaps[key];
      final latitude = entry.latitude, longitude = entry.longitude;
      if (bitmap != null && latitude != null && longitude != null) {
        desired[key] = (position: ll.LatLng(latitude, longitude), bitmap: bitmap, markerId: entry.markerId);
      }
    }
    final dotLocation = widget.dotLocationNotifier?.value;
    final dotBitmap = _dotMarkerBitmap;
    if (dotLocation != null && dotBitmap != null) {
      desired[dotKey] = (position: dotLocation, bitmap: dotBitmap, markerId: 'dot');
    }

    if (_markersNeedReset) {
      await _requireSuccess(session, '清理百度地图标记', controller.cleanAllMarkers);
      _activeMarkers.clear();
      _markersNeedReset = false;
    }
    final changed = desired.entries.where((kv) => _activeMarkers[kv.key]?.visual != kv.value).toList();
    final obsolete = _activeMarkers.keys.where((key) => _activeMarkers[key]!.visual != desired[key]).toList();
    if (changed.isEmpty && obsolete.isEmpty) return;

    final positions = await coordinates.toMapMany(changed.map((kv) => kv.value.position).toList());
    _ensureSessionActive(session);
    try {
      for (final key in obsolete) {
        final marker = _activeMarkers[key]!.marker;
        await _requireSuccess(session, '移除百度地图标记', () => controller.removeMarker(marker));
        _activeMarkers.remove(key);
      }
      for (var i = 0; i < changed.length; i++) {
        final MapEntry(:key, value: visual) = changed[i];
        final isDot = key == dotKey;
        final marker = BMFMarker.iconData(
          position: positions[i],
          iconData: visual.bitmap,
          anchorX: .5,
          anchorY: isDot ? .5 : 1,
          zIndex: isDot ? 1 : 0,
          customMap: {'markerId': visual.markerId},
        );
        await _requireSuccess(session, '添加百度地图标记', () => controller.addMarker(marker));
        _activeMarkers[key] = (visual: visual, marker: marker);
      }
    } catch (_) {
      // Native failures can have side effects before the reply arrives.
      if (_isSessionActive(session)) _markersNeedReset = true;
      rethrow;
    }
  }

  Future<void> _syncTracks(_BaiduMapSession session) async {
    final tracks = widget.tracksNotifier.value;
    final previous = _syncedTracks;
    if (identical(previous, tracks) || (previous != null && previous.length == tracks.length && Iterable<int>.generate(tracks.length).every((i) => previous[i].color == tracks[i].color && listEquals(previous[i].points, tracks[i].points)))) {
      _syncedTracks = tracks;
      return;
    }
    final polylines = <BMFPolyline>[];
    for (final track in tracks) {
      if (track.points.length < 2) continue;
      final points = await coordinates.toMapMany(track.points);
      _ensureSessionActive(session);
      polylines.add(
        BMFPolyline.colorline(
          coordinates: points,
          strokerColor: track.color,
          width: MapThemeData.trackWidth.round(),
          dottedLine: false,
          lineCapType: BMFLineCapType.LineCapRound,
          lineJoinType: BMFLineJoinType.LineJoinRound,
          clickable: false,
        ),
      );
    }

    _syncedTracks = null;
    await replaceTrackedOverlays(
      active: _trackOverlays,
      replacements: polylines,
      remove: (overlay) => _requireSuccess(session, '清理百度地图轨迹', () => session.controller.removeOverlay(overlay.id)),
      add: (overlay) => _requireSuccess(session, '添加百度地图轨迹', () => session.controller.addPolyline(overlay)),
    );
    _ensureSessionActive(session);
    _syncedTracks = tracks;
  }

  Future<void> _dispatchMapTap(_BaiduMapSession session, BMFCoordinate coordinate) async {
    final callback = widget.onMapTap;
    if (callback != null) {
      final location = await coordinates.fromMap(coordinate);
      _ensureSessionActive(session);
      callback(location);
    }
  }

  Future<void> _dispatchMapLongPress(_BaiduMapSession session, BMFCoordinate coordinate) async {
    final callback = widget.onMarkerLongPress;
    if (callback == null) return;
    final location = await coordinates.fromMap(coordinate);
    _ensureSessionActive(session);
    final match = ImageMarker.markerMatch(location, bounds.zoom, _geoEntryByMarkerKey.values.toSet());
    if (match != null) callback(match, location);
  }

  void _dispatchMarkerTap(_BaiduMapSession session, BMFMarker marker) {
    if (!_isSessionActive(session)) return;
    final markerId = marker.customMap?['markerId'] as String?;
    if (markerId == null || markerId == 'dot') return;
    for (final geoEntry in _geoEntryByMarkerKey.values) {
      if (geoEntry.markerId == markerId) {
        widget.onMarkerTap?.call(geoEntry);
        return;
      }
    }
  }

  Future<void> _updateBoundsAndNotifyIdle(_BaiduMapSession session) async {
    final request = ++_boundsRequest;
    final controller = session.controller;
    final status = await _invoke(session, controller.getMapStatus);
    final coordinateBounds = status?.coordinateBounds ?? await _invoke(session, controller.getVisibleMapBounds);
    if (status == null || coordinateBounds == null) return;

    final sw = await coordinates.fromMap(coordinateBounds.southwest);
    _ensureSessionActive(session);
    final ne = await coordinates.fromMap(coordinateBounds.northeast);
    _ensureSessionActive(session);
    final cameraCenter = status.targetGeoPt;
    final center = cameraCenter == null ? null : await coordinates.fromMap(cameraCenter);
    _ensureSessionActive(session);
    if (request != _boundsRequest) return;
    final updatedBounds = ZoomedBounds(
      sw: sw,
      ne: ne,
      zoom: status.fLevel ?? bounds.zoom,
      rotation: -(status.fRotation ?? 0),
      center: center,
    );
    widget.boundsNotifier.value = updatedBounds;
    widget.controller.notifyIdle(updatedBounds);
    _updateMarkers();
  }

  Future<void> _moveTo(_BaiduMapSession session, ll.LatLng location) async {
    final point = await coordinates.toMap(location);
    await _requireSuccess(
      session,
      '移动百度地图',
      () => session.controller.setCenterCoordinate(point, true, animateDurationMs: 250),
    );
  }

  Future<void> _zoomBy(_BaiduMapSession session, double delta) async {
    final status = await _invoke(session, session.controller.getMapStatus);
    final zoom = (status?.fLevel ?? bounds.zoom) + delta;
    final targetZoom = zoom.clamp(widget.minZoom ?? 4, widget.maxZoom ?? 21).toDouble();
    final center = await coordinates.toMap(bounds.projectedCenter);
    _ensureSessionActive(session);
    widget.onUserZoomChange?.call(targetZoom);
    await _requireSuccess(
      session,
      '缩放百度地图',
      () => session.controller.setNewLatLngZoom(coordinate: center, zoom: targetZoom, animateDurationMs: 250),
    );
  }

  Future<void> _resetRotation(_BaiduMapSession session) async {
    await _requireSuccess(
      session,
      '重置百度地图方向',
      () => session.controller.setNewMapStatus(mapStatus: BMFMapStatus(fRotation: 0), animateDurationMs: 250),
    );
  }

  Future<void> _updateMapStyle(_BaiduMapSession session) async {
    final interactive = context.read<MapThemeData>().interactive;
    final center = await coordinates.toMap(bounds.projectedCenter);
    await _requireSuccess(
      session,
      '切换百度地图样式',
      () => session.controller.updateMapOptions(_buildMapOptions(interactive, center: center)),
    );
  }

  void _runSafely(_BaiduMapSession session, Future<void> Function() operation) {
    if (!_isSessionActive(session)) return;
    unawaited(() async {
      try {
        await operation();
      } catch (error, stack) {
        if (!_isSessionActive(session) || error is _BaiduMapSessionEnded) return;
        debugPrint('Baidu map operation failed: $error\n$stack');
        _showMapError();
      }
    }());
  }

  void _runCurrent(Future<void> Function(_BaiduMapSession session) operation) {
    final session = _currentSession;
    if (session != null) _runSafely(session, () => operation(session));
  }

  void _showMapError() {
    if (!mounted || _mapError != null) return;
    setState(() => _mapError = '百度地图暂不可用，请切换其他地图样式');
  }

  _BaiduMapSession? get _currentSession {
    final controller = _serviceMapController;
    return controller == null ? null : (controller: controller, generation: _sessionGeneration);
  }

  bool _isSessionActive(_BaiduMapSession session) => mounted && identical(_serviceMapController, session.controller) && _sessionGeneration == session.generation;

  void _ensureSessionActive(_BaiduMapSession session) {
    if (!_isSessionActive(session)) throw const _BaiduMapSessionEnded();
  }

  Future<R> _invoke<R>(_BaiduMapSession session, Future<R> Function() operation) async {
    _ensureSessionActive(session);
    final result = await operation();
    _ensureSessionActive(session);
    return result;
  }

  Future<void> _requireSuccess(_BaiduMapSession session, String action, Future<bool> Function() operation) async {
    if (!await _invoke(session, operation)) throw StateError('$action失败');
  }
}
