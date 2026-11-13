$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskFlutter = Join-Path $projectRoot '.tooling\flutter\bin\flutter.bat'
if (-not (Test-Path -LiteralPath $taskFlutter)) {
    throw '请先运行 scripts/bootstrap.ps1 安装项目固定的 Flutter SDK'
}

Push-Location $projectRoot
try {
    & $taskFlutter clean
    if ($LASTEXITCODE -ne 0) { throw '切换百度 flavor 前清理失败' }
    Copy-Item -LiteralPath 'flavors/pubspec_baidu.yaml' -Destination 'pubspec.yaml'
    Copy-Item -LiteralPath 'flavors/pubspec_baidu.lock' -Destination 'pubspec.lock'
    & $taskFlutter pub get --enforce-lockfile
    if ($LASTEXITCODE -ne 0) { throw '百度 flavor 依赖解析失败' }
} finally {
    Pop-Location
}
