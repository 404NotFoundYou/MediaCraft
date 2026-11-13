#!/bin/bash
if [ ! -d "scripts" ]; then
  cd ..
fi

./flutterw clean

sed -i 's|plugins/aves_services_.*|plugins/aves_services_baidu|g' "pubspec.yaml"
sed -i 's|plugins/aves_report_.*|plugins/aves_report_console|g' "pubspec.yaml"

./flutterw pub get

mkdir -p flavors
cp pubspec.lock flavors/pubspec_baidu.lock
cp pubspec.yaml flavors/pubspec_baidu.yaml
