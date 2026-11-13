# MediaCraft 重建来源与验证记录

记录日期：2026-11-12。

## 重建来源

MediaCraft 当前 Flutter 工程由 Aves 完整源码重建，不是仅参考界面或重新实现部分功能。

| 项目 | 固定值 |
| --- | --- |
| 上游仓库 | `https://github.com/deckerst/aves` |
| 上游分支 | `develop` |
| 上游提交 | `9d3f1ff19f680db043c9d80831c6f222e0f8220c` |
| 上游许可证 | BSD 3-Clause License |
| Flutter SDK | `3.47.8` |
| Windows SDK 路径 | `.tooling/flutter` |
| Dart SDK 约束 | `>=3.13.7 <4.0.0` |
| UI 依赖 | Flutter Material 组件与 MediaCraft 自有视觉样式 |
| Dart package namespace | `aves`（保留上游内部标识） |
| Android applicationId | `com.mediacraft.app` |
| libre applicationId | `com.mediacraft.app.libre` |

导入时保留 Aves 的 `lib/`、`plugins/`、`test/`、`test_driver/`、Android 工程、flavor 脚本、资源与原有模块边界。MediaCraft 后续修改应继续保留上游版权和许可证，并在变更记录中区分上游能力与 MediaCraft 自有验证。

## Flavor 边界

默认开发与无 Google 服务验证使用 `libre` flavor。该 flavor 默认选择 `aves_services_none` 等无 Google 服务实现；这只描述依赖配置，不等于所有联网功能都被禁用。地图瓦片、反向地理编码或用户主动访问的链接仍可能使用网络。

Play、Izzy 与 libre 的依赖和发布配置不同，不能用一个 flavor 的构建结果推断另一个 flavor 已通过验证。

## Windows 构建入口

```powershell
.\scripts\bootstrap.ps1
.\scripts\build.ps1
```

`bootstrap.ps1` 将 Flutter 3.47.8 安装到项目内 `.tooling/flutter`。`build.ps1` 只使用该 SDK，并依次执行 `flutter pub get`、限定目录静态分析、`flutter test --concurrency=1`、libre debug APK 构建和 `:app:testLibreDebugUnitTest`。任一步骤失败都会中止，不能把后续步骤记为通过。

仓库根目录的 `flutterw` 同样使用 `.tooling/flutter` 中独立固定的 Flutter 3.47.8。`.gitmodules` 已移除，构建不再依赖旧的 `.flutter` Git 子模块。

源码中的 `package:aves/...` 和 Android namespace `deckers.thibault.aves` 是为兼容完整上游模块保留的内部标识，不是应用发布身份。发布身份由 Android applicationId 决定。

## 当前验证状态

本记录不宣称全部功能验收完成。导入后至少需要分别验证：

1. 固定 Flutter 3.47.8 的依赖解析与代码生成。
2. 原有单元测试、widget 测试和适用的集成测试。
3. libre debug APK 的构建、安装和冷启动。
4. 媒体权限、索引、浏览、详情、搜索、地图、分享和文件写入流程。
5. Android TV、媒体 viewer/picker、后台扫描等平台入口。
6. MediaCraft UI 在目标 Android GPU/渲染后端上的显示与性能。
7. Release 签名、混淆、升级和商店渠道流程。

只有实际执行且保存结果的项目才能标记为通过。未执行、被跳过或只在旧壳层验证过的项目均保持“未验证”。

### 2026-11-12 检查点

已通过：

- libre 当前实际启用的 16 个分析目录已完成静态分析，结果为 `No issues found`。本地 `build.ps1` 分析 `lib`、`test`、`test_driver` 以及 `.dart_tool/package_config.json` 中当前选中的插件目录，不分析相互排斥的非 active Google/无 Google 平台实现。
- 完整 Flutter 测试结果为 85/85 通过，包含原有测试及本轮新增测试。
- Android JVM 测试结果为 18/18 通过、0 跳过，其中重复项引擎 9 项、MIME/标准媒体目录 6 项、时区 2 项、数学工具 1 项。XML 结果保存在 `build/app/test-results/testLibreDebugUnitTest`。
- libre debug APK 已完成构建。
- 用户最初拒绝系统安装确认，随后明确允许；APK 已在 Honor YOK-AN10（Android 12）安装并冷启动成功。最初拒绝不计为安装失败，后续明确授权后的安装结果才计为通过。

局部恢复验证：

- native 构建曾因 `device_info_plus` 的跨盘 Kotlin 增量缓存失败。关闭 Kotlin 增量后，以 `-Pkotlin.incremental=false` 定向重试该插件的 `compileDebugKotlin`，结果为 `BUILD SUCCESSFUL`。
- 根 `android/gradle.properties` 当前采用低内存、单 worker 配置：Gradle 最大堆 2 GB、`org.gradle.workers.max=1`、`org.gradle.parallel=false`、`kotlin.incremental=false`，并使用 in-process Kotlin 编译。
- CI 的 Flutter analyzer 当前限定为 `lib test test_driver`，避免未被当前 flavor 选中的互斥 Google 平台依赖产生误报。本地脚本在此基础上额外分析实际 active 插件目录。

尚未通过：

