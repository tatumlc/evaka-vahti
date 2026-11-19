# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""Interaktiivinen, paikallinen asetusten määritys."""
import json,os,getpass
from pathlib import Path
from urllib.parse import urlparse
from .core import PRIVATE,CONFIG,get_calendar

def main():
    print('eVaka-vahti – käyttöönotto (tiedot tallennetaan vain tähän laitteeseen)')
    print('Syötä oman kuntasi eVaka-palvelun osoite, esim. https://espoonvarhaiskasvatus.fi')
    url=input('eVaka-verkkosivun osoite: ').strip().rstrip('/')
    if not url.startswith('https://') or not urlparse(url).netloc:
        raise SystemExit('Virhe: osoitteen pitää alkaa https://')
    if url.endswith('/api'): api=url
    else: api=url+'/api'
    username=input('eVaka-kirjautumissähköposti: ').strip()
    password=getpass.getpass('eVaka-salasana (ei näy näytöllä): ')
    if not username or not password:raise SystemExit('Tunnukset eivät saa olla tyhjiä.')
    cid=input('Lapsen eVaka childId (voi jättää tyhjäksi, jos vain yksi lapsi): ').strip()
    hc1=input('Valinnainen Healthchecks-hoitoajat-ping-URL (Enter ohittaa): ').strip()
    hc2=input('Valinnainen Healthchecks-läsnäolo-ping-URL (Enter ohittaa): ').strip()
    hc3=input('Valinnainen Healthchecks-viestit-ping-URL (Enter ohittaa): ').strip()
    for value in (hc1,hc2,hc3):
        if value and (not value.startswith('https://hc-ping.com/') or len(value)>200):
            raise SystemExit('Healthchecks-osoitteen on oltava https://hc-ping.com/...')
    PRIVATE.mkdir(parents=True,exist_ok=True,mode=0o700)
    os.chmod(PRIVATE,0o700)
    cfg={'api_url':api,'username':username,'password':password,'child_id':cid or None,
         'healthchecks_hoito':hc1,'healthchecks_lasna':hc2,'healthchecks_viestit':hc3}
    fd=os.open(CONFIG,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w',encoding='utf8') as out:json.dump(cfg,out,ensure_ascii=False,indent=2)
    os.chmod(CONFIG,0o600)
    print('Asetukset tallennettu suojattuun private/config.json-tiedostoon.')
    print('HUOM: Tämä versio tukee vain kyseisessä eVaka-palvelussa sallittua weak-login-kirjautumista.')
    print('Tarkista toiminta: ./tarkista.sh')
if __name__=='__main__':main()
