# GitHub Release 更新实现约定

2026-11-11。用户已授权公开仓库、增加应用内更新、完成后发布 GitHub Release，并选择：专用发布签名；应用内下载 APK，再打开 Android 系统安装界面。

## 范围和发布格式

- 固定来源：`404NotFoundYou/MediaCraft` 的 GitHub 正式 Release，不使用访问令牌。
- 手动“检查更新”入口；未点击不进行更新网络请求，媒体仍只在本机处理。
- 计划首个正式签名版本 `0.2.0`，versionCode `2`，tag `v0.2.0`。
- Release 附件：`MediaCraft-0.2.0.apk`、同名 `.sha256` 校验文件和 `update.json`。
- `update.json` 字段：schemaVersion=1、versionName、versionCode、packageName=`com.mediacraft.app`、minSdk、apkName、sha256。
- 旧 0.1.0 调试签名与新正式签名不同，首次迁移需要卸载调试版后安装正式版；用户已确认。后续正式版本沿用同一专用签名。

## 固定共享接口

`update/UpdateModels.kt` 由 root 维护，其他开发者不改字段，有缺项先通知。

### 规则与 JSON（领域开发者）

- `object ReleasePolicy`：常量 `OWNER`、`REPO`、`API_URL`、`PACKAGE_NAME`、`MAX_APK_BYTES`。
- `fun manifestAsset(release: GitHubRelease): ReleaseAsset`：仅受信仓库相应 tag 下的 update.json。
- `fun resolve(release: GitHubRelease, manifest: UpdateManifest, current: InstalledVersion, sdk: Int): UpdateCheckResult`：校验稳定版本、tag/manifest、Android versionCode 单调增长、包名、最低系统、唯一 APK 附件、HTTPS/仓库绑定 URL、大小和 SHA256。不能把错误当作“已最新”。
- `fun isAllowedNetworkUrl(url: String): Boolean`：只允许 HTTPS/无 userinfo/标准端口，初始 GitHub 固定 API/仓库附件及必要 GitHub CDN（api.github.com、github.com、release-assets.githubusercontent.com、objects.githubusercontent.com、github-releases.githubusercontent.com）。初始附件还须严格绑定 OWNER/REPO/tag/name，不能仅因域名允许就接受其他仓库。
- `object UpdateJson`：`parseRelease(text)`、`parseManifest(text)`、`encodeUpdate(AppUpdate)`、`decodeUpdate(text)`。JSON 字段类型/范围非法显式报错。
- 单元测试覆盖正常升级、当前/旧发布、草稿预发布、缺附件、错 tag/包名/hash/URL、SDK不兼容、JSON损坏。

### 网络、下载与校验（数据开发者）

- `GitHubUpdateClient(context: Context) : UpdateClient`，构造中可增加测试用 HTTP transport，但生产来源固定。
- 匿名调用 latest API；404 是无 Release，403/429/网络错误可区分显示，禁止伪装“当前最新”。
- 请求/重定向严格使用上面的域名边界，最多5次；连接与读取超时，API/manifest响应限2 MiB，APK限制 MAX_APK_BYTES（建议256 MiB）。不关闭 TLS 校验。
- 流式下载到 cacheDir/updates，自有固定安全文件名；进度与取消，校验字节数和 SHA256。失败只清理自身临时文件。
- 使用 PackageManager 读取 APK 包名、versionCode、签名，核对安装包与 manifest、当前已安装签名一致且不是降级；系统安装器仍负责最终验证和用户确认。
- `verify` 可在设置授权返回时重新核对缓存 APK。对错误包、错误签名、缺少摘要/安装包的情况显式失败。
- 单独文件及测试仅放 `update/GitHubUpdateClient.kt`、`update/ApkVerifier.kt`（及必要 network helper）和对应测试，勿改规则模型或 UI。

### UI/安装集成（UI开发者）

- 独立 UpdateViewModel + UpdateDialog/Update入口，不把网络状态塞进媒体扫描 ViewModel。
- 顶部易发现、可访问“检查更新”，显示当前版本、检查中/无新版/无发布/错误/新版说明/下载进度/校验完成。
- 单次更新操作，支持取消，不自动下载或安装。
- 下载后通过 FileProvider 的 cache/updates 路径和 ACTION_VIEW APK MIME 交给系统安装；先检查 canRequestPackageInstalls，未授权则由用户进入该应用“允许此来源”设置，返回后重新 verify 再打开安装器。
- 保存已验证文件路径及 UpdateJson.encodeUpdate 元数据用于配置/进程恢复；不能恢复后直接无校验安装，不吞网络/Activity错误。
- MainActivity、ui/Update*、MediaCraftApp入口由 UI开发者修改。root 添加 INTERNET / REQUEST_INSTALL_PACKAGES / FileProvider 的 manifest 和 paths。

## root 负责

- Git 分支、仓库公开前只读检查与 GitHub 可见性调整。
- 版本、BuildConfig、签名与 Gradle / Manifest / FileProvider 路径。
- 专用密钥及本地 signing.properties 均不提交 Git；示例配置只含占位符。
- README/测试说明/发布脚本，统一构建测试与真实 Release API/下载/安装验证，提交推送与发布附件/校验。

不要各自启动 Gradle 或改其他开发者目录；统一由 root 构建，避免并行输出污染。
