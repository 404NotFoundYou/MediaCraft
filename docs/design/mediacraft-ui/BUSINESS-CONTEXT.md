# MediaCraft UI 重设计业务上下文

## 0. 文档说明

- 代码库：MediaCraft
- 分析范围：全项目的用户可见业务能力，重点用于重新设计移动端 UI 信息架构和 HTML 静态原型。
- 范围边界：覆盖图库组织、搜索、地图、查看器、编辑与文件操作、重复项清理、回收站、设置、隐私、数据维护、关于与系统入口。构建发布流程和底层插件实现仅记录会影响界面的差异。
- 分析时间：2026-11-17
- 结论等级：`代码明确体现` 表示当前可执行代码直接支持；`根据代码推断` 表示多个入口或调用关系共同支持；`待确认` 表示需产品或设备验证。
- 原型边界：HTML 原型只模拟本机媒体、权限、地图、清理结果和系统动作，不读取真实设备文件，不请求 Android 权限，不执行删除、移动、分享、编辑或联网更新。
- 验证边界：本次只完成代码审计和静态设计依据整理，未执行 Android 设备、不同系统版本、TV 布局、百度 SDK 或各 flavor 的真机验证。

## 1. 业务总览与业务主线

### 1.1 系统定位

MediaCraft 是面向个人 Android 设备用户的本地媒体图库和元数据探索工具。核心对象是设备上的照片、视频及其元数据；主要目标是浏览和组织媒体、查看详细信息和地理分布、执行本地文件操作，并通过回收站、权限管理和重复项复核降低误操作风险。

项目没有账号、租户、后台管理员或多人协作体系。UI 中的能力差异主要来自 Android 权限、设备能力、`AppMode`、TV/移动布局和构建 flavor，而不是登录角色。

### 1.2 完整业务闭环

1. 用户首次启动应用，阅读条款并明确接受；可选择是否允许读取已安装应用信息，部分 flavor 可选择错误报告。
2. 应用进入主页并请求 Android 照片、视频和媒体位置权限，加载当前获准访问的本机媒体。
3. 用户从图库、相册、标签、地点、地图、搜索、文件浏览或系统 Intent 进入一个媒体集合。
4. 用户筛选、排序、分组、选择媒体，或打开单个媒体进入照片/视频查看器。
5. 用户查看元数据、位置、直方图和特殊媒体内容，或执行收藏、标签、评分、重命名、移动、复制、分享、编辑、位置修改、加入相册、回收站等动作。
6. 文件变更由 Android MediaStore、SAF 或文件 API 执行；权限不足时先请求授权，取消或失败时保持原数据并反馈原因。
7. 用户可从回收站恢复或永久删除，也可使用重复项清理扫描完全重复文件；每组默认首项为保留项，用户可主动更换，待清理项经手动勾选和最终复核后移入回收站。
8. 用户通过设置调整导航、缩略图、查看器、视频、隐私、无障碍、显示、语言格式，并可导入/导出个性化数据。

### 1.3 模块地图

| 模块 | 业务职责 | 主要入口 | 关键证据 | 置信度 |
|---|---|---|---|---|
| 首页与导航 | 承载默认主页、抽屉/底部导航及外部 Intent 路由 | `/`、`HomePage` | `lib/widgets/home/home_page.dart`、`lib/widgets/navigation/` | 代码明确体现 |
| 图库与组织 | 时间线/网格浏览、筛选、相册、标签、地点、国家、收藏、动态相册 | Collection、Albums、Tags、Places | `lib/widgets/collection/`、`lib/widgets/filter_grids/`、`lib/model/filters/` | 代码明确体现 |
| 相册组与标签组 | 作为相册或标签的层级导航容器，组织筛选入口但不搬移、复制或重新归档媒体文件 | Albums、Tags | `lib/widgets/filter_grids/albums_page.dart`、`tags_page.dart`、`lib/model/grouping/common.dart` | 代码明确体现 |
| 保险库 | 作为相册列表中的特殊实体相册，支持创建、配置、锁定和解锁，并按保险库设置决定是否使用回收站 | Albums | `lib/widgets/filter_grids/albums_page.dart`、`common/action_delegates/album_set.dart`、`lib/widgets/common/action_mixins/vault_aware.dart` | 代码明确体现 |
| 搜索与统计 | 搜索媒体和元数据，展示集合统计 | Search、Stats | `lib/widgets/search/`、`lib/widgets/stats/` | 代码明确体现 |
| 地图 | 按地理位置浏览、聚合、筛选和编辑位置 | `/map` | `lib/widgets/map/map_page.dart` | 代码明确体现 |
| 查看器 | 照片、视频、全景、多页、动态照片、幻灯片与详细信息 | EntryViewer | `lib/widgets/viewer/` | 代码明确体现 |
| 编辑与文件操作 | 图片编辑、重命名、移动、复制、分享、打印、壁纸及元数据修改 | 查看器/多选操作 | `lib/widgets/editor/`、`lib/widgets/collection/entry_set_action_delegate.dart` | 代码明确体现 |
| 重复项清理 | 扫描完全重复媒体，默认保留每组首项，由用户手动勾选待清理项并复核后移入回收站 | `/duplicates` | `lib/widgets/duplicates/duplicate_page.dart` | 代码明确体现 |
| 隐私与维护 | 权限、隐藏项、SAF 授权、回收站、数据导入导出、缓存与更新 | Settings、About | `lib/widgets/settings/`、`lib/widgets/about/` | 代码明确体现 |

