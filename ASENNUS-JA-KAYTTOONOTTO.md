Author: Tatu Satoniitty
Contact: tatu.satoniitty@gmail.com

# eVaka-vahti – asennus- ja käyttöönotto-ohje

## Mitä tämä tekee?

Seuraa käyttäjän **oman eVaka-tilin** kautta lapsen hoitoaikavarauksia ja päiväkodin eVakaan kirjaamia saapumis- ja lähtömerkintöjä sekä uusia vastaanotettuja viestejä ja vastauksia. Ilmoitukset tulevat Androidin paikallisilmoituksina. Tarkistukset tehdään hoitoajoille puolen tunnin välein sekä läsnäoloille kaksi kertaa tunnissa ja viesteille kaksi kertaa tunnissa.

**Tärkeät rajaukset:** Tiedot eivät ole reaaliaikaista paikannusta. Lähtö näkyy vasta, kun päiväkoti on kirjannut lähdön eVakaan. Eri kuntien eVaka-kirjautumistavat ja API-osoitteet voivat erota. Tämän version toteutus käyttää `/citizen/auth/weak-login`-rajapintaa. Vahvaan Suomi.fi-tunnistautumiseen tai muuhun kirjautumistapaan se ei sellaisenaan sovellu. Älä käytä ohjelmaa vastoin palvelun käyttöehtoja tai tietosuojavaatimuksia.

## 1. Asenna Androidiin

Asenna samasta luotettavasta lähteestä, esimerkiksi F-Droidista:
- **Termux:** https://f-droid.org/packages/com.termux/
- **Termux:API:** https://f-droid.org/packages/com.termux.api/
- **Termux:Boot:** https://f-droid.org/packages/com.termux.boot/

Avaa Termux:Boot kerran. Salli Termux:API:n ilmoitukset sekä akun optimointien poikkeukset Termuxille, Termux:API:lle ja Termux:Bootille puhelimen asetuksissa. Valmistajakohtaiset akunhallinnan rajoitukset voivat yhä keskeyttää ajastukset.

## 2. Siirrä ZIP puhelimeen

Lataa **evaka-vahti.zip** Androidin Lataukset-kansioon. Avaa Termux ja suorita:

```sh
termux-setup-storage
pkg install -y unzip
cd ~
unzip -o ~/storage/downloads/evaka-vahti.zip
```

ZIP sisältää `evaka-vahti/`-hakemiston, jonka tulee sijaita polussa `~/evaka-vahti/`.

## 3. Käyttöönotto ja omat tunnukset

```sh
cd ~/evaka-vahti
bash ./asenna.sh
```

Asennin kysyy oman kunnan eVaka-palvelun HTTPS-osoitteen (esim. `https://espoonvarhaiskasvatus.fi`), eVaka-käyttäjätunnuksen ja salasanan. Salasana ei näy näytöllä. Halutessasi syötä seurattavan lapsen `childId` ja Healthchecks-kuittausosoitteet (myös viestivahdille). Jos eVakassa näkyy vain yksi lapsi, `childId` voidaan jättää tyhjäksi. **Älä jaa kirjautumistietoja** tai `private/`-hakemistoa muille.

Huom. Jos palvelu käyttää vahvaa tunnistautumista eikä erillistä salasanaa, tämä asentaja ei pysty kirjautumaan. Älä anna pankkitunnuksia tälle ohjelmalle.

Asennin lisää vain omat rivinsä crontabiin, säilyttäen muut tehtävät, sekä tekee Termux:Boot-käynnistysskriptin.

## 4. Kokeile heti

```sh
~/evaka-vahti/tarkista.sh
```

Katso lokit:

```sh
tail -n 20 ~/evaka-vahti/logs/hoito.log
tail -n 20 ~/evaka-vahti/logs/lasna.log
tail -n 20 ~/evaka-vahti/logs/viestit.log
```

Ensimmäisellä onnistuneella ajolla syntyy lähtötilanne. Vanhoista kirjauksista ei tehdä ilmoituksia; seuraavat muutokset havaitaan vasta myöhemmillä tarkistuksilla. Testaa ilmoituksia vasta todellisella kirjauksella tai turvallisessa testitilissä. Älä tyhjennä tilatiedostoja jatkuvasti.

## 5. Näin käytät

```sh
# Hae hoitoaikojen uudet muutokset heti
~/evaka-vahti/aja.sh hoito

# Tarkista uudet saapumis- ja lähtömerkinnät heti
~/evaka-vahti/aja.sh lasna

# Tarkista uudet eVaka-viestit heti
~/evaka-vahti/aja.sh viestit

# Käynnistä wake lock, cron ja kaikki kolme testiajoa
~/evaka-vahti/tarkista.sh

# Näytä ajastukset
crontab -l
```

Wake lock auttaa pitämään prosessin käynnissä, mutta ei takaa, etteikö Android lopettaisi taustatehtäviä. Healthchecks.io-palveluun voi luoda vapaaehtoiset seurannat, joiden ping-osoitteet syötetään asennuksessa. Healthchecksille lähetetään vain elossaolokuittaus, ei lapsen nimeä eikä tapahtumatietoja.

