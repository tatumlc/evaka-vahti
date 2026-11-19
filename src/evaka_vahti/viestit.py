# Author: Tatu Satoniitty
# Contact: tatu.satoniitty@gmail.com
# SPDX-License-Identifier: MIT

"""eVaka-viestivahti: lähettäjä, otsikko ja vastaukset omiin viesteihin."""
import subprocess
from .core import authenticated_session, settings, state_file, read_state, write_state


def notify_private(title, message):
    """Ilmoita paikallisesti, mutta älä kirjoita lähettäjän nimeä tai otsikkoa lokiin."""
    subprocess.run(['termux-notification', '--title', title, '--content', message],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)


# Viestin sisältöä ei tallenneta. Ilmoituksessa näkyvät käyttäjän pyytämät
# lähettäjän nimi ja keskustelun otsikko (myös lukitusnäytöllä, asetuksista riippuen).
MAX_PAGES = 100


def fetch_messages(cfg):
    """Palauta {saapuneen viestin tunniste: ilmoitustiedot}.

    Tunnisteet eivät sisällä viestin sisältöä, otsikkoa tai lähettäjän nimeä.
    """
    found = {}
    with authenticated_session(cfg) as sess:
        base = cfg['api_url']
        account = sess.get(base + '/citizen/messages/my-account', timeout=30)
        account.raise_for_status()
        my_id = account.json().get('accountId')
        if not isinstance(my_id, str) or not my_id:
            raise RuntimeError('Viestitilin tunniste puuttuu')
        for page in range(1, MAX_PAGES + 1):
            resp = sess.get(base + '/citizen/messages/received', params={'page': page}, timeout=30)
            resp.raise_for_status()
            data = resp.json()
            if not isinstance(data, dict) or not isinstance(data.get('data'), list):
                raise RuntimeError('Viestien vastauksen rakenne muuttunut')
            for thread in data['data']:
                if not isinstance(thread, dict) or not isinstance(thread.get('id'), str):
                    raise RuntimeError('Viestiketjun rakenne muuttunut')
                if thread.get('type') == 'Regular':
                    messages = thread.get('messages')
                    if not isinstance(messages, list):
                        raise RuntimeError('Viestilistan rakenne muuttunut')
                    sent_times = []
                    for msg in messages:
                        if not isinstance(msg, dict) or not isinstance(msg.get('id'), str):
                            raise RuntimeError('Viestin rakenne muuttunut')
                        sender = msg.get('sender')
                        if not isinstance(sender, dict) or not isinstance(sender.get('id'), str):
                            raise RuntimeError('Viestin lähettäjätieto puuttuu')
                        if sender['id'] == my_id and isinstance(msg.get('sentAt'), str):
                            sent_times.append(msg['sentAt'])
                    subject = clean_label(thread.get('title'), 'Ei otsikkoa')
                    for msg in messages:
                        sender = msg['sender']
                        if sender['id'] == my_id:
                            continue
                        stamp = msg.get('sentAt')
                        reply = isinstance(stamp, str) and any(t < stamp for t in sent_times)
                        found['m:' + msg['id']] = {
                            'sender': clean_label(sender.get('name'), 'Tuntematon lähettäjä'),
                            'subject': subject,
                            'reply': reply,
                        }
                elif thread.get('type') == 'Redacted':
                    # Salaisuus- tai tunnistautumisrajoituksen takia tässä ei ole
                    # viestin yksilöllistä id:tä eikä otsikkoa.
                    stamp = thread.get('lastMessageSentAt')
                    if stamp:
                        found['r:' + thread['id'] + ':' + str(stamp)] = {
                            'sender': clean_label((thread.get('sender') or {}).get('name'), 'Tuntematon lähettäjä'),
                            'subject': 'Suojattu viesti (otsikko ei saatavilla)',
                            'reply': False,
                        }
                else:
                    raise RuntimeError('Tuntematon viestiketjun tyyppi')
            pages = data.get('pages')
            if not isinstance(pages, int) or pages < 0:
                raise RuntimeError('Viestien sivumäärä puuttuu')
            if page >= pages:
                return found
    raise RuntimeError('Liikaa viestisivuja turvalliseen tarkistukseen')


def clean_label(value, fallback):
    if not isinstance(value, str) or not value.strip():
        return fallback
    # Vain ilmoituksen ulkoasu; ei lisätä tiedoista komentoriviargumentteja.
    return ' '.join(value.split())[:120]


def run():
    current = fetch_messages(settings())
    path = state_file('viestit.json')
    previous = read_state(path)
    if previous is None:
        write_state(path, sorted(current))
        print('Viestivahti alustettu. Vanhoista viesteistä ei ilmoiteta.')
        return
    if not isinstance(previous, list) or not all(isinstance(x, str) for x in previous):
        raise RuntimeError('Viestivahdin tilatiedosto on virheellinen')
    new = sorted(set(current) - set(previous))
    # Tallennetaan vain tunnisteet. Muuttunut otsikko ei laukaise uudelleen.
    for key in new:
        data = current[key]
        title = ('eVaka-vahti: VASTAUS OMAAN VIESTIIN' if data['reply'] else
                 'eVaka-vahti: uusi viesti')
        notify_private(title, 'Lähettäjä: ' + data['sender'] + '\nOtsikko: ' + data['subject'])
    write_state(path, sorted(current))
    print(f'Viestivahti: {len(new)} uutta saapunutta viestiä.' if new else 'Ei uusia eVaka-viestejä.')
