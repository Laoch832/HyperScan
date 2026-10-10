#!/usr/bin/env bash
set -euo pipefail
mkdir -p /workspace/tools

fetch_checked() {
  local artifact_url="$1" artifact_path="$2" algorithm="$3" expected_digest="$4"
  if ! test -f "$artifact_path" || ! printf '%s  %s\n' "$expected_digest" "$artifact_path" | "${algorithm}sum" --check --status; then
    curl --fail --location --retry 2 --max-time 60 "$artifact_url" --output "$artifact_path"
  fi
  printf '%s  %s\n' "$expected_digest" "$artifact_path" | "${algorithm}sum" --check
}

# Checksums published by the respective upstream release pages/SDK repository XML.
fetch_checked https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar /workspace/tools/apktool_3.0.3.jar sha256 dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423
fetch_checked https://dl.google.com/android/repository/build-tools_r36_linux.zip /workspace/tools/android-build-tools.zip sha1 b0b6376977657e8ad9b969bacf4093601da2c6fb
fetch_checked https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip /workspace/tools/android-platform-tools.zip sha1 477254aa5f903c15cf51001717bdf347fb6b53e0

if ! test -x /workspace/tools/android-build-tools/android-16/apksigner; then
  mkdir -p /workspace/tools/android-build-tools
  unzip -q /workspace/tools/android-build-tools.zip -d /workspace/tools/android-build-tools
fi
if ! test -x /workspace/tools/platform-tools/adb; then
  unzip -q /workspace/tools/android-platform-tools.zip -d /workspace/tools
fi
java -jar /workspace/tools/apktool_3.0.3.jar --version
/workspace/tools/android-build-tools/android-16/apksigner version
/workspace/tools/platform-tools/adb version

# API 36 compile stubs for the internal diagnostic provider; upstream SDK XML SHA-1.
fetch_checked https://dl.google.com/android/repository/platform-36_r02.zip /workspace/tools/platform-36_r02.zip sha1 2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576
if ! test -f /workspace/tools/android-platform-36/android-36/android.jar; then
  mkdir -p /workspace/tools/android-platform-36
  unzip -q /workspace/tools/platform-36_r02.zip -d /workspace/tools/android-platform-36
fi
