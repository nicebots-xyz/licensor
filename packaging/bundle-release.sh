#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright: 2026 NiceBots.xyz
set -euo pipefail

# Assemble native licensor release archives from pre-staged platform inputs produced in CI.
#
# Usage: packaging/bundle-release.sh <version> <staging-dir>
#   staging-dir contains one subdirectory per platform (artifact names from CI), e.g.:
#     linux-x86_64/licensor-x86_64-unknown-linux-gnu
#     linux-aarch64/...

VERSION="${1:?version required}"
STAGING="${2:?staging directory required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/dist"
WORK_ROOT=""

if [[ ! "$VERSION" =~ ^v[0-9]+[.][0-9]+[.][0-9]+([-+][A-Za-z0-9._-]+)?$ ]]; then
  echo "Release version must look like v1.2.3: $VERSION" >&2
  exit 1
fi

cleanup() {
  if [[ -n "$WORK_ROOT" && -d "$WORK_ROOT" ]]; then
    rm -rf "$WORK_ROOT"
  fi
}
trap cleanup EXIT

rm -rf "$DIST"
mkdir -p "$DIST"
WORK_ROOT="$(mktemp -d "$DIST/.work.XXXXXX")"

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
}

bundle_unix() {
  local archive_name="$1"
  local platform_dir="$2"
  local native_file="$3"
  require_platform_inputs "$platform_dir" "$native_file"

  local work="$WORK_ROOT/$archive_name"
  mkdir -p "$work"
  cp "$platform_dir/$native_file" "$work/licensor"
  chmod +x "$work/licensor"
  tar -C "$work" -czf "$DIST/licensor-${VERSION}-${archive_name}.tar.gz" .
}

bundle_windows() {
  local archive_name="$1"
  local platform_dir="$2"
  local native_file="$3"
  require_platform_inputs "$platform_dir" "$native_file"

  local work="$WORK_ROOT/$archive_name"
  mkdir -p "$work"
  cp "$platform_dir/$native_file" "$work/licensor.exe"
  (cd "$work" && zip -qr "$DIST/licensor-${VERSION}-${archive_name}.zip" .)
}

bundle_unix "linux-x86_64" "$STAGING/linux-x86_64" "licensor-x86_64-unknown-linux-gnu"
bundle_unix "linux-aarch64" "$STAGING/linux-aarch64" "licensor-aarch64-unknown-linux-gnu"
bundle_unix "macos-x86_64" "$STAGING/macos-x86_64" "licensor-x86_64-apple-darwin"
bundle_unix "macos-aarch64" "$STAGING/macos-aarch64" "licensor-aarch64-apple-darwin"
bundle_windows "windows-x86_64" "$STAGING/windows-x86_64" "licensor-x86_64-pc-windows.exe"

(
  cd "$DIST"
  sha256sum licensor-"${VERSION}"-* > SHA256SUMS.txt
)

echo "Release bundles written to $DIST"
