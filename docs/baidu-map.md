# 百度地图 flavor

MediaCraft 通过独立的 `baidu` flavor 集成百度地图 SDK。该 APK 使用
`plugins/aves_services_baidu` 提供内嵌百度地图，同时继续保留 OpenFreeMap、
OpenTopoMap、OSM HOT 和 Stamen Watercolor 等非商业地图样式。此集成不会跳转
到百度地图 App，也不会改变 `libre`、`play` 或 `izzy` flavor 的依赖。

百度平台实现固定使用 Flutter 百度地图插件 `flutter_baidu_mapapi_map 4.0.2` 和
`flutter_baidu_mapapi_base 4.0.2`。升级这两个插件时必须同步更新百度 flavor 的
锁文件并重新完成地图交互与 release 构建验证。

Android SDK Map/Util 使用 8.2.0。百度 Flutter 插件遗留的
`android.arch.lifecycle:extensions` 会引入与 AndroidX 重复的 support 类，
因此只在 `baidu` configuration 中排除此旧依赖，继续使用插件源码已采用的
AndroidX 生命周期实现。百度 JNI 类的保留规则只由百度插件传入。
百度 Flutter 地图插件的编译 SDK 另行对齐到项目的 37，以满足当前生命周期插件
要求的 36+；这不改变应用的最低 Android 版本，也不跳过 release AAR 校验。

百度和 C2PA 都携带 `libc++_shared.so`。已对三个 ABI 核对动态符号，C2PA 0.0.15
的运行库满足百度 8.2.0 的全部 C++ 符号需求。百度 variant 选择这一份运行库，
`assembleBaidu*` 会校验最终 APK 中每个百度 ABI 的运行库 SHA-256；依赖顺序或
SDK 版本变化导致哈希不符时构建失败，不能直接放宽校验绕过冲突。

## 百度开放平台配置

在百度地图开放平台创建 Android 类型的 AK，并绑定实际发布包名和签名 SHA-1：

- release 包名：`com.mediacraft.app.baidu`
- debug 包名：`com.mediacraft.app.baidu.debug`

release 和 debug 使用不同签名时，应分别登记对应 SHA-1。AK 可以通过以下任一方式
传入构建：

1. 在未提交版本控制的 `android/key.properties` 中增加
   `baiduApiKey=<BAIDU_MAP_API_KEY>`。
2. 设置环境变量 `MEDIACRAFT_BAIDU_API_KEY`。

本地属性优先于环境变量。未配置时构建占位值为 `NONE`，APK 可以完成编译，但百度
底图鉴权不会成功。不要把真实 AK 写入 `android/key_template.properties` 或提交到
版本控制。

2026-11-13 本地 release APK 经 `apksigner verify --verbose --print-certs` 核验，
实际证书 SHA-1 为 `58:27:DB:E8:AC:62:AE:52:D8:68:F4:D8:C0:10:EB:FF:B7:B0:09:FC`。
该值只适用于当前本地 release keystore；使用其他签名或应用商店重签名时，应从
实际交付 APK 重新提取。debug 签名不能用这个 release 值代替。

## 应用 flavor

首次生成或依赖版本变化后，在 Git Bash 或兼容 Bash 的环境执行：

```bash
scripts/gen_flavor_baidu.sh
```

日常切换到已锁定的百度依赖：

```bash
scripts/apply_flavor_baidu.sh
```

Windows PowerShell 可以直接使用 `./scripts/apply_flavor_baidu.ps1`；后续构建命令中的
`./flutterw` 替换为 `./.tooling/flutter/bin/flutter.bat`。

构建 debug APK：

```bash
./flutterw build apk --debug --flavor baidu -t lib/main_baidu.dart
```

构建 release APK：

```bash
./flutterw build apk --release --flavor baidu -t lib/main_baidu.dart
```

## 坐标与隐私

媒体 EXIF GPS 坐标通常按 WGS84 保存，百度地图使用 BD-09LL。百度平台实现必须在
渲染标记、轨迹、地图中心时调用 SDK 的 GPS → BD09 转换。SDK 没有反向 WGS84
接口，因此地图点选通过同一正向转换器做有界数值反求，只有正向校验误差小于
0.000001 度时才返回 WGS84；不收敛会报错，绝不把 BD09 当作媒体 GPS 保存。
这不等于已完成实际地理精度验收，仍须用真实 GPS 样本验证国内、境外及边界位置。

百度样式不支持 GeoTIFF 图片叠加，界面会明确提示切换到其他地图样式。

百度 SDK 只在用户选择百度样式并真实同意隐私条款后初始化；同意状态保存在应用的
本地偏好中。未同意时继续允许选择 OpenFreeMap 等地图样式。默认样式保持为
OpenFreeMap Bright。SDK 初始化调用完成与异步 AK 授权成功是两个检查点，授权失败
会在地图区域显示提示。
用户可以在“设置 → 隐私 → 权限”撤回百度地图授权；撤回先销毁地图视图，再调用
SDK 的隐私撤回接口，并清除本地同意记录。下次选择百度样式需要重新同意。
SDK 未提供完整反初始化 API，因此这里不承诺进程内所有底层线程立即停止。

