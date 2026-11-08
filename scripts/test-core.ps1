# Compiles the format code (modules/gta-native/.../core) with kotlinc and runs a self-test against a
# folder with real game files (default D:\Dev\gta-backup\SFRP64). Nothing in that folder is modified:
# writes go to copies in a temp folder.
param([string]$Game = 'D:\Dev\gta-backup\SFRP64')
$ErrorActionPreference = 'Stop'
$tools = if ($env:GT_TOOLS) { $env:GT_TOOLS } else { 'D:\Dev\utils' }
$env:JAVA_HOME = "$tools\Android\jdk21"
$kotlinc = "$tools\Android\plugins\Kotlin\kotlinc\bin\kotlinc.bat"
$root = Split-Path -Parent $PSScriptRoot
$core = "$root\modules\gta-native\android\src\main\java\io\github\timmylich\gtatool\core"
$out = Join-Path $env:TEMP 'gut-core-test'
New-Item -ItemType Directory -Force $out | Out-Null
& $kotlinc (Get-ChildItem "$core\*.kt").FullName "$root\scripts\CoreTest.kt" -include-runtime -d "$out\test.jar" 2>&1 | Where-Object { $_ -notmatch 'warning' }
& "$env:JAVA_HOME\bin\java.exe" -Xmx2g -cp "$out\test.jar" CoreTestKt $Game $out
if ($LASTEXITCODE -ne 0) { throw "core test failed" }
