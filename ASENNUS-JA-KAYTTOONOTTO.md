Author: Tatu Satoniitty
Contact: tatu.satoniitty@gmail.com

# eVaka-vahti – asennus- ja käyttöönotto-ohje

## Mitä tämä tekee?

Seuraa käyttäjän **oman eVaka-tilin** kautta lapsen hoitoaikavarauksia ja päiväkodin eVakaan kirjaamia saapumis- ja lähtömerkintöjä. Ilmoitukset tulevat Androidin paikallisilmoituksina. Tarkistukset tehdään hoitoajoille puolen tunnin välein sekä läsnäoloille kaksi kertaa tunnissa.

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

Asennin kysyy oman kunnan eVaka-palvelun HTTPS-osoitteen (esim. `https://espoonvarhaiskasvatus.fi`), eVaka-käyttäjätunnuksen ja salasanan. Salasana ei näy näytöllä. Halutessasi syötä seurattavan lapsen `childId` ja Healthchecks-kuittausosoitteet. Jos eVakassa näkyy vain yksi lapsi, `childId` voidaan jättää tyhjäksi. **Älä jaa kirjautumistietoja** tai `private/`-hakemistoa muille.

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
```

Ensimmäisellä onnistuneella ajolla syntyy lähtötilanne. Vanhoista kirjauksista ei tehdä ilmoituksia; seuraavat muutokset havaitaan vasta myöhemmillä tarkistuksilla. Testaa ilmoituksia vasta todellisella kirjauksella tai turvallisessa testitilissä. Älä tyhjennä tilatiedostoja jatkuvasti.

## 5. Näin käytät

```sh
# Hae hoitoaikojen uudet muutokset heti
~/evaka-vahti/aja.sh hoito

# Tarkista uudet saapumis- ja lähtömerkinnät heti
~/evaka-vahti/aja.sh lasna

# Käynnistä wake lock, cron ja molemmat testiajot
~/evaka-vahti/tarkista.sh

# Näytä ajastukset
crontab -l
```

Wake lock auttaa pitämään prosessin käynnissä, mutta ei takaa, etteikö Android lopettaisi taustatehtäviä. Healthchecks.io-palveluun voi luoda vapaaehtoiset seurannat, joiden ping-osoitteet syötetään asennuksessa. Healthchecksille lähetetään vain elossaolokuittaus, ei lapsen nimeä eikä tapahtumatietoja.

## 6. Asetusten muuttaminen ja poistaminen

Aja `bash ~/evaka-vahti/asenna.sh` uudelleen muuttaaksesi asetuksia. Tällöin luodaan uudet asetukset, mutta vanhaa seurantatilaa ei tyhjennetä automaattisesti. Jos vaihdat **seurattavaa lasta**, poista vanhat `private/hoitoajat.json` ja `private/lasnaolo.json` ennen seuraavaa ajoa, jotta eri lasten tapahtumat eivät sekoitu. Älä koskaan jaa niitä.

Poista ajastukset komennolla `~/evaka-vahti/poista.sh`. Ohjelman yksityiset asetukset pysyvät laitteellasi, kunnes poistat `~/evaka-vahti`-hakemiston itse.

## 7. Tietosuoja ja vastuut

- `private/config.json` sisältää salasanasi selväkielisenä tiedostona laitteella, käyttöoikeuksilla `600`. Tämä on jäännösriski, vaikka tiedoston oikeudet rajataan. Lukitse puhelin ja pidä varmuuskopiot salattuina.
- `private/*.json` sisältää lapsen tunnisteita ja varaus-/läsnäolotietoja. `logs/` sisältää tapahtumatietoja. Älä julkaise näitä.
- Älä lähetä koko omasta asennuksestasi tehtyä ZIP-varmuuskopiota muille.
- Ohjelma on epävirallinen ja tulee testata omalla käyttäjätilillä. Kuntakohtainen yhteensopivuus ei ole taattu.

## 8. Lähdekoodi ja lisenssit

eVaka-vahdin lähdekoodi löytyy hakemistosta `src/evaka_vahti/`. Ohjelman tekijä on Tatu Satoniitty, ja sen lisenssiehdot löytyvät tiedostosta `LICENSE-VAHTI.txt`. Jakelupaketti sisältää vain eVaka-vahdin toimintaan tarvittavat ohjelmatiedostot, ei varsinaisen eVaka-palvelun lähdekoodia.
