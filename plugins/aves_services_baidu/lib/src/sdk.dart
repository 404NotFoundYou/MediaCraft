import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class BaiduMapSdk {
  static const channel = MethodChannel('deckers.thibault/aves/baidu');
  static const consentKey = 'baidu_map_privacy_agreed_v1';
  static final error = ValueNotifier<String?>(null);
  static final consent = ValueNotifier(false);
  static Future<void>? _initializing;
  static bool _initialized = false;
  static int _generation = 0;

  static Future<bool> hasApiKey() async {
    channel.setMethodCallHandler((call) async {
      if (call.method == 'onAuthError') error.value = '百度地图授权失败，请选择其他地图样式';
      if (call.method == 'onAuthSuccess') error.value = null;
    });
    await loadConsent();
    final config = await channel.invokeMapMethod<String, Object?>('getConfiguration');
    return config?['hasApiKey'] == true;
  }

  static Future<void> loadConsent() async {
    consent.value = (await SharedPreferences.getInstance()).getBool(consentKey) == true;
  }

  static Future<void> agreeAndInitialize() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(consentKey, true);
    consent.value = true;
    await initialize();
  }

  static Future<void> initialize() async {
    if (!consent.value) throw StateError('未同意百度地图隐私政策');
    if (_initialized) return;
    final pending = _initializing;
    if (pending != null) return pending;

    final generation = _generation;
    final initialization = channel.invokeMethod<void>('initialize', {'privacyAgreed': true});
    _initializing = initialization;
    try {
      await initialization;
      if (generation == _generation && consent.value) _initialized = true;
    } finally {
      if (identical(_initializing, initialization)) _initializing = null;
    }
  }

  static Future<void> revoke() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(consentKey);
    consent.value = false;
    _generation++;
    _initialized = false;
    _initializing = null;

    // Consent listeners remove active platform views in this frame. Revoke the
    // native SDK only after those views have been disposed.
    await SchedulerBinding.instance.endOfFrame;
    await channel.invokeMethod<void>('revokePrivacy');
    error.value = null;
  }

  @visibleForTesting
  static void resetForTesting() {
    consent.value = false;
    error.value = null;
    _generation++;
    _initialized = false;
    _initializing = null;
  }
}
