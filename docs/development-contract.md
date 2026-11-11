# 开发接口约定

2026-11-11。用于并行开发对齐，正式业务边界以 product-design.md 为准。

共享模型位于 `com.mediacraft.app.domain.Models.kt`，禁止各层另造同名模型。应用 Android 11+，包名 com.mediacraft.app，单 app 模块。

## 领域规则接口（domain 包）

- `DuplicateScanner(source: MediaSource)`：`suspend fun scan(scope: ScanScope, onProgress: (ScanProgress) -> Unit = {}): ScanResult`。必须可协程取消，流式 SHA256/字节核对，疑似版本只作候选；组不可链式把互不相同项拉到一起。
- `RenamePlanner`：`fun create(selected: List<MediaItem>, allMedia: List<MediaItem>, options: RenameOptions): RenamePlan`。预览锁定随机名称，序号稳定排序，同目录碰撞和交换命名拒绝，无需改名标记不执行。
- `QualityRecommender`：由领域开发者自行提供内部接口供 scanner 使用，不给 HDR/帧率/跨编码互有优劣组强行打分。缺元数据显式不能可靠推荐。

## Android 数据接口（data 包）

- `MediaRepository(context: Context) : MediaSource`。额外提供 `suspend fun find(id: String, includeTrashed: Boolean = false): MediaItem?`，返回新快照；`suspend fun existingNames(media: MediaItem): Set<String>` 查询同卷同目录名称。
- `OperationRepository(context: Context, media: MediaRepository)`：
  - `val records: Flow<List<OperationRecord>>`
  - `suspend fun prepare(type: OperationType, specs: List<OperationSpec>, sourceId: String? = null): PreparedOperation`：持久化计划并校验真实状态。限制单次最多 200 项，UI 显式提示分批；不要截断用户输入。
  - `fun authorization(operation: PreparedOperation): IntentSender`：重命名/撤销用 createWriteRequest；TRASH 用 createTrashRequest(true)；RESTORE 用 false。
  - `suspend fun complete(id: String, approved: Boolean): OperationRecord`：授权通过不等同实际成功，逐项读取系统结果；改名需执行并核对结果，记录逐项状态。取消不得计成功。
  - `suspend fun undoSpecs(record: OperationRecord): List<OperationSpec>`：只针对成功项；检查原名占用、当前名和身份；恢复仍由 prepare+系统授权+complete 完成。
  - `suspend fun reconcilePending(excludeId: String? = null)`：中断后按当前状态对账，不自动重放写操作；排除仍在等待系统回调的活动计划。
- `data class PreparedOperation(val id: String, val type: OperationType, val uris: List<String>)`，可放在 data 包。
- Room 持久化操作日志。逐项执行前后更新；重命名前/后校验内容摘要；TRASH 的 keeper 不可丢失或变更；EXACT 在提交系统请求前重读双方摘要/字节核对。
- `OperationSpec.validationError` 表示不可执行项的明确原因，必须持久化为失败结果，不能静默丢弃。`prepare` 返回的 URI 仅含有效项；为空时直接完成并呈现失败记录，不打开系统授权。
- 目录和名称碰撞共用 `normalizedMediaName`：Unicode NFC + Locale.ROOT 大小写规范化；目录标识按卷和相对路径，不含图片/视频集合分类。

## UI

MainActivity、ui/** 归 UI 开发者；负责 ViewModel 和授权 launcher、部分权限、扫描取消、生命周期恢复、真实相册列表、照片缩放/视频同步对比、各组保留与确认、命名预览、日志和尝试撤销。依赖上述固定接口。扫描或操作时不得套用陈旧的授权/选择计划，任何异步操作异常都显示结果；不吞取消异常。

root 负责 Gradle/Manifest/资源、共享模型、构建、独立测试和集成修复。其他开发者不修改这些文件；需要改模型先发消息协调。
