import 'dart:convert';

import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/widgets/duplicates/duplicate_options.dart';
import 'package:aves/widgets/editor/transform/cropper.dart';
import 'package:aves/widgets/viewer/controls/cast.dart';
import 'package:aves_video_mpv/src/controller_video.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'dart:ui' as ui;

import '../common.dart';
import '../fake/media_store_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(setUpAllServices);
  tearDownAll(tearDownAllServices);
  test('目录边界、精确相册和大小可同时限定扫描', () {
    final entry = FakeMediaStoreService.newImage('/DCIM/相册/旅行', 'a')..sizeBytes = 1048576;
    expect(const DuplicateFilters(directory: '/DCIM/相册', minMiB: 1, maxMiB: 1).accepts(entry), isTrue);
    expect(const DuplicateFilters(directory: '/DCIM/相').accepts(entry), isFalse);
    expect(const DuplicateFilters(album: '/DCIM/相册').accepts(entry), isFalse);
    expect(const DuplicateFilters(maxMiB: .5).accepts(entry), isFalse);
  });
  test('保留策略以分辨率或指定目录排序，同分保留扫描顺序', () {
    final first = FakeMediaStoreService.newImage('/DCIM', 'a', id: 1);
    final second = FakeMediaStoreService.newImage('/DCIM/pinned', 'b', id: 2)..width *= 2;
    expect(DuplicateKeeper.resolution.choose([first, second], ''), 2);
    expect(DuplicateKeeper.directory.choose([first, second], '/DCIM/pinned'), 2);
    expect(DuplicateKeeper.largest.choose([first, second], ''), 1);
  });
  test('失败媒体与原因同时进入 JSON 和可安全打开的 CSV', () {
    const result = DuplicateScanResult(
      groups: [],
      issues: [DuplicateIssue(entryId: 9, uri: '=1+1', code: 'denied', message: '授权,"过期"')],
      cancelled: false,
    );
    expect(jsonDecode(duplicateReport(result, csv: false))['rows'].single['error'], 'denied 授权,"过期"');
    expect(duplicateReport(result, csv: true), contains('"\'=1+1"'));
    expect(duplicateReport(result, csv: true), contains('"denied 授权,""过期"""'));
  });
  test('旋转和拉直的裁剪框在图像坐标内有有限的平移空间', () {
    const view = Size(240, 180), image = Size(320, 200);
    final crop = Rect.fromCenter(center: view.center(Offset.zero), width: 160, height: 80);
    final normal = cropPanPadding(image, view, crop, Matrix4.identity(), 1);
    final turn = cropPanPadding(image, view, crop, Matrix4.rotationZ(1.5707963267948966), 1);
    expect(normal.left, 40);
    expect(turn.left, closeTo(50, .00001));
    expect(turn.top, closeTo(20, .00001));
    final tilt = cropPanPadding(image, view, crop, Matrix4.rotationZ(.25), .8);
    expect(tilt.left.isFinite && tilt.top.isFinite, isTrue);
    expect(tilt.top, lessThan(normal.top));
  });
  test('截图的旋转实际改变 JPEG 像素，普通查看器可正确显示', () async {
    // 48 x 24, red on the left and black on the right.
    final png = base64Decode('iVBORw0KGgoAAAANSUhEUgAAADAAAAAYCAIAAAAzn+mLAAAANklEQVR4nO3OQREAIAzAMIZ/zyAhnz0bAb3OOztmqXOXOmsakoakIWlIGpKGpCFpSBqShqQh+etiATArC0W7AAAAAElFTkSuQmCC');
    final codec = await ui.instantiateImageCodec(orientCapturedFrame((png, 90)));
    final image = (await codec.getNextFrame()).image;
    expect((image.width, image.height), (24, 48));
    final rgba = (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!;
    expect(rgba.getUint8((6 * image.width + 8) * 4), greaterThan(220));
    expect(rgba.getUint8((40 * image.width + 8) * 4), lessThan(20));
    image.dispose();
    codec.dispose();
  });
  test('仅在设备明确声明不支持时拒绝投屏', () {
    expect(acceptsCastMime(null, 'image/heic'), isTrue);
    expect(acceptsCastMime({'video/*'}, 'image/heic'), isFalse);
    expect(acceptsCastMime({'*'}, 'image/heic'), isTrue);
    expect(acceptsCastMime({'Image/JPEG; profile=a'}, 'image/jpeg'), isTrue);
  });
}
