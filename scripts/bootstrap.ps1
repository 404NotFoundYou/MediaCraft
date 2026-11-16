param([string]$FlutterVersion = '3.47.8')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$taskFlutterRoot = Join-Path $projectRoot '.tooling\flutter'
if (-not (Test-Path -LiteralPath $taskFlutterRoot)) {
    & git clone --depth 1 --branch $FlutterVersion https://github.com/flutter/flutter.git $taskFlutterRoot
    if ($LASTEXITCODE -ne 0) { throw 'Flutter SDK 下载失败' }
}
$actualVersion = (& git -C $taskFlutterRoot describe --tags --exact-match HEAD 2>$null)
if ($LASTEXITCODE -ne 0 -or $actualVersion -ne $FlutterVersion) {
    $displayVersion = if ($actualVersion) { $actualVersion } else { '未标记的提交' }
    throw "项目 Flutter SDK 版本不匹配：期望 $FlutterVersion，实际 $displayVersion。请移走 .tooling/flutter 后重新执行 bootstrap。"
}
$sdkChanges = (& git -C $taskFlutterRoot status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0 -or $sdkChanges) {
    throw '项目 Flutter SDK 工作区不干净，请重新创建 .tooling/flutter'
}
& (Join-Path $taskFlutterRoot 'bin\flutter.bat') --version
if ($LASTEXITCODE -ne 0) { throw 'Flutter SDK 初始化失败' }
