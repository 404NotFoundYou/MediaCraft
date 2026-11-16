#!/usr/bin/env bash
set -euo pipefail

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_root=$(mktemp -d)
cleanup() {
  find "$test_root" -depth -delete
}
trap cleanup EXIT

seed_generator() {
  local dir=$1
  mkdir -p "$dir/scripts" "$dir/flavors" "$dir/mockbin"
  cp "$project_root/scripts/generate_flavor_snapshot.sh" "$dir/scripts/"
  cat > "$dir/pubspec.yaml" <<'EOF'
workspace:
  - plugins/aves_services_none
  - plugins/aves_report_console
dependencies:
  aves_services_platform:
    path: plugins/aves_services_none
  aves_report_platform:
    path: plugins/aves_report_console
EOF
  printf 'original-lock\n' > "$dir/pubspec.lock"
  cp "$dir/pubspec.yaml" "$dir/flavors/pubspec_libre.yaml"
  cp "$dir/pubspec.lock" "$dir/flavors/pubspec_libre.lock"
  cp "$dir/pubspec.yaml" "$dir/original.yaml"
  cp "$dir/pubspec.lock" "$dir/original.lock"
  cp "$dir/flavors/pubspec_libre.yaml" "$dir/old-snapshot.yaml"
  cp "$dir/flavors/pubspec_libre.lock" "$dir/old-snapshot.lock"
  cat > "$dir/flutterw" <<'EOF'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >> mock.log
if [ "$*" = clean ]; then exit 0; fi
if [ "$*" = 'pub get' ]; then
  printf 'generated-lock\n' > pubspec.lock
  exit "${FAIL_CODE:-0}"
fi
if [ "$*" = 'pub get --enforce-lockfile' ]; then
  exit "${RESTORE_CODE:-0}"
fi
exit 0
EOF
  chmod +x "$dir/flutterw"
}

parse_failure="$test_root/parse-failure"
seed_generator "$parse_failure"
set +e
(cd "$parse_failure" && FAIL_CODE=37 bash scripts/generate_flavor_snapshot.sh libre aves_services_none aves_report_console)
status=$?
set -e
[ "$status" -eq 37 ]
cmp "$parse_failure/pubspec.yaml" "$parse_failure/original.yaml"
cmp "$parse_failure/pubspec.lock" "$parse_failure/original.lock"
cmp "$parse_failure/flavors/pubspec_libre.yaml" "$parse_failure/old-snapshot.yaml"
cmp "$parse_failure/flavors/pubspec_libre.lock" "$parse_failure/old-snapshot.lock"
grep -qx 'pub get --enforce-lockfile' "$parse_failure/mock.log"
echo 'PASS parse failure rollback (exit 37)'

publish_failure="$test_root/publish-failure"
seed_generator "$publish_failure"
cat > "$publish_failure/mockbin/mv" <<'EOF'
#!/usr/bin/env bash
count=0
[ -f .mv-count ] && count=$(cat .mv-count)
count=$((count + 1))
printf '%s' "$count" > .mv-count
if [ "$count" -eq 2 ]; then exit 41; fi
exec /usr/bin/mv "$@"
EOF
chmod +x "$publish_failure/mockbin/mv"
set +e
(cd "$publish_failure" && PATH="$publish_failure/mockbin:$PATH" bash scripts/generate_flavor_snapshot.sh libre aves_services_none aves_report_console)
status=$?
set -e
[ "$status" -eq 41 ]
cmp "$publish_failure/pubspec.yaml" "$publish_failure/original.yaml"
cmp "$publish_failure/pubspec.lock" "$publish_failure/original.lock"
cmp "$publish_failure/flavors/pubspec_libre.yaml" "$publish_failure/old-snapshot.yaml"
cmp "$publish_failure/flavors/pubspec_libre.lock" "$publish_failure/old-snapshot.lock"
echo 'PASS half-publish rollback (exit 41)'

