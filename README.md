# MediaCraft

MediaCraft 是基于 [Aves](https://github.com/deckerst/aves) 完整源码开发的 Android 媒体图库与元数据浏览器衍生版。当前源码基线来自 Aves `develop` 分支提交 `9d3f1ff19f680db043c9d80831c6f222e0f8220c`，保留了上游原有模块、测试、插件与多 flavor 工程结构。

本项目不是从零实现，也不是只参考 Aves 的界面。Aves 源码依照 BSD 3-Clause License 使用和修改；原始许可证全文保留在根目录 [LICENSE](LICENSE)，归属信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 当前状态

- Flutter SDK 固定为 `3.47.8`，约束记录在 `pubspec.yaml`。
- Windows 构建使用仓库内 `.tooling/flutter`，由 `scripts/bootstrap.ps1` 安装固定版本，避免依赖系统 Flutter。
- 默认开发目标是 `libre` flavor；该 flavor 使用无 Google 服务的平台实现，不默认依赖 Google Play 服务。
- Dart/Flutter 源码的内部 package namespace 仍为 `aves`，用于保持上游模块兼容；Android 应用 ID 已独立为 `com.mediacraft.app`，libre flavor 追加 `.libre`。
- MediaCraft UI 使用 Flutter Material 组件与 MediaCraft 自有视觉样式。
- Aves 原有功能模块和测试均保留，但这不表示 MediaCraft 的全部功能已经在当前品牌、flavor、设备和发布配置下完成验收。
- 早期原生 Android 壳层已经删除，不再是可构建或可回退的产品实现。此前生成的测试 APK 仅为临时验证产物，不是最终发布包，也不能代表当前 Flutter 源码状态。
- 当前 libre debug APK 已完成核心构建与真机验证，并在 Honor YOK-AN10（Android 12）安装和冷启动。Flutter 测试 85/85、Android JVM 测试 18/18 已通过，实际启用的 16 个分析目录为 `No issues found`。
- 真机重复项扫描、过期结果拦截、移入应用回收站、即时列表刷新和照片/视频恢复均已验证；四个 QA 文件两轮往返后 SHA-256 完全一致。
- 本地 H.264 视频播放与画面渲染已验证。网络媒体和 libmpv 高级选项尚未覆盖。

## 功能基础

继承自 Aves 的主要能力包括图片与视频浏览、元数据查看、相册与标签导航、搜索、地图、媒体查看器/选择器，以及 Android 平台集成。MediaCraft 会在此基础上调整产品身份、视觉层与后续功能；具体完成度以当前源码、测试及 [重建与验证记录](docs/rebuild-and-validation.md) 为准。

## 构建

Windows 首次构建先安装项目固定的 Flutter 3.47.8，随后执行统一构建脚本。构建脚本依次运行依赖解析、静态分析、Flutter 测试、libre debug APK 构建和 Android JVM 测试。

```powershell
.\scripts\bootstrap.ps1
.\scripts\build.ps1
```

`build.ps1` 使用 `.tooling/flutter/bin/flutter.bat`，产出目标为 `lib/main_libre.dart` 的 libre debug APK。签名发布、Play/Izzy flavor 和商店分发需要额外凭据与对应服务配置，不能由 debug 构建结果替代。

Play flavor 的 Google Services/Firebase 配置必须由构建者提供 MediaCraft 自有的 `android/app/google-services.json`；仓库不提交 Aves 上游 Firebase 配置。

百度内嵌地图使用独立的 `baidu` flavor，并保留现有免费地图样式；AK、包名、构建方式及验证边界见 [百度地图接入](docs/baidu-map.md)。

当前详细测试、真机数据和未覆盖项见 [重建与验证记录](docs/rebuild-and-validation.md)。本地交付文件为 `artifacts/MediaCraft-1.15.10-libre-debug-arm64.apk`，SHA-256 为 `7684e1e6ca72757ecce300fd75bf768c38f4aecf2baf7ec3e63fec9ab1ac76a3`。该 APK 已通过 v2 调试签名校验、真机覆盖安装及冷启动，手机内安装包摘要与交付文件一致。本次四个 QA 媒体及专用测试目录已清理，验证报告和截图保存在 `artifacts/validation/`。

## 来源与许可证

- MediaCraft: <https://github.com/404NotFoundYou/MediaCraft>
- Aves upstream: <https://github.com/deckerst/aves>
- Aves BSD 3-Clause License: [LICENSE](LICENSE)
- 第三方通知: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 完整许可证副本: [licenses/](licenses/)
