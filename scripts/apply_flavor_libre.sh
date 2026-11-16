#!/bin/bash
set -euo pipefail
if [ ! -d "scripts" ]; then
  cd ..
fi

./flutterw clean

cp flavors/pubspec_libre.lock pubspec.lock
cp flavors/pubspec_libre.yaml pubspec.yaml

./flutterw pub get --enforce-lockfile
bash scripts/verify_flavor.sh libre
