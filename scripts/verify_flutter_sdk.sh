#!/usr/bin/env bash
set -euo pipefail

expected_version="3.47.8"
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
flutter_root="$project_root/.tooling/flutter"

if [ ! -x "$flutter_root/bin/flutter" ]; then
  echo "Flutter SDK is missing. Run scripts/bootstrap.ps1 or ./flutterw." >&2
  exit 1
fi

actual_version=$(git -C "$flutter_root" describe --tags --exact-match HEAD 2>/dev/null || true)
if [ "$actual_version" != "$expected_version" ]; then
  echo "Expected Flutter $expected_version, but .tooling/flutter is at '${actual_version:-an untagged revision}'." >&2
  echo "Remove or relocate .tooling/flutter, then bootstrap the pinned SDK again." >&2
  exit 1
fi
if [ -n "$(git -C "$flutter_root" status --porcelain --untracked-files=normal)" ]; then
  echo "The pinned Flutter SDK checkout is dirty. Recreate .tooling/flutter before building." >&2
  exit 1
fi
