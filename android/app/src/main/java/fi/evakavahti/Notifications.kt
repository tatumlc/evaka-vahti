// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.UUID

class Notices(private val context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    private fun channel(watch: Watch, reply: Boolean) = if (reply) "replies" else watch.name
    fun create() {
        for ((id,name) in Watch.entries.map { it.name to it.label } + ("replies" to "Vastaukset omiin viesteihin")) {
            val importance = if (id == "replies") NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            manager.createNotificationChannel(NotificationChannel(id,name,importance).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
        }
    }
    fun cancel(w: Watch) {
        manager.activeNotifications.filter { it.tag?.startsWith(w.name + "/") == true }.forEach { manager.cancel(it.tag,it.id) }
    }
    fun post(n: Notice, detailed: Boolean) {
        val id = channel(n.watch,n.reply)
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val body = if (detailed) n.detail else "Tarkemmat tiedot sovelluksen tapahtumahistoriassa."
        val intent = PendingIntent.getActivity(context, 1, Intent(context,MainActivity::class.java)
            .putExtra("history",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context,id).setSmallIcon(R.drawable.status)
            .setContentTitle("eVaka-vahti: " + n.title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setLocalOnly(true)
            .setContentIntent(intent).setAutoCancel(true)
        try { manager.notify(n.watch.name + "/" + UUID.randomUUID(), 1, builder.build()) } catch (_: SecurityException) { /* Permission changed */ }
    }
}
