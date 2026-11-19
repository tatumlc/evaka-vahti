Author: Tatu Satoniitty
Contact: tatu.satoniitty@gmail.com

# eVaka-vahti v0.2.1-test – uusi viestivahti

Uusi toiminto `./aja.sh viestit` tarkistaa eVakaan saapuneet viestit noin puolen tunnin välein.

- Ilmoittaa uudesta saapuneesta viestistä tai vanhaan keskusteluun tulleesta vastauksesta.
- Ei ilmoita omista lähetetyistä viesteistä.
- Ei näytä viestien sisältöjä tai lähettäjätietoja Androidin ilmoituksessa.
- Ensimmäinen tarkistus tallentaa lähtötilanteen ilman vanhojen viestien hälytyksiä.
- Viestin lukutilaa eVakassa ei muuteta.
- Viestivahti tallentaa vain viestien tunnisteet `private/viestit.json`-tiedostoon.

## Päivitys aiemmasta versiosta

Varmuuskopioi ensin nykyinen toimiva asennus ja erityisesti `private/`-hakemisto.
Jos uusi paketti kopioidaan olemassa olevan asennuksen päälle, säilytä
`~/evaka-vahti/private/` sekä sen sisältö muuttumattomana.

Korvaa ohjelmatiedostot uudella paketilla ja suorita:

```sh
cd ~/evaka-vahti
bash ./asenna.sh
./tarkista.sh
```

Asennin säilyttää olemassa olevan `private/config.json`-tiedoston. Ajastus lisätään
viestivahdille klo xx:10 ja xx:40. Valinnainen Healthchecks-ping-osoite voidaan
lisätä `private/config.json`-tiedostoon avaimella `healthchecks_viestit`.

## Testaus

```sh
~/evaka-vahti/aja.sh viestit
tail -n 20 ~/evaka-vahti/logs/viestit.log
```

Testiversio: eVakan viestirajapinnan kuntakohtaiset erot voivat vaikuttaa toimintaan.
Ohjelma käyttää käyttäjän omaa sallittua eVaka-kirjautumista.

Viestivahdin ilmoituksissa näkyvät nyt lähettäjä ja otsikko; vastaukset omiin viesteihin korostetaan.
