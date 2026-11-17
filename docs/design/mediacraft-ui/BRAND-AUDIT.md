# MediaCraft 品牌残留审计

## 审计说明

- 审计时间：2026-11-17
- 审计目标：确认面向用户的产品介绍、商店文案、欢迎文案、帮助链接和导出文件名已使用 MediaCraft 品牌，同时保留必要的开源版权、历史证据和内部兼容标识。
- 此份审查边界：读取当前品牌、帮助、导出和发布素材源码，记录准确状态；仅对经核实无依赖且无法复现的旧截图后处理脚本执行清理。验证与其余生产 UI 改造由根代理统一收尾。
- 结论分级：
  - `已清理`：当前文本或入口已不再向用户介绍或推广 Aves。
  - `必须保留`：许可证、版权、依赖来源、历史记录或内部兼容标识，不属于产品品牌残留。
  - `待处理`：仍可能在用户界面或发布素材中出现旧品牌，需要后续修改或重新生成。

## 结论摘要

当前 README、欢迎/条款、本地化欢迎语、Fastlane 文本描述、关于页主入口、帮助链接目标和导出文件名已经切换到 MediaCraft。源码中仍存在大量 `aves` 字样，但绝大多数是 Dart package、类名、插件、数据库兼容字段、构建变量或许可证来源，不能作为品牌清理对象直接删除或重命名。

当前应用源码中的用户可见品牌清理已经完成：

1. 重复清理确认和回收站提示已改为无旧品牌的“回收站”“操作报告”。
2. 元数据编辑写入的 XMP Software 标识已由 `Aves v...` 改为 `MediaCraft v...`。
3. 调试 trace 标签已改为 MediaCraft。
4. 56 份品牌位图的栅格接缝已修复并完成视觉检查。
5. Fastlane 的 308 张 `phoneScreenshots` 已由真实应用截图统一替换。

品牌素材生成已经完成，但这些 Fastlane 素材尚未上传到应用商店；本审计不把本地 metadata 更新等同于商店发布完成。

## 已清理的用户可见范围

### README 与条款

| 范围 | 当前状态 | 证据 |
|---|---|---|
| 项目首页介绍 | 已改为 MediaCraft 自身定位，不再以 Aves 衍生项目作为产品介绍 | `README.md` |
| 使用与隐私说明 | 已移除上游项目宣传和上游仓库入口，保留通用开源许可说明 | `assets/terms.md`、`assets/terms.txt` |
| 开源边界 | 原始版权、BSD 3-Clause 和第三方许可集中指向本地许可证文件 | `README.md`、`assets/terms.md` |

### 关于页、帮助与更新

| 范围 | 当前状态 | 证据 |
|---|---|---|
| 关于页身份 | 显示 MediaCraft 名称、图标、版本和 MediaCraft GitHub | `lib/widgets/about/app_ref.dart:AppReference` |
| 派生介绍 | 已从手机和 TV 关于页移除，旧组件源码已删除 | `lib/widgets/about/about_mobile_page.dart`、`about_tv_page.dart` |
| 上游译者展示 | 已从关于页移除，旧展示源码已删除 | `lib/widgets/about/about_mobile_page.dart`、`about_tv_page.dart` |
| 更新检查 | 使用 MediaCraft GitHub Latest Release，业务保留 | `lib/services/app_update_service.dart:AppUpdateService.latestReleaseUri` |
| Bug 反馈 | 指向 MediaCraft GitHub Issue，诊断收集业务保留 | `lib/widgets/about/bug_report.dart:_BugReportContentState.bugReportUrl` |
| 帮助入口 | 自动导出和视频硬件加速帮助均使用本地 MediaCraft 帮助弹窗，不再依赖 Aves Wiki 或外部 FAQ | `lib/widgets/about/help_dialog.dart:showMediaCraftHelp` |

当前 `AppReference` 不再包含 `avesFaq`。`lib/widgets/aves_app.dart` 的自动导出帮助和 `lib/widgets/settings/video/playback_page.dart` 的硬件加速帮助都直接调用 `showMediaCraftHelp`，帮助内容由应用本地提供。

