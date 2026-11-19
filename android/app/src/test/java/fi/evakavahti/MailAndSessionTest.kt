// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MailAndSessionTest {
    private val myId="self"
    private val own=Sender(myId,"Testihuoltaja")
    private val other=Sender("two","Testiryhmä")
    private val noMail=Sample()
    private fun msg(id: String, sender: Sender, minute: Int)=Message(id,sender,"2026-10-01T10:${minute.toString().padStart(2,'0')}:00+03:00")
    private fun thread(vararg m: Message)=ThreadItem("t","Regular","Testiotsikko",messages=m.toList())
    private fun scan(old: Sample?,vararg threads: ThreadItem)=messages(old,myId,emptySet()) { Page(1,threads.toList()) }
    @Test fun newMessageAndReplyToOwnEvenInExistingThread() {
        val old=msg("1",other,0)
        val ownMessage=msg("2",own,1)
        val response=msg("3",other,2)
        val first=scan(noMail,thread(old))
        assertEquals("UUSI VIESTI",first.notices.single().title)
        val after=scan(first.sample,thread(old,ownMessage,response))
        assertEquals("VASTAUS OMAAN VIESTIIN",after.notices.single().title)
        assertTrue(after.notices.single().detail.contains("Lähettäjä: Testiryhmä"))
        assertTrue(after.notices.single().reply)
        assertTrue(scan(after.sample,thread(old,ownMessage,response)).notices.isEmpty())
    }
    @Test fun replyToUserInitiatedThreadAndOwnPostsSilent() {
        val initial=scan(noMail,thread(msg("a",own,0)))
        assertTrue(initial.notices.isEmpty())
        assertTrue(scan(initial.sample,thread(msg("a",own,0),msg("b",other,2))).notices.single().reply)
    }
    @Test fun redactedDoesNotTreatEveryTimestampChangeAsIncoming() {
        val t=ThreadItem("r","Redacted",sender=other,lastMessageSentAt=msg("a",other,0).sentAt,hasUnreadMessages=true)
        val initial=scan(null,t)
        assertTrue(initial.notices.isEmpty())
        assertTrue(scan(initial.sample,t.copy(lastMessageSentAt=msg("x",own,3).sentAt)).notices.isEmpty())
        assertTrue(scan(noMail,t).notices.single().detail.startsWith("Suojattu"))
        assertTrue(scan(noMail,t.copy(sender=own)).notices.isEmpty())
    }
    @Test fun hiddenTitleOrSenderProducesNeutralMessage() {
        val n=scan(noMail,thread(msg("a",other,1)).copy(title=null)).notices.single()
        assertEquals("Avaa viesti eVakassa.",n.detail)
    }
    @Test fun equalTimestampsAcrossPagesAndRepeat() {
        val groups=listOf(thread(msg("a",other,3)),thread(msg("b",other,3)))
        val initial=scan(null,groups.first())
        val result=messages(initial.sample,myId,emptySet()) { Page(2,listOf(groups[it-1])) }
        assertEquals(1,result.notices.size)
        assertTrue(scan(result.sample,*groups.toTypedArray()).notices.isEmpty())
    }
    @Test fun pageFailureDoesNotReturnPartialBaseline() {
        val initial=scan(null,thread(msg("a",other,1)))
        assertThrows(IOException::class.java) { messages(initial.sample,myId,emptySet()) {
            if (it==2) throw IOException()
            Page(2,listOf(thread(msg("b",other,2))))
        } }
        assertEquals(1,scan(initial.sample,thread(msg("b",other,2))).notices.size)
    }
    @Test fun validRenewalOnceAndSameOwner() {
        var renewals=0
        val s=SessionRenewal("account",Login("test.invalid","fiction")) { renewals++; "account" }
        var calls=0
        assertEquals("ok",s.read { if (++calls==1) throw HttpFailure(401); "ok" })
        assertEquals(1,renewals)
        assertThrows(HttpFailure::class.java) { s.read { throw HttpFailure(401) } }
        assertNotNull(s.blocked)
        assertEquals(1,renewals)
    }
    @Test fun noConsentWrongOwnerAndInvalidPasswordStopRequests() {
        for (s in listOf(SessionRenewal("a",null) { error("unexpected") },
            SessionRenewal("a",Login("test","fiction")) { "b" },
            SessionRenewal("a",Login("test","fiction")) { throw HttpFailure(403,true) })) {
            assertThrows(HttpFailure::class.java) { s.read { throw HttpFailure(401) } }
            assertThrows(HttpFailure::class.java) { s.read { error("unexpected retry") } }
            assertNotNull(s.blocked)
        }
    }
    @Test fun networkAndAccessFailuresNeverAttemptLogin() {
        val s=SessionRenewal("a",Login("test","fiction")) { error("unexpected") }
        assertThrows(IOException::class.java) { s.read { throw IOException("sensitive") } }
        assertThrows(HttpFailure::class.java) { s.read { throw HttpFailure(403) } }
        assertNull(s.blocked)
        assertFalse(problem(IOException("secret")).contains("secret"))
    }
}
