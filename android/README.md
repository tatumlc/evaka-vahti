# eVaka-vahti Androidille

Yksityinen testiversio 0.1.1-test. Kotlin- ja Compose-sovellus Android 10:lle ja uudemmille. Sovellustunnus on `fi.evakavahti.android`. Puhelimessa ei tarvita Termuxia, Pythonia, kehitystyökaluja eikä taustapalvelinta.

## Asennus ja ensimmäinen kokeilu

1. Lataa testipaketin APK puhelimeen. Avaa se Lataukset-kansiosta. Jos Android pyytää, salli tuntemattomien sovellusten asennus vain käyttämällesi tiedostonavaajalle. Oikeuden voi poistaa heti asennuksen jälkeen.
2. Avaa **eVaka-vahti → Valitse Espoo → Kokeile paikallisilla testitiedoilla**. Testitila toimii myös ilman verkkoyhteyttä. Valitse Testilapsi A ja pelkkä yöhoitovahti. Kaikki neljä kytkintä ovat aluksi pois päältä.
3. Viimeistele käyttöönotto ja salli ilmoitukset. Ensimmäinen onnistunut tarkistus luo lähtötilanteen. Paina etusivulla **Luo testimuutos** ja tarkista yöhoitoilmoitus sekä tapahtumahistoria. Kokeile myös muita kytkimiä. Testiaineiston toinen muutos tuottaa vastauksen huoltajan omaan viestiin.
4. Valitse **Asetukset → Vahdit ja ilmoitukset**. Sammuta vahti, tee testimuutos toisen vahdin ollessa päällä ja kytke ensimmäinen uudelleen päälle. Vain seuraavien muutosten kuuluu synnyttää tapahtumia. Tarkista saman aineiston toistuva lukeminen painamalla **Tarkista nyt**.
5. Voit siirtyä testitilasta Espoon kevyttunnukseen kohdasta **Asetukset → Kirjautuminen**. Tilin vaihtuessa tehdään lapsi- ja vahtivalinnat uudelleen. Käytä vain omaa huoltajatiliäsi. Ensimmäisessä oikeassa testissä vertaa lapsiluetteloa, lähtötilan kellonaikaa ja seuraavaa luonnollista muutosta kunnan viralliseen palveluun.

Oletusilmoitus näyttää tapahtumatyypin. Avaa **Vahdit ja ilmoitukset → Näytä ilmoituksissa yksityiskohdat**, jos haluat ajat tai viestien lähettäjät ja otsikot näkyviin avatulla puhelimella. Androidin ilmoituskanavat ja lupa voivat estää ilmoituksen. Historia toimii myös luvan ollessa pois päältä. Samalla puhelimella käytössä oleva Termux-vahti voi antaa oman erillisen ilmoituksensa.

## Taustatoiminta ja rajaukset

