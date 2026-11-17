# MediaCraft UI 改造验证记录

日期：2026-11-17。工作区：`D:\AiProject\MediaCraft`。本记录对应已确认 HTML 原型之后的 Flutter 实现阶段；原型阶段的验证历史仍保留在 `VALIDATION.md`。

## 交付与业务范围

默认信息架构调整为“媒体 / 相册 / 探索 / 工具”，采用纸白、森林绿、小圆角和固定导航。媒体、相册、搜索、地图、统计、重复清理、设置及隐私页面已重排；查看器使用独立深色画布，视频与内部编辑器保留原控制器及委托。

原有媒体读写、格式支持、筛选组合、动态相册、分组、保险库、回收站、设置导入导出、系统 Intent 和 TV 分支继续使用原业务模型。已保存的用户导航、主题和快捷操作偏好优先于新默认值。具体映射及证据见 `IMPLEMENTATION.md`、`BUSINESS-CONTEXT.md` 和三个 capability 数据文件。

本轮还清理了用户可见的上游项目介绍、推广入口、旧图标、商店图片和导出前缀。必要的 BSD 版权、许可证、第三方归属、历史审计及内部 `aves` 包名、原生通道等兼容标识继续保留，见 `BRAND-AUDIT.md`。

## 自动检查与构建

使用项目固定 Flutter 3.47.8 / Dart 3.13.7，未升级依赖、锁文件或应用版本（仍为 `0.6.3+187`）。

| 项目 | libre | baidu |
|---|---|---|
| `pub get --enforce-lockfile` | 成功 | 成功 |
| analyze：`lib test test_driver` 与当前 13 个本地插件根，共 16 个分析根 | 0 issues | 0 issues |
| 全部 Flutter 测试 | 140 通过 | 140 通过 |
| Android App JVM 单元测试 | 23 通过，0 失败/错误/跳过 | 23 通过，0 失败/错误/跳过 |
| 默认 ABI Debug APK | 构建成功 | 构建成功 |
| APK 原生 ABI 实际检查 | armeabi-v7a / arm64-v8a / x86_64 | armeabi-v7a / arm64-v8a / x86_64 |
| 百度原生地图 SDK | 不包含 | 包含；构建中的 C++ runtime 校验任务成功 |

Flutter 测试包含重复清理取消、最终字节复核失败时不移动、成功移动后重新扫描，以及新导航、已有导航配置、Picker 维护入口限制、固定筛选保留、320px 宽和 2 倍字体下的导航可见性。原生测试包含重复引擎、图像提供器和 MediaStore 权限等 4 个测试类。

`plugins/aves_map/test` 没有可执行的 `_test.dart` 文件，未将空目录标记为测试通过。本轮没有执行商店发布、Release 签名/AAB、CI 或真机验收。构建仍有依赖产生的 Java 8/Gradle 弃用提示，未将构建成功表述为“无警告”。

主要命令：

```powershell
.\.tooling\flutter\bin\flutter.bat analyze --no-pub lib test test_driver <当前本地插件根>
.\.tooling\flutter\bin\flutter.bat test --no-pub --reporter expanded
.\.tooling\flutter\bin\flutter.bat build apk --no-pub --debug --flavor libre -t lib/main_libre.dart
.\.tooling\flutter\bin\flutter.bat build apk --no-pub --debug --flavor baidu -t lib/main_baidu.dart
.\android\gradlew.bat -p android :app:testLibreDebugUnitTest --no-daemon --max-workers=1 '-Ptarget=lib/main_libre.dart'
.\android\gradlew.bat -p android :app:testBaiduDebugUnitTest --no-daemon --max-workers=1 '-Ptarget=lib/main_baidu.dart'
```

切换 flavor 后均使用对应依赖快照。本地默认已恢复 `libre`，根 `pubspec.yaml` / `pubspec.lock` 与 `flavors/pubspec_libre.*` 的 SHA-256 一致；GitHub 正式发布默认 `baidu` 的规则未改。

日志、JUnit XML、机器可读结果与校验和位于 `artifacts/ui-redesign-2026-11-17/`：`analyze-*.log`、`flutter-tests-*.log`、`android-tests-*.log`、`junit-*`、`build-*.log`、`verification.json`、`SHA256SUMS.txt`。

## 模拟器验证

