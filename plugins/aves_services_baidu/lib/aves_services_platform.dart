import 'package:aves_map/aves_map.dart';
import 'package:aves_services/aves_services.dart';
import 'package:aves_services_platform/src/map.dart';
import 'package:aves_services_platform/src/sdk.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart';
import 'package:url_launcher/url_launcher.dart';

class PlatformMobileServices extends MobileServices {
  bool _hasApiKey = false;
  String? _configurationError;

  @override
  Future<void> init() async {
    try {
      _hasApiKey = await BaiduMapSdk.hasApiKey();
    } catch (error, stack) {
      _configurationError = '百度地图暂不可用，请选择其他地图样式';
      debugPrint('Failed to read Baidu map configuration: $error\n$stack');
    }
  }

  @override
  bool get isServiceAvailable => _hasApiKey && _configurationError == null;

  @override
  Widget buildPrivacySettings(BuildContext context) {
    return Material(
      type: MaterialType.transparency,
      child: ValueListenableBuilder<bool>(
        valueListenable: BaiduMapSdk.consent,
        builder: (context, consent, _) => ListTile(
          enabled: consent,
          leading: const Icon(Icons.map_outlined),
          title: const Text('撤回百度地图授权'),
          subtitle: const Text('撤回后，百度地图不会再初始化，直到你重新同意'),
          onTap: consent
              ? () async {
                  try {
                    await BaiduMapSdk.revoke();
                  } catch (error, stack) {
                    debugPrint('Failed to revoke Baidu map privacy consent: $error\n$stack');
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('撤回授权未完成，请重启应用后重试')),
                      );
                    }
                  }
                }
              : null,
        ),
      ),
    );
  }

  @override
  EntryMapStyle? get defaultMapStyle => EntryMapStyles.openFreeMapBright;

  @override
  List<EntryMapStyle> get mapStyles => [EntryMapStyles.baiduNormal, EntryMapStyles.baiduSatellite, EntryMapStyles.openFreeMapBright];

  @override
  Widget buildMap<T>({
    required AvesMapController controller,
    required Listenable clusterListenable,
    required ValueNotifier<ZoomedBounds> boundsNotifier,
    required EntryMapStyle style,
    required TransitionBuilder decoratorBuilder,
    required WidgetBuilder buttonPanelBuilder,
    required MarkerClusterBuilder<T> markerClusterBuilder,
    required MarkerWidgetBuilder<T> markerWidgetBuilder,
    required MarkerImageReadyChecker<T> markerImageReadyChecker,
    required ValueNotifier<LatLng?>? dotLocationNotifier,
    required ValueNotifier<List<GeoTrack>> tracksNotifier,
    required ValueNotifier<double>? overlayOpacityNotifier,
    required MapOverlay? overlayEntry,
    required UserZoomChangeCallback? onUserZoomChange,
    required MapTapCallback? onMapTap,
    required MarkerTapCallback<T>? onMarkerTap,
    required MarkerLongPressCallback<T>? onMarkerLongPress,
  }) {
    return _BaiduMapAccess(
      unavailableMessage: _configurationError ?? (!_hasApiKey ? '当前版本未启用百度地图，请选择其他地图样式' : null),
      decoratorBuilder: decoratorBuilder,
      buttonPanelBuilder: buttonPanelBuilder,
      mapBuilder: (_) => EntryBaiduMap<T>(
        controller: controller,
        clusterListenable: clusterListenable,
        boundsNotifier: boundsNotifier,
        style: style,
        decoratorBuilder: decoratorBuilder,
        buttonPanelBuilder: buttonPanelBuilder,
        markerClusterBuilder: markerClusterBuilder,
        markerWidgetBuilder: markerWidgetBuilder,
        markerImageReadyChecker: markerImageReadyChecker,
        dotLocationNotifier: dotLocationNotifier,
        tracksNotifier: tracksNotifier,
        overlayOpacityNotifier: overlayOpacityNotifier,
        overlayEntry: overlayEntry,
        onUserZoomChange: onUserZoomChange,
        onMapTap: onMapTap,
        onMarkerTap: onMarkerTap,
        onMarkerLongPress: onMarkerLongPress,
      ),
    );
  }
}

class _BaiduMapAccess extends StatefulWidget {
  final String? unavailableMessage;
  final TransitionBuilder decoratorBuilder;
  final WidgetBuilder buttonPanelBuilder, mapBuilder;

  const new({required this.unavailableMessage, required this.decoratorBuilder, required this.buttonPanelBuilder, required this.mapBuilder});

  @override
  State<_BaiduMapAccess> createState() => _BaiduMapAccessState();
}

class _BaiduMapAccessState extends State<_BaiduMapAccess> {
  bool _ready = false, _busy = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadConsent();
  }

  Future<void> _loadConsent() async {
    if (widget.unavailableMessage == null) {
      try {
        if (BaiduMapSdk.consent.value) {
          await BaiduMapSdk.initialize();
          _ready = BaiduMapSdk.consent.value;
        }
      } catch (error, stack) {
        _error = '百度地图初始化失败，请选择其他地图样式';
        debugPrint('Failed to initialize Baidu map: $error\n$stack');
      }
    }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _agree() async {
    setState(() {
      _busy = true;
      _ready = false;
      _error = null;
    });
    try {
      await BaiduMapSdk.agreeAndInitialize();
      _ready = BaiduMapSdk.consent.value;
    } catch (error, stack) {
      _error = '百度地图初始化失败，请选择其他地图样式';
      debugPrint('Failed to initialize Baidu map: $error\n$stack');
    }
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([BaiduMapSdk.error, BaiduMapSdk.consent]),
      builder: (context, _) {
        final sdkError = BaiduMapSdk.error.value;
        final error = widget.unavailableMessage ?? _error ?? sdkError;
        if (_ready && !_busy && BaiduMapSdk.consent.value && error == null) return widget.mapBuilder(context);
        return Stack(
          children: [
            widget.decoratorBuilder(
              context,
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: _busy
                      ? const CircularProgressIndicator()
                      : error != null
                      ? Text(error, textAlign: TextAlign.center)
                      : SingleChildScrollView(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Text('百度地图将联网加载地图，并由百度 SDK 处理必要的设备与网络信息。请阅读隐私政策后选择是否启用。', textAlign: TextAlign.center),
                              TextButton(
                                onPressed: () async {
                                  final opened = await launchUrl(Uri.parse('https://lbsyun.baidu.com/index.php?title=openprivacy'), mode: LaunchMode.externalApplication);
                                  if (!opened && mounted) setState(() => _error = '暂时无法打开隐私政策，请稍后重试');
                                },
                                child: const Text('百度地图隐私政策'),
                              ),
                              FilledButton(onPressed: _agree, child: const Text('同意并显示地图')),
                              const Text('不启用百度地图时，请使用地图样式按钮选择其他地图。', textAlign: TextAlign.center),
                            ],
                          ),
                        ),
                ),
              ),
            ),
            widget.buttonPanelBuilder(context),
          ],
        );
      },
    );
  }
}