## 6. Asetusten muuttaminen ja poistaminen

Muuta asetuksia komennolla `cd ~/evaka-vahti && PYTHONPATH=./src python -m evaka_vahti.maarita`. Käynnistys `bash ~/evaka-vahti/asenna.sh` säilyttää jo olemassa olevat tunnukset ja seurantatilan eikä kysy niitä uudelleen. Jos vaihdat **seurattavaa lasta**, poista vanhat `private/hoitoajat.json` ja `private/lasnaolo.json` ennen seuraavaa ajoa, jotta eri lasten tapahtumat eivät sekoitu. Älä koskaan jaa niitä.

Poista ajastukset komennolla `~/evaka-vahti/poista.sh`. Ohjelman yksityiset asetukset pysyvät laitteellasi, kunnes poistat `~/evaka-vahti`-hakemiston itse.

## Uusi viestivahti

Viestivahti käyttää käyttäjän oman kirjautuneen eVaka-istunnon rajapintoja
`/citizen/messages/my-account` ja `/citizen/messages/received`.
Se ilmoittaa uusista vastaanotetuista viesteistä ja uusista vastauksista
myös aiemmin alkaneissa viestiketjuissa. Omista lähettämistäsi viesteistä
ei ilmoiteta. **Ilmoitus ei sisällä viestin tekstiä, otsikkoa eikä lähettäjää.**

Ensimmäinen onnistunut tarkistus tallentaa nykyisten viestien tunnisteet,
jotta vanhoista viesteistä ei tehdä hälytyksiä. Viestien lukeminen vahdilla
ei merkitse niitä eVakassa luetuiksi. Arkistoidut tai käyttöoikeuksien
ulkopuolella olevat viestit eivät välttämättä näy tarkistuksessa.
Vahvaa tunnistautumista vaativat viestit voivat näkyä vain peitettyinä,
jolloin vahti havaitsee ketjun aikaleiman muutoksen eikä välttämättä
jokaista erillistä viestiä. Tämä on testiversio, jonka toiminta pitää
varmistaa omassa eVaka-ympäristössä.

Jos päivität aiemmasta versiosta **ilman asetusten menettämistä**,
korvaa ohjelmatiedostot mutta säilytä olemassa oleva `private/`.
Lisää kolmas cron-rivi ajamalla `bash ~/evaka-vahti/asenna.sh`
(asentaja säilyttää jo määritetyt asetukset). Voit myös lisätä manuaalisesti
cron-rivin `10,40 * * * * ~/evaka-vahti/aja.sh viestit`, mutta cronissa
käytä kotihakemiston absoluuttista polkua.

## 7. Tietosuoja ja vastuut

- `private/config.json` sisältää salasanasi selväkielisenä tiedostona laitteella, käyttöoikeuksilla `600`. Tämä on jäännösriski, vaikka tiedoston oikeudet rajataan. Lukitse puhelin ja pidä varmuuskopiot salattuina.
- `private/*.json` sisältää lapsen tunnisteita, varaus-/läsnäolotietoja sekä viestitunnisteita (ei viestien sisältöä). `logs/` sisältää tapahtumatietoja. Älä julkaise näitä.
- Älä lähetä koko omasta asennuksestasi tehtyä ZIP-varmuuskopiota muille.
- Ohjelma on epävirallinen ja tulee testata omalla käyttäjätilillä. Kuntakohtainen yhteensopivuus ei ole taattu.

## 8. Lähdekoodi ja lisenssit

eVaka-vahdin lähdekoodi löytyy hakemistosta `src/evaka_vahti/`. Ohjelman tekijä on Tatu Satoniitty, ja sen lisenssiehdot löytyvät tiedostosta `LICENSE-VAHTI.txt`. Jakelupaketti sisältää vain eVaka-vahdin toimintaan tarvittavat ohjelmatiedostot, ei varsinaisen eVaka-palvelun lähdekoodia.


## Uusi v0.2.1: viestien lähettäjä ja otsikko

Viestivahdin ilmoituksessa näkyvät **lähettäjä** ja **keskustelun otsikko**.
Vastaus käyttäjän aiemmin samaan ketjuun lähettämään viestiin erottuu
otsikolla **VASTAUS OMAAN VIESTIIN**. Suojatuista viesteistä ei välttämättä
ole saatavilla otsikkoa tai vastauksen tunnistamiseen tarvittavia tietoja.
Viestin sisältöä ei näytetä eikä tallenneta. Lähettäjä ja otsikko näkyvät
puhelimen ilmoituksessa ja mahdollisesti lukitusnäytöllä.

Jos v0.2.0-viestivahti on ollut käytössä, aiemmin ilmoitetuista viesteistä
ei tule uusia hälytyksiä; uusiin saapuneisiin tulee tarkemmat ilmoitukset.
