# MediaCraft UI 生产实现映射

## 1. 文档定位

本文件记录 `docs/design/mediacraft-ui/` 静态原型落地到 Flutter 生产代码后的实际映射。判断依据是当前仓库源码，而不是原型中的示例数据、页面数量或模拟交互。

静态原型用于验证信息架构、视觉层级和状态表达，其中的照片、数量、容量、位置、权限结果和操作反馈均为演示状态。生产实现只复用项目已有业务能力，没有因为原型展示而新增云端账号、AI 分类、相似照片推荐、通用文件管理或其他未在源码中存在的能力。

## 2. 四项导航

新的默认底部导航为“媒体 / 相册 / 探索 / 工具”。底栏采用固定表面、主题强调色选中状态，并继续读取用户保存的 `bottomNavigationActions`。

| 导航 | 生产页面 | 保留的业务能力 | 主要文件 |
|---|---|---|---|
| 媒体 | `CollectionPage` | 当前媒体集合、组合筛选、排序、分段、布局、搜索、选择与批量操作 | `lib/widgets/collection/collection_page.dart`、`collection_grid.dart`、`app_bar.dart` |
| 相册 | `AlbumListPage` | 实体相册、动态相册、保险库、相册组、置顶、隐藏、封面和组内下钻 | `lib/widgets/filter_grids/albums_page.dart`、`common/action_delegates/album_set.dart` |
| 探索 | `ExplorePage` | 地图、国家、地点、标签、高级媒体类型和组合搜索入口 | `lib/widgets/discovery/explore_page.dart` |
| 工具 | `ToolsPage` | 重复清理、目录浏览、统计、回收站、设置、数据维护和关于 | `lib/widgets/discovery/tools_page.dart` |

默认值位于 `lib/model/settings/defaults.dart`。路由、标题和图标映射位于 `lib/widgets/navigation/nav_item.dart` 与 `nav_display.dart`。用户已有的持久化导航不会被新默认静默覆盖；底栏编辑器提供用户主动选择的推荐布局，同时保留原有增删、排序、相册和标签快捷入口。

抽屉、底栏和 TV Rail 仍使用同一套 `AvesNavItem` 序列化模型。旧设置中的页面和筛选书签继续可解析，自定义导航能力没有被四项默认结构替代。

## 3. 媒体与相册

媒体库将原型的品牌行、大标题、搜索、全部/照片/视频分段、收藏快捷筛选和布局入口映射到原 `CollectionLens` 与 `EntrySetActionDelegate`：

- 图片与视频分段通过兼容筛选替换 MIME 条件，不清除相册、标签、地点、收藏等其他组合条件。
- 浏览态保留地图、幻灯片、统计、动态相册、桌面快捷方式、设为主页和重新扫描。
- 选择态继续使用原分享、删除、恢复、复制、移动、转换、旋转、元数据编辑、GPX 和剪贴板动作。
- 回收站仍是 `TrashFilter` 集合，使用恢复和永久删除语义。
- 外部和内部 Picker 不展示普通浏览 Header，固定筛选模式仍禁止移除调用方提供的范围。

相册页新增根级“全部相册 / 动态相册 / 相册组”视图，但数据仍来自 `CollectionSource`、`dynamicAlbums` 和 `albumGrouping`。相册组点击后继续通过 `FilterGroupNotifier` 下钻，面包屑和返回父组逻辑保留；根级视图不会把组内媒体复制成新的相册对象。

相关实现：

- `lib/widgets/common/bars/mediacraft_browse_header.dart`
- `lib/widgets/collection/app_bar.dart`
- `lib/widgets/filter_grids/albums_page.dart`
- `lib/widgets/filter_grids/common/app_bar.dart`
- `lib/widgets/filter_grids/common/filter_nav_page.dart`
- `lib/widgets/filter_grids/common/filter_grid_page.dart`

## 4. 探索与工具

### 探索

探索页使用真实 `CollectionSource`：地图 Hero 优先显示当前媒体库中带 GPS 的媒体缩略图；没有可用媒体时展示空态。媒体类型卡片按真实集合计算数量，点击后进入带相应 `MimeFilter` 或 `TypeFilter` 的 `CollectionPage`。

