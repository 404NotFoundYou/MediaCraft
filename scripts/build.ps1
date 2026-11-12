$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskFlutter = Join-Path $projectRoot '.tooling\flutter\bin\flutter.bat'
if (-not (Test-Path -LiteralPath $taskFlutter)) {
    throw '请先运行 scripts/bootstrap.ps1 安装项目固定的 Flutter SDK'
}

Push-Location $projectRoot
try {
    & $taskFlutter pub get
    if ($LASTEXITCODE -ne 0) { throw '依赖解析失败' }
    # Platform implementations share package names across flavors. Analyze the
    # implementations selected by pub rather than mutually exclusive flavors.
    $packageConfig = Get-Content -LiteralPath '.dart_tool\package_config.json' -Raw | ConvertFrom-Json
    $activePluginRoots = @($packageConfig.packages | Where-Object { $_.rootUri.StartsWith('../plugins/') } | ForEach-Object { Join-Path $projectRoot $_.rootUri.Substring(3) })
    & $taskFlutter analyze --no-pub lib test test_driver @activePluginRoots
    if ($LASTEXITCODE -ne 0) { throw '静态分析失败' }
    & $taskFlutter test --concurrency=1
    if ($LASTEXITCODE -ne 0) { throw '测试失败' }
    & $taskFlutter build apk --debug --flavor libre -t lib/main_libre.dart
    if ($LASTEXITCODE -ne 0) { throw 'APK 构建失败' }
    & (Join-Path $projectRoot 'android\gradlew.bat') -p (Join-Path $projectRoot 'android') :app:testLibreDebugUnitTest --no-daemon --max-workers=1 '-Ptarget=lib/main_libre.dart'
    if ($LASTEXITCODE -ne 0) { throw 'Android 单元测试失败' }
} finally {
    Pop-Location
}