只操作 `emulator-5554` / 原有 AVD `MediaCraft_API36`（Android API 36，x86_64）。本轮启动的是无窗口模拟器实例，未操作连接的 Honor 实体手机。两个最终 Debug 包均已安装到模拟器；测试媒体为本轮准备的照片、合成 GPS 照片及 2 秒视频，不使用用户媒体。

| 路径 | 实际观察及结果 | 证据（相对 artifacts 根目录） |
|---|---|---|
| 首次使用 | 百度版条款接受前继续按钮禁用；接受后进入系统照片/视频授权，再加载 22 项媒体 | `baidu-final/01-welcome.png` |
| 四项导航与媒体选择 | 四项可进入对应页面；选择媒体后仍显示原分享、删除与更多操作；返回恢复浏览态 | `libre-final/zh-CN/01-media.png` |
| 相册 | 实体相册计数及封面显示正常；全部/动态/组切换、空态、排序、反向及分区可操作 | `libre-final/zh-CN/02-albums.png` |
| 搜索 | 输入 `lake` 后选择文本条件，进入 3 项真实结果并显示可移除条件 | `libre-final/search-results.png` |
| 探索和工具 | GPS hero 使用真实媒体，类型计数和工具入口来自集合；回收站移入前后计数变化可见 | `libre-final/zh-CN/03-explore.png`、`04-tools.png` |
| 照片与详情 | 查看器、缩略条、收藏/编辑/分享/信息/删除入口；详情显示实际尺寸、日期、GPS 与地址 | `libre-final/zh-CN/05-viewer.png`、`media-details.png` |
| 两类编辑入口 | 查看器“编辑”唤起系统外部编辑器；显式 `ACTION_EDIT` 进入应用内部裁剪/旋转工作台，取消未保存 | `libre-final/editor.png`、`editor-transform.png` |
| 视频 | 实际读取 2 秒视频并操作播放；更多菜单保留截帧、静音、倍速、AB、轨道和锁定；无音轨样本的轨道项禁用 | `libre-final/video-player.png` |
| 统计与 CSV | 数量、容量、时长和 GPS 指标显示真实数据；选择 Title 导出后获得 239 字节 CSV，解析为表头 + 22 条媒体 | `exports/mediacraft-stats-20261117_060431.csv`、`exports/validation.json` |
| 重复清理 | 1 组完全相同样本，默认勾选 0 项；切换保留项后保留项仍不可清理；取消确认保持两份文件不变 | `libre-final/zh-CN/07-duplicates.png` |
| 移入和恢复 | 经最终复核及媒体管理授权，移动 1 项，结果自动重扫为无重复；回收站显示 1 项；恢复后为空，两份 SHA-256 均与移动前一致 | `libre-final/duplicate-move-result.png`、`bin-after-clean.png` |
| 设置与品牌 | 8 分类正常加载；关于页显示 MediaCraft 名称/标志/仓库入口；本地完整 BSD 许可证可打开 | `libre-final/zh-CN/07-settings.png`、`about.png`、`upstream-license.png` |
| 深色/大字体/横屏 | 复核深色对比、查看器白色系统栏、2 倍字体底栏完整、横屏可滚动 | `libre-final/dark-explore.png`、`large-text-media.png`、`large-text-explore.png`、`landscape-explore.png` |
| 百度地图 | 首次选择时显示隐私说明；同意后实际加载普通及卫星底图，媒体聚合、缩略条和日期地址面板显示 | `baidu-final/04-baidu-consent.png`、`05-baidu-map.png`、`06-baidu-satellite.png`、`07-final-map.png` |

重复样本 `lake.jpg` 与 `lake_copy.jpg` 移动前、取消后、恢复后的共同 SHA-256：`99dfc179ec10cc1350d5cacf0d45a2557f624335d8c187f81496cc0d981b206a`。

OpenFreeMap 初次进入时显示了标记、归属与媒体面板，但底图停留为网格，因此未将该服务的瓦片加载记为通过。百度普通和卫星底图则有实际显示证据。

设备验证覆盖上述代表性流程；TV 遥控、保险库全部认证方式、实体设备/旧 Android 版本、DLNA、所有特殊格式、全部第三方编辑器、所有地图服务和多语言排版没有做全面验收。源码入口复核不能替代这些设备验证。

## 验证中发现并已修复的问题

