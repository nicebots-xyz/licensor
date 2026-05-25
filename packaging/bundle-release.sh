#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright: 2026 NiceBots.xyz
set -euo pipefail

# Bundle licensor release archives with native binary, JVM jar, Temurin 21 JRE, and launchers.
#
# Usage: packaging/bundle-release.sh <version> <staging-dir>
#   staging-dir must contain per-platform native binaries named:
#     licensor-x86_64-unknown-linux-gnu
#     licensor-aarch64-unknown-linux-gnu
#     ... (see NATIVE_ASSETS below)

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

fetch_jre() {
  local platform="$1"
  local dest="$2"
  local os="" arch=""
  case "$platform" in
    linux-x64)       os=linux; arch=x64 ;;
    linux-aarch64)   os=linux; arch=aarch64 ;;
    macos-x64)       os=mac; arch=x64 ;;
    macos-aarch64)   os=mac; arch=aarch64 ;;
    windows-x64)     os=windows; arch=x64 ;;
    windows-aarch64) os=windows; arch=aarch64 ;;
    *) echo "Unknown JRE platform: $platform" >&2; return 1 ;;
  esac

  local api="https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=${arch}&image_type=jre&os=${os}"
  local url
  url="$(curl -fsSL "$api" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['link'])")"

  mkdir -p "$dest"
  local archive
  archive="$(mktemp)"
  curl -fsSL "$url" -o "$archive"
  if [[ "$url" == *.zip ]]; then
    mkdir -p "$dest/jre"
    unzip -q "$archive" -d "$dest"
    rm -rf "$dest/jre"
    mv "$dest"/jdk-* "$dest/jre"
  else
    tar -xzf "$archive" -C "$dest"
    mv "$dest"/jdk-* "$dest/jre"
  fi
  rm -f "$archive"
}

bundle_unix() {
  local name="$1"
  local jre_platform="$2"
  local native_src="$STAGING/$3"
  local work="$DIST/_work-$name"
  rm -rf "$work"
  mkdir -p "$work"
  cp "$JAR" "$work/licensor.jar"
  cp "$ROOT/packaging/licensor" "$work/licensor"
  chmod +x "$work/licensor"
  if [[ -f "$native_src" ]]; then
    cp "$native_src" "$work/licensor-native"
    chmod +x "$work/licensor-native"
  fi
  fetch_jre "$jre_platform" "$work"
  tar -C "$work" -czf "$DIST/licensor-${VERSION}-${name}.tar.gz" .
  rm -rf "$work"
}

bundle_windows() {
  local name="$1"
  local jre_platform="$2"
  local native_src="$STAGING/$3"
  local work="$DIST/_work-$name"
  rm -rf "$work"
  mkdir -p "$work"
  cp "$JAR" "$work/licensor.jar"
  cp "$ROOT/packaging/licensor.cmd" "$work/licensor.cmd"
  if [[ -f "$native_src" ]]; then
    cp "$native_src" "$work/licensor.exe"
  fi
  fetch_jre "$jre_platform" "$work"
  (cd "$work" && zip -qr "$DIST/licensor-${VERSION}-${name}.zip" .)
  rm -rf "$work"
}

# Linux
bundle_unix "linux-x86_64" "linux-x64" "licensor-x86_64-unknown-linux-gnu"
bundle_unix "linux-aarch64" "linux-aarch64" "licensor-aarch64-unknown-linux-gnu"

# macOS
bundle_unix "macos-x86_64" "macos-x64" "licensor-x86_64-apple-darwin"
bundle_unix "macos-aarch64" "macos-aarch64" "licensor-aarch64-apple-darwin"

# Windows
bundle_windows "windows-x86_64" "windows-x64" "licensor-x86_64-pc-windows.exe"
bundle_windows "windows-aarch64" "windows-aarch64" "licensor-aarch64-pc-windows.exe"

(
  cd "$DIST"
  sha256sum licensor-"${VERSION}"-* > SHA256SUMS.txt
)

echo "Release bundles written to $DIST"
