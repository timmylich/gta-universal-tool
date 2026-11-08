# Runs Gradle with the toolchain and caches on drive D: (GT_TOOLS, default D:\Dev\utils: Android\SDK, Android\jdk21, gradle-home).
# Usage:  powershell -File scripts/gradle.ps1 testDebugUnitTest assembleRelease
#         powershell -File scripts/gradle.ps1 -Install    (release build, then adb install over the current app)
param([switch]$Install, [Parameter(ValueFromRemainingArguments)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'

$tools = if ($env:GT_TOOLS) { $env:GT_TOOLS } else { 'D:\Dev\utils' }
$tmp = "$tools\tmp"
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:JAVA_HOME = "$tools\Android\jdk21"
$env:ANDROID_HOME = "$tools\Android\SDK"
$env:ANDROID_USER_HOME = "$tools\android-home"
$env:GRADLE_USER_HOME = "$tools\gradle-home"
$env:TEMP = $tmp
$env:TMP = $tmp
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$tmp"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
"sdk.dir=$(($env:ANDROID_HOME -replace '\\', '/') -replace ':', '\:')" | Set-Content -NoNewline local.properties

if ($Install -and -not $Tasks) { $Tasks = @('assembleRelease') }
.\gradlew.bat @Tasks --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }

if ($Install) {
  $dir = 'app\build\outputs\apk\release'
  $apk = if (Test-Path "$dir\app-release.apk") { "$dir\app-release.apk" } else { throw 'No signed APK: keystore.properties is missing' }
  Write-Host "APK: $apk ($([math]::Round((Get-Item $apk).Length / 1MB, 1)) MB)"
  & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r $apk
  if ($LASTEXITCODE -ne 0) { throw "adb install failed ($LASTEXITCODE)" }
}
