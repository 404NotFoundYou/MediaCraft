$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskFlutter = Join-Path $projectRoot '.tooling\flutter\bin\flutter.bat'
if (-not (Test-Path -LiteralPath $taskFlutter)) {
    throw '请先运行 scripts/bootstrap.ps1 安装项目固定的 Flutter SDK'
}
$expectedFlutterVersion = '3.47.8'
$actualFlutterVersion = (& git -C (Join-Path $projectRoot '.tooling\flutter') describe --tags --exact-match HEAD 2>$null)
if ($LASTEXITCODE -ne 0 -or $actualFlutterVersion -ne $expectedFlutterVersion) {
    throw "项目 Flutter SDK 版本不匹配：期望 $expectedFlutterVersion，实际 $actualFlutterVersion"
}
$sdkChanges = (& git -C (Join-Path $projectRoot '.tooling\flutter') status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0 -or $sdkChanges) { throw '项目 Flutter SDK 工作区不干净' }

Push-Location $projectRoot
try {
    & $taskFlutter clean
    if ($LASTEXITCODE -ne 0) { throw '切换百度 flavor 前清理失败' }
    Copy-Item -LiteralPath 'flavors/pubspec_baidu.yaml' -Destination 'pubspec.yaml'
    Copy-Item -LiteralPath 'flavors/pubspec_baidu.lock' -Destination 'pubspec.lock'
    & $taskFlutter pub get --enforce-lockfile
    if ($LASTEXITCODE -ne 0) { throw '百度 flavor 依赖解析失败' }
    if ((Get-FileHash 'pubspec.yaml').Hash -ne (Get-FileHash 'flavors/pubspec_baidu.yaml').Hash -or
        (Get-FileHash 'pubspec.lock').Hash -ne (Get-FileHash 'flavors/pubspec_baidu.lock').Hash) {
        throw '当前依赖配置与百度 flavor 快照不一致'
    }
    $packageConfig = Get-Content -LiteralPath '.dart_tool\package_config.json' -Raw | ConvertFrom-Json
    $servicesPackage = $packageConfig.packages | Where-Object { $_.name -eq 'aves_services_platform' }
    if (-not $servicesPackage -or -not $servicesPackage.rootUri.Replace('\', '/').Contains('/plugins/aves_services_baidu')) {
        throw '当前 package_config 未启用百度的 aves_services_baidu 实现'
    }
} finally {
    Pop-Location
}
