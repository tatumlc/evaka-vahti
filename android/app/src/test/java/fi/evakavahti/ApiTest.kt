// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ApiTest {
    private fun client(vararg handlers: (Request) -> Response): Api {
        val sequence=handlers.iterator()
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request()
            assertEquals("espoonvarhaiskasvatus.fi",request.url.host)
            assertEquals("https",request.url.scheme)
            assertEquals("1",request.header("x-evaka-csrf"))
            sequence.next()(request)
        }.build()
        return Api(emptyList(),http)
    }
    private fun response(r: Request,code: Int,body: String, vararg cookie: String): Response {
        val b=Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody())
        cookie.forEach { b.addHeader("Set-Cookie",it) }
        return b.build()
    }
    @Test fun weakLoginPersistsOnlySecureHostSessionAndVerifiesOwner() {
        val api=client({ r ->
            assertEquals("POST",r.method)
            assertEquals("/api/citizen/auth/weak-login",r.url.encodedPath)
            response(r,200,"OK","evaka.eugw.session=fiction; Path=/; Secure; HttpOnly","extra=unused; Path=/; Secure")
        }, { r ->
            assertEquals("/api/citizen/auth/status",r.url.encodedPath)
            assertEquals("evaka.eugw.session=fiction",r.header("Cookie"))
            response(r,200,"""{"loggedIn":true,"user":{"details":{"id":"test-owner"}}}""")
        })
        assertEquals("test-owner",api.signIn(Login("test","sample")))
        assertEquals(1,api.cookies().size)
    }
    @Test fun invalidCredentialsAndRateLimitExposeNoResponseBody() {
        for (code in listOf(403,429)) {
            val api=client({ r -> response(r,code,"personal response details") })
            val e=assertThrows(HttpFailure::class.java) { api.signIn(Login("test","sample")) }
            assertEquals(code,e.status)
            assertFalse(problem(e).contains("personal"))
        }
    }
    @Test fun deniedOrExpiredCalendarNeverLooksLikeEmptyBookings() {
        val d=LocalDate.parse("2026-05-04")
        for (code in listOf(401,403,503)) {
            val api=client({ r -> response(r,code,"{}") })
            assertEquals(code,assertThrows(HttpFailure::class.java) { api.calendar(d,d) }.status)
        }
        val api=client({ throw IOException("fake offline") })
        assertThrows(IOException::class.java) { api.calendar(d,d) }
    }
    @Test fun incompleteCalendarRejected() {
        val d=LocalDate.parse("2026-05-04")
        val api=client({ r -> response(r,200,"""{"children":[],"days":[]}""") })
        assertThrows(IllegalArgumentException::class.java) { api.calendar(d,d) }
    }
    @Test fun actualWireFieldsAndUnknownPersonalFieldsIgnored() {
        val d=LocalDate.parse("2026-05-04")
        val api=client({ r ->
            assertEquals("2026-05-04",r.url.queryParameter("from"))
            assertEquals("/api/citizen/reservations",r.url.encodedPath)
            response(r,200,"""{"children":[{"id":"demo","firstName":"Testi","preferredName":"","lastName":"unused"}],"days":[{"date":"2026-05-04","children":[{"childId":"demo","reservations":[{"type":"NO_TIMES"}],"attendances":[{"start":"08:00:00","end":null}]}]}]}""")
        })
        val c=api.calendar(d,d)
        assertEquals("Testi",c.children.single().label)
        assertNull(c.days.single().children.single().attendances.single().end)
        assertFalse(wire.encodeToString(c).contains("unused"))
    }
}