## 发布验证

正式发布前需使用 release 签名的真机包验证：百度 AK 鉴权、详情页小地图、地图页、
普通与卫星样式切换、媒体标记和聚合、轨迹、缩放与移动、位置选择、坐标精度、
OpenFreeMap 等非商业地图样式切换，以及拒绝隐私授权时的行为。

## 2026-11-13 地图样式预览修复

样式选择列表现在为百度地图、百度卫星地图和 OpenFreeMap Bright 分别使用独立的
128×128 预览资源；其余样式的既有预览映射保持不变。三张资源均已纳入
`assets/map_samples/`，并由百度 ARM64 release APK 打包校验。

本次生成的包为 `com.mediacraft.app.baidu`，versionCode `18502`，SHA-256 为
`C939F09F3ECCF43A4796A9E486E18170A8608898EEF7006FBD6BE25F4F9ED003`。已在荣耀
设备上执行覆盖安装（`adb install -r`），未清除应用数据或测试媒体；安装后
UIAutomator 已列出六个地图样式条目。设备随后再次变为 `offline`，因此右侧预览
图的最终截图复核仍待设备稳定连接后完成。

## 2026-11-13 首轮验证（未配置 AK）

- 百度依赖集：16 个分析目标 `No issues found`。
- Flutter 测试：现有 87 项、百度新增 15 项，总计 102/102 通过，无跳过。
- 百度 debug 通用 APK 构建通过，包名 `com.mediacraft.app.baidu.debug`。
- 百度 ARM64 release 构建、R8/AAR 检查和内置 C++ 哈希校验通过；包名
  `com.mediacraft.app.baidu`，versionName `0.6.1`，versionCode `18502`。
- release APK 的 v2 签名验证通过。
- 恢复默认 libre 依赖后，libre debug APK 构建通过；清单没有百度 AK，插件注册表
  没有百度插件，APK 没有百度原生库。根 `pubspec.yaml`/`pubspec.lock` 已恢复原值。
- Android `testLibreDebugUnitTest` 本轮实际执行 18/18，无失败、无跳过。
- 本次未配置 AK（最终清单占位为 `NONE`），未安装百度测试包到设备，未完成
  百度底图、真实坐标、生命周期或真机性能验收；测试中的坐标转换器为 mock。
- GeoTIFF 叠加层继续由其他地图样式提供，百度样式显示不支持提示。

本地验证包保留在
`artifacts/baidu-2026-11-13/MediaCraft-0.6.1-baidu-arm64-release-no-ak.apk`，
文件 SHA-256 为
`B31DBE76290EB647635B02F3B35F6A125FB2D06637C78305E3A0329E37621E93`。
它用于构建和签名验证，配置真实 AK 后必须重新构建再进行地图验收。

测试遗留进程已结束。清理百度插件 `build`/`.dart_tool` 临时测试缓存的请求被自动
审批审查拒绝（工具仅返回 `blocked by policy`），因此约 82 MB 测试缓存保留。
验证 APK、校验值和日志仍用于后续验收，继续保留。

## 2026-11-13 AK 申请

已在用户百度开放平台账号下创建应用：

- 应用名称：`MediaCraft`。
- 应用编号：`124685153`。
- 应用类型：Android 应用。
- 已启用服务：Android 地图 SDK。
- 绑定发布包名：`com.mediacraft.app.baidu`。
- 发布证书 SHA-1：`58:27:DB:E8:AC:62:AE:52:D8:68:F4:D8:C0:10:EB:FF:B7:B0:09:FC`。

AK 已保存到被 Git 忽略的本地 `android/key.properties` 的 `baiduApiKey`，
本文档不记录 AK 原文。此 AK 面向 release 包；debug 包名为
`com.mediacraft.app.baidu.debug`，如需该包在线验证，应为其另行登记匹配的 AK。
后台应用创建成功并不等同于设备端 SDK 鉴权或底图加载已验收。

带 AK 的 ARM64 release 已重新构建，并完成以下核对：APK 包内 AK 与本地新 AK
一致，包名和签名证书 SHA-1 与后台绑定一致，v2 签名验证通过，assemble 内置
C++ 运行库校验通过。此次只变更授权配置，沿用前轮代码测试结果，未重复运行
Flutter/JVM 测试；未安装到设备验证在线鉴权和实际底图。

本地验证包：`artifacts/baidu-2026-11-13/MediaCraft-0.6.1-baidu-arm64-release.apk`。
SHA-256：`1153617267D78F99F669DF5D43BB6A0265AB4DAFB8716381669C343685FC0344`。
项目根依赖已恢复默认 libre，AK 本地配置继续保留。
