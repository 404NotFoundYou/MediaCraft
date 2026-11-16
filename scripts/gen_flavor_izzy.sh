#!/bin/bash
set -euo pipefail
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec bash "$project_root/scripts/generate_flavor_snapshot.sh" izzy aves_services_google aves_report_console