## 2. 用户身份、权限与运行模式

### 2.1 身份和模式

| 身份/模式 | 含义 | 能力差异 | 证据 | 置信度 |
|---|---|---|---|---|
| 个人设备用户 | 唯一业务使用者，管理当前设备可访问媒体 | 没有账号级数据隔离；范围由本机媒体和系统授权决定 | `README.md`、`lib/model/source/collection_source.dart` | 代码明确体现 |
| `AppMode.main` | 常规完整图库应用 | 提供完整导航和管理能力 | `lib/app_mode.dart` | 代码明确体现 |
| 文件选择/壁纸等模式 | 由系统或外部应用启动的受限任务 | 页面入口、可选媒体和可用动作会收窄 | `lib/app_mode.dart`、`lib/widgets/home/home_page.dart` | 代码明确体现 |
| 移动端布局 | 手机/平板的触控界面 | 支持底部操作、手势、移动端权限和系统入口 | `lib/model/settings/settings.dart`、各设置 section | 代码明确体现 |
| TV 布局 | 遥控器和大屏界面 | 隐藏部分移动端专属设置和操作 | `lib/widgets/settings/settings_page.dart` | 代码明确体现 |
| `libre` flavor | 默认本地开发版本，无 Google 服务平台实现 | 地图至少提供 OpenFreeMap | `lib/main_libre.dart`、`plugins/aves_services_none/` | 代码明确体现 |
| `baidu` flavor | GitHub 正式发布默认版本 | 增加百度普通/卫星地图及单独隐私同意 | `lib/main_baidu.dart`、`plugins/aves_services_baidu/` | 代码明确体现 |

### 2.2 Android 权限与数据范围

| 权限/授权 | 控制范围 | UI 要求 | 证据 |
|---|---|---|---|
| 照片与视频权限 | 应用可加载的媒体集合 | 需要受限、拒绝、部分授权和空图库状态 | `lib/model/app/permissions.dart`、`lib/widgets/home/home_page.dart` |
| 媒体位置权限 | 读取未脱敏地理元数据 | 地图和位置详情应能表达信息缺失 | `lib/model/app/permissions.dart` |
| 管理所有媒体 | 支持设备时减少逐项写入确认 | 设置中展示当前状态并跳转系统授权 | `lib/widgets/settings/privacy/permissions/manage_media.dart` |
| MediaStore 写授权 | 修改指定媒体 | 操作前请求，拒绝后不继续 | `lib/widgets/common/action_mixins/permission_aware.dart` |
| SAF 目录授权 | 访问受限目录或导出目录 | 可查看、添加和撤回已授权目录 | `lib/services/storage_permission_service.dart`、`access_grants_page.dart` |
| 百度地图隐私同意 | 初始化百度 SDK 和联网地图 | 同意前只显示说明、政策链接和替代地图入口 | `plugins/aves_services_baidu/lib/aves_services_platform.dart` |

## 3. 核心业务流程

### 3.1 媒体发现与浏览

`系统媒体/获准目录 -> 建立本地集合 -> 图库/相册/标签/地点/搜索/地图 -> 筛选排序分组 -> 单项查看或多选操作`

