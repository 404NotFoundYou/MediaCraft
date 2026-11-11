# MediaCraft

本地 Android 照片、视频整理工具。最低 Android 11（API 30），无需登录，不申请互联网或所有文件访问权限。

## 产品范围

- 完全重复：流式摘要与字节核对。
- 疑似同内容版本：照片像素建议、视频可比较指标建议；候选必须人工核对，不能把相似度当成确定重复。
- 随机、自定义模板批量命名：预览、冲突校验、扩展名保留。
- 系统回收站、逐项操作记录、尝试撤销与中断对账。

详细规则见 [产品设计](docs/product-design.md)。

## 构建

安装 JDK 17、Android SDK Platform 36 和 Build Tools。配置 `JAVA_HOME`，在忽略提交的 `local.properties` 中设置 `sdk.dir`，或配置 `ANDROID_HOME`。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Windows 也可运行 `scripts/build.ps1`，它复用当前配置的 JDK/SDK 环境。

测试 APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。这是调试签名包，发布签名由使用者单独配置，密钥不得提交代码库。

## 设备验证

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

仪器测试只应创建、操作自身带测试前缀的媒体；不要对个人媒体运行清理或永久删除。系统确认属于产品实际流程，应用不会将扫描结果自动删除。

构建、测试与交付状态见 [开发验证记录](docs/implementation-status.md)，未验证项目不得视为已完成。

手机安装与验收步骤见 [Android 14 及以上测试说明](docs/testing-on-android14.md)。
