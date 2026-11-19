# Testausraportti

## Rajaus

Tässä raportissa erotetaan algoritmeja ja HTTP-sopimusta koskevat testit, Androidin laitetestit ja tilikohtainen Espoo-testi. Testiympäristöön ei syötetty huoltajan tunnuksia, lasten tietoja tai oikeita viestejä. Android 10 -emulaattori ajoi varsinaista APK:ta ohjelmallisella CPU-emulaatiolla.

## Rakennuksen tulos

Versiossa 0.1.1-test (versionCode 2) onnistui Gradlen ajo `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Yhteensä **29 testiä läpäisi** (15 vahti- ja asetustestiä, 9 viesti- ja istuntotestiä, 5 HTTP-testiä). Ohitettuja testejä ei ollut. Android Lint raportoi 0 virhettä ja 4 saatavilla olevia uudempia riippuvuusversioita koskevaa varoitusta. Valitut Compose- ja OkHttp-versiot sopivat SDK 36 -koontiin. Uudemman Compose BOM -version koontiyritys vaati SDK 37:n ja uudemman Android Gradle -liitännäisen.

APK on 26 021 155 tavua. `apksigner verify --verbose --print-certs` vahvisti v2-allekirjoituksen. Asennuspaketin pienin SDK on 29, kohde-SDK 36. APK:n SHA-256 on `9e30a3a61dd9db7fbe390d9251b5e89f9daf08031b6f8b01ed99822f36afc76b`. Debug-allekirjoittajan varmenteen SHA-256 on `70b68f4c51c277e19ad5ddac93de25b806051208ab6ca9a2aebd9d0e6537ac4f`.

## Android 10 -emulaattori

Käyttöönoton testitilassa kaikki neljä kytkintä olivat aluksi pois päältä. Yöhoito- ja viestivahti kytkettiin päälle, päivä- ja läsnäolovahti jäivät pois. Lähtötilan historia oli tyhjä. Androidin ilmoituspalvelusta löytyivät yöhoitomuutos ja toiseen kanavaan ohjattu vastaus omaan viestiin. Pakotetun prosessin pysäytyksen ja sovelluksen uudelleen avaamisen jälkeen valinnat säilyivät.

Android käynnistettiin uudelleen, mikä varmistettiin Linuxin käynnistystunnisteen vaihtumisesta. Kehitysversion 0.1.0-test päälle asennettiin samalla avaimella allekirjoitettu 0.1.1-test. Uusi APK avasi tallennetun tilan, kaksi valittua vahtia ja aiemman viestihistorian. Asetuksista molemmat vahdit sammutettiin ja etusivu näytti 0/4. Android 10:n ensimmäinen yritys tarkistaa järjestelmän työjono heti käynnistyksessä jäi epäselväksi, kun ohjelmallisen emulaattorin System UI jumiutui ja Android käynnisti järjestelmäprosessin uudelleen.

## Android 16 -emulaattori

Lopullinen jaettava 0.1.1-test asennettiin puhtaaseen Android 16:n AOSP-testilaitteeseen. Ensimmäisen asennusyrityksen aikana emulaattorin järjestelmäpalvelu katkesi. Uusinta Androidin käynnistyttyä onnistui. Suomenkielinen käyttöönotto käytiin läpi käyttöliittymästä. Järjestelmän lupaikkunassa hyväksyttiin ilmoitukset ja Androidin pakettihallinnasta vahvistettiin `POST_NOTIFICATIONS: granted=true`.

Yöhoitovahti ja viestivahti toimivat tässä kokeessa muiden vahtien ollessa pois päältä. Alussa historia oli tyhjä. Androidin ilmoituspalvelusta vahvistettiin sekä yöhoitovarauksen muutos että `VASTAUS OMAAN VIESTIIN`. Etusivun 2/4-valinta ja historia säilyivät sovellusprosessin pakotetun pysäyttämisen ja uuden käynnistyksen jälkeen. Testit käyttivät paikallista testitilaa. Myös WorkManagerin järjestelmätyön käynnistyminen ja päättyminen näkyivät Androidin JobSchedulerissa. Lopuksi molemmat vahdit sammutettiin asetuksista, etusivu näytti 0/4 ja aktiivisten ilmoitusten tarkistus onnistui.

Seuraavassa kokeessa otettiin käyttöön pelkkä yöhoitovahti ja 15 minuutin tarkistusväli. Lähtötilan tallennuttua etusivu suljettiin kotinäppäimellä ja koko emulaattori käynnistettiin uudelleen. Käynnistystunniste vaihtui. Androidin lukituksen avaamisen jälkeen WorkManager vastaanotti BOOT_COMPLETED-tapahtuman ja palautti sovelluksen työn JobScheduleriin ennen kuin sovelluksen käyttöliittymää avattiin. Liian aikaisin järjestelmätyökalulla käynnistetty työ kirjasi odottavansa asetettua tarkistusväliä.

Kun 15 minuutin vähimmäisväli oli kulunut, ajokelpoinen työ käynnistettiin testissä Androidin JobSchedulerin komennolla `cmd jobscheduler run -s -n androidx.work.systemjobscheduler fi.evakavahti.android 8`. Työn tunniste oli tässä ajossa 8 ja vaihtelee asennuksittain. Valitsin `-s` säilytti ajon ehdot. WorkManager kirjasi `PollWorker`-työn tulokseksi **SUCCESS**. Sovellus oli taustalla koko uudelleenkäynnistyksen jälkeisen kokeen ajan. Koe todensi työn palautumisen ja onnistuneen ajon paikallisella aineistolla. Androidin vapaasti valitsema suoritushetki ja fyysisen puhelimen virransäästön vaikutus vaativat erillisen pitkäaikaistestin.

## Automaattiset JVM-testit

`WatchTest` kattaa päivävarausten lisäämisen, muuttumisen ja poistamisen, toisen aikavälin lisäämisen samalle päivälle, keskiyön sekä 23.59/00.00-rajan yli yhdistyvät vuorot, eri lapset, yöhoidon itsenäisyyden ja yöhoidon kaksoisilmoitusten eston. Läsnäolotesteissä on avoin käynti, jälkikirjattu suljettu käynti, keskiyön lähtö ja tilapäisesti puuttuva kirjaus. Lisäksi testataan kaikki 16 kytkinyhdistelmää, viimeisen onnistumisajan säilyminen, kaikkien vahtien sammutus, hiljaiset lähtötilat, uudelleen käyttöönotto, muuttunut lapsivalinta, liikkuvan hakuikkunan rajat ja tilan tallennus–luku-kierros.

`MailAndSessionTest` kattaa saapuvan viestin, aiempaan ketjuun saapuvan vastauksen, käyttäjän itse aloittaman ketjun, omien lähetysten suodatuksen, puuttuvat yksityiskohdat, suojatun ketjun epävarman lähettäjän, sivutuksen, yhtäaikaiset aikaleimat ja sivuvirheen. Istuntotestit tarkistavat vanhenemisen, käyttäjän suostumuksen, yhden uusintayrityksen, väärän tilin ja virheelliset tunnukset.

`ApiTest` käyttää OkHttp-testivastauksia. Se tarkistaa HTTPS-palveluosoitteen, CSRF-otsakkeen, kirjautumisen, istuntoevästeen rajauksen, tilinomistajan, kalenterin JSON-kentät, puutteellisen päivävälin sekä HTTP-virheet 401, 403, 429 ja 503 ja verkkokatkoksen. Näissä testeissä palvelin ei ole Espoon tuotantopalvelu.

## Toistaminen

Aja projektihakemistossa `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Gradlen yksityiskohtainen JUnit-raportti syntyy hakemistoon `app/build/reports/tests/testDebugUnitTest/`.

