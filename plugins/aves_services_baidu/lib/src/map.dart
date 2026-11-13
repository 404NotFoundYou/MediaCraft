import 'dart:async';
import 'dart:typed_data';

import 'package:aves_map/aves_map.dart';
import 'package:aves_services_platform/src/coordinates.dart';
import 'package:flutter/material.dart';
import 'package:flutter_baidu_mapapi_base/flutter_baidu_mapapi_base.dart';
import 'package:flutter_baidu_mapapi_map/flutter_baidu_mapapi_map.dart';
import 'package:latlong2/latlong.dart' as ll;
import 'package:provider/provider.dart';

typedef _BaiduMapSession = ({BMFMapController controller, int generation});

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
  final List<BMFPolyline> _trackOverlays = [];

  BMFMapController? _serviceMapController;
  int _sessionGeneration = 0;
  Map<MarkerKey<T>, GeoEntry<T>> _geoEntryByMarkerKey = {};
  Uint8List? _dotMarkerBitmap;
  late Future<BMFCoordinate> _initialCenter;
  bool _overlaySyncRequested = false;
  bool _overlaySyncing = false;
  String? _mapError;
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
            _markerBitmaps[key] = bitmap;
            _scheduleOverlaySync();
          },
        ),
        widget.decoratorBuilder(context, _buildMap()),
        widget.buttonPanelBuilder(context),
        if (_mapError != null || widget.overlayEntry != null)
          Positioned(
            left: 8,
            right: 8,
            bottom: 8,
            child: Material(
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  _mapError ?? '百度地图暂不支持 GeoTIFF 叠加层，请切换到其他地图样式。',
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
    _trackOverlays.clear();
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
    setState(() => _geoEntryByMarkerKey = widget.markerClusterBuilder());
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
          await _syncOverlays(session);
        } catch (error, stack) {
          if (!_isSessionActive(session) || error is _BaiduMapSessionEnded) break;
          debugPrint('Failed to synchronize Baidu map overlays: $error\n$stack');
          _showMapError();
        }
      }
    } finally {
      _overlaySyncing = false;
      if (_overlaySyncRequested && mounted) _scheduleOverlaySync();
    }
  }

  Future<void> _syncOverlays(_BaiduMapSession session) async {
    final controller = session.controller;

    final markers = <BMFMarker>[];
    final markerEntries = _geoEntryByMarkerKey.entries.where((entry) {
      final bytes = _markerBitmaps[entry.key];
      final latitude = entry.value.latitude;
      final longitude = entry.value.longitude;
      return bytes != null && latitude != null && longitude != null;
    }).toList();
    final markerPositions = await coordinates.toMapMany(
      markerEntries.map((entry) => ll.LatLng(entry.value.latitude!, entry.value.longitude!)).toList(),
    );
    _ensureSessionActive(session);
    for (var i = 0; i < markerEntries.length; i++) {
      final entry = markerEntries[i];
      markers.add(
        BMFMarker.iconData(
          position: markerPositions[i],
          iconData: _markerBitmaps[entry.key]!,
          anchorX: .5,
          anchorY: 1,
          customMap: {'markerId': entry.value.markerId},
        ),
      );
    }

    final dotLocation = widget.dotLocationNotifier?.value;
    if (dotLocation != null && _dotMarkerBitmap != null) {
      markers.add(
        BMFMarker.iconData(
          position: await coordinates.toMap(dotLocation),
          iconData: _dotMarkerBitmap!,
          anchorX: .5,
          anchorY: .5,
          zIndex: 1,
          customMap: const {'markerId': 'dot'},
        ),
      );
    }

    final polylines = <BMFPolyline>[];
    for (final track in widget.tracksNotifier.value) {
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

    await _requireSuccess(session, '清理百度地图标记', controller.cleanAllMarkers);
    if (markers.isNotEmpty) await _requireSuccess(session, '添加百度地图标记', () => controller.addMarkers(markers));
    for (final overlay in _trackOverlays) {
      await _requireSuccess(session, '清理百度地图轨迹', () => controller.removeOverlay(overlay.id));
    }
    _ensureSessionActive(session);
    _trackOverlays
      ..clear()
      ..addAll(polylines);
    for (final polyline in polylines) {
      await _requireSuccess(session, '添加百度地图轨迹', () => controller.addPolyline(polyline));
    }
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
