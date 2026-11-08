#!/usr/bin/env sh
# Linux/macOS twin of test-core.ps1: compiles the Kotlin format code with kotlinc and runs the self-test.
# Usage: scripts/test-core.sh [game-folder]   (kotlinc and java must be on PATH)
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
out=${TMPDIR:-/tmp}/gut-core-test
mkdir -p "$out"
kotlinc "$root"/modules/gta-native/android/src/main/java/io/github/timmylich/gtatool/core/*.kt "$root/scripts/CoreTest.kt" \
  -include-runtime -d "$out/test.jar" -nowarn
java -Xmx2g -cp "$out/test.jar" CoreTestKt "${1:-none}" "$out"
