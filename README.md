# MediaCraft

本地 Android 照片、视频整理工具。最低 Android 11（API 30），无需登录。媒体扫描和文件处理在本机完成，不申请所有文件访问权限。媒体详情可读取 GPS 并通过系统地理编码服务查询地名；用户加载在线地图时访问 OpenStreetMap。地名和地图服务只接收坐标，不接收媒体文件。手动检查更新和下载 APK 时访问 GitHub。

## 产品范围

- 完全重复：流式摘要与字节核对。
- 疑似同内容版本：照片像素建议、视频可比较指标建议；候选必须人工核对，不能把相似度当成确定重复。
- 随机、自定义模板批量命名：预览、冲突校验、扩展名保留。
- 系统回收站、逐项操作记录、尝试撤销与中断对账。
- 从 GitHub 正式 Release 检查更新、应用内下载与校验 APK，再由用户确认系统安装。
- 媒体详情中的拍摄位置：按需读取照片 EXIF / 视频位置元数据，展示地名、地址和坐标，支持复制坐标、加载地图与外部地图查看；无位置、权限不足和查询失败分别提示。HTML 原型与验收边界见 [位置详情说明](docs/location-details.md)。

详细规则见 [产品设计](docs/product-design.md)。

## 构建

安装 JDK 17、Android SDK Platform 36 和 Build Tools。配置 `JAVA_HOME`，在忽略提交的 `local.properties` 中设置 `sdk.dir`，或配置 `ANDROID_HOME`。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Windows 也可运行 `scripts/build.ps1`，它复用当前配置的 JDK/SDK 环境。

测试 APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。这是调试签名包，发布签名由使用者单独配置，密钥不得提交代码库。

正式版从 0.2.0 开始使用专用发布签名。构建与发布流程见 [发布说明](docs/releasing.md)；旧 0.1.0 调试版首次迁移需要卸载后安装，之后正式版可覆盖升级。

## 设备验证

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

仪器测试只应创建、操作自身带测试前缀的媒体；不要对个人媒体运行清理或永久删除。系统确认属于产品实际流程，应用不会将扫描结果自动删除。

当前版本 0.4.0 的构建、测试与交付状态见 [0.4.0 验证记录](docs/releases/v0.4.0-validation.md)，历史发布见 [0.2.0 验证记录](docs/releases/v0.2.0-validation.md)。未验证项目不得视为已完成。

手机安装与验收步骤见 [Android 14 及以上测试说明](docs/testing-on-android14.md)。
