#!/data/data/com.termux/files/usr/bin/bash
# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT
set -u
BASE="$(cd "$(dirname "$0")" && pwd)"
echo '=== eVaka-vahti – tarkistus ==='
termux-wake-lock && echo 'Wake lock pyydetty käyttöön' || echo 'VAROITUS: wake lock ei onnistunut'
pgrep -x crond >/dev/null || crond
pgrep -x crond >/dev/null && echo 'Cron: käynnissä' || echo 'VIRHE: Cron ei käynnissä'
crontab -l | grep -F "$BASE/aja.sh" || echo 'VAROITUS: Ajastuksia ei löytynyt'
"$BASE/aja.sh" hoito
HOITO=$?
"$BASE/aja.sh" lasna
LASNA=$?
"$BASE/aja.sh" viestit
VIESTIT=$?
echo "Paluuarvot: hoito=$HOITO, lasna=$LASNA, viestit=$VIESTIT"
echo 'Viimeiset lokirivit:'
tail -n 4 "$BASE/logs/hoito.log" "$BASE/logs/lasna.log" "$BASE/logs/viestit.log" 2>/dev/null || true
[[ $HOITO -eq 0 && $LASNA -eq 0 && $VIESTIT -eq 0 ]]
