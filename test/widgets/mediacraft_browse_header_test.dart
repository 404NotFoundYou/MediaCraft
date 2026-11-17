import 'package:aves/widgets/common/bars/mediacraft_browse_header.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final scale in [1.0, 2.0]) {
    testWidgets('窄屏和 $scale 倍字体下仍能搜索与进入媒体选择', (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      var searches = 0;
      var selections = 0;
      final scaler = TextScaler.linear(scale);
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: MediaQueryData(size: const Size(320, 900), textScaler: scaler),
            child: Scaffold(
              body: Align(
                alignment: Alignment.topCenter,
                child: SizedBox(
                  height: MediaCraftBrowseHeader.preferredHeight(scaler, hasControls: true),
                  child: MediaCraftBrowseHeader(
                    title: '媒体库',
                    subtitle: '已授权的 12 项媒体',
                    searchHint: '搜索媒体',
                    onSearch: () => searches++,
                    trailing: TextButton(onPressed: () => selections++, child: const Text('选择')),
                    controls: MediaCraftBrowseSegments(
                      labels: const {0: '全部', 1: '照片', 2: '视频'},
                      selected: 0,
                      onChanged: (_) {},
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull, reason: '字体放大不应让搜索或选择入口被溢出错误遮挡');
      await tester.tap(find.text('搜索媒体'));
      await tester.tap(find.text('选择'));
      expect(searches, 1);
      expect(selections, 1);
    });
  }
}
