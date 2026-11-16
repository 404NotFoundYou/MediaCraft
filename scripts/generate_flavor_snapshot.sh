#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: scripts/generate_flavor_snapshot.sh <flavor> <services plugin> <report plugin>" >&2
  exit 2
fi

flavor=$1
services_plugin=$2
report_plugin=$3
case "$flavor:$services_plugin:$report_plugin" in
  play:aves_services_google:aves_report_crashlytics|izzy:aves_services_google:aves_report_console|libre:aves_services_none:aves_report_console|baidu:aves_services_baidu:aves_report_console) ;;
  *) echo "Unsupported flavor dependency combination." >&2; exit 2 ;;
esac

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
mkdir -p flavors

original_yaml=$(mktemp)
original_lock=$(mktemp)
snapshot_yaml=$(mktemp "flavors/pubspec_${flavor}.yaml.XXXXXX")
snapshot_lock=$(mktemp "flavors/pubspec_${flavor}.lock.XXXXXX")
previous_yaml=$(mktemp)
previous_lock=$(mktemp)
cp pubspec.yaml "$original_yaml"
cp pubspec.lock "$original_lock"
cp "flavors/pubspec_${flavor}.yaml" "$previous_yaml"
cp "flavors/pubspec_${flavor}.lock" "$previous_lock"
publishing=0

cleanup() {
  status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    cp "$original_yaml" pubspec.yaml
    cp "$original_lock" pubspec.lock
    if [ "$publishing" -eq 1 ]; then
      cp "$previous_yaml" "flavors/pubspec_${flavor}.yaml"
      cp "$previous_lock" "flavors/pubspec_${flavor}.lock"
    fi
    ./flutterw pub get --enforce-lockfile || echo "Failed to restore package_config.json after flavor generation failure." >&2
  fi
  rm -f "$original_yaml" "$original_lock" "$snapshot_yaml" "$snapshot_lock" "$previous_yaml" "$previous_lock"
  exit "$status"
}
trap cleanup EXIT

./flutterw clean
sed -i "s|plugins/aves_services_.*|plugins/$services_plugin|g" pubspec.yaml
sed -i "s|plugins/aves_report_.*|plugins/$report_plugin|g" pubspec.yaml
./flutterw pub get
cp pubspec.yaml "$snapshot_yaml"
cp pubspec.lock "$snapshot_lock"
publishing=1
mv "$snapshot_yaml" "flavors/pubspec_${flavor}.yaml"
mv "$snapshot_lock" "flavors/pubspec_${flavor}.lock"
publishing=0