batch_failure="$test_root/batch-failure"
mkdir -p "$batch_failure/scripts"
cp "$project_root/scripts/gen_flavors.sh" "$batch_failure/scripts/"
printf 'input-yaml\n' > "$batch_failure/pubspec.yaml"
printf 'input-lock\n' > "$batch_failure/pubspec.lock"
cp "$batch_failure/pubspec.yaml" "$batch_failure/original.yaml"
cp "$batch_failure/pubspec.lock" "$batch_failure/original.lock"
for flavor in izzy libre play baidu; do
  code=0
  [ "$flavor" = izzy ] && code=23
  printf '#!/usr/bin/env bash\nexit %s\n' "$code" > "$batch_failure/scripts/gen_flavor_${flavor}.sh"
done
cat > "$batch_failure/flutterw" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> mock.log
exit 55
EOF
chmod +x "$batch_failure/flutterw"
set +e
(cd "$batch_failure" && bash scripts/gen_flavors.sh)
status=$?
set -e
[ "$status" -eq 23 ]
cmp "$batch_failure/pubspec.yaml" "$batch_failure/original.yaml"
cmp "$batch_failure/pubspec.lock" "$batch_failure/original.lock"
grep -qx 'pub get --enforce-lockfile' "$batch_failure/mock.log"
echo 'PASS batch rollback preserves original exit 23 while restore returns 55'

platform_check="$test_root/platform-check"
mkdir -p "$platform_check/scripts" "$platform_check/flavors" "$platform_check/.dart_tool" "$platform_check/mockbin"
cp "$project_root/scripts/verify_flavor.sh" "$platform_check/scripts/"
printf 'same-yaml\n' > "$platform_check/pubspec.yaml"
cp "$platform_check/pubspec.yaml" "$platform_check/flavors/pubspec_baidu.yaml"
printf 'same-lock\n' > "$platform_check/pubspec.lock"
cp "$platform_check/pubspec.lock" "$platform_check/flavors/pubspec_baidu.lock"
cat > "$platform_check/mockbin/python3" <<'EOF'
#!/usr/bin/env bash
exec python "$@"
EOF
chmod +x "$platform_check/mockbin/python3"
cat > "$platform_check/.dart_tool/package_config.json" <<'EOF'
{"packages":[{"name":"aves_services_platform","rootUri":"../plugins/aves_services_none"}]}
EOF
set +e
(cd "$platform_check" && PATH="$platform_check/mockbin:$PATH" bash scripts/verify_flavor.sh baidu >/dev/null 2>&1)
status=$?
set -e
[ "$status" -ne 0 ]
sed -i 's/aves_services_none/aves_services_baidu/' "$platform_check/.dart_tool/package_config.json"
(cd "$platform_check" && PATH="$platform_check/mockbin:$PATH" bash scripts/verify_flavor.sh baidu)
echo 'PASS stale active services platform rejected'

sdk_dirty="$test_root/sdk-dirty"
mkdir -p "$sdk_dirty/scripts" "$sdk_dirty/.tooling/flutter/bin"
cp "$project_root/scripts/verify_flutter_sdk.sh" "$sdk_dirty/scripts/"
printf '#!/usr/bin/env sh\n' > "$sdk_dirty/.tooling/flutter/bin/flutter"
chmod +x "$sdk_dirty/.tooling/flutter/bin/flutter"
(cd "$sdk_dirty/.tooling/flutter" && git init -q && git config user.email test@example.invalid && git config user.name test && git add bin/flutter && git commit -qm init && git tag 3.47.8)
(cd "$sdk_dirty" && bash scripts/verify_flutter_sdk.sh)
printf '# dirty\n' >> "$sdk_dirty/.tooling/flutter/bin/flutter"
set +e
(cd "$sdk_dirty" && bash scripts/verify_flutter_sdk.sh >/dev/null 2>&1)
status=$?
set -e
[ "$status" -ne 0 ]
echo 'PASS dirty exact-tag SDK rejected'
git -C "$sdk_dirty/.tooling/flutter" checkout -q -- bin/flutter
printf 'untracked\n' > "$sdk_dirty/.tooling/flutter/local-file"
set +e
(cd "$sdk_dirty" && bash scripts/verify_flutter_sdk.sh >/dev/null 2>&1)
status=$?
set -e
[ "$status" -ne 0 ]
echo 'PASS untracked exact-tag SDK rejected'