- Yksi WorkManager-työ hoitaa käytössä olevat vahdit. Oletusväli on 30 minuuttia, muut vaihtoehdot ovat 15 ja 60 minuuttia. Espoon tarkistus tarvitsee verkkoyhteyden. Virransäästö, sovelluksen käyttörajoitukset ja valmistajan akunhallinta vaikuttavat viiveeseen. Androidin pakotettu pysäytys voi estää työn seuraavaan sovelluksen avaukseen saakka.
- Jokaisella vahdilla on oma vertailutila ja viimeinen onnistumisaika. Pois kytkeminen poistaa kyseisen vertailutilan. Viimeisimmän onnistumisen aika jää näkyviin. Uudelleen kytkeminen ja lapsivalintojen muutokset alustavat seurannan hiljaisesti. Kaikkien vahtien ollessa pois ajastus perutaan.
- Varauksia vertaillaan Helsingin paikallispäivissä noin kuukauden päähän. Haussa on lisäpäiviä yön yli jatkuvien varausten yhdistämiseen. Saman lapsen peräkkäiset osat, joista edellinen loppuu päivän loppuun ja seuraava alkaa keskiyöllä, tulkitaan jatkuvaksi varaukseksi. Palvelu ei anna osille yhteistä varaus-ID:tä. Päällekkäiseltä vertailujaksolta verrataan päivittäisiä aikajoukkoja, myös kellonajattomia varauksia. Samana pysyneet aikavälit suodatetaan ensin. Jos samalta päivältä poistuu ja sille tulee useita aikoja, jäljelle jääneet vanhat ja uudet ajat kohdistetaan aikajärjestyksessä. Aivan seurantaikkunan ulkopuoliset muutokset ja kahden tarkistuksen välissä palautunut muutos voivat jäädä havaitsematta.
- Läsnäolot perustuvat päiväkodin eVakaan kirjaamiin riveihin. Tarkistus lukee kuluvan päivän ja seitsemän edellistä päivää. Myöhemmin ilmestyvä valmis käynti tuottaa sekä saapumis- että lähtötapahtuman. Sovellus ei kerää sijaintia.
- Viestivahti lukee saapuneiden keskustelujen sivut ja vertaa lähettäjää tilin omaan viestitunnisteeseen. Se seuraa valittujen lasten viestejä ja tilin yhteisiä viestejä. Viestien tekstisisältö voi olla mukana palvelimen verkkovastauksessa. Sitä ei sisällytetä sovelluksen tietomalliin, tallennukseen eikä ilmoitukseen. Oman viestin jälkeen samaan ketjuun tulevalla vastauksella on oma korkeamman tärkeyden kanava. Käyttäjä voi muuttaa kanavan asetuksia Androidissa.
- Suojatusta keskustelusta sovellus voi ilmoittaa neutraalisti, kun palvelusta saadaan viite saapuneeseen lukemattomaan viestiin. Pelkkä aikaleiman muutos ei yksilöi lähettäjää. Tällaiseen ketjuun saapuvien yksittäisten vastausten ja omiin viesteihin vastaamisen tunnistusta ei voida taata. Arkistoidut keskustelut eivät kuulu saapuneiden hakuun.
- Ensimmäinen viestihaku sivuttaa saapuneet. Myöhemmät haut voivat loppua, kun palvelun aktiivisuusjärjestyksessä saavutetaan edellistä tarkistusta vanhempi ketju. Turvaraja on 200 sivua. Kesken jäänyttä hakua ei hyväksytä lähtötilaksi.
- Historia säilyttää 100 viimeistä tapahtumaa. Android-ilmoitus lähetetään tapahtuman tallennuksen jälkeen. Prosessin sulkeutuminen juuri näiden välissä voi jättää tapahtuman pelkkään historiaan.

## Kirjautuminen, tietosuoja ja käyttöehdot

Ensimmäinen kohde on **Espoo**. Kunnan omassa palvelussa on vahva tunnistautuminen ja kevyttunnuksen käyttöönotto. Virallinen eVaka-lähdekoodi sisältää myös pääsyavaimia tukevan kirjautumistavan. Tämä APK käyttää ainoastaan huoltajan käyttöön ottamaa eVakan sähköposti–salasana-kirjautumista kiinteässä Espoon HTTPS-osoitteessa. Vahvaa kirjautumista tai pääsyavainta tarvitsevat toiminnot hoidetaan kunnan omassa palvelussa. Jos kalenterin haku epäonnistuu onnistuneen kirjautumisen jälkeen, käyttöönotosta voi jatkaa tilin yhteisten viestien seurantaan. Lapsiluettelon voi hakea uudelleen. Muita kuntia ei voi valita tässä versiossa.

Istuntoeväste, valinnat, vahtien vertailutiedot ja historia on salattu AES-GCM:llä Android Keystoren avaimella sovelluksen yksityisessä DataStoressa. Salasana tallennetaan vain käyttäjän valitessa taustakirjautumisen. Vanhentuneen istunnon uusinta tarkistaa saman tilinomistajan. Hylätty kirjautuminen keskeyttää taustakirjautumiset. Automaattiset varmuuskopiot on poistettu käytöstä. Ilmoitukset ovat paikallisia, lukitusnäytöltä salattuja ja ilman viestirunkoa. Sovellus ei käytä analytiikkaa eikä ulkoista ilmoituspalvelua.

Historiasta voi poistaa tapahtumat. **Asetukset → Kirjaudu ulos ja poista paikalliset tiedot** poistaa myös vahtien tilat ja tunnukset sekä peruu ajastukset. Sovellus yrittää sulkea palvelinistunnon myös eVakassa. Tietojen poistaminen tästä sovelluksesta koskee paikallista kopiota.

