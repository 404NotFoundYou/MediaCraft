#!/bin/bash
set -euo pipefail
if [ ! -d "scripts" ]; then
  cd ..
fi

./flutterw clean

cp flavors/pubspec_play.lock pubspec.lock
cp flavors/pubspec_play.yaml pubspec.yaml

./flutterw pub get --enforce-lockfile
bash scripts/verify_flavor.sh play