地图入口创建仅包含已定位媒体的 `CollectionLens`，其生命周期由 `MapPage` 释放。国家、地点、标签入口继续使用原筛选网格；高级搜索继续使用 `CollectionSearchPageRoute`，没有创建新的搜索引擎。

### 工具

工具页的重复清理、文件夹、统计、回收站、设置和关于均直接打开原生产页面。回收站数量与容量来自 `source.trashedEntries`，没有使用原型示例数字。统计直接使用当前可见媒体集合，不持有额外临时 `CollectionLens`。

Tools 属于维护入口：非 `AppMode.main` 时，底栏和抽屉隐藏该入口；标准导航收到 Tools 请求时会回到 `CollectionPage`。内部固定 Picker 会在旧路由上下文复制当前筛选范围，并保持原 `AppMode`，防止进入维护 UI 或逃逸调用方限定的集合。

## 5. 查看、视频与编辑

查看器、视频播放器、详情和编辑器采用原型的视觉层级，但继续调用原业务委托和播放器控制器：

- 查看器保留连续浏览、收藏、分享、详情、删除、快捷动作和更多操作。
- 视频保留播放、跳转、逐帧、截帧、静音、轨道、字幕、倍速、AB 循环、外部播放器和锁定状态。
- 详情保留基础信息、拍摄参数、位置、标签、评分、EXIF/XMP、内容凭证、元数据移除与导出。
- 查看器中的“编辑”动作继续调用 Android 系统外部编辑器，没有被改为直接打开项目内部编辑器。
- 项目内部裁剪、比例、旋转和翻转编辑器仍通过 `ACTION_EDIT` Intent 进入，没有新增滤镜或调色功能。该入口已在模拟器中成功打开，并完成取消返回验证，取消时未保存修改。
- Motion Photo、全景、多页、SVG 和 GeoTIFF 等特殊媒体仍按格式和设备支持条件出现。

查看器继续使用独立深色视觉环境：系统状态栏和导航栏在查看器内使用深色样式，与普通页面主题分离。深色主题 seed tonal 已提高对比度，避免深色表面与文字、图标层级过近。

底部导航高度和拖动条留白会根据 `fontScale` 动态调整，避免大字体挤压标签和手势区域。导航测试新增 2 倍字体回归场景，用于覆盖底栏尺寸和文本布局。

主要文件：`lib/widgets/viewer/`、`lib/widgets/editor/`、`lib/widgets/viewer/action/`、`lib/widgets/viewer/info/`。

## 6. 搜索、地图与统计

搜索界面继续构造现有 `CollectionFilter`：文本、日期、格式、相册、地点、标签、评分、元数据和内容凭证可组合，搜索历史仍受隐私设置控制。动态相册保存的是筛选规则，不复制媒体。

地图仍由 `MapPage`、`GeoMap` 和 flavor 提供的地图样式驱动，保留聚合、区域筛选、缩略条、外部地图、轨迹、位置编辑和桌面快捷方式。GeoTIFF 覆盖和外部轨迹只在调用方提供相应上下文时出现。

统计页继续从真实条目计算数量、容量、时长、MIME、日期、国家、省州、地点、标签、相册和评分。排行导出为 CSV；字段可选的媒体明细支持 CSV 或 JSON，这两条导出流程没有合并。

主要文件：

- `lib/widgets/search/collection_search_delegate.dart`
- `lib/widgets/map/map_page.dart`、`lib/widgets/common/map/`
- `lib/widgets/stats/stats_page.dart`、`top_page.dart`
- `lib/widgets/dialogs/export_collection_stats_page.dart`

## 7. 设置与隐私

设置仍保留导航、缩略图、查看器、视频、隐私、无障碍、显示、语言与格式等分类，以及设置搜索和 JSON 导入导出。新布局没有覆盖用户已有主题、动态配色、纯黑/深色、语言、单位、日历、快捷动作和导航偏好。

隐私能力保持原边界：

