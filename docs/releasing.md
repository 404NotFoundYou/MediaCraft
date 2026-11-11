# 构建与发布 GitHub Release

## 签名

正式版使用专用密钥。把 `signing.properties.example` 复制为本机的 `signing.properties`，填写密钥路径与密码。密钥放在仓库之外，完整配置已被 Git 忽略。备份密钥与本机签名配置；后续覆盖升级必须保持签名一致。

缺少有效签名配置时，正式版构建会明确失败。不要把私钥、密码或完整配置上传到公开仓库或 Release。

## 发布步骤

1. 修改 `app/build.gradle.kts` 的默认版本名与递增 versionCode，完成测试并提交。
2. 生成正式签名 APK、更新清单和校验文件：

```powershell
.\scripts\release.ps1 -Version 0.2.0 -VersionCode 2
```

3. 为已提交并推送的版本创建 tag `v0.2.0` 和正式 GitHub Release，上传 `artifacts/releases/v0.2.0/` 中的三个附件，并使用对应版本说明。
4. 从匿名 latest API 和 APK 下载地址核对 tag、versionCode、摘要及签名；应用内检查应该识别当前版或新版。

草稿和预发布不进入稳定更新通道。发布完成后不覆盖同版本附件；修正发布内容时递增版本号与 versionCode。

## 更新来源

固定使用 `404NotFoundYou/MediaCraft` 的 GitHub Release，不在应用中保存 GitHub 令牌。`update.json` 把 tag、APK 名称、版本、包名、最低系统和 SHA-256 绑定起来；安装时还会与当前应用签名比较。

首个正式签名版为 0.2.0。0.1.0 调试版与它签名不同，首次迁移需要卸载调试版后安装，之后正式版可持续覆盖更新。
