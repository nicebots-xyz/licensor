#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright: 2026 NiceBots.xyz
set -euo pipefail

# Assemble licensor release archives from pre-staged platform inputs produced in CI.
#
# Usage: packaging/bundle-release.sh <version> <staging-dir>
#   staging-dir contains one subdirectory per platform (artifact names from CI), e.g.:
#     linux-x86_64/licensor-x86_64-unknown-linux-gnu
#     linux-x86_64/jre/
#     linux-aarch64/...
#
# Temurin JREs are installed in the native-image workflow via actions/setup-java
# (java-package: jre, java-version-file: .java-version). This script performs no
# network downloads.

VERSION="${1:?version required}"
STAGING="${2:?staging directory required}"
JAR_PATH="${3:-target/scala-*/licensor-assembly-*.jar}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/dist"
mkdir -p "$DIST"

JAR="$(ls -1 $ROOT/$JAR_PATH 2>/dev/null | head -1)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "Assembly jar not found at $ROOT/$JAR_PATH" >&2
  exit 1
fi

require_platform_inputs() {
  local platform_dir="$1"
  local native_file="$2"
  if [[ ! -d "$platform_dir" ]]; then
    echo "Missing platform staging directory: $platform_dir" >&2
    exit 1
  fi
  if [[ ! -f "$platform_dir/$native_file" ]]; then
    echo "Missing native binary: $platform_dir/$native_file" >&2
    exit 1
  fi
  if [[ ! -d "$platform_dir/jre" ]]; then
    echo "Missing bundled JRE: $platform_dir/jre" >&2
    exit 1
  fi
}

bundle_unix() {
  local archive_name="$1"
  local platform_dir="$2"
  local native_file="$3"
  require_platform_inputs "$platform_dir" "$native_file"

  local work="$DIST/_work-$archive_name"
  rm -rf "$work"
  mkdir -p "$work"
  cp "$JAR" "$work/licensor.jar"
  cp "$ROOT/packaging/licensor" "$work/licensor"
  chmod +x "$work/licensor"
  cp "$platform_dir/$native_file" "$work/licensor-native"
  chmod +x "$work/licensor-native"
  cp -a "$platform_dir/jre" "$work/jre"
  tar -C "$work" -czf "$DIST/licensor-${VERSION}-${archive_name}.tar.gz" .
  rm -rf "$work"
}

bundle_windows() {
  local archive_name="$1"
  local platform_dir="$2"
  local native_file="$3"
  require_platform_inputs "$platform_dir" "$native_file"

  local work="$DIST/_work-$archive_name"
  rm -rf "$work"
  mkdir -p "$work"
  cp "$JAR" "$work/licensor.jar"
  cp "$ROOT/packaging/licensor.cmd" "$work/licensor.cmd"
  cp "$platform_dir/$native_file" "$work/licensor.exe"
  cp -a "$platform_dir/jre" "$work/jre"
  (cd "$work" && zip -qr "$DIST/licensor-${VERSION}-${archive_name}.zip" .)
  rm -rf "$work"
}

bundle_unix "linux-x86_64" "$STAGING/linux-x86_64" "licensor-x86_64-unknown-linux-gnu"
bundle_unix "linux-aarch64" "$STAGING/linux-aarch64" "licensor-aarch64-unknown-linux-gnu"
bundle_unix "macos-x86_64" "$STAGING/macos-x86_64" "licensor-x86_64-apple-darwin"
bundle_unix "macos-aarch64" "$STAGING/macos-aarch64" "licensor-aarch64-apple-darwin"
bundle_windows "windows-x86_64" "$STAGING/windows-x86_64" "licensor-x86_64-pc-windows.exe"
bundle_windows "windows-aarch64" "$STAGING/windows-aarch64" "licensor-aarch64-pc-windows.exe"

(
  cd "$DIST"
  sha256sum licensor-"${VERSION}"-* > SHA256SUMS.txt
)

echo "Release bundles written to $DIST"
