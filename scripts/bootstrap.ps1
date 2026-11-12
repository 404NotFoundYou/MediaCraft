param([string]$FlutterVersion = '3.47.8')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskFlutterRoot = Join-Path $projectRoot '.tooling\flutter'
if (-not (Test-Path -LiteralPath $taskFlutterRoot)) {
    & git clone --depth 1 --branch $FlutterVersion https://github.com/flutter/flutter.git $taskFlutterRoot
    if ($LASTEXITCODE -ne 0) { throw 'Flutter SDK 下载失败' }
}
& (Join-Path $taskFlutterRoot 'bin\flutter.bat') --version
if ($LASTEXITCODE -ne 0) { throw 'Flutter SDK 初始化失败' }
