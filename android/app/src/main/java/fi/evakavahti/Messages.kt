// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import java.time.OffsetDateTime

private fun stamp(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()
private fun String?.label() = this?.replace(Regex("[\\p{C}\\s]+"), " ")?.trim()?.take(120).orEmpty()

/** The inbox is paged by descending participant activity. Complete equal-time pages as well. */
fun messages(old: Sample?, myId: String, selected: Set<String>, read: (Int) -> Page): Observation {
    val notices = linkedMapOf<String,Notice>()
    val watermark = old?.cursor ?: 0
    var newest = watermark
    val boundary = mutableMapOf<String,Long>()
    val redacted = old?.values.orEmpty().filterKeys { it.startsWith("r/") }.toMutableMap()
    var number = 1
    do {
        val page = read(number)
        require(page.pages >= 0)
        var olderThread = false
        for (thread in page.data) {
            val last = when (thread.type) {
                "Regular" -> {
                    val items = requireNotNull(thread.messages)
                    val visible = thread.children.isEmpty() || thread.children.any { it.childId in selected }
                    var mine = false
                    // The server orders messages by sent_at, created. Preserve the tie-break.
                    for (msg in items) {
                        val time = stamp(msg.sentAt)
                        val key = "m/" + msg.id
                        if (time >= watermark) boundary[key] = time
                        if (msg.sender.id == myId) { mine = true; continue }
                        if (!visible || old == null || time < watermark || key in old.values) continue
                        val sender = msg.sender.name.label()
                        val title = thread.title.label()
                        val detail = if (sender.isEmpty() || title.isEmpty()) "Avaa viesti eVakassa."
                            else "Lähettäjä: $sender\nOtsikko: $title"
                        notices[key] = Notice(Watch.MAIL, if (mine) "VASTAUS OMAAN VIESTIIN" else "UUSI VIESTI", detail, mine)
                    }
                    items.maxOfOrNull { stamp(it.sentAt) }
                }
                "Redacted" -> {
                    val time = thread.lastMessageSentAt?.let(::stamp)
                    val key = "r/" + thread.id
                    val previous = old?.values?.get(key)
                    if (time != null) {
                        // sender is the FIRST sender. A timestamp alone cannot identify the last author.
                        val incoming = thread.hasUnreadMessages && (previous?.endsWith("/false") == true ||
                            (previous == null && thread.sender != null && thread.sender.id != myId))
                        if (old != null && time > watermark && incoming)
                            notices[key] = Notice(Watch.MAIL, "UUSI VIESTI", "Suojattu keskustelu. Avaa eVaka.")
                        redacted[key] = "$time/${thread.hasUnreadMessages}"
                    }
                    time
                }
                else -> error("Tuntematon viestiketju")
            }
            if (last != null) {
                newest = maxOf(last, newest)
                if (last < watermark) olderThread = true
            }
        }
        if (number >= page.pages || (old != null && olderThread)) break
        require(number++ < 200) { "Liikaa viestisivuja" }
    } while (true)
    val ids = boundary.filterValues { it == newest }.mapValues { "" }
    val ties = if (newest == watermark) old?.values.orEmpty().filterKeys { it.startsWith("m/") } else emptyMap()
    return Observation(Sample(values = redacted + ties + ids, cursor = newest), notices.values.toList())
}