- libre release 构建和 MediaCraft 发布签名已于 2026-11-12 验证；Play/Izzy flavor、商店渠道和对应升级流程仍未验证。本次调试包的覆盖安装已通过。
- 网络视频播放、流媒体及 libmpv 高级 OSC/cache 配置尚未覆盖；本地播放验证不能外推这些能力。

最终交付记录：

| 项目 | 当前值 |
| --- | --- |
| APK | `artifacts/MediaCraft-1.15.10-libre-debug-arm64.apk` |
| SHA-256 | `7684e1e6ca72757ecce300fd75bf768c38f4aecf2baf7ec3e63fec9ab1ac76a3` |
| 签名校验 | 通过，APK Signature Scheme v2，Android Debug 证书；见 `artifacts/validation/apk-signature.txt` |
| 覆盖安装与冷启动 | Honor YOK-AN10 / Android 12，安装成功、`Status: ok`、`LaunchState: COLD`；交付文件与手机安装包摘要一致 |
| QA 清理 | 四个媒体文件、两处专用媒体目录和六个手机临时文件已移除，MediaStore 残留记录为零；清理后再次冷启动成功 |

### 2026-11-12 发布签名检查点

| APK | `artifacts/MediaCraft-1.15.10-libre-release-arm64.apk` |
| SHA-256 | `21f2c18c931b18ea12d4b5deeb501a84a3fc034b2a54b7a555900810b047348e` |
| 签名校验 | 通过，APK Signature Scheme v2，`MediaCraft Release` RSA 4096 证书；证书 SHA-256 为 `6454e954515b3ae190061204778d3774659acd395c76ee99806973697af37b29`；见 `artifacts/validation/apk-release-signature.txt` |

### Honor YOK-AN10 真机重复项检查

测试设备为 Honor YOK-AN10，Android 12。测试在唯一目录 `MediaCraft_QA_01a1cd4e` 中创建四个专用媒体文件：

- 两张 JPEG，各 674 B，内容构成一组完全重复项。
- 两个 MP4，各 50,661 B，内容构成一组完全重复项。

扫描准确识别出两组重复项。照片和视频各选择一个 B 副本，两轮移入应用回收站并恢复后，四个文件的 SHA-256 均与原值一致：

- JPEG：`60fe6bf1d82e56531866c2c5405333f2eeed20b97adb34fde5db6f4555e29e12`
- MP4：`b537f4553f60202d779934098423f98b6c8762ed4b48356a08b6dd5bdc3542b8`

结构化证据保存在 `artifacts/validation/media-roundtrip.json`。其中记录两组完全重复项、两个入回收站副本、两个恢复副本、四个文件恢复前后 SHA 一致，以及外部修改媒体被阻止清理。

恢复与安全边界验证：

- `preserveFileBytes` 和移动前复核已阻止去重流程修改文件字节；Aves 其他入口继续保留原有默认行为。
- 视频 `Movies` 目录按 MIME 类型识别的修复已在 Honor Android 12 上恢复成功，对应 6 项 Android JVM 测试已纳入 18/18 结果。
- 最新 native 构建在移入回收站目标路径不可见时会显式失败，不再通过永久删除作为兜底。
- 扫描完成后从应用外部修改文件，UI 会把结果标记为过期并停止清理，没有执行删除或移动。
- 修复回收站 lens 在 `toBin` 后不刷新的问题并增加回归测试；最新 APK 真机验证从清理页返回回收站后立即显示两项，无需重启应用。

### 播放与 About 真机检查

- 1 秒 H.264 测试视频已在本机播放器中播放并渲染画面，截图证据为 `artifacts/validation/qa-player.png`。
- libmpv 输出了可选 OSC/cache 相关的非阻断日志，本地播放正常。这不证明网络媒体或高级播放配置已经通过。
- About 页面已在真机显示中文来源说明，Aves 许可证入口可访问；完整许可证同时可通过应用内许可证页离线查看。

### 交付资源清理

清理前已停止 MediaCraft 调试应用，并重新核对四个 QA 文件的 SHA-256。只删除 `Pictures/MediaCraft_QA_01a1cd4e/` 和 `Movies/MediaCraft_QA_01a1cd4e/` 中这四个文件对应的 MediaStore 记录；查询确认零残留后，以非递归方式移除空目录。用户其他媒体未纳入清理范围。

本任务专用的 `adb logcat --uid=10188` 捕获进程已核对并停止，手机 `/data/local/tmp/mediacraft-qa-*` 临时文件已清理。本地 `.buildlog/` 已在归档必要日志后移除，清理 20 项临时资源、992,172 B。必要日志、JUnit XML、真机截图、APK、摘要和签名报告保留在 `artifacts/`，供交付和审计使用；固定 SDK `.tooling/flutter` 与当前构建缓存保留，作为后续构建所需资源。

## 历史产物边界

此前原生 Android 壳层已经删除，不能作为当前源码的构建入口或回滚实现。旧测试 APK 是迁移过程中的临时产物，不是最终发布包；它既不能证明当前 Flutter 工程可构建，也不能证明继承自 Aves 的所有模块在 MediaCraft 中已验收。

## 后续验证记录格式

每次验证应记录日期、提交、Flutter 版本、flavor、命令、设备/系统版本、结果与未覆盖项。失败或跳过必须显式记录，不能合并为“测试通过”。