Laitetestin skripti on `tests/emulator_smoke.py`. Aja se vain erillisessä, tuoreessa testiemulaattorissa, johon APK on asennettu. Skripti valitsee paikallisen testitilan, muuttaa emulaattorin näyttökokoa ja vahvistaa kahden vahdin ilmoitukset. Isäntäkoneessa tarvitaan Python 3 ja Android SDK:n adb. Valinnainen `--reboot` käynnistää testiemulaattorin uudelleen. `--phase setup` ja `--phase verify` mahdollistavat saman kokeen jakamisen kahteen osaan. Erillinen `--phase background` ajetaan onnistuneen kokonaistestin jälkeen. Se ottaa paikallisessa testitilassa käyttöön yöhoitovahdin, jättää sovelluksen taustalle, käynnistää emulaattorin uudelleen ja tarkistaa järjestelmän työjonon ennen käyttöliittymän avaamista.

```sh
adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk
python3 tests/emulator_smoke.py --serial emulator-5554 --reboot
```

## Omalla laitteella varmistettavat asiat

- Vertaa Espoosta palautuvaa lapsiluetteloa, varausten ja läsnäolon aikoja sekä ensimmäisen tarkistuksen tulosta viralliseen palveluun. Uusi normaali kirjaus on paras vertailutapahtuma.
- Kokeile vuoropäivähoidossa myös yön molempia kalenteripäiviä. Tarkista alku, loppu ja mahdollinen poisto.
- Vertaa yhteistä viestiä, lapsikohtaista viestiä ja aiemmin itse aloittamaasi keskustelua. Suojatun ketjun tila voi jäädä epäselväksi kevyellä tunnistautumisella.
- Tarkista puhelimen omassa ilmoitusnäkymässä kanavan tärkeys, yksityiskohtien valinta ja lukitusnäyttö. Anna taustavahtien toimia esimerkiksi yön yli. Kirjaa Android-versio, vahti, tarkistuksen kellonaika ja tapahtuman tyyppi. Henkilötietoja tai evästeitä ei tarvita virheraporttiin.
