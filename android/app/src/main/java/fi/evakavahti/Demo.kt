// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import java.time.LocalDate
import java.time.Instant

object Demo {
    const val owner = "local-demo"
    val children = listOf(Child("demo-a","Testilapsi A"), Child("demo-b","Testilapsi B"))
    fun calendar(today: LocalDate, step: Int): CalendarResponse {
        val days = (0L..2L).map { offset ->
            Day(today.plusDays(offset).toString(), children.map { child ->
                val periods = when (offset) {
                    0L -> listOf(Times(if (step % 2 == 0) "08:00" else "08:30","15:00"))
                    1L -> listOf(Times(if (step % 2 == 0) "20:00" else "21:00","23:59"))
                    else -> listOf(Times("00:00","07:00"))
                }
                val visits = if (offset == 0L && step > 0) listOf(Attendance("08:10", if (step >= 2) "15:10" else null)) else emptyList()
                ChildDay(child.id, periods.map { Reservation("TIMES",it) }, visits)
            })
        }
        return CalendarResponse(children, days)
    }
    fun inbox(old: Sample?, selected: Set<String>, step: Int): Observation {
        val items = (0..step.coerceAtMost(200)).map { i ->
            Message("demo-$i", if (i == 1) Sender("me","Testihuoltaja") else Sender("other","Testiryhmä"),
                Instant.ofEpochSecond(1800000000L+i).toString())
        }
        return messages(old,"me",selected) { Page(1,listOf(ThreadItem("thread","Regular","Testitiedote",messages=items))) }
    }
}
