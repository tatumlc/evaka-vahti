// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class Repository(private val app: VahtiApp) {
    private val checks = Mutex()
    private val edits = Mutex()
    private val notices = Notices(app)
    val data = app.store.data
    private val jobs get() = WorkManager.getInstance(app)
    private fun constraints(s: State) = Constraints.Builder().apply {
        if (s.owner != Demo.owner) setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()
    private fun active(s: State) = s.done && s.owner.isNotEmpty() && s.watches.isNotEmpty() && !s.needsLogin
    private fun schedule(s: State) {
        if (!active(s)) {
            jobs.cancelUniqueWork("periodic")
            jobs.cancelUniqueWork("manual")
            return
        }
        val job = PeriodicWorkRequestBuilder<PollWorker>(s.minutes,TimeUnit.MINUTES)
            .setInitialDelay(s.minutes, TimeUnit.MINUTES).setConstraints(constraints(s)).build()
        jobs.enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.UPDATE, job)
    }
    private fun enqueue(s: State) {
        if (active(s)) jobs.enqueueUniqueWork("manual",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<PollWorker>().setConstraints(constraints(s)).build())
    }
    suspend fun restore() = edits.withLock { notices.create(); schedule(data.first()) }
    suspend fun manual() = edits.withLock {
        val s = data.first()
        if (active(s)) jobs.enqueueUniqueWork("manual",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PollWorker>().setConstraints(constraints(s)).build())
    }
    suspend fun change(refresh: Boolean = false, clearNotifications: Boolean = false, transform: (State)->State) = edits.withLock {
        val previous = data.first()
        val s = app.store.updateData(transform)
        (previous.watches.keys - s.watches.keys).forEach(notices::cancel)
        if (clearNotifications) notices.manager.cancelAll()
        schedule(s)
        if (refresh) enqueue(s)
    }
    suspend fun erase(): Boolean = withContext(Dispatchers.IO) {
        checks.withLock {
            val s = data.first()
            change(clearNotifications = true) { State() }
            if (s.owner == Demo.owner || s.cookies.isEmpty()) true else try {
                Api(s.cookies).logout()
                true
            } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        }
    }
    suspend fun signIn(login: Login?, remember: Boolean) = withContext(Dispatchers.IO) {
        checks.withLock {
            val api = Api(emptyList())
            val owner = login?.let { api.signIn(it) } ?: Demo.owner
            val date = LocalDate.now(helsinki)
            var childrenError = ""
            val children = if (login == null) Demo.children else try {
                api.calendar(date,date).children
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (e is HttpFailure && e.status == 401) throw e
                childrenError = problem(e)
                emptyList()
            }
            change(refresh = true, clearNotifications = true) { current ->
                val base = if (owner == current.owner) current.select(current.selected.intersect(children.map { it.id }.toSet())) else State()
                base.copy(owner = owner, cookies = api.cookies(), login = login?.takeIf { remember },
                    children = children, childrenError = childrenError, needsLogin = false,
                    watches = if (base.needsLogin) base.watches.mapValues { Monitor() } else base.watches)
            }
        }
    }

    suspend fun refreshChildren() = withContext(Dispatchers.IO) {
        checks.withLock {
            val s = data.first()
            if (s.owner == Demo.owner) return@withLock
            val api = Api(s.cookies)
            val today = LocalDate.now(helsinki)
            val children = api.calendar(today,today).children
            change { current ->
                if (current.owner != s.owner) current else current.select(current.selected.intersect(children.map { it.id }.toSet()))
                    .copy(children = children, childrenError = "", cookies = api.cookies())
            }
        }
    }

    suspend fun check(): Unit = withContext(Dispatchers.IO) {
        checks.withLock {
            val initial = data.first()
            if (!active(initial)) return@withLock
            val api = Api(initial.cookies)
            val today = LocalDate.now(helsinki)
            val session = SessionRenewal(initial.owner, initial.login, api::signIn)
            var calendar: Result<CalendarResponse>? = null
            for ((w, monitor) in initial.watches) {
                val result = runCatching {
                    if (w == Watch.MAIL) {
                        if (initial.owner == Demo.owner) Demo.inbox(monitor.sample,initial.selected,initial.demoStep)
                        else session.read { api.inbox(monitor.sample,initial.selected) }
                    } else {
                        if (initial.selected.isEmpty()) error("Lapsivalinta")
                        if (calendar == null) calendar = runCatching {
                            if (initial.owner == Demo.owner) Demo.calendar(today,initial.demoStep)
                            else {
                                val reservations = initial.watches.keys.any { it == Watch.CARE || it == Watch.NIGHT }
                                val from = today.minusDays(if (Watch.ATTEND in initial.watches) 7 else 2)
                                session.read { api.calendar(from, if (reservations) today.plusDays(32) else today) }
                            }
                        }
                        val response = calendar.getOrThrow()
                        if (response.children != initial.children) {
                            val allowed = response.children.map { it.id }.toSet()
                            change(refresh = !allowed.containsAll(initial.selected), clearNotifications = !allowed.containsAll(initial.selected)) { current ->
                                if (current.owner != initial.owner || current.children != initial.children) current
                                else current.select(current.selected.intersect(allowed)).copy(children = response.children, childrenError = "")
                            }
                        }
                        calendarObservation(w, initial, response, today)
                    }
                }
                result.exceptionOrNull()?.let { if (it !is Exception || it is CancellationException) throw it }
                edits.withLock {
                    var delivery = emptyList<Notice>()
                    val now = System.currentTimeMillis()
                    val updated = app.store.updateData { current ->
                        delivery = emptyList()
                        if (initial.owner != current.owner || current.watches[w]?.generation != monitor.generation ||
                            (w == Watch.CARE && (Watch.NIGHT in initial.watches) != (Watch.NIGHT in current.watches))) return@updateData current
                        val observation = result.getOrNull()
                        val next = if (observation != null) {
                            delivery = observation.notices
                            monitor.copy(sample = observation.sample, checked = now, error = "")
                        } else {
                            val ex = result.exceptionOrNull() as Exception
                            monitor.copy(error = if (ex.message == "Lapsivalinta") "Päivitä seurattavien lasten valinta asetuksissa." else problem(ex))
                        }
                        current.copy(cookies = api.cookies(), watches = current.watches + (w to next),
                            history = (delivery.asReversed().map { Entry(now,it) } + current.history).take(100))
                    }
                    delivery.forEach { notices.post(it, updated.detailed) }
                }
            }
            if (session.blocked != null) edits.withLock {
                val next = app.store.updateData { if (it.owner == initial.owner)
                    it.copy(needsLogin = true, cookies = emptyList(), login = null) else it }
                schedule(next)
            }
        }
    }
}
class PollWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        (applicationContext as VahtiApp).repo.check()
        return Result.success()
    }
}
