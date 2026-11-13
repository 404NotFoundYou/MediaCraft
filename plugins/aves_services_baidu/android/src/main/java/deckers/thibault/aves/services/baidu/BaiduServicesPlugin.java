package deckers.thibault.aves.services.baidu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

// Keep SDK initialization behind the map's consent screen, including background engines.
public class BaiduServicesPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
    private static boolean initialized = false;
    private Context context;
    private MethodChannel channel;
    private BroadcastReceiver authReceiver;

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "deckers.thibault/aves/baidu");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        try {
            switch (call.method) {
                case "getConfiguration": {
                    Map<String, Object> config = new HashMap<>();
                    config.put("hasApiKey", hasApiKey());
                    result.success(config);
                    break;
                }
                case "initialize": {
                    if (!Boolean.TRUE.equals(call.argument("privacyAgreed"))) {
                        result.error("baidu-privacy", "Baidu map consent is required", null);
                        return;
                    }
                    if (!hasApiKey()) {
                        result.error("baidu-key", "Baidu map API key is not configured", null);
                        return;
                    }
                    listenForAuthErrors();
                    if (!initialized) {
                        SDKInitializer.setAgreePrivacy(context, true);
                        SDKInitializer.initialize(context);
                        SDKInitializer.setCoordType(CoordType.BD09LL);
                        initialized = true;
                    }
                    result.success(null);
                    break;
                }
                case "toMapCoordinates": {
                    if (!initialized) {
                        result.error("baidu-init", "Baidu map is not initialized", null);
                        return;
                    }
                    List<List<Number>> points = call.argument("points");
                    if (points == null) {
                        result.error("baidu-coordinates", "Coordinates are required", null);
                        return;
                    }
                    List<List<Double>> converted = new ArrayList<>();
                    for (List<Number> point : points) {
                        if (point.size() != 2) throw new IllegalArgumentException("Invalid coordinate");
                        double latitude = point.get(0).doubleValue();
                        double longitude = point.get(1).doubleValue();
                        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                            throw new IllegalArgumentException("Invalid coordinate");
                        }
                        LatLng location = new CoordinateConverter()
                                .from(CoordinateConverter.CoordType.GPS)
                                .coord(new LatLng(latitude, longitude)).convert();
                        if (location == null) throw new IllegalStateException("Coordinate conversion failed");
                        List<Double> item = new ArrayList<>();
                        item.add(location.latitude);
                        item.add(location.longitude);
                        converted.add(item);
                    }
                    result.success(converted);
                    break;
                }
                case "revokePrivacy": {
                    if (initialized) SDKInitializer.setAgreePrivacy(context, false);
                    initialized = false;
                    result.success(null);
                    break;
                }
                default:
                    result.notImplemented();
            }
        } catch (Exception error) {
            result.error("baidu-sdk", error.getClass().getSimpleName(), null);
        }
    }

    private boolean hasApiKey() throws PackageManager.NameNotFoundException {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                context.getPackageName(), PackageManager.GET_META_DATA);
        String key = info.metaData == null ? null : info.metaData.getString("com.baidu.lbsapi.API_KEY");
        return key != null && !key.trim().isEmpty() && !"NONE".equals(key);
    }

    private void listenForAuthErrors() {
        if (authReceiver != null) return;
        authReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivedContext, Intent intent) {
                if (SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR.equals(intent.getAction())) {
                    // Never forward the SDK error text: it may contain the application's AK.
                    channel.invokeMethod("onAuthError", null);
                } else if (SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK.equals(intent.getAction())) {
                    channel.invokeMethod("onAuthSuccess", null);
                }
            }
        };
        IntentFilter filter = new IntentFilter(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
        filter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(authReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(authReceiver, filter);
        }
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        if (authReceiver != null) {
            context.unregisterReceiver(authReceiver);
            authReceiver = null;
        }
    }
}
