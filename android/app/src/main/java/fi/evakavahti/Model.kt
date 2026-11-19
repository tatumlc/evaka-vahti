// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val wire = Json { ignoreUnknownKeys = true }
val helsinki: ZoneId = ZoneId.of("Europe/Helsinki")
val fiDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

@Serializable enum class Watch(val label: String, val hint: String) {
    CARE("Hoitoaikavahti", "Uudet, muuttuneet ja poistetut varaukset."),
    NIGHT("Yöhoitovahti", "Yön yli jatkuvat varaukset ja niiden muutokset."),
    ATTEND("Läsnäolovahti", "eVakaan kirjatut saapumiset ja lähdöt."),
    MAIL("Viestivahti", "Saapuneet viestit ja vastaukset omiin viesteihin.")
}
@Serializable data class Child(val id: String, val firstName: String, val preferredName: String = "") {
    val label get() = preferredName.ifBlank { firstName }
}
@Serializable data class Login(val username: String, val password: String)
@Serializable data class Notice(val watch: Watch, val title: String, val detail: String, val reply: Boolean = false)
@Serializable data class Entry(val at: Long, val notice: Notice)
@Serializable data class Sample(val values: Map<String,String> = emptyMap(), val from: String = "", val to: String = "", val cursor: Long = 0, val accountId: String? = null)
@Serializable data class Monitor(val generation: String = java.util.UUID.randomUUID().toString(), val sample: Sample? = null, val checked: Long = 0, val error: String = "")
@Serializable data class State(
    val demoStep: Int = 0, val done: Boolean = false, val owner: String = "", val cookies: List<String> = emptyList(),
    val login: Login? = null, val needsLogin: Boolean = false,
    val children: List<Child> = emptyList(), val childrenError: String = "", val selected: Set<String> = emptySet(),
    val watches: Map<Watch,Monitor> = emptyMap(), val lastSuccessful: Map<Watch,Long> = emptyMap(), val history: List<Entry> = emptyList(),
    val detailed: Boolean = false, val minutes: Long = 30
) {
    fun toggle(w: Watch, on: Boolean): State {
        if (on == (w in watches)) return this
        val last = watches[w]?.checked?.takeIf { it > 0 }
        return copy(watches = if (on) watches + (w to Monitor()) else watches - w,
            lastSuccessful = if (last != null) lastSuccessful + (w to last) else lastSuccessful)
    }
    fun select(ids: Set<String>) = if (selected == ids) this else copy(selected = ids,
        watches = watches.mapValues { Monitor() }, lastSuccessful = emptyMap(), history = emptyList())
}
@Serializable data class CalendarResponse(val children: List<Child>, val days: List<Day>)
@Serializable data class Day(val date: String, val children: List<ChildDay>)
@Serializable data class ChildDay(val childId: String, val reservations: List<Reservation>, val attendances: List<Attendance>)
@Serializable data class Reservation(val type: String, val range: Times? = null)
@Serializable data class Times(val start: String, val end: String)
@Serializable data class Attendance(val start: String, val end: String?)
@Serializable data class Sender(val id: String, val name: String? = null)
@Serializable data class Message(val id: String, val sender: Sender, val sentAt: String)
@Serializable data class MessageChild(val childId: String)
@Serializable data class ThreadItem(val id: String, val type: String, val title: String? = null,
    val children: List<MessageChild> = emptyList(), val messages: List<Message>? = null,
    val sender: Sender? = null, val lastMessageSentAt: String? = null, val hasUnreadMessages: Boolean = false)
@Serializable data class Page(val pages: Int, val data: List<ThreadItem>)
data class Observation(val sample: Sample, val notices: List<Notice>)

private fun clock(s: String) = LocalTime.parse(s).format(DateTimeFormatter.ofPattern("HH:mm"))
private data class Span(val day: LocalDate, val start: String, val until: LocalDate, val end: String) {
    val night get() = until > day
    fun display() = when {
        start.isEmpty() -> "Ilman kellonaikaa"
        night -> "${day.format(fiDate)} klo $start – ${until.format(fiDate)} klo $end"
        else -> "$start–$end"
    }
}