- 固定 Picker 请求工具路由时保留原范围，并回退到媒体选择页面。
- 品牌位图曲线的放射接缝改为连续 mask 一次合成。
- 查看器系统栏图标与视频时间文字适配深色背景。
- 深色强调色采用品牌色生成的深色调色板，避免直接使用过暗的森林绿。
- 2 倍字体底栏溢出改为随字体度量增高，并同步滚动条/底部留白；新回归测试通过。
- 最终导航测试的 Provider 类型及两条格式 lint 已修正，并重新跑完检查。

切换 flavor 清理缓存后，第一次百度 Flutter 测试卡在 ICU4X 官方 Windows 原生依赖下载。已停止该次测试，仅将 SHA-256 与 hook 固定值一致的官方 DLL 放入工具预期缓存后重跑；没有改依赖、hook、SDK 或绕过测试。最终两个 flavor 的完整测试均通过。

首次 CSV 自动化在保存回调完成前返回，留下 0 字节占位文件，未计为通过。再次保存并等待返回统计页后，已验证上述非空 CSV 内容；两个测试导出文件均纳入清理范围。

## 商店图片与原型

14 张中英文原图均为模拟器真实截图。截图通过 Flutter 官方调试服务临时隐藏 Debug 角标，无生产代码开关；未修改媒体内容或合成不存在的功能。`libre-final/overview.png` 为 2 行 7 列总览。

44 个 locale 共 308 张 `phoneScreenshots` 全部替换。`zh-CN` 使用中文，其他 43 个（含繁体中文与 RTL 目录）使用英文回退，不能据此声称这些语言已完成本地化截图。每张按比例缩至 350×778，纸白上下补边至原有 350×817，未拉伸或裁掉控件。另已替换 44 张 Feature Graphic、商店 icon、TV Banner 和 Android launcher。

手机截图总量从 80,365,742 字节降至 42,907,444 字节，减少 37,458,298 字节。来源、逐图哈希和回退映射保存在 `store-screenshots.json`。这些改动仅在本地 metadata，未上传商店。

HTML 原型和阶段预览继续保留，可直接打开 `index.html`。生产实现采用真实数据，原型内的全部示例状态不代表新增对应业务能力。

## 产物

| 文件 | 用途 |
|---|---|
| `artifacts/ui-redesign-2026-11-17/MediaCraft-0.6.3-ui-libre-debug.apk` | 本地默认 libre 调试包 |
| `artifacts/ui-redesign-2026-11-17/MediaCraft-0.6.3-ui-baidu-debug.apk` | 百度地图调试包 |
| `artifacts/ui-redesign-2026-11-17/SHA256SUMS.txt` | 最终两个 APK 的 SHA-256 |
| `artifacts/ui-redesign-2026-11-17/verification.json` | 测试数量、包大小、ABI、SDK 边界 |
| `artifacts/ui-redesign-2026-11-17/libre-final/overview.png` | 实际 UI 总览 |

产物目录被现有 `.gitignore` 忽略，但文件在工作区真实存在并可直接取用。未提交、推送或发布代码。

## 资源清理

最终清理清单、释放大小和模拟器状态记录于 `artifacts/ui-redesign-2026-11-17/cleanup.json`。交付 APK、日志、JUnit XML、最终截图、CSV 验证证据、原型及业务审计文档保留；原始 AVD、实体手机和其他项目资源不属于清理范围。

已清理 18 个本轮测试媒体、2 个测试导出文件、1 个设备端临时 XML（合计 1,532,207 字节），并移除对应媒体索引；清理前后原有 4 个媒体的 ID/路径集合一致。已移除 4,662,265 字节早期 UI 预览和 20,398,411 字节临时下载/放大图；本轮模拟器实例及 4173 原型服务器已停止。原始 AVD 与安装的最终 Debug 应用保留。

主机 `D:\AiProject\MediaCraft\build` 目录约 7,613,550,531 字节保留。清理前已确认路径位于本工作区、无活动构建任务、最终 APK/JUnit 已另行归档，但自动审批审查仍拒绝递归删除该目录，仅返回 `blocked by policy`，未提供更具体理由。未绕过该拒绝或把该目录计为已释放。

`.dart_tool` 的正常构建缓存及 UI 检查辅助脚本按后续开发/验证复现用途保留，不清理其他项目或系统缓存。