- 隐藏规则只控制筛选对象的可见性，不提供认证或加密。
- 保险库是带系统认证、图案、PIN 或密码的特殊实体相册。
- 回收站关闭仍需处理现有内容及使用回收站策略的保险库。
- SAF 授权、媒体权限、位置权限和管理所有媒体仍由 Android 系统及原服务处理。
- 百度地图隐私同意只在选择百度地图样式时进入，不被普通设置页提前代替。

主要文件：`lib/widgets/settings/`、`lib/widgets/settings/privacy/`、`lib/model/vaults/`、`lib/widgets/common/action_mixins/vault_aware.dart`。

## 8. 欢迎、空态与异常

首次使用页保留条款接受、可选应用信息访问、按 flavor 出现的错误报告选择，以及后续媒体权限流程。拒绝媒体权限不会强制退出应用。

生产 UI 继续提供媒体加载失败、权限受限、空集合、重复扫描取消或失败、文件变化、字节复核失败、SAF 列表失败、地图初始化失败、更新检查失败和设置导入失败等状态。原型中的错误文本和结果是演示，生产结果由真实服务返回。

主要文件：`lib/widgets/welcome_page.dart`、`lib/widgets/home/home_error.dart`、`lib/widgets/collection/load_error.dart`、`lib/widgets/duplicates/`、`lib/widgets/settings/privacy/access_grants_page.dart`。

## 9. Flavor、TV 与 AppMode 边界

- 本地开发和默认依赖配置继续使用 `libre` flavor。
- GitHub 正式发布默认使用 `baidu` flavor 和 `com.mediacraft.app.baidu`，不会因此改变本地默认 flavor。
- `libre` 使用无 Google 服务的平台实现；`baidu` 增加百度普通/卫星地图、Key/签名要求和地图隐私同意。OpenFreeMap 仍是可用替代样式。
- TV 布局继续使用 `TvRail`、焦点导航和 TV 操作行；移动端品牌 Header 与固定底栏不会替代 TV 结构。
- 壁纸、屏保、查看、编辑和预览地图继续走 `HomePage` 的专用重定向分支。
- 外部单选、多选和筛选条件 Picker 保留原返回协议；内部固定 Picker 保留调用方筛选范围，维护工具不会突破模式限制。

## 10. 品牌与内部兼容标识

用户可见的应用名称、Logo、启动资源、商店素材、说明文本和主要界面已使用 MediaCraft 品牌。品牌资源只能在已有授权和项目使用边界内分发；第三方照片、地图服务、图标字体和开源依赖继续遵守各自来源与许可证。

项目基于 Aves 源码衍生，许可证、原始版权和第三方通知必须保留。内部 `aves` 包名、类名、数据库结构、通道名和兼容字段在不直接面向用户时继续保留，以避免破坏数据、插件和上游兼容；内部标识保留不表示 Aves 官方合作或背书。

## 11. 验证与证据

静态原型验证历史保留在：

- `docs/design/mediacraft-ui/VALIDATION.md`
- `docs/design/mediacraft-ui/previews/validation.json`
- `docs/design/mediacraft-ui/previews/`

生产实现的验证结果如下：

| 验证项 | libre | baidu |
|---|---:|---:|
| Flutter analyze | 0 issues | 0 issues |
| Flutter tests | 140 通过 | 140 通过 |
| Android JVM tests | 23 通过、0 skip | 23 通过、0 skip |
| Debug 默认 ABI 构建 | 成功 | 成功 |

`docs/design/mediacraft-ui/UI-VERIFICATION.md` 由根代理整理最终命令、产物和设备证据。商店截图生成记录位于 `artifacts/ui-redesign-2026-11-17/store-screenshots.json`，libre 最终 UI 总览位于 `artifacts/ui-redesign-2026-11-17/libre-final/overview.png`。

已验证内部编辑器通过 `ACTION_EDIT` 在模拟器打开并取消返回，未保存修改。其他设备兼容、TV 遥控器完整流程、DLNA 投放、所有地图服务组合和全部特殊媒体格式未做全面实测；本文不据此声称全面设备验收、商店发布或生产部署完成。