Espoon asiointiohje ja tietosuojaseloste kuvaavat kunnan palvelua, huoltajan asiointia ja kaupungin tietojenkäsittelyä. Tarkastetuista julkisista aineistoista ei löytynyt nimenomaista lupaa tämän epävirallisen asiakasohjelman automaattiselle käytölle. Lähdekoodin lisenssi koskee ohjelmakoodia. Palvelun voimassa olevat ehdot ja tilikohtaiset oikeudet on huomioitava erikseen. Kuntien hyväksyntä ja mahdollisen yleisen jakelun tietosuojavelvoitteet on vielä selvitettävä ennen julkaisua.

- [Espoon asiointiohje](https://www.espoo.fi/fi/kasvatus-ja-opetus/varhaiskasvatus/varhaiskasvatuksen-asiointipalvelu-evaka)
- [Espoon tietosuojaseloste](https://www.espoo.fi/fi/kaupunki-ja-paatoksenteko/turvallisuus/tietosuoja/kasvun-ja-oppimisen-toimialan-tietosuojaselosteet/tietosuojaseloste-henkilotietojen-kasittely-varhaiskasvatuspalveluiden-asiakastietorekisteri)
- [Androidin taustatyön ajoitus](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## Tekninen rakenne ja lähteet

- `Api.kt` rajaa verkkopyynnöt Espoon HTTPS-palveluun ja käsittelee istunnon sekä virhevastaukset. Kirjautumista lukuun ottamatta käytetään lukupyyntöjä, mukaan lukien virallisen palvelun GET-uloskirjautuminen. Viestejä ei merkitä luetuiksi.
- `Model.kt` ja `Messages.kt` sisältävät vertailulogiikan. `Store.kt` huolehtii salatusta tallennuksesta. `Repository.kt` sovittaa vahtien tilamuutokset, ajastukset ja tarkistustulokset yhteen. `Notifications.kt` vastaa ilmoituskanavista. `MainActivity.kt` sisältää suomenkieliset Compose-näkymät.
- Vertailulähteenä oli [eVaka-vahti/main, 07ba659](https://github.com/tatumlc/evaka-vahti/tree/07ba659e254dac56073ae236f3e4231556ee6626). Android-versio käyttää alkuperäisen vahdin MIT-lisenssiä. Kehittäjä on Tatu Satoniitty, tatu.satoniitty@gmail.com.
- Virallinen toteutus tarkistettiin versiosta [espoon-voltti/evaka, 9514290](https://github.com/espoon-voltti/evaka/tree/95142909f913a8ae89103390b8284e0f42dec4b2). Espoon julkinen `/api/citizen/auth/status` ilmoitti tarkistushetkellä saman version sekä `loggedIn=false`. Tilikohtaisia tuotantopyyntöjä ei tehty.
- [Kirjautumisreitti](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/apigw/src/enduser/routes/auth-weak-login.ts) ja [istuntoeväste](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/apigw/src/shared/session.ts)
- [Varaukset ja läsnäolo](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/service/src/main/kotlin/evaka/core/reservations/ReservationControllerCitizen.kt) ja [keskiyön merkitys](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/service/src/main/kotlin/evaka/core/shared/domain/TimeRangeEndpoint.kt)
- [Viestireitit](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/service/src/main/kotlin/evaka/core/messaging/MessageControllerCitizen.kt) ja [suojatun ketjun tietorakenne](https://github.com/espoon-voltti/evaka/blob/95142909f913a8ae89103390b8284e0f42dec4b2/service/src/main/kotlin/evaka/core/messaging/Message.kt)

## Rakentaminen

Avaa tämä hakemisto Android Studiossa tai asenna JDK 17 ja Android SDK Platform 36 sekä Build Tools 35.0.0. Aseta SDK:n sijainti Android Studiossa tai ympäristömuuttujalla `ANDROID_HOME`. Aja tämän hakemiston juuressa:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windowsissa vastaava käynnistin on `gradlew.bat`. APK löytyy polusta `app/build/outputs/apk/debug/app-debug.apk`. Testipaketti käyttää debug-allekirjoitusta. Debuggerin liittäminen on poistettu käytöstä. Saman sovelluksen paikallaan päivitys edellyttää samaa allekirjoitusavainta. Lähdepakettiin ei sisälly allekirjoitusavainta.

Tämä on epävirallinen sovellus, eikä se ole eVaka-palvelun tai kuntien virallinen tuote.
