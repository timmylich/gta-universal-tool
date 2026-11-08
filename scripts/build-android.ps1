# Build a release APK and optionally install it on a connected device.
#
# Everything (toolchain, Gradle/npm caches, temp files) stays on drive D:.
#   GT_TOOLS   root with NodeJS, Android\SDK, Android\jdk21, gradle-home, platform-tools (default D:\Dev\utils)
#
# Usage:  powershell -File scripts/build-android.ps1 [-Install] [-Clean]
param([switch]$Install, [switch]$Clean)
$ErrorActionPreference = 'Stop'

$tools = if ($env:GT_TOOLS) { $env:GT_TOOLS } else { 'D:\Dev\utils' }
$tmp = "$tools\tmp"
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:JAVA_HOME = "$tools\Android\jdk21"
$env:ANDROID_HOME = "$tools\Android\SDK"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = "$tools\android-home"
$env:GRADLE_USER_HOME = "$tools\gradle-home"
$env:npm_config_cache = "$tools\npm-cache"
$env:TEMP = $tmp
$env:TMP = $tmp
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$tmp"
$env:NODE_ENV = 'production'
$env:Path = "$tools\NodeJS;$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Clean -or -not (Test-Path android)) {
  npx expo prebuild --platform android $(if ($Clean) { '--clean' }) --no-install
}
"sdk.dir=$($env:ANDROID_HOME -replace '\\', '/')" | Set-Content android\local.properties

Push-Location android
.\gradlew.bat assembleRelease --no-daemon "-Dorg.gradle.jvmargs=-Xmx4g -Djava.io.tmpdir=$tmp" -PreactNativeArchitectures=arm64-v8a
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Gradle failed ($LASTEXITCODE)" }
Pop-Location

$apk = 'android\app\build\outputs\apk\release\app-release.apk'
Write-Host "APK: $apk ($([math]::Round((Get-Item $apk).Length / 1MB, 1)) MB)"
if ($Install) { adb install -r $apk }
