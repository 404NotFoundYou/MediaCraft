#!/bin/bash
set -euo pipefail
if [ ! -d "scripts" ]; then
  cd ..
fi

./flutterw clean

cp flavors/pubspec_izzy.lock pubspec.lock
cp flavors/pubspec_izzy.yaml pubspec.yaml

./flutterw pub get --enforce-lockfile
bash scripts/verify_flavor.sh izzy
