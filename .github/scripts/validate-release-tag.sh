#!/usr/bin/env bash
# One tag format for every release path: the updater derives versionCode from vX.Y.Z.
set -euo pipefail

tag="${1:-}"
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Release tag must match ^v\\d+\\.\\d+\\.\\d+ (e.g. v0.6.0); got '$tag'"
  exit 1
fi

echo "Release tag OK: $tag"
