#!/data/data/com.termux/files/usr/bin/bash
# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT
set -euo pipefail
BASE="$(cd "$(dirname "$0")" && pwd)"
if [[ "$BASE" != "$HOME/evaka-vahti" ]]; then
  echo 'Asenna paketti Termuxin kotihakemistoon nimellä ~/evaka-vahti.' >&2
  echo 'Ohje: cp -r ~/storage/downloads/evaka-vahti ~/evaka-vahti' >&2
  exit 1
fi
pkg install -y python python-pip cronie termux-api curl
python -m pip install requests
mkdir -p "$BASE/private" "$BASE/logs" "$HOME/.termux/boot"
chmod 700 "$BASE/private" "$BASE/logs"
export PYTHONPATH="$BASE/src"
python -m evaka_vahti.maarita
# Älä ylikirjoita käyttäjän muita cron-ajastuksia.
START='# BEGIN evaka-vahti'; END='# END evaka-vahti'
(crontab -l 2>/dev/null || true) | awk -v s="$START" -v e="$END" '$0==s{skip=1;next}$0==e{skip=0;next}!skip{print}' > "$BASE/private/cron-muut.tmp"
{
  cat "$BASE/private/cron-muut.tmp"
  echo "$START"
  echo "*/30 * * * * $BASE/aja.sh hoito"
  echo "5,35 * * * * $BASE/aja.sh lasna"
  echo "$END"
} | crontab -
rm -f "$BASE/private/cron-muut.tmp"
BOOT="$HOME/.termux/boot/evaka-vahti-cron"
cat > "$BOOT" <<'BOOTEND'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
pgrep -x crond >/dev/null || crond
BOOTEND
chmod 700 "$BOOT"
termux-wake-lock || true
pgrep -x crond >/dev/null || crond
printf '\nAsennus valmis. Aja seuraavaksi: ~/evaka-vahti/tarkista.sh\n'
