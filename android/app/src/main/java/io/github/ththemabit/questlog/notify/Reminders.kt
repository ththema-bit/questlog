package io.github.ththemabit.questlog.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ththemabit.questlog.MainActivity
import io.github.ththemabit.questlog.QuestlogApp
import io.github.ththemabit.questlog.ai.GameMaster
import io.github.ththemabit.questlog.R
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Moment
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.QuestState
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.StoryContext
import io.github.ththemabit.questlog.core.TemplateStoryteller
import io.github.ththemabit.questlog.core.WindowStatus
import io.github.ththemabit.questlog.widget.QuestWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Erinnerungen (F03): stündlicher Blick aufs Board. Meldet, wenn ein Fenster aufgeht,
 * wenn es morgen zugeht, morgens eine Übersicht und abends, wenn die Serie wackelt.
 * Ruhezeiten und Tageslimit gelten für alles.
 */
object Reminders {
    const val CHANNEL = "quests"
    private const val WORK = "questlog-reminders"
    private val story = TemplateStoryteller()

    fun setup(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Quests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Fenster gehen auf, Fenster schließen, Morgen-Übersicht"
            }
        )
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    data class Note(val key: String, val questId: String?, val title: String, val text: String, val mini: Boolean)

    /** Welche Benachrichtigungen jetzt fällig wären (ohne Ruhezeit/Limit). Reine Logik, testbar. */
    fun pending(save: SaveData, game: GameState, hour: Int): List<Note> {
        val s = save.settings
        val persona = Persona.active(s)
        val today = game.today
        val out = ArrayList<Note>()
        fun line(m: Moment, ctx: StoryContext, salt: Long) = story.line(persona, m, ctx, salt)
        // Texte des KI-Spielleiters, falls vorhanden. Sonst Vorlagen.
        val ai = s.ai
        val brief = if (ai.ready && ai.briefing) GameMaster.morningBriefing(save, today) else null
        fun lore(q: io.github.ththemabit.questlog.core.Quest) = if (ai.ready && ai.questTexts) GameMaster.textsFor(save, q) else null
        val due = game.board.overdue + game.board.open
        if (s.digestHour >= 0 && hour == s.digestHour && due.isNotEmpty()) {
            val top = due.first()
            out += Note("digest-$today", null, "Questlog: ${due.size} ${if (due.size == 1) "Quest wartet" else "Quests warten"}",
                brief ?: line(Moment.DIGEST, StoryContext(quest = top.quest.title, count = due.size), today.toEpochDay()), false)
        }
        for (qs in game.quests) {
            val q = qs.quest
            if (!q.recurring || q.archived) continue
            val cycle = qs.lastDone ?: q.createdAt
            if (s.notifyOpen && qs.status == WindowStatus.OPEN && q.minDays != null && q.minDays > 0 && qs.daysSince == q.minDays) {
                out += Note("open-${q.id}-$cycle", q.id, q.title, lore(q)?.reminder ?: line(Moment.WINDOW_OPEN, StoryContext(quest = q.title), cycle), q.mini != null)
            }
            if (s.notifyClosing && qs.status == WindowStatus.OPEN && qs.closesIn == 1) {
                out += Note("closing-${q.id}-$cycle", q.id, q.title, line(Moment.CLOSING_SOON, StoryContext(quest = q.title), cycle + 1), q.mini != null)
            }
        }
        if (hour >= 19 && game.player.streakAtRisk && game.player.todayCount == 0) {
            out += Note("streak-$today", null, "Serie: ${game.player.streak} Tage", line(Moment.STREAK_RISK, StoryContext(), today.toEpochDay()), false)
        }
        return out
    }

    fun quiet(hour: Int, from: Int, to: Int): Boolean = if (from > to) hour >= from || hour < to else hour in from until to

    suspend fun run(context: Context) {
        val store = QuestlogApp.store(context)
        store.refresh()
        val app = store.awaitState()
        val save = app.save
        val s = save.settings
        if (!s.remindersOn || app.game.paused) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        if (quiet(hour, s.quietFrom, s.quietTo)) return
        val today = LocalDate.now(zone)
        val sentToday = save.meta.sentNotifications.values.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today }
        var budget = s.maxPerDay - sentToday
        if (budget <= 0) return
        val fresh = pending(save, app.game, hour).filter { it.key !in save.meta.sentNotifications }
        val sent = HashMap<String, Long>()
        val nm = NotificationManagerCompat.from(context)
        for (n in fresh) {
            if (budget <= 0) break
            runCatching { nm.notify(n.key.hashCode(), build(context, n)) }.onSuccess {
                sent[n.key] = now
                budget--
            }
        }
        if (sent.isNotEmpty()) {
            val cutoff = now - 4L * 24 * 3600 * 1000
            store.meta { m -> m.copy(sentNotifications = (m.sentNotifications.filterValues { it > cutoff } + sent)) }
            store.flush()
        }
    }

    private fun build(context: Context, n: Note): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, n.key.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                n.questId?.let { putExtra(MainActivity.EXTRA_QUEST, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF9F1C.toInt())
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setContentIntent(open)
            .setAutoCancel(true)
        n.questId?.let { id ->
            b.addAction(0, "Erledigt", actionIntent(context, id, false, n.key.hashCode()))
            if (n.mini) b.addAction(0, "Mini-Version", actionIntent(context, id, true, n.key.hashCode()))
        }
        return b.build()
    }

    private fun actionIntent(context: Context, questId: String, mini: Boolean, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, (questId + mini).hashCode(),
            Intent(context, QuestActionReceiver::class.java).apply {
                putExtra("questId", questId)
                putExtra("mini", mini)
                putExtra("notificationId", notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Reminders.run(applicationContext) }
        return Result.success()
    }
}

/** „Erledigt“ und „Mini-Version“ direkt aus der Benachrichtigung (F04). */
class QuestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val questId = intent.getStringExtra("questId") ?: return
        val mini = intent.getBooleanExtra("mini", false)
        val nid = intent.getIntExtra("notificationId", 0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val store = QuestlogApp.store(context)
                store.awaitState()
                store.complete(questId, mini = mini)
                store.flush()
                NotificationManagerCompat.from(context).cancel(nid)
                QuestWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