### 本地化与商店文本

| 范围 | 当前状态 | 证据 |
|---|---|---|
| 多语言欢迎语 | ARB 与生成的本地化代码中不再存在独立 Aves 产品名 | `lib/l10n/`、`lib/l10ngen/` |
| Fastlane 完整描述 | 52 份现有完整描述已使用 MediaCraft 品牌 | `fastlane/metadata/android/*/full_description.txt` |
| Fastlane 简短描述 | 当前扫描未发现 Aves 或上游仓库品牌 | `fastlane/metadata/android/*/short_description.txt` |
| Fastlane 标题 | 当前目录没有 `title.txt`，商店标题由其他发布配置决定 | `fastlane/metadata/android/` |

### 导出文件名

| 用户可见文件 | 当前前缀 | 证据 |
|---|---|---|
| 诊断日志 | `mediacraft-logs-*` | `lib/widgets/about/bug_report.dart` |
| 启动/调试日志 | `mediacraft-debug-logs-*` | `lib/widgets/home/home_page.dart` |
| 设置导出 | `mediacraft-settings-*` | `lib/widgets/settings/settings_action_delegate.dart` |
| 自动设置导出 | 发布模式为 `mediacraft-settings-auto`，调试/性能模式分别为 `mediacraft-debug-settings-auto`、`mediacraft-profile-settings-auto` | `lib/widgets/aves_app.dart` |
| 初始化失败数据库导出 | `mediacraft-database-*` | `lib/widgets/home/home_error.dart` |
| GPX 导出 | `mediacraft-gpx-*` | `lib/widgets/collection/entry_set_action_delegate.dart` |
| 统计排行 CSV | `mediacraft-stats-*` | `lib/widgets/stats/top_page.dart` |
| 媒体明细 CSV/JSON | `mediacraft-stats-*`，扩展名由所选格式决定 | `lib/widgets/dialogs/export_collection_stats_page.dart` |
| 单项元数据导出 | `<原媒体文件名>-metadata` | `lib/widgets/viewer/action/entry_info_action_delegate.dart` |

普通媒体复制、转换和导出沿用用户媒体名或目标目录语义，不使用 Aves 品牌前缀。

### 其他可见写入与调试标识

| 范围 | 最终状态 | 证据 |
|---|---|---|
| 重复清理提示 | 使用“回收站”“操作报告”，不再显示 Aves 产品名 | `lib/widgets/duplicates/duplicate_page.dart` |
| XMP Software writer | 写入 `MediaCraft v...` | `lib/model/entry/extensions/metadata_edition.dart` |
| 调试 trace 标签 | 使用 MediaCraft 标签 | `lib/widgets/debug/general.dart` |

## 商店图片素材

- `fastlane/metadata/android/` 的 44 个 locale 目录各包含 7 张新 `phoneScreenshots`，合计 308 张。
- 截图来自 14 张真实应用源图：简体中文 7 张、英文 7 张，源图尺寸为 `1080x2400`。
- 商店图先等比缩放为 `350x778`，再使用纸白背景上下补边到 `350x817`，没有拉伸画面比例。
- `zh-CN` 使用中文截图；其余 43 个 locale（包括 `zh-Hant` 和 RTL 语言目录）使用英文截图回退。当前没有为这些 locale 生成独立本地化截图，发布说明必须明确这一点。
- feature graphic、icon、banner、launcher 等共 56 份品牌位图已经按 MediaCraft 品牌重做，栅格接缝已修复并完成视觉检查。
- 截图生成清单见 `artifacts/ui-redesign-2026-11-17/store-screenshots.json`；最终 libre UI 总览见 `artifacts/ui-redesign-2026-11-17/libre-final/overview.png`。
- 上述素材尚未上传到应用商店，不能据此声明线上商店素材已更新。

## 全量可见字符串扫描结论

当前 `lib/` 用户可见字符串扫描只剩两类 Aves 名称，均必须保留：

