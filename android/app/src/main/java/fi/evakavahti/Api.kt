// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Espoo {
    const val origin = "https://espoonvarhaiskasvatus.fi"
    val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(25, TimeUnit.SECONDS).build()
}
class HttpFailure(val status: Int, val signingIn: Boolean = false): Exception()
fun problem(e: Exception): String = when (e) {
    is HttpFailure -> when {
        e.status == 429 -> "Palvelu rajoittaa pyyntöjä. Yritä myöhemmin."
        e.signingIn && e.status in 400..499 -> "Kirjautuminen epäonnistui. Tarkista eVakan kevyttunnukset."
        e.status == 401 -> "Istunto on vanhentunut. Kirjaudu uudelleen."
        e.status == 403 -> "eVaka ei myöntänyt käyttöoikeutta."
        else -> "eVaka ei voinut käsitellä pyyntöä (HTTP ${e.status})."
    }
    is IOException -> "Verkkoyhteys epäonnistui. Tarkista yhteys."
    else -> "Palvelun vastausta ei voitu käsitellä turvallisesti."
}
class Api(saved: List<String>, private val http: OkHttpClient = Espoo.http) {
    private val origin = Espoo.origin.toHttpUrl()
    private var session: Cookie? = saved.mapNotNull { Cookie.parse(origin,it) }.lastOrNull { valid(it) }
    private fun valid(c: Cookie) = c.name == "evaka.eugw.session" && c.secure && c.domain == origin.host
    fun cookies() = listOfNotNull(session?.toString())
    private fun request(path: String, body: RequestBody? = null): String {
        val url = (Espoo.origin + "/api/citizen/" + path).toHttpUrl()
        val builder = Request.Builder().url(url).header("x-evaka-csrf","1").header("Accept","application/json")
        session?.takeIf { it.matches(url) && it.expiresAt > System.currentTimeMillis() }?.let {
            builder.header("Cookie", "${it.name}=${it.value}")
        }
        if (body != null) builder.post(body)
        http.newCall(builder.build()).execute().use { res ->
            for (cookie in Cookie.parseAll(url, res.headers)) if (valid(cookie)) session = cookie
            if (path == "auth/logout" && res.code in setOf(200,302,303)) return ""
            if (!res.isSuccessful) throw HttpFailure(res.code, body != null)
            val source = requireNotNull(res.body).source()
            require(!source.request(8L * 1024 * 1024 + 1)) { "Liian suuri vastaus" }
            return source.readUtf8()
        }
    }
    fun logout() { request("auth/logout") }
    fun signIn(login: Login): String {
        val payload = wire.encodeToString(login).toRequestBody("application/json".toMediaType())
        request("auth/weak-login", payload)
        val status = wire.parseToJsonElement(request("auth/status")).jsonObject
        if (status["loggedIn"]?.jsonPrimitive?.boolean != true || session == null) throw HttpFailure(401)
        return status.getValue("user").jsonObject.getValue("details").jsonObject.getValue("id").jsonPrimitive.content
    }
    fun calendar(from: LocalDate, to: LocalDate): CalendarResponse {
        val data = wire.decodeFromString<CalendarResponse>(request("reservations?from=$from&to=$to"))
        val expected = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        require(data.days.map { LocalDate.parse(it.date) }.sorted() == expected)
        return data
    }
    fun inbox(old: Sample?, children: Set<String>): Observation {
        val id = old?.accountId ?: wire.parseToJsonElement(request("messages/my-account")).jsonObject.getValue("accountId").jsonPrimitive.content
        require(id.isNotBlank() && id != "null")
        val result = messages(old, id, children) { wire.decodeFromString<Page>(request("messages/received?page=$it")) }
        return result.copy(sample = result.sample.copy(accountId = id))
    }
}

/** Each check can renew a consented weak session once, for the same account only. */
class SessionRenewal(private val owner: String, private val credentials: Login?, private val renew: (Login) -> String) {
    private var attempted = false
    var blocked: HttpFailure? = null
        private set
    fun <T> read(request: () -> T): T {
        blocked?.let { throw it }
        try { return request() } catch (first: HttpFailure) {
            if (first.status != 401) throw first
            if (attempted || credentials == null) { blocked = first; throw first }
            attempted = true
            try {
                if (renew(credentials) != owner) throw HttpFailure(401)
                return request()
            } catch (second: HttpFailure) {
                if (second.signingIn || second.status == 401) blocked = second
                throw second
            }
        }
    }
}
