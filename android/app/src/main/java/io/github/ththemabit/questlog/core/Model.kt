package io.github.ththemabit.questlog.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Gespeicherter Spielstand. Alles, was die App weiß, steht hier drin: Quests, ein Protokoll
 * aller Ereignisse (erledigt, Teilaufgabe, Kauf, Wette) und die Einstellungen.
 * Punkte, Glut, Serien, Rekorde und Meilensteine werden NICHT gespeichert, sondern bei jedem
 * Start aus dem Protokoll nachgerechnet (siehe Engine). Dadurch stimmen Nachtragen und
 * Rückgängig immer, und ein Backup ist einfach diese eine Datei.
 */

@Serializable
data class SaveData(
    val version: Int = 1,
    val quests: List<Quest> = emptyList(),
    val events: List<Event> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    /** Pausen (Urlaub, krank): Tage darin zählen weder für Verfall noch für Serien oder Fenster. */
    val pauses: List<PauseRange> = emptyList(),
    /** Montage (ISO-Datum) der Wochen, die als Hardcore-Woche laufen. */
    val hardcoreWeeks: List<String> = emptyList(),
    val settings: Settings = Settings(),
    val meta: Meta = Meta(),
    /** Vorgefertigte Texte, z. B. von einem späteren KI-Erzähler. Schlüssel -> Text. */
    val storyCache: Map<String, String> = emptyMap(),
)

@Serializable
data class Quest(
    val id: String,
    val title: String,
    val category: String = "sonstiges",
    /** 1..5 */
    val importance: Int = 3,
    /** geschätzter Aufwand in Minuten */
    val effortMin: Int = 15,
    /** Überwindung 1..5 (wie sehr man sie hasst) */
    val dread: Int = 3,
    /** frühestens nach so vielen Tagen wieder sinnvoll (null = keine Untergrenze) */
    val minDays: Int? = null,
    /** spätestens nach so vielen Tagen fällig (null = keine Obergrenze) */
    val maxDays: Int? = null,
    /** Termin für einmalige Quests (ISO-Datum), optional */
    val dueDate: String? = null,
    /** Text der Mini-Version („ein Teller statt der ganzen Spüle“), null = keine */
    val mini: String? = null,
    val why: String? = null,
    val ifThen: String? = null,
    val note: String? = null,
    val subtasks: List<Subtask> = emptyList(),
    /** Boss: großes Vorhaben, Teilaufgaben sind Treffer */
    val boss: Boolean = false,
    val createdAt: Long,
    val archived: Boolean = false,
) {
    val recurring: Boolean get() = minDays != null || maxDays != null
}

@Serializable
data class Subtask(val id: String, val title: String, val weight: Int = 1)

@Serializable
data class Reward(val id: String, val name: String, val price: Int, val icon: String = "chest")

@Serializable
data class PauseRange(val from: String, val to: String? = null)

@Serializable
sealed class Event {
    abstract val id: String
    abstract val at: Long
}

/** Quest erledigt (ganz oder als Mini-Version). */
@Serializable
@SerialName("done")
data class Done(
    override val id: String,
    override val at: Long,
    val questId: String,
    val mini: Boolean = false,
    /** gestoppte Zeit in Sekunden (Speedrun, Drei-Song-Challenge) */
    val durationSec: Int? = null,
    /** Modifier-Schlüssel, siehe Modifiers */
    val mods: List<String> = emptyList(),
    /** Zeitpunkt des Roulette-Drehs, falls die Quest daraus kam */
    val spinAt: Long? = null,
    /** Chaos-Funktionen, die beim Erledigen aktiv waren (Feiertage, Hochzeiten) */
    val chaos: List<String> = emptyList(),
    /** Lautstärke-Spitze beim Kampfschrei (0..100), falls benutzt */
    val shout: Int? = null,
    /** nachgetragen statt live abgehakt */
    val backfilled: Boolean = false,
) : Event()

/** Eine Teilaufgabe (oder ein Boss-Treffer) erledigt. */
@Serializable
@SerialName("hit")
data class Hit(
    override val id: String,
    override val at: Long,
    val questId: String,
    val subtaskId: String,
    val chaos: List<String> = emptyList(),
) : Event()

/** Belohnung aus dem Shop gekauft. */
@Serializable
@SerialName("buy")
data class Buy(
    override val id: String,
    override val at: Long,
    val rewardId: String,
    val name: String,
    val price: Int,
) : Event()

/** Doppelt oder nichts: Glut-Einsatz auf eine Quest bis zur Frist. */
@Serializable
@SerialName("wager")
data class Wager(
    override val id: String,
    override val at: Long,
    val questId: String,
    val deadline: Long,
) : Event()

@Serializable
data class Settings(
    val remindersOn: Boolean = true,
    /** Stunde für den Morgen-Sammelpush, -1 = aus */
    val digestHour: Int = 8,
    val quietFrom: Int = 22,
    val quietTo: Int = 7,
    val maxPerDay: Int = 3,
    val notifyOpen: Boolean = true,
    val notifyClosing: Boolean = true,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val chaos: Chaos = Chaos(),
    /** Erzähler-Stimme für Texte und Pushs (nur mit Chaos „Erzähler“) */
    val narrator: String = "chronist",
)

/** Chaos-Kammer: alles standardmäßig aus, einzeln einschaltbar. */
@Serializable
data class Chaos(
    val comicCurse: Boolean = false,
    val narrators: Boolean = false,
    val gerald: Boolean = false,
    val lazyTax: Boolean = false,
    val dustBunnies: Boolean = false,
    val weddings: Boolean = false,
    val saga: Boolean = false,
    val credits: Boolean = false,
    val holidays: Boolean = false,
    val battleCry: Boolean = false,
) {
    /** Chaos-Regeln, die beim Erledigen ins Ereignis geschrieben werden (damit Umschalten die Vergangenheit nicht ändert). */
    fun enabledKeys(): List<String> = buildList {
        if (holidays) add(CHAOS_HOLIDAYS)
        if (weddings) add(CHAOS_WEDDINGS)
        if (comicCurse) add(CHAOS_CURSE)
    }

    companion object {
        const val CHAOS_HOLIDAYS = "feiertage"
        const val CHAOS_WEDDINGS = "hochzeit"
        const val CHAOS_CURSE = "fluch"
    }
}

@Serializable
data class Meta(
    val onboarded: Boolean = false,
    /** Montag der Woche, deren Rückblick zuletzt gezeigt wurde */
    val seenRecapWeek: String? = null,
    /** gelesene Briefe (Steuerbescheide, Hochzeitsanzeigen) */
    val seenLetters: List<String> = emptyList(),
    /** zuletzt gesehene Meilensteine (für „neu“-Markierung) */
    val seenAchievements: List<String> = emptyList(),
    val pendingSpin: PendingSpin? = null,
    /** Schlüssel bereits gesendeter Benachrichtigungen -> Zeitpunkt */
    val sentNotifications: Map<String, Long> = emptyMap(),
    /** Zufallssaat für Gerald & Co., einmal pro Installation */
    val seed: Long = 0L,
)

@Serializable
data class PendingSpin(val questId: String, val at: Long, val blind: Boolean = false)
