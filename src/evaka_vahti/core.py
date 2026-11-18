# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""Yhteinen kirjautuminen, asetukset ja paikalliset ilmoitukset."""
import json
import os
import subprocess
from pathlib import Path
from datetime import date, timedelta
from urllib.parse import urlparse

import requests

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / "private"
CONFIG = PRIVATE / "config.json"

def settings():
    if not CONFIG.is_file():
        raise RuntimeError("Asennusta ei ole viimeistelty. Suorita: ./asenna.sh")
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    if not data.get("username") or not data.get("password"):
        raise RuntimeError("Tunnukset puuttuvat. Suorita ./asenna.sh")
    api = data.get("api_url", "").rstrip("/")
    parsed = urlparse(api)
    if parsed.scheme != "https" or not parsed.netloc or not api.endswith("/api"):
        raise RuntimeError("Virheellinen API-osoite asetuksissa (vaaditaan HTTPS ja /api).")
    return data

def get_calendar(start, end):
    cfg = settings()
    with requests.Session() as sess:
        sess.headers.update({"x-evaka-csrf": "1"})
        login = sess.post(cfg["api_url"].rstrip("/") + "/citizen/auth/weak-login",
                          json={"username": cfg["username"], "password": cfg["password"]},
                          timeout=25)
        login.raise_for_status()
        resp = sess.get(cfg["api_url"].rstrip("/") + "/citizen/reservations",
                        params={"from": start.isoformat(), "to": end.isoformat()}, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        if not isinstance(data, dict) or not isinstance(data.get("days"), list):
            raise RuntimeError("eVakan vastauksen rakenne muuttunut")
        return data

def selected_id(cfg, data):
    cid = cfg.get("child_id")
    ids = {str(c.get("childId")) for d in data.get("days", []) for c in d.get("children", []) if c.get("childId")}
    if cid:
        return str(cid)
    if len(ids) == 1:
        return next(iter(ids))
    if len(ids) > 1:
        raise RuntimeError("Näkyvissä useita lapsia: määritä child_id private/config.json -tiedostoon")
    return None

def state_file(name):
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    return PRIVATE / name

def read_state(path):
    if not path.is_file():return None
    return json.loads(path.read_text(encoding="utf-8"))

def write_state(path, value):
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    temp = path.with_name(path.name + ".tmp")
    with temp.open('w', encoding='utf-8') as fh:
        json.dump(value, fh, ensure_ascii=False, indent=2)
    os.chmod(temp, 0o600)
    temp.replace(path)

def notify(title, message):
    # Älä tallenna lapsen tietoja etäpalveluun: vain Androidin paikallisilmoitus.
    try:
        subprocess.run(["termux-notification", "--title", title, "--content", message],
                       check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    except (OSError, subprocess.TimeoutExpired):
        pass
    print(title + ": " + message.replace("\n", " | "))
