param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2100000000)][int]$VersionCode
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'signing.properties'))) {
    throw '缺少本地 signing.properties，不能构建正式签名发布包。'
}
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $sdkPath) { throw '请配置 ANDROID_HOME 或 ANDROID_SDK_ROOT。' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw '未找到 Android Build Tools。' }
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') ":app:assembleRelease" "-PappVersionName=$Version" "-PappVersionCode=$VersionCode" '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw '正式版构建失败。' }
    $apkSource = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $apkSource)) { throw '未找到正式签名 APK。' }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose $apkSource
    if ($LASTEXITCODE -ne 0) { throw 'APK 签名校验失败。' }
    $badging = (& (Join-Path $buildTools.FullName 'aapt.exe') dump badging $apkSource) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw '无法读取 APK 元数据。' }
    $package = [regex]::Match($badging, "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
    $sdk = [regex]::Match($badging, "(?m)^sdkVersion:'(\d+)'")
    if (-not $package.Success -or -not $sdk.Success) { throw 'APK 元数据不完整。' }
    if ($package.Groups[1].Value -ne 'com.mediacraft.app' -or [long]$package.Groups[2].Value -ne $VersionCode -or $package.Groups[3].Value -ne $Version) {
        throw 'APK 包名或版本与发布参数不一致。'
    }
    $releaseDirectory = Join-Path $projectRoot "artifacts/releases/v$Version"
    New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
    $apkName = "MediaCraft-$Version.apk"
    $apkOutput = Join-Path $releaseDirectory $apkName
    Copy-Item -LiteralPath $apkSource -Destination $apkOutput -Force
    $hash = (Get-FileHash -LiteralPath $apkOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = [ordered]@{
        schemaVersion = 1
        versionName = $Version
        versionCode = $VersionCode
        packageName = 'com.mediacraft.app'
        minSdk = [int]$sdk.Groups[1].Value
        apkName = $apkName
        sha256 = $hash
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $releaseDirectory 'update.json'), ($manifest | ConvertTo-Json) + "`n", $encoding)
    [System.IO.File]::WriteAllText((Join-Path $releaseDirectory "$apkName.sha256"), "$hash  $apkName`n", $encoding)
    Write-Output "发布附件已生成：$releaseDirectory"
    Write-Output "APK SHA-256：$hash"
} finally { Pop-Location }