- 原型需要同时表达内容网格、时间分段、空状态、加载失败、选择模式和集合上下文。
- 相册、标签、地点等不是独立媒体副本，而是对同一媒体集合的不同筛选和组织视角。
- 搜索历史可在隐私设置中禁用；禁用时立即清空既有历史。

### 3.2 查看器与媒体处理

`打开媒体 -> 查看画面/播放视频 -> 查看信息与元数据 -> 执行快捷动作或进入编辑 -> 请求所需权限 -> 保存或反馈失败`

需要保留的查看器能力包括：缩放与翻页、照片/视频播放、字幕和播放控制、全景、多页媒体、动态照片、幻灯片、直方图、拍摄参数、日期、描述、评分、标签和位置。具体动作根据媒体类型、设备能力和当前 `AppMode` 动态显示。

### 3.3 地图浏览与位置编辑

`有坐标媒体 -> 地图点位/聚合 -> 点击单点进入查看器，点击聚合进入筛选图库 -> 可编辑或移除聚合内媒体位置`

地图还支持样式切换、缩放、外部地图打开、快捷方式和轨迹显示。`baidu` flavor 默认仍可使用 OpenFreeMap；选择百度样式时才进入百度隐私同意与 SDK 初始化流程。

### 3.4 重复项清理

`选择范围 -> 扫描 -> 查看读取问题和完全重复组 -> 默认首项为保留项（可主动更换） -> 手动勾选待清理项 -> 确认数量与容量 -> 最终字节级复核 -> 移入回收站 -> 自动重新扫描`

关键约束：

- 扫描范围为全部、照片或视频，只处理当前可见、已授权、可访问且未进入回收站的媒体。
- 扫描不会自动删除；每组默认首项为保留项，待清理选择默认为空，不会自动勾选其余文件。
- 用户可主动更换每组保留项；保留项不能同时成为待处理项。
- UI 必须展示扫描准备、读取文件、核对内容、完成、取消、失败、无重复项和读取问题。
- 提交前校验媒体仍存在、URI 未变化、未进入回收站。
- 最终对保留项和待处理项执行字节级复核；复核失败时不移动任何文件。
- 只移入回收站，不永久删除。回收站关闭时必须阻止操作并引导启用。
- 成功后重新扫描，以当前文件状态刷新结果。

证据：`lib/widgets/duplicates/duplicate_page.dart` 的 `_startScan()`、`_reviewAndDelete()`、`_verifyBeforeMove()`；`duplicate_selection.dart` 的 `keep()`、`select()`、`isValid()`。

### 3.5 设置与个性化

设置中心包含八个一级分类：

| 分类 | 主要能力 |
|---|---|
| 导航 | 默认主页、保持亮屏、双击返回退出、侧栏菜单、底部导航、确认对话框 |
| 缩略图 | 集合快速操作、缩略图叠加层、连拍识别模式 |
| 查看器 | 快速操作、叠加层、幻灯片、手势、刘海区域、动态照片自动播放、背景 |
| 视频 | 视频显示、播放行为、控件、字幕主题、视频缩略图方式 |
| 隐私 | 权限、隐藏项、存储访问、回收站、搜索历史、自动导出设置 |
| 无障碍 | 捏合替代操作、动画、生效时间 |
| 显示 | 明暗主题、强调色、动态配色、模糊、亮度、刷新率、TV 界面 |
| 语言和格式 | 界面语言、日历、坐标格式、单位制、数字格式 |

设置页还支持搜索，以及 JSON 导入/导出封面、动态相册、收藏和设置。HTML 原型应模拟选择项和成功/失败反馈，不进行真实文件读写。

### 3.6 回收站与隐私维护

- 启用回收站后，普通删除和重复项清理可先进入可恢复区域。
- 保险库是相册列表中的特殊实体相册，可创建、配置、锁定和解锁；支持系统认证、图案、PIN 和密码等锁定方式。系统认证在支持的设备上由系统本地认证能力承载，包括可用的生物识别方式。
- 关闭全局回收站时，仅当至少一个保险库已启用自己的回收站策略时阻止关闭；未启用该策略的保险库不构成依赖。
- 回收站非空时，关闭前必须确认并永久删除其中项目；删除失败或取消后保持启用。
- 隐藏项与保险库是不同机制：隐藏项只控制筛选条件的可见性，不提供认证或加密；每条隐藏规则分别启用、停用或移除恢复显示，不存在统一暂停全部规则的业务开关。
- 存储访问页列出 SAF 授权目录并支持逐个撤回。
- 自动导出设置需要用户选择目标目录，取消选择时不启用。

