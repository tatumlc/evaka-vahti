// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class WatchTest {
    private val date = LocalDate.parse("2026-11-19")
    private val a = Child("a","Testilapsi")
    private val b = Child("b","Toinen")
    private fun state(vararg w: Watch) = w.fold(State(selected=setOf(a.id))) { s, guard -> s.toggle(guard,true) }
    private fun day(offset: Long, vararg times: Pair<String,String>, visits: List<Attendance> = emptyList(), id: String = a.id) =
        Day(date.plusDays(offset).toString(),listOf(ChildDay(id,times.map { Reservation("TIMES", Times(it.first,it.second)) },visits)))
    private fun cal(vararg days: Day) = CalendarResponse(listOf(a,b),days.toList())
    private fun State.observe(w: Watch,c: CalendarResponse) = calendarObservation(w,this,c,date)
    private fun State.saved(w: Watch,c: CalendarResponse): State {
        val monitor = watches.getValue(w)
        return copy(watches=watches + (w to monitor.copy(sample=observe(w,c).sample)))
    }
    private val empty = cal(day(0))
    private val regular = cal(day(0,"08:00" to "16:00"))
    private val night = cal(day(0,"20:00" to "23:59"),day(1,"00:00" to "07:00"))
    @Test fun addsChangesRemovesCare() {
        val w=Watch.CARE
        val s=state(w).saved(w,empty)
        assertTrue(s.observe(w,regular).notices.single().title.contains("lisätty"))
        val base=s.saved(w,regular)
        val change=base.observe(w,cal(day(0,"08:00" to "15:30"))).notices.single()
        assertTrue(change.detail.contains("16:00") && change.detail.contains("15:30"))
        assertTrue(base.observe(w,empty).notices.single().title.contains("poistettu"))
    }
    @Test fun secondBookingOnSameDayIsAnAdditionAndRemoval() {
        val w=Watch.CARE
        val first=cal(day(0,"08:00" to "12:00"))
        val two=cal(day(0,"08:00" to "12:00","16:00" to "18:00"))
        val s=state(w).saved(w,first)
        val added=s.observe(w,two).notices.single()
        assertTrue(added.title.endsWith("lisätty") && !added.detail.contains("08:00"))
        val removed=s.saved(w,two).observe(w,first).notices.single()
        assertTrue(removed.title.endsWith("poistettu") && removed.detail.contains("16:00"))
    }
    @Test fun nightWorksAloneAndShowsBothDates() {
        val w=Watch.NIGHT
        val s=state(w).saved(w,empty)
        val new=s.observe(w,night).notices.single()
        assertTrue(new.detail.contains("19.11.2026 klo 20:00") && new.detail.contains("20.11.2026 klo 07:00"))
        val base=s.saved(w,night)
        assertTrue(base.observe(w,empty).notices.single().title.contains("poistettu"))
        assertTrue(base.observe(w,cal(day(0,"19:00" to "23:59"),day(1,"00:00" to "07:00"))).notices.single().title.contains("muuttunut"))
    }
    @Test fun chainsMidnightAndFullDayAndNextMorning() {
        val w=Watch.NIGHT
        val result=state(w).saved(w,empty).observe(w,cal(day(0,"20:00" to "00:00"),day(1,"00:00" to "00:00"),day(2,"00:00" to "09:00"))).notices
        assertTrue(result.single().detail.contains("21.11.2026 klo 09:00"))
    }
    @Test fun noNightAcrossDifferentChildrenOrGap() {
        val w=Watch.NIGHT
        val s=state(w).copy(selected=setOf("a","b")).saved(w,empty)
        assertTrue(s.observe(w,cal(day(0,"22:00" to "23:59"),day(1,"00:00" to "05:00",id="b"),day(2,"00:00" to "06:00"))).notices.isEmpty())
    }
    @Test fun separateNightSuppressesOnlyNightDuplicate() {
        val s=state(Watch.NIGHT,Watch.CARE).saved(Watch.CARE,empty).saved(Watch.NIGHT,empty)
        val mixed=cal(day(0,"08:00" to "10:00","20:00" to "23:59"),day(1,"00:00" to "07:00"))
        assertEquals(1,s.observe(Watch.CARE,mixed).notices.size)
        assertEquals(1,s.observe(Watch.NIGHT,mixed).notices.size)
        assertEquals(1,s.toggle(Watch.NIGHT,false).observe(Watch.CARE,night).notices.size)
    }
    @Test fun attendanceIncludesBothEndsEvenIfEnteredLate() {
        val w=Watch.ATTEND
        val past=cal(day(-3,visits=listOf(Attendance("09:10:00","16:20:00"))))
        val entries=state(w).saved(w,empty).observe(w,past).notices
        assertEquals(listOf("Saapuminen kirjattu","Lähtö kirjattu"),entries.map { it.title })
        assertTrue(entries.last().detail.contains("16.11.2026 klo 16:20"))
    }
    @Test fun closingAnOpenVisitOnlyProducesDeparture() {
        val w=Watch.ATTEND
        val s=state(w).saved(w,cal(day(0,visits=listOf(Attendance("08:00",null)))))
        val entry=s.observe(w,cal(day(0,visits=listOf(Attendance("08:00","00:00"))))).notices.single()
        assertTrue(entry.detail.contains("20.11.2026 klo 00:00"))
    }
    @Test fun attendanceTemporarilyMissingDoesNotReplay() {
        val w=Watch.ATTEND
        val visit=cal(day(0,visits=listOf(Attendance("08:00","15:00"))))
        val s=state(w).saved(w,visit).saved(w,empty)
        assertTrue(s.observe(w,visit).notices.isEmpty())
    }
    @Test fun everyToggleCombinationSurvivesSerialization() {
        for (mask in 0..15) {
            val selected=Watch.entries.filterIndexed { i,_ -> mask and (1 shl i) != 0 }
            val expected=state(*selected.toTypedArray())
            val restored=wire.decodeFromString<State>(wire.encodeToString(expected))
            assertEquals(expected,restored)
            assertEquals(selected.toSet(),restored.watches.keys)
            assertTrue(selected.fold(restored) { s,w -> s.toggle(w,false) }.watches.isEmpty())
        }
    }
    @Test fun enableReenableAndProcessRestartDoNotReplay() {
        val w=Watch.CARE
        val s=state(w)
        assertTrue(s.observe(w,regular).notices.isEmpty())
        val saved=s.saved(w,regular)
        assertTrue(wire.decodeFromString<State>(wire.encodeToString(saved)).observe(w,regular).notices.isEmpty())
        val resumed=saved.toggle(w,false).toggle(w,true)
        assertNotEquals(s.watches[w]?.generation,resumed.watches[w]?.generation)
        assertTrue(resumed.observe(w,night).notices.isEmpty())
    }
    @Test fun disablingRetainsLastSuccessButDropsSensitiveBaseline() {
        val w=Watch.NIGHT
        val s=state(w).copy(watches=mapOf(w to Monitor(sample=Sample(mapOf("k" to "v")), checked=42)))
        val off=s.toggle(w,false)
        assertEquals(42L,off.lastSuccessful[w])
        assertFalse(off.watches.containsKey(w))
        assertNull(off.toggle(w,true).watches.getValue(w).sample)
    }
    @Test fun outsideOverlappingCalendarWindowIsNotAChange() {
        val w=Watch.CARE
        val s=state(w).saved(w,cal(day(-1,"08:00" to "12:00")))
        assertTrue(calendarObservation(w,s,cal(day(31,"08:00" to "12:00")),date.plusDays(1)).notices.isEmpty())
    }
    @Test fun unselectedChildExcludedAndSelectionChangeRebaselines() {
        val w=Watch.CARE
        val s=state(w).saved(w,empty)
        val other=cal(day(0,"09:00" to "16:00",id="b"))
        assertTrue(s.observe(w,other).notices.isEmpty())
        assertTrue(s.select(setOf("b")).observe(w,other).notices.isEmpty())
    }
    @Test fun unknownServerShapeFailsInsteadOfReportingDeletions() {
        assertThrows(Exception::class.java) { wire.decodeFromString<CalendarResponse>("{}") }
        val broken=cal(Day(date.toString(),listOf(ChildDay("a",listOf(Reservation("unexpected")),emptyList()))))
        assertThrows(Exception::class.java) { state(Watch.CARE).saved(Watch.CARE,regular).observe(Watch.CARE,broken) }
    }
}
