import 'dart:async';

import 'package:aves/model/settings/settings.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/widgets/common/bars/app_bar.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../common.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  testWidgets('返回浅色浏览页后状态栏区域重新使用深色图标', (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 852));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final navigatorKey = GlobalKey<NavigatorState>();
    const mediaQuery = MediaQueryData(size: Size(412, 852), padding: EdgeInsets.only(top: 24));
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<Settings>.value(value: settings),
          Provider<DurationsData>.value(value: DurationsData.noAnimation()),
        ],
        child: MaterialApp(
          navigatorKey: navigatorKey,
          theme: ThemeData(
            appBarTheme: const AppBarThemeData(
              titleTextStyle: TextStyle(fontSize: 20),
              systemOverlayStyle: SystemUiOverlayStyle.dark,
            ),
          ),
          builder: (context, child) => MediaQuery(data: mediaQuery, child: child!),
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                AvesAppBar(
                  contentHeight: kToolbarHeight,
                  pinned: true,
                  leading: null,
                  title: const Text('媒体库'),
                  actions: (context, maxWidth) => [],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    SystemUiOverlayStyle? statusBarStyle() => tester.binding.renderViews.single.debugLayer?.find<SystemUiOverlayStyle>(const Offset(206, 12));

    expect(statusBarStyle()?.statusBarIconBrightness, Brightness.dark, reason: '浏览页的样式必须覆盖顶部安全区，不能只覆盖工具栏内容');
    unawaited(
      navigatorKey.currentState!.push<void>(
        MaterialPageRoute(
          builder: (context) => const AnnotatedRegion<SystemUiOverlayStyle>(
            value: SystemUiOverlayStyle.light,
            child: Scaffold(backgroundColor: Colors.black, body: SizedBox.expand()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(statusBarStyle()?.statusBarIconBrightness, Brightness.light);

    navigatorKey.currentState!.pop();
    await tester.pumpAndSettle();
    expect(statusBarStyle()?.statusBarIconBrightness, Brightness.dark, reason: '查看器退出后不能把白色图标留在浅色浏览页上');
  });
}