private fun reservations(days: List<Pair<LocalDate,ChildDay>>): List<Span> {
    val spans = days.flatMap { (day, child) -> child.reservations.map {
        when (it.type) {
            "NO_TIMES" -> Span(day, "", day, "")
            "TIMES" -> {
                val r = requireNotNull(it.range)
                val a = clock(r.start)
                val b = clock(r.end)
                Span(day, a, if (b == "00:00" || b < a) day.plusDays(1) else day, b)
            }
            else -> error("Varaustyyppi")
        }
    } }.distinct().sortedWith(compareBy({ it.day }, { it.start }))
    val result = mutableListOf<Span>()
    for (span in spans) {
        val last = result.lastOrNull()
        val joinsAtMidnight = last != null && span.start == "00:00" && (
            (last.end == "23:59" && last.until.plusDays(1) == span.day) ||
            (last.end == "00:00" && last.until == span.day))
        if (last != null && joinsAtMidnight) {
            result[result.lastIndex] = last.copy(until = span.until, end = span.end)
        } else result += span
    }
    return result
}

fun calendarObservation(w: Watch, state: State, response: CalendarResponse, today: LocalDate): Observation {
    val rows = mutableMapOf<String,String>()
    val from = if (w == Watch.ATTEND) today.minusDays(7) else today.minusDays(1)
    val to = if (w == Watch.ATTEND) today else today.plusDays(30)
    val authorized = response.children.map { it.id }.toSet()
    val allowed = state.selected.intersect(authorized)
    for (child in allowed) {
        val days = response.days.flatMap { day -> day.children.filter { it.childId == child }.map { LocalDate.parse(day.date) to it } }
        if (w == Watch.ATTEND) {
            for ((d, c) in days.filter { it.first in from..to }) for (a in c.attendances) {
                val arrival = clock(a.start)
                rows["$child|$d|in|$arrival"] = arrival
                if (a.end != null) rows["$child|$d|out|$arrival"] = clock(a.end)
            }
        } else {
            reservations(days).filter { it.day in from..to && (w != Watch.NIGHT || it.night) }
                .groupBy { "$child|${it.day}|${if (it.night) "night" else "day"}" }
                .forEach { (k, v) -> rows[k] = v.joinToString(", ") { it.display() } }
        }
    }
    val previous = state.watches[w]?.sample
    // A recorded visit can disappear temporarily during corrections. Remember seen
    // arrivals and departures only inside the authorized, bounded attendance window.
    if (w == Watch.ATTEND && previous != null) {
        for ((key, value) in previous.values) {
            val (child, date) = key.split('|')
            if (child in allowed && date >= from.toString() && date <= to.toString()) rows.putIfAbsent(key, value)
        }
    }
    val sample = Sample(rows, from.toString(), to.toString())
    val old = previous ?: return Observation(sample, emptyList())
    val notices = mutableListOf<Notice>()
    for (key in (rows.keys + old.values.keys).sorted()) {
        val (id, date, kind) = key.split('|')
        if (id !in allowed || date < sample.from || date > sample.to || date < old.from || date > old.to) continue
        if (w == Watch.CARE && kind == "night" && Watch.NIGHT in state.watches) continue
        val before = old.values[key]
        val after = rows[key]
        if (before == after) continue
        val recordDay = LocalDate.parse(date)
        val dateText = (if (kind == "out" && after == "00:00") recordDay.plusDays(1) else recordDay).format(fiDate)
        val label = response.children.first { it.id == id }.label
        if (w == Watch.ATTEND) {
            if (after != null) notices += Notice(w, if (kind == "in") "Saapuminen kirjattu" else "Lähtö kirjattu", "$label · $dateText klo $after")
        } else {
            val subject = if (kind == "night") "Yöhoitovaraus" else "Hoitoaikavaraus"
            // No stable booking IDs in the citizen calendar. Remove unchanged slots
            // first, then pair remaining slots in calendar order within this day.
            val oldSlots = before?.split(", ").orEmpty()
            val newSlots = after?.split(", ").orEmpty()
            val removed = oldSlots.filterNot { it in newSlots }
            val added = newSlots.filterNot { it in oldSlots }
            for (i in 0 until maxOf(removed.size, added.size)) {
                val prior = removed.getOrNull(i)
                val next = added.getOrNull(i)
                val change = when { prior == null -> "lisätty"; next == null -> "poistettu"; else -> "muuttunut" }
                val detail = listOfNotNull("$label · $dateText", prior?.let { "Ennen: $it" }, next?.let { "Nyt: $it" }).joinToString("\n")
                notices += Notice(w, "$subject $change", detail)
            }
        }
    }
    return Observation(sample, notices)
}
