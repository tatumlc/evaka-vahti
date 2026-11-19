// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

private val teal = Color(0xff00695b)
private val mint = Color(0xffd0eee4)
private val paper = Color(0xfff6f9f6)
private val timeFormat = DateTimeFormatter.ofPattern("d.M.yyyy 'klo' HH.mm").withZone(helsinki)
private fun time(ms: Long) = timeFormat.format(Instant.ofEpochMilli(ms))
private enum class Screen(val title: String) {
    HOME("Etusivu"), WATCHES("Vahdit ja ilmoitukset"), SETTINGS("Asetukset"), HISTORY("Tapahtumahistoria"),
    ABOUT("Tietoja sovelluksesta"), LOGIN("Kirjautuminen"), CHILDREN("Seurattavat lapset")
}
class MainActivity: ComponentActivity() {
    private val historyIntent = mutableStateOf(false)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        historyIntent.value = intent.getBooleanExtra("history", false)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
        historyIntent.value = intent.getBooleanExtra("history", false)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = teal, primaryContainer = mint, background = paper,
                surface = paper, onSurface = Color(0xff172b26), onSurfaceVariant = Color(0xff42564f))) { ApplicationUi() }
        }
    }

    @Composable private fun ApplicationUi() {
        val app = application as VahtiApp
        val repo = app.repo
        val scope = rememberCoroutineScope()
        var error by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val flow = remember { repo.data.catch { error = "Paikallista salattua tallennusta ei voitu avata. Voit nollata sovelluksen Androidin asetuksista." } }
        val current by flow.collectAsState(initial = null)
        val run: (suspend () -> Unit) -> Unit = { action ->
            if (!busy) {
                busy = true
                scope.launch {
                    try { error = ""; action() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = problem(e) }
                    finally { busy = false }
                }
            }
        }
        LaunchedEffect(Unit) { try { repo.restore() } catch (_: Exception) { error = "Sovelluksen tietoja ei voitu avata." } }
        val s = current
        if (s == null) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { if (error.isEmpty()) CircularProgressIndicator() else Text(error) }
            return
        }
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var step by rememberSaveable { mutableIntStateOf(if (s.owner.isEmpty()) 1 else 3) }
        var confirm by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf("") }
        val wizard = !s.done
        LaunchedEffect(historyIntent.value,s.done) {
            if (historyIntent.value && s.done) { screen = Screen.HISTORY; historyIntent.value = false }
        }
        val back: ()->Unit = {
            error = ""; notice = ""
            if (wizard) step = (step-1).coerceAtLeast(1) else screen = when (screen) {
                Screen.WATCHES, Screen.LOGIN, Screen.CHILDREN, Screen.ABOUT -> Screen.SETTINGS
                else -> Screen.HOME
            }
        }
        BackHandler((wizard && step>1) || (!wizard && screen != Screen.HOME)) { if (!busy) back() }
        val manager = remember { Notices(this).manager }
        var permitted by remember { mutableStateOf(manager.areNotificationsEnabled()) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = manager.areNotificationsEnabled() }
        val request = {
            if (Build.VERSION.SDK_INT >= 33 && !manager.areNotificationsEnabled())
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        val go: (Screen) -> Unit = { error=""; notice=""; screen=it }
        Scaffold(containerColor = paper, bottomBar = {
            if (!wizard) NavigationBar {
                listOf(Triple(Screen.HOME,"⌂","Etusivu"),Triple(Screen.HISTORY,"≡","Historia"),Triple(Screen.SETTINGS,"⚙","Asetukset")).forEach { (tab,icon,label) ->
                    NavigationBarItem(selected = if (tab==Screen.SETTINGS) screen !in setOf(Screen.HOME,Screen.HISTORY) else screen==tab,
                        onClick = { if (!busy) go(tab) },icon = { Text(icon,fontSize=23.sp) },label = { Text(label) })
                }
            }
        }) { inset ->
            key(if (wizard) "step$step" else screen.name) {
                Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if ((wizard && step>1) || (!wizard && screen !in listOf(Screen.HOME,Screen.HISTORY,Screen.SETTINGS)))
                        TextButton(onClick=back,enabled=!busy) { Text("‹  Takaisin") }
                    Text(if (wizard) "KÄYTTÖÖNOTTO   $step / 5" else if (s.owner==Demo.owner) "eVaka-vahti  ·  Paikallinen testitila" else "eVaka-vahti  ·  Espoo",
                        style=MaterialTheme.typography.labelLarge,color=teal)
                    if (wizard) LinearProgressIndicator(progress={ step/5f },modifier=Modifier.fillMaxWidth())
                    val title = if (wizard) listOf("Valitse kunta","Kirjaudu eVakaan","Valitse lapset","Valitse vahdit","Ilmoitukset ja vahvistus")[step-1] else screen.title
                    Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
                    if (notice.isNotEmpty()) Text(notice,color=teal)
                    val toggle: (Watch,Boolean)->Unit = { w,on -> run { repo.change(refresh=on) { it.toggle(w,on) } } }
                    val choose: (String,Boolean)->Unit = { id,on -> run { repo.change(refresh=true,clearNotifications=true) { it.select(if(on) it.selected+id else it.selected-id) } } }
                    val login: (Login?,Boolean)->Unit = { cred,remember ->
                        run { repo.signIn(cred,remember); step=3; screen=Screen.SETTINGS }
                    }
                    val manually = { run { repo.manual(); notice = "Tarkistus pyydetty. Android suorittaa sen, kun ehdot täyttyvät." } }
                    if (wizard) when (step) {
                        1 -> {
                            Panel("Espoo", "espoonvarhaiskasvatus.fi\nEnsimmäinen testikunta. Tilikohtainen toimivuus on vielä varmistettava.")
                            Button(onClick={ step=2 }) { Text("Valitse Espoo") }
                        }
                        2 -> LoginPanel(busy,login)
                        3 -> {
                            Children(s,choose)
                            if (s.childrenError.isNotEmpty()) OutlinedButton(enabled=!busy,onClick={ run { repo.refreshChildren() } }) { Text("Yritä lapsihakua uudelleen") }
                            Button(onClick={ step=4 },enabled=!busy) { Text("Jatka vahtien valintaan") }
                        }
                        4 -> {
                            Text("Valitse haluamasi vahdit. Ensimmäinen tarkistus tallentaa lähtötilanteen hiljaisesti.")
                            WatchList(s,busy,toggle)
                            Button(onClick={ step=5 },enabled=!busy) { Text("Jatka ilmoituksiin") }
                        }
                        5 -> {
                            Panel("Valitut vahdit",s.watches.keys.joinToString("\n") { it.label }.ifBlank { "Kaikki vahdit ovat pois päältä." })
                            Text("Androidin virransäästö voi viivästyttää tarkistuksia. Taustaseuranta ei ole reaaliaikaista.")
                            Text(if (permitted) "Ilmoitukset sallittu." else "Ilmoitukset eivät ole käytössä. Tapahtumat näkyvät historiassa.")
                            OutlinedButton(onClick=request) { Text(if (permitted) "Ilmoitusasetukset" else "Salli ilmoitukset") }
                            Button(onClick={ run { repo.change(refresh=true) { it.copy(done=true) }; screen=Screen.HOME } },enabled=!busy) { Text("Viimeistele käyttöönotto") }
                        }
                    } else when (screen) {
                        Screen.HOME -> {
                            if (s.needsLogin) { Panel("Kirjaudu uudelleen","Taustatarkistukset odottavat kirjautumista."); Button(onClick={ go(Screen.LOGIN) }) { Text("Kirjaudu") } }
                            Text("${s.watches.size} / 4 vahtia käytössä",color=teal,fontWeight=FontWeight.SemiBold)
                            WatchList(s,busy,null)
                            Button(onClick=manually,enabled=s.watches.isNotEmpty() && !busy && !s.needsLogin) { Text("Tarkista nyt") }
                            OutlinedButton(onClick={ go(Screen.WATCHES) }) { Text("Vahdit ja ilmoitukset") }
                            if (s.owner == Demo.owner) OutlinedButton(enabled=!busy && s.watches.isNotEmpty(),onClick={
                                run { repo.check(); repo.change { it.copy(demoStep=(it.demoStep+1).coerceAtMost(200)) }; repo.check(); notice="Testitiedot päivitetty." }
                            }) { Text("Luo testimuutos") }
                        }
                        Screen.WATCHES -> {
                            WatchList(s,busy,toggle)
                            Button(onClick=manually,enabled=s.watches.isNotEmpty() && !busy && !s.needsLogin) { Text("Tarkista nyt") }
                            Choice("Näytä ilmoituksissa yksityiskohdat","Sallii ajat, nimet ja otsikot avatulla puhelimella. Ilmoitukset piilotetaan lukitusnäytöltä.",s.detailed,!busy) {
                                run { repo.change(clearNotifications=true) { st -> st.copy(detailed=it) } }
                            }
                            OutlinedButton(onClick={ startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) }) { Text("Androidin ilmoituskanavat") }
                        }
                        Screen.SETTINGS -> {
                            Panel("Kunta",if (s.owner == Demo.owner) "Paikallinen testitila · keinotekoiset tiedot" else "Espoo · testikäytössä")
                            LinkRow("Kirjautuminen") { go(Screen.LOGIN) }
                            LinkRow("Seurattavat lapset (${s.selected.size})") { go(Screen.CHILDREN) }
                            LinkRow("Vahdit ja ilmoitukset") { go(Screen.WATCHES) }
                            Text("Tarkistusväli",fontWeight=FontWeight.Bold)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(15L,30L,60L).forEach {
                                FilterChip(selected=s.minutes==it,enabled=!busy,onClick={ run { repo.change { st -> st.copy(minutes=it) } } },label={ Text("$it min") })
                            } }
                            Text("Tarkistusväli on tavoite. Android voi lykätä työtä virransäästön tai verkkoyhteyden vuoksi.")
                            LinkRow("Tietoja sovelluksesta") { go(Screen.ABOUT) }
                            Text("Asetukset, istunto ja enintään 100 viimeisintä tapahtumaa säilyvät salattuina tällä laitteella.")
                            OutlinedButton(onClick={ confirm="all" }) { Text("Kirjaudu ulos ja poista paikalliset tiedot") }
                        }
                        Screen.LOGIN -> LoginPanel(busy,login, demo=s.owner==Demo.owner)
                        Screen.CHILDREN -> {
                            Children(s,choose)
                            OutlinedButton(enabled=!busy,onClick={ run { repo.refreshChildren(); notice="Lapsiluettelo päivitetty." } }) { Text("Päivitä lapsiluettelo") }
                        }
                        Screen.HISTORY -> {
                            if (s.history.isEmpty()) Panel("Ei vielä tapahtumia","Uudet muutokset näkyvät ensimmäisen onnistuneen tarkistuksen jälkeen.")
                            else {
                                OutlinedButton(onClick={ confirm="history" }) { Text("Tyhjennä historia") }
                                s.history.forEach { Panel(it.notice.title,time(it.at)+"\n"+it.notice.detail) }
                            }
                        }
                        Screen.ABOUT -> {
                            Panel("eVaka-vahti","Versio ${BuildConfig.VERSION_NAME}\nKehittäjä: Tatu Satoniitty\nYhteystiedot: tatu.satoniitty@gmail.com\nLähdekoodi: https://github.com/tatumlc/evaka-vahti")
                            Text("Tämä on epävirallinen sovellus, eikä se ole eVaka-palvelun tai kuntien virallinen tuote.")
                            Text("MIT-lisenssi · © 2026 Tatu Satoniitty")
                            Text(remember { assets.open("LICENSE-VAHTI.txt").bufferedReader().use { it.readText() } },style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (confirm.isNotEmpty()) AlertDialog(onDismissRequest={ confirm="" },title={ Text(if (confirm=="all") "Poistetaanko kaikki paikalliset tiedot?" else "Tyhjennetäänkö historia?") },
            text={ Text(if(confirm=="all") "Tunnukset, istunto, lapsivalinnat, vahdit ja tapahtumat poistetaan tästä sovelluksesta." else "Tapahtumat ja näkyvät ilmoitukset poistetaan. Vahtien lähtötilanne säilyy.") },
            dismissButton={ TextButton(onClick={ confirm="" }) { Text("Peruuta") } },confirmButton={ TextButton(onClick={
                val all=confirm=="all"; confirm=""; run { if (all) {
                        val ended = repo.erase()
                        step=1; screen=Screen.HOME
                        notice=if (ended) "" else "Paikalliset tiedot poistettiin. Palvelinistunnon sulkemista ei voitu vahvistaa."
                    } else repo.change(clearNotifications=true) { it.copy(history=emptyList()) } }
            }) { Text("Poista") } })
    }
}

@Composable private fun LoginPanel(busy: Boolean, login: (Login?,Boolean)->Unit, demo: Boolean = true) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var rememberLogin by remember { mutableStateOf(false) }
    Text("Käytä Espoon eVakan kevyttunnusta ja salasanaa. Luo tunnus tarvittaessa kunnan omassa palvelussa vahvasti tunnistautuneena. Syötä pankkitunnukset vain viralliseen tunnistuspalveluun.")
    OutlinedTextField(user,{ user=it.take(128) },label={ Text("eVakan sähköposti") },singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
    OutlinedTextField(pass,{ pass=it.take(128) },label={ Text("eVakan salasana") },singleLine=true,visualTransformation=PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
    Choice("Salli kirjautuminen taustalla","Tallenna kevyttunnus ja salasana salattuina, jotta vahti voi uusia vanhentuneen istunnon.",rememberLogin,!busy) { rememberLogin=it }
    Button(onClick={ val credentials=Login(user.trim(),pass); pass=""; login(credentials,rememberLogin) },enabled=!busy && user.isNotBlank() && pass.isNotBlank()) { Text("Kirjaudu Espoon eVakaan") }
    if (demo) OutlinedButton(onClick={ login(null,false) },enabled=!busy) { Text("Kokeile paikallisilla testitiedoilla") }
}

@Composable private fun Children(s: State, change: (String,Boolean)->Unit) {
    Text("Valitse kalenterissa seurattavat lapset. Viestivahti seuraa myös tilin yhteisiä viestejä.")
    if (s.childrenError.isNotEmpty()) Text("Lapsiluettelon haku epäonnistui. ${s.childrenError} Voit jatkaa tilin yhteisten viestien seurantaan.")
    else if (s.children.isEmpty()) Text("Tilillä ei näy valittavia lapsia.")
    for (c in s.children) Choice(c.label,"",c.id in s.selected,true) { change(c.id,it) }
}
@Composable private fun WatchList(s: State, busy: Boolean, toggle: ((Watch,Boolean)->Unit)?) {
    for (w in Watch.entries) {
        val m=s.watches[w]
        val status=when {
            m==null -> "Pois päältä"
            s.needsLogin -> "Vaatii kirjautumisen"
            m.checked==0L -> "Odottaa lähtötilannetta"
            else -> "Käytössä"
        }
        val description = buildList {
            if (toggle != null) add(w.hint)
            add(status)
            val checked = m?.checked?.takeIf { it > 0 } ?: s.lastSuccessful[w]
            add(if (checked == null) "Ei onnistunutta tarkistusta" else "Tarkistettu ${time(checked)}")
            if (m?.error?.isNotEmpty()==true) add(m.error)
            if (w==Watch.ATTEND && toggle!=null) add("Kirjaukset voivat tulla jälkikäteen. Lapsen sijaintia ei seurata.")
        }.joinToString("\n")
        if (toggle==null) Panel(w.label,description) else Choice(w.label,description,m!=null,!busy) { toggle(w,it) }
    }
}
@Composable private fun Panel(title: String, body: String) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(body,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun Choice(title: String, body: String, checked: Boolean, enabled: Boolean, update: (Boolean)->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Row(Modifier.fillMaxWidth().toggleable(checked,enabled,role=Role.Switch,onValueChange=update).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold); if(body.isNotEmpty()) Text(body,style=MaterialTheme.typography.bodyMedium) }
            Switch(checked,onCheckedChange=null,enabled=enabled)
        }
    }
}
@Composable private fun LinkRow(label: String, action: ()->Unit) {
    OutlinedButton(onClick=action,modifier=Modifier.fillMaxWidth()) { Text(label,modifier=Modifier.weight(1f)); Text("›") }
}
