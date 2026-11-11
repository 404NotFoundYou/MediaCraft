param([switch]$Connected)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw '请配置指向 JDK 17 或更新兼容 JDK 的 JAVA_HOME。'
}
if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
Push-Location $projectRoot
try {
    $tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug', '--console=plain')
    if ($Connected) { $tasks += 'connectedDebugAndroidTest' }
    & (Join-Path $projectRoot 'gradlew.bat') @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle 失败，退出码 $LASTEXITCODE。" }
} finally { Pop-Location }
