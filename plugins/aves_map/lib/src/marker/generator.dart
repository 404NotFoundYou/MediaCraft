import 'dart:ui' as ui;

import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:material_ui/material_ui.dart';

// generate bitmap from widget, for Google map
class const MarkerGeneratorWidget<T extends Key>({
  super.key,
  required final List<Widget> markers,
  required final bool Function(T markerKey) isReadyToRender,
  required final void Function(T markerKey, Uint8List bitmap) onRendered,
}) extends StatefulWidget {
  @override
  State<MarkerGeneratorWidget<T>> createState() => _MarkerGeneratorWidgetState<T>();
}

class _MarkerGeneratorWidgetState<T extends Key> extends State<MarkerGeneratorWidget<T>> {
  final Set<_MarkerGeneratorItem<T>> _items = {};
  bool _frameCheckPending = false;

  @override
  void initState() {
    super.initState();
    _checkNextFrame();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncItems();
  }

  @override
  void didUpdateWidget(covariant MarkerGeneratorWidget<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncItems();
    _checkNextFrame();
  }

  void _syncItems() {
    final devicePixelRatio = MediaQuery.devicePixelRatioOf(context);
    final markerKeys = widget.markers.map((marker) => marker.key as T).toSet();
    _items.removeWhere((item) => !markerKeys.contains(item.markerKey));
    widget.markers.forEach((markerWidget) {
      final item = _getOrCreate(
        markerKey: markerWidget.key as T,
        devicePixelRatio: devicePixelRatio,
      );
      item.globalKey = GlobalKey();
    });
  }

  void _checkNextFrame() {
    if (_frameCheckPending) return;
    _frameCheckPending = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        if (!mounted) return;
        final readyItems = _items.where((item) => item.isWaiting && widget.isReadyToRender(item.markerKey)).toSet();
        await Future.wait(
          readyItems.map((item) async {
            final bitmap = await item.render();
            if (bitmap != null && mounted && _items.contains(item)) {
              widget.onRendered(item.markerKey, bitmap);
            }
          }),
        );
      } finally {
        _frameCheckPending = false;
        if (mounted && _items.any((item) => item.isWaiting)) {
          setState(() {});
          _checkNextFrame();
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Transform.translate(
      offset: Offset(MediaQuery.sizeOf(context).width, 0),
      child: Material(
        type: MaterialType.transparency,
        child: Stack(
          children: _items.map((item) {
            return RepaintBoundary(
              key: item.globalKey,
              child: widget.markers.firstWhereOrNull((v) => v.key == item.markerKey) ?? const SizedBox(),
            );
          }).toList(),
        ),
      ),
    );
  }

  _MarkerGeneratorItem _getOrCreate({
    required T markerKey,
    required double devicePixelRatio,
  }) {
    final existingItem = _items.firstWhereOrNull((v) => v.markerKey == markerKey);
    if (existingItem != null) return existingItem;

    final newItem = _MarkerGeneratorItem(
      markerKey: markerKey,
      devicePixelRatio: devicePixelRatio,
    );
    _items.add(newItem);
    return newItem;
  }
}

enum MarkerGeneratorItemState { waiting, rendering, done }

class _MarkerGeneratorItem<T extends Key>({
  required final T markerKey,
  required final double devicePixelRatio,
}) {
  GlobalKey? globalKey;
  MarkerGeneratorItemState state = MarkerGeneratorItemState.waiting;

  bool get isWaiting => state == MarkerGeneratorItemState.waiting;

  Future<Uint8List?> render() async {
    Uint8List? bytes;
    final _globalKey = globalKey;
    state = MarkerGeneratorItemState.rendering;
    try {
      final renderObject = _globalKey?.currentContext?.findRenderObject();
      if (renderObject is RenderRepaintBoundary) {
        final boundary = renderObject;
        if (boundary.hasSize && boundary.size != Size.zero) {
          final image = await boundary.toImage(pixelRatio: devicePixelRatio);
          try {
            final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
            bytes = byteData?.buffer.asUint8List();
          } finally {
            image.dispose();
          }
        }
      }
    } catch (error) {
      // The render object may disappear or become temporarily unavailable between frames.
      debugPrint('failed to render image for key=$_globalKey with error=$error');
    } finally {
      state = bytes != null ? MarkerGeneratorItemState.done : MarkerGeneratorItemState.waiting;
    }
    return bytes;
  }

  @override
  String toString() => '$runtimeType#${shortHash(this)}{markerKey=$markerKey, globalKey=$globalKey, state=$state}';
}
