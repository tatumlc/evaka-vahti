#!/data/data/com.termux/files/usr/bin/bash
# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT
set -euo pipefail
BASE="$(cd "$(dirname "$0")" && pwd)"
TASK="${1:-}"
case "$TASK" in hoito|lasna|viestit) ;; *) echo 'Käyttö: ./aja.sh hoito|lasna|viestit' >&2; exit 2;; esac
mkdir -p "$BASE/logs"
chmod 700 "$BASE/logs"
export PYTHONPATH="$BASE/src"
LOG="$BASE/logs/$TASK.log"
printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$TASK" >> "$LOG"
if python -m evaka_vahti "$TASK" >> "$LOG" 2>&1; then
  URL="$(python - "$BASE/private/config.json" "$TASK" <<'PYTHON'
import sys,json
from pathlib import Path
p=Path(sys.argv[1]);d=json.loads(p.read_text())
print(d.get({'hoito':'healthchecks_hoito','lasna':'healthchecks_lasna','viestit':'healthchecks_viestit'}[sys.argv[2]],''))
PYTHON
)"
  if [[ -n "$URL" ]]; then
    curl --fail --silent --show-error --connect-timeout 10 --max-time 20 "$URL" >/dev/null || echo 'VAROITUS: Healthchecks-kuittaus epäonnistui' >> "$LOG"
  fi
  echo "OK: $TASK"
else
  echo "VIRHE: $TASK (katso $LOG)" >&2
  exit 1
fi