1. `lib/model/app/dependencies.dart` 中第三方 fork 的真实仓库或项目来源名，用于依赖归属和开源许可展示。
2. `lib/widgets/about/license_registry.dart` 中 “Aves (upstream)” BSD 许可证主体，用于展示原始版权和许可证。

除此之外，当前 `lib/` 用户可见产品文案中未发现需要继续替换的 Aves 品牌残留。内部 package、类、插件、通道和构建标识不属于用户可见字符串扫描结果。

## 必须保留的 Aves/aves 标识

### 许可证和版权

| 保留项 | 原因 | 证据 |
|---|---|---|
| “Aves (upstream)”许可证注册项 | 用于离线展示原始 BSD 版权与许可，属于必须保留的授权证据 | `lib/widgets/about/license_registry.dart` |
| BSD 3-Clause 正文与原始版权 | 删除会破坏许可证合规 | `LICENSE` |
| 第三方来源和许可证 | 用于版权、审计和依赖归属 | `THIRD_PARTY_NOTICES.md`、`licenses/` |
| 依赖源码 URL | 表明实际依赖来源，不是产品推广链接 | `pubspec.yaml`、`flavors/pubspec_*.yaml`、`lib/model/app/dependencies.dart` |

应用内许可证页可以继续出现 Aves 上游名称，因为该名称用于标识许可证主体，而不是产品品牌或支持入口。

### 内部兼容标识

以下标识参与编译、运行、数据兼容或发布配置，不能在品牌清理中批量重命名：

- Dart package 名 `name: aves` 和 `package:aves/...` import。
- `AvesScaffold`、`AvesApp`、`AvesLocale`、`AvesEntry` 等类名。
- `plugins/aves_*` 插件目录、package 和 flavor 服务选择。
- Android namespace、MethodChannel 名和现有签名环境变量 `AVES_*`。
- 数据或调试兼容标识，例如 `aves-thumbnail`。

这些名称不会作为 MediaCraft 的产品介绍展示给普通用户。大规模改名会影响 import、原生通道、构建脚本、存储兼容和发布密钥，应另立迁移项目处理。

### 历史与审计文档

下列文件中的 Aves 来源说明是历史事实和验证边界，不应为品牌视觉统一而删除：

- `docs/rebuild-and-validation.md`
- `docs/releases/v0.6.0.md`
- Git 历史提交信息
- `.github/workflows/release.yml` 中解释已知构建问题的上游 Issue 注释

历史文档不属于当前商店文案或应用产品介绍。若未来对外发布文档站点，应在发布层面区分“产品文档”和“工程审计记录”。

## 旧截图脚本处理

已删除 `scripts/screenshot_post_process.sh`，依据如下：

1. 仓库内没有脚本调用方；GitHub Actions、发布流程、其他脚本和文档均未引用它。
2. 仓库不存在脚本要求的 `screenshots/raw` 输入目录。
3. 脚本硬编码依赖维护者个人目录 `~/code/aves_extra/screenshots/` 下的设备遮罩和边框素材，当前环境不存在这些资源。
4. 脚本自首次完整源码导入后没有独立维护记录，内容仍面向 IzzyOnDroid、Google Play 和旧 README 截图布局。
5. 当前 Fastlane 图片已直接存放在 metadata 目录，删除该脚本不会删除或修改现有截图。

因此该脚本无法从仓库内独立复现，也不属于当前可执行发布链路。后续截图应使用 MediaCraft 自有、仓库可追踪的生成或人工验收流程，不应恢复对个人目录素材的隐式依赖。

## 发布前品牌验收清单

- 上传并核对应用商店中的 feature graphic、图标和 `phoneScreenshots`；当前仅完成仓库 metadata 更新。
- 如需真正本地化的商店截图，为 `zh-Hant`、RTL 语言及其他 locale 单独生成对应语言源图；当前统一使用英文回退。
- 确认 Android launcher label、通知、系统选择器和桌面小组件均显示 MediaCraft。
- 确认关于页只提供 MediaCraft 源码、更新、反馈、隐私和许可证入口。
- 确认许可证页仍可离线查看 Aves BSD 版权及第三方许可。
