#!/bin/bash
set -euo pipefail
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec bash "$project_root/scripts/generate_flavor_snapshot.sh" libre aves_services_none aves_report_console
