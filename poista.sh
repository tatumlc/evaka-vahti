#!/data/data/com.termux/files/usr/bin/bash
# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT
set -euo pipefail
BASE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp)"
(crontab -l 2>/dev/null || true) | awk '$0=="# BEGIN evaka-vahti"{skip=1;next}$0=="# END evaka-vahti"{skip=0;next}!skip{print}' > "$TMP"
crontab "$TMP";rm -f "$TMP"
rm -f "$HOME/.termux/boot/evaka-vahti-cron"
echo 'eVaka-vahdin ajastukset poistettu. Halutessasi poista ~/evaka-vahti erikseen.'
