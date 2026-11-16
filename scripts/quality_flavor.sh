#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: scripts/quality_flavor.sh <libre|baidu>" >&2
  exit 2
fi

flavor=$1
case "$flavor" in
  libre) target=lib/main_libre.dart; gradle_task=testLibreDebugUnitTest ;;
  baidu) target=lib/main_baidu.dart; gradle_task=testBaiduDebugUnitTest ;;
  *) echo "Quality checks are not configured for flavor: $flavor" >&2; exit 2 ;;
esac

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
bash "scripts/apply_flavor_${flavor}.sh"
bash scripts/verify_flavor.sh "$flavor"
./flutterw gen-l10n

active_roots_file=$(mktemp)
trap 'rm -f "$active_roots_file"' EXIT
python3 - <<'PY' > "$active_roots_file"
import json
from pathlib import Path

config = json.loads(Path('.dart_tool/package_config.json').read_text(encoding='utf-8'))
for package in config['packages']:
    root = package['rootUri']
    if root.startswith('../plugins/'):
        print(root.removeprefix('../'))
PY
mapfile -t active_plugin_roots < "$active_roots_file"
rm -f "$active_roots_file"
trap - EXIT
if [ "${#active_plugin_roots[@]}" -eq 0 ]; then
  echo "No active workspace plugins were found in package_config.json." >&2
  exit 1
fi

./flutterw analyze --no-pub lib test test_driver "${active_plugin_roots[@]}"
./flutterw test --no-pub --concurrency=1
for plugin_root in "${active_plugin_roots[@]}"; do
  if find "$plugin_root/test" -type f -name '*_test.dart' -print -quit 2>/dev/null | grep -q .; then
    (cd "$plugin_root" && "$project_root/flutterw" test --no-pub --concurrency=1)
  fi
done
./flutterw build apk --no-pub --debug --flavor "$flavor" -t "$target"
./android/gradlew -p android ":app:$gradle_task" --no-daemon --max-workers=1 "-Ptarget=$target"
