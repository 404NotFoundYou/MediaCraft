#!/bin/bash
set -euo pipefail
if [ ! -d "scripts" ]; then
  cd ..
fi

original_yaml=$(mktemp)
original_lock=$(mktemp)
cp pubspec.yaml "$original_yaml"
cp pubspec.lock "$original_lock"
restore_original() {
  status=$?
  trap - EXIT
  cp "$original_yaml" pubspec.yaml
  cp "$original_lock" pubspec.lock
  restore_status=0
  ./flutterw pub get --enforce-lockfile || restore_status=$?
  rm -f "$original_yaml" "$original_lock"
  if [ "$status" -ne 0 ]; then
    exit "$status"
  fi
  exit "$restore_status"
}
trap restore_original EXIT

bash scripts/gen_flavor_izzy.sh
bash scripts/gen_flavor_libre.sh
bash scripts/gen_flavor_play.sh
bash scripts/gen_flavor_baidu.sh
