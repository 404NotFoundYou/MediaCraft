#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: scripts/verify_flavor.sh <play|izzy|libre|baidu>" >&2
  exit 2
fi

flavor=$1
case "$flavor" in
  play|izzy|libre|baidu) ;;
  *) echo "Unknown flavor: $flavor" >&2; exit 2 ;;
esac

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"

cmp -s pubspec.yaml "flavors/pubspec_${flavor}.yaml" || {
  echo "pubspec.yaml does not match the committed $flavor flavor snapshot." >&2
  exit 1
}
cmp -s pubspec.lock "flavors/pubspec_${flavor}.lock" || {
  echo "pubspec.lock does not match the committed $flavor flavor snapshot." >&2
  exit 1
}

case "$flavor" in
  play|izzy) expected_services=aves_services_google ;;
  libre) expected_services=aves_services_none ;;
  baidu) expected_services=aves_services_baidu ;;
esac
python3 - "$expected_services" <<'PY'
import json
import sys
from pathlib import Path

expected = sys.argv[1]
config_path = Path('.dart_tool/package_config.json')
if not config_path.is_file():
    raise SystemExit('Missing .dart_tool/package_config.json; run pub get for the selected flavor.')
config = json.loads(config_path.read_text(encoding='utf-8'))
package = next((item for item in config['packages'] if item['name'] == 'aves_services_platform'), None)
if package is None or not package['rootUri'].replace('\\', '/').rstrip('/').endswith(f'/plugins/{expected}'):
    actual = None if package is None else package['rootUri']
    raise SystemExit(f'Expected aves_services_platform from {expected}, found {actual}.')
PY
