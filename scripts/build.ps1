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
    if ((Get-FileHash 'pubspec.yaml').Hash -ne (Get-FileHash 'flavors/pubspec_libre.yaml').Hash -or
        (Get-FileHash 'pubspec.lock').Hash -ne (Get-FileHash 'flavors/pubspec_libre.lock').Hash) {
        throw 'build.ps1 仅验证 libre flavor；请先应用已提交的 libre flavor 快照'
    }
    & $taskFlutter pub get --enforce-lockfile
    if ($LASTEXITCODE -ne 0) { throw '依赖解析失败' }
    # Platform implementations share package names across flavors. Analyze the
    # implementations selected by pub rather than mutually exclusive flavors.
    $packageConfig = Get-Content -LiteralPath '.dart_tool\package_config.json' -Raw | ConvertFrom-Json
    $servicesPackage = $packageConfig.packages | Where-Object { $_.name -eq 'aves_services_platform' }
    if (-not $servicesPackage -or -not $servicesPackage.rootUri.Replace('\', '/').Contains('/plugins/aves_services_none')) {
        throw '当前 package_config 未启用 libre 的 aves_services_none 实现'
    }
    $activePluginRoots = @($packageConfig.packages | Where-Object { $_.rootUri.StartsWith('../plugins/') } | ForEach-Object { Join-Path $projectRoot $_.rootUri.Substring(3) })
    & $taskFlutter analyze --no-pub lib test test_driver @activePluginRoots
    if ($LASTEXITCODE -ne 0) { throw '静态分析失败' }
    & $taskFlutter test --no-pub --concurrency=1
    if ($LASTEXITCODE -ne 0) { throw '测试失败' }
    & $taskFlutter build apk --no-pub --debug --flavor libre -t lib/main_libre.dart
    if ($LASTEXITCODE -ne 0) { throw 'APK 构建失败' }
    & (Join-Path $projectRoot 'android\gradlew.bat') -p (Join-Path $projectRoot 'android') :app:testLibreDebugUnitTest --no-daemon --max-workers=1 '-Ptarget=lib/main_libre.dart'
    if ($LASTEXITCODE -ne 0) { throw 'Android 单元测试失败' }
} finally {
    Pop-Location
}