## 4. 核心数据实体

| 实体 | 业务含义 | 关键关系 | 证据 |
|---|---|---|---|
| `AvesEntry` | 单个照片或视频及其路径、类型、时间、位置、大小、回收站状态等 | 属于一个或多个筛选视图，可被选择、查看和修改 | `lib/model/entry/entry.dart` |
| `CollectionSource` | 当前应用可访问媒体的统一来源 | 提供可见、已回收及按 ID 查询的条目 | `lib/model/source/collection_source.dart` |
| `CollectionFilter` | 相册、标签、位置、收藏、类型等筛选条件 | 多个筛选共同形成当前集合视图 | `lib/model/filters/` |
| `CollectionLens` | 当前集合的筛选、排序和分组上下文 | 驱动图库、地图和查看器的打开范围 | `lib/model/viewer/`、`lib/widgets/collection/` |
| `AlbumGroupFilter` / `TagGroupFilter` | 相册或标签的层级容器 | 包含子筛选入口，仅改变导航与组织关系，不搬移媒体文件 | `lib/model/filters/container/album_group.dart`、`tag_group.dart`、`lib/model/grouping/common.dart` |
| `VaultDetails` | 保险库实体相册的名称、自动锁定、回收站策略和锁定方式配置 | 对应专用存储相册，由保险库注册表管理并通过相册筛选进入 | `lib/model/vaults/details.dart`、`lib/widgets/filter_grids/common/action_delegates/album_set.dart` |
| `DuplicateGroup` | 内容完全一致的一组媒体 | 包含多个 `DuplicateMember`，初始化时首项作为默认保留项 | `lib/model/duplicates/duplicate_models.dart`、`duplicate_selection.dart` |
| `DuplicateSelection` | 每组默认首项为保留项且待处理集合为空；用户可更换保留项并显式勾选待处理项 | 生成提交前不可变快照 | `lib/widgets/duplicates/duplicate_selection.dart` |
| `Settings` | 全局本地偏好 | 影响导航、显示、查看器、隐私和格式 | `lib/model/settings/settings.dart` |
| `EntryMapStyle` | 地图渲染方式 | 来自基础样式、服务 flavor 或自定义样式 | `plugins/aves_map/lib/src/style.dart` |
| 回收站条目 | `trashed=true` 的媒体 | 可恢复或永久删除，不参与重复扫描 | `lib/model/filters/trash.dart`、`duplicate_page.dart` |

## 5. 关键规则与异常处理

### 5.1 关键规则

| 场景 | 规则 | UI 结果 | 证据 |
|---|---|---|---|
| 首次启动 | 必须接受条款后才能继续 | 继续按钮在未勾选时禁用 | `lib/widgets/welcome_page.dart` |
| 媒体权限拒绝 | 不强制退出应用 | 展示受限或空集合，并保留设置入口 | `lib/widgets/home/home_page.dart` |
| 重复清理容量 | 待处理容量为所选条目 `sizeBytes` 求和 | 按钮显示数量和预计空间 | `duplicate_page.dart:_selectedBytes` |
| 重复清理安全 | 每组保留一份且最终字节复核通过 | 否则显示复核失败并停止 | `duplicate_selection.dart`、`duplicate_page.dart` |
| 回收站关闭 | 已启用回收站策略的保险库存在，或现有回收项目永久删除未完成时不能关闭 | 警告、确认或保持原开关 | `privacy.dart:setBinUsage()`、`lib/model/vaults/details.dart:useBin` |
| 保险库访问 | 锁定状态下需按配置完成系统认证、图案、PIN 或密码验证 | 验证成功后解锁；取消或失败时不进入保险库 | `lib/widgets/common/action_mixins/vault_aware.dart:_tryUnlock()` |
| 隐藏规则 | 每条隐藏筛选条件独立启用、停用或移除 | 只影响图库可见性，不替代保险库认证 | `lib/widgets/settings/privacy/hidden_items_page.dart` |
| 设置导入导出 | 可选择封面、动态相册、收藏、设置；支持旧版无版本号数据 | 成功或失败反馈 | `settings_action_delegate.dart` |
| 更新检查 | 读取 GitHub Latest Release 并比较前三段版本号 | 最新、发现新版、检查失败 | `lib/services/app_update_service.dart` |
| 百度地图 | 未同意隐私或 SDK/Key 不可用时不得初始化百度地图 | 显示说明/错误，同时允许切换 OpenFreeMap | `aves_services_baidu/lib/aves_services_platform.dart` |

