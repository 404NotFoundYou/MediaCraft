# MediaCraft

MediaCraft 是一个仅用于个人学习、研究和本地测试的 Android 媒体图库项目，提供照片与视频浏览、元数据查看、地图定位、查看器、文件选择和重复媒体清理等功能。

本项目基于 [Aves](https://github.com/deckerst/aves) 开源项目的完整源码衍生开发。Aves 上游源码依照 BSD 3-Clause License 使用，原始版权和许可证保留在 [LICENSE](LICENSE)；第三方来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目内部仍保留部分 `aves` 命名空间，是为了兼容上游模块和数据结构，不代表 MediaCraft 与 Aves 官方存在合作或背书关系。

## 使用边界

MediaCraft 是个人学习项目，不是商业产品、商业服务或可售卖的软件包。未经项目维护者书面许可，不得将本项目、构建产物、修改版本或其功能包装成收费产品、付费服务、广告产品、商业分发包或其他盈利用途。

这项使用边界是 MediaCraft 项目的发布政策。它不改变 Aves BSD 3-Clause License 对上游源码本身授予的权利；如果你重新使用 Aves 原始代码，仍必须遵守 Aves 的许可证和版权要求。MediaCraft 自有修改、品牌、图标、文案和构建产物不得被用于商业盈利。

本项目不提供任何商业支持、服务等级承诺或数据恢复承诺。使用前请自行备份重要媒体，所有功能和设备兼容性都应以实际验证结果为准。

## 当前实现

- Flutter 3.47.8，项目内置 SDK 位于 `.tooling/flutter`。
- 默认 `libre` flavor 使用无 Google 服务的平台实现。
- `baidu` flavor 集成百度地图 SDK；百度地图 Key 必须匹配对应包名、签名和平台配置。
- Android applicationId 为 `com.mediacraft.app`，libre 和 baidu flavor 分别追加对应后缀。
- UI 使用 Flutter Material 组件和 MediaCraft 自有视觉样式，不依赖 `liquid_glass_widgets`。

## 构建

Windows 环境：

```powershell
.\scripts\bootstrap.ps1
.\tooling\flutter\bin\flutter.bat pub get --enforce-lockfile
.\tooling\flutter\bin\flutter.bat analyze lib
.\tooling\flutter\bin\flutter.bat test
```

构建 libre Debug APK：

```powershell
.\tooling\flutter\bin\flutter.bat build apk --debug --flavor libre -t lib/main_libre.dart
```

构建百度地图 Release APK：

```powershell
Copy-Item flavors\pubspec_baidu.yaml pubspec.yaml -Force
Copy-Item flavors\pubspec_baidu.lock pubspec.lock -Force
.\tooling\flutter\bin\flutter.bat build apk --release --flavor baidu -t lib/main_baidu.dart --split-per-abi
```

百度地图 Release 包需要有效的百度 Key、签名配置和对应 SHA-1。构建完成后应恢复默认 flavor 配置，并使用 APK 签名校验和 SHA-256 校验文件确认交付物。

## 功能基础

项目保留 Aves 的主要媒体能力，包括图库网格、相册和标签导航、搜索、地图、媒体查看器、编辑入口、文件选择、回收站、设置和多语言支持；MediaCraft 另外维护重复媒体清理及百度地图 flavor。功能是否完成，以当前源码、测试和设备验证记录为准。

## 来源与许可证

- MediaCraft：<https://github.com/404NotFoundYou/MediaCraft>
- Aves：<https://github.com/deckerst/aves>
- Aves BSD 3-Clause License：[LICENSE](LICENSE)
- 第三方通知：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 开源许可证目录：[licenses/](licenses/)

MediaCraft 的修改由本项目维护，不代表上游作者为本项目提供支持或背书。