### 5.2 异常状态清单

| 异常场景 | 系统处理 | 原型需要表达 |
|---|---|---|
| 主集合初始化失败 | 显示错误详情、Bug 报告和数据库导出 | 独立错误页及导出成功/失败反馈 |
| 权限被拒绝或部分授权 | 只加载可访问媒体 | 权限受限提示、授权入口、空态 |
| 文件操作授权取消 | 中止修改 | 保留原状态并给出非破坏性反馈 |
| 重复扫描取消/失败 | 不处理任何文件 | 可重试状态 |
| 重复结果已变化 | 阻止提交并要求重新扫描 | 明确说明文件移动、消失或已回收 |
| 重复字节复核失败 | 不移动文件 | 列出问题并保留选择上下文 |
| SAF 目录列表失败 | 显示错误 | 错误与重试状态 |
| 百度政策无法打开/初始化失败 | 不启用百度 SDK | 错误提示及替代样式入口 |
| 更新请求失败 | 不改变当前版本 | “稍后重试”反馈 |
| 设置导入格式错误 | 不应用数据 | 失败反馈 |

## 6. 影响面与上下游依赖

| 依赖 | 作用 | 失败影响 | 设计注意 |
|---|---|---|---|
| Android MediaStore | 读取和修改系统媒体 | 集合不完整或写操作失败 | 显式显示授权范围和操作结果 |
| Android SAF | 受限目录、导入导出和目标目录授权 | 无法读写指定目录 | 目录授权应可查看和撤回 |
| 本地 SQLite 数据库 | 缓存媒体索引、元数据和应用状态 | 首页初始化失败 | 保留诊断和数据库导出入口 |
| 文件系统 | 移动、复制、重命名、回收和重复字节读取 | 单项或批量操作部分失败 | 操作报告不能只显示统一成功 |
| 百度地图 SDK | `baidu` flavor 的普通/卫星地图 | 百度样式不可用 | OpenFreeMap 仍应可选 |
| GitHub Releases API | 手动检查更新 | 无法判断新版本 | 展示检查失败，不阻断应用 |
| 外部 Android 应用 | 分享、外部地图、打印、快捷方式等 | 对应动作不可用 | 依据设备能力动态显示或失败反馈 |

## 7. 术语

| 术语 | 解释 | 业务上下文 |
|---|---|---|
| Entry | 一个照片或视频媒体条目 | 浏览、查看、编辑和文件操作的基本对象 |
| Collection | 经筛选、排序和分组后的媒体集合视图 | 图库、搜索、地图聚合和查看器上下文 |
| Filter | 相册、标签、地点、媒体类型、收藏等筛选条件 | 可组合形成当前集合 |
| Dynamic Album | 由规则自动形成的相册 | 可随媒体元数据变化自动更新 |
| Vault/保险库 | 相册列表中的特殊实体相册，可创建、配置、锁定和解锁；支持系统认证、图案、PIN、密码，并可独立选择是否使用回收站 | 相册组织、隐私访问和回收站依赖 |
| 相册组/标签组 | 用于组织相册或标签筛选入口的层级容器 | 调整导航层级，不移动或复制媒体文件 |
| Bin/Trash | 应用回收站 | 支持恢复和永久删除，是重复清理的前置能力 |
| SAF | Android Storage Access Framework | 用户授予特定目录访问权的系统机制 |
| MediaStore | Android 系统媒体数据库和写入授权机制 | 决定媒体可见性和修改权限 |
| AppMode | 应用被启动时的任务模式 | 完整图库、文件选择、壁纸等模式的能力不同 |
| Flavor | 不同构建服务组合 | `libre` 与 `baidu` 的地图服务和隐私流程不同 |
| 完全重复 | 文件内容经扫描与复核一致的一组媒体 | 默认保留首项，用户可更换；待清理项必须手动勾选 |

## 8. 证据索引

| 编号 | 文件/位置 | 说明 |
|---|---|---|
| E01 | `README.md` | 产品定位、主要能力、flavor 与发布边界 |
| E02 | `lib/widgets/home/home_page.dart` | 启动权限、Intent 路由、主页初始化错误 |
| E03 | `lib/widgets/welcome_page.dart` | 首次条款与可选权限开关 |
| E04 | `lib/widgets/navigation/` | 抽屉、底部栏和 TV 导航 |
| E05 | `lib/widgets/collection/` | 集合浏览、多选与批量操作 |
| E06 | `lib/widgets/viewer/` | 查看器、视频、信息、特殊媒体与动作 |
| E07 | `lib/widgets/editor/` | 图像编辑和裁剪 |
| E08 | `lib/widgets/map/map_page.dart` | 地图点位、聚合、查看器和位置编辑流程 |
| E09 | `plugins/aves_services_baidu/lib/aves_services_platform.dart` | 百度隐私同意、撤回、错误和替代地图 |
| E10 | `lib/widgets/duplicates/duplicate_page.dart` | 重复扫描、复核、回收站限制和异常状态 |
| E11 | `lib/widgets/duplicates/duplicate_selection.dart` | 每组默认首项保留、待清理集合默认为空及手动选择约束 |
| E12 | `lib/widgets/settings/settings_page.dart` | 设置八分类 |
| E13 | `lib/widgets/settings/privacy/privacy.dart` | 回收站、搜索历史、隐藏项和自动导出规则 |
| E14 | `lib/widgets/settings/settings_action_delegate.dart` | 设置数据导入导出 |
| E15 | `lib/widgets/settings/privacy/access_grants_page.dart` | SAF 授权目录管理 |
| E16 | `lib/widgets/about/about_mobile_page.dart` | 关于、更新、数据占用、许可与致谢 |
| E17 | `lib/widgets/about/data_usage.dart` | 数据占用与缓存清理 |
| E18 | `lib/services/app_update_service.dart` | GitHub Latest Release 更新检查 |
| E19 | `lib/model/app/permissions.dart` | Android 媒体权限集合 |
| E20 | `lib/app_mode.dart`、`lib/app_flavor.dart` | 运行模式与 flavor 差异 |
| E21 | `lib/widgets/filter_grids/albums_page.dart` | 保险库作为相册列表中的独立分类，以及相册组的层级展示 |
| E22 | `lib/widgets/filter_grids/common/action_delegates/album_set.dart` | 创建、配置和锁定保险库；相册组组织动作 |
| E23 | `lib/widgets/common/action_mixins/vault_aware.dart` | 系统认证、图案、PIN、密码的配置与解锁流程 |
| E24 | `lib/widgets/filter_grids/tags_page.dart`、`lib/model/grouping/common.dart` | 相册组和标签组的层级容器关系 |

## 9. 待确认问题

| 问题 | 影响范围 | 建议确认方式 |
|---|---|---|
| 新 UI 的一级导航最终采用“图库/相册/探索/工具”还是其他分组？ | 首页和全局导航 | 对 HTML 原型进行信息架构评审 |
| 系统认证、图案、PIN、密码在目标 Android 版本和设备上的兼容性、取消、连续失败及系统认证不可用时如何恢复？ | 保险库创建、配置、锁定与解锁 | 按 Android 版本和设备能力进行真机矩阵验证，并覆盖失败恢复路径 |
| 文件选择、壁纸、屏保和 TV 模式是否需要分别制作独立高保真页面？ | 原型范围和工作量 | 确认首轮原型验收范围 |
| 百度地图同意文案是否需要法务固定版本，是否允许在地图页之外提前授权？ | `baidu` flavor 隐私体验 | 依据发布政策和隐私文本确认；当前代码只在使用百度地图时请求 |
| 不同 Android 版本的部分照片授权、永久拒绝和系统设置返回路径如何展示？ | 权限状态设计 | Android 设备矩阵验证 |
| 批量文件操作的部分成功报告是否需要在首轮原型中完整展开？ | 移动、复制、删除等结果页 | 对操作报告组件进行源码和设备走查 |
| 静态原型中的样例媒体能否使用通用占位素材，还是需要项目指定素材？ | 视觉表达 | 产品确认素材授权与风格 |
