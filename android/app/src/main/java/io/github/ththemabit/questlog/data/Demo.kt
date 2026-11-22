package io.github.ththemabit.questlog.data

import io.github.ththemabit.questlog.core.Chaos
import io.github.ththemabit.questlog.core.Done
import io.github.ththemabit.questlog.core.Event
import io.github.ththemabit.questlog.core.Hit
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.core.Subtask
import java.time.LocalDate
import java.time.ZoneId

/** Beispiel-Spielstand für Screenshots und Tests: drei Wochen Haushalt, Sport und ein Boss. */
object Demo {
    fun save(today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), chaos: Chaos = Chaos()): SaveData {
        fun at(daysAgo: Int, h: Int, m: Int = 0): Long = today.minusDays(daysAgo.toLong()).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val created = at(23, 9)
        fun q(id: String, title: String, cat: String, imp: Int, effort: Int, dread: Int, min: Int?, max: Int?, mini: String? = null, why: String? = null) =
            Quest(id, title, cat, imp, effort, dread, min, max, mini = mini, why = why, createdAt = created)

        val quests = listOf(
            q("bad", "Bad putzen", "bad", 4, 20, 4, 4, 9, "Nur das Waschbecken", "Damit Besuch kommen kann, ohne dass ich panisch putze."),
            q("saugen", "Staubsaugen", "haushalt", 3, 20, 3, 3, 7, "Nur den Flur"),
            q("spuele", "Spülmaschine ausräumen", "kueche", 3, 5, 3, 1, 2, "Nur das Besteck"),
            q("waesche", "Wäsche waschen", "waesche", 3, 15, 2, 3, 7),
            q("muell", "Müll rausbringen", "haushalt", 3, 5, 2, 2, 4),
            q("kuehl", "Kühlschrank ausmisten", "kueche", 2, 20, 4, 14, 30, "Ein Fach"),
            q("fenster", "Fenster putzen", "haushalt", 2, 45, 4, 30, 60, "Ein Fenster"),
            q("laufen", "Laufen gehen", "sport", 4, 30, 4, 2, 4, "Zehn Minuten um den Block"),
            q("dehnen", "Dehnen", "sport", 3, 10, 2, 1, 2),
            q("vokabeln", "Vokabeln wiederholen", "lernen", 3, 15, 3, 1, 2, "Fünf Karten"),
            q("oma", "Oma anrufen", "sozial", 4, 20, 2, 5, 10),
            Quest(
                "steuer", "Steuererklärung", "papierkram", 5, 180, 5, boss = true, createdAt = at(9, 18),
                subtasks = listOf(
                    Subtask("s1", "Belege sammeln"), Subtask("s2", "Lohnsteuerbescheinigung finden"),
                    Subtask("s3", "Werbungskosten eintragen"), Subtask("s4", "Sonderausgaben eintragen"), Subtask("s5", "Abschicken"),
                ),
            ),
            Quest("geschenk", "Geschenk für Lea besorgen", "sozial", 4, 30, 2, dueDate = today.plusDays(3).toString(), createdAt = at(2, 20)),
        )

        val plan: Map<String, List<Int>> = mapOf(
            "bad" to listOf(21, 14, 8),
            "saugen" to listOf(22, 17, 12, 6, 2),
            "spuele" to listOf(23, 22, 20, 19, 17, 16, 14, 13, 12, 10, 9, 7, 6, 5, 3, 1),
            "waesche" to listOf(20, 15, 10, 4),
            "muell" to listOf(21, 18, 15, 12, 9, 5),
            "kuehl" to listOf(19),
            "fenster" to listOf(),
            "laufen" to listOf(22, 19, 16, 13, 10, 7, 4, 1),
            "dehnen" to listOf(21, 20, 18, 16, 15, 13, 11, 9, 8, 6, 4, 2, 1, 0),
            "vokabeln" to listOf(23, 22, 21, 19, 18, 16, 14, 12, 11, 9, 7, 5, 3, 2, 0),
            "oma" to listOf(17, 9),
        )
        val events = ArrayList<Event>()
        var n = 0
        for ((qid, days) in plan) {
            for (d in days) {
                val hour = 8 + (n * 5) % 12
                events += Done("d${n++}", at(d, hour, (n * 7) % 60), qid, chaos = chaos.enabledKeys(),
                    durationSec = if (qid == "spuele") 180 + (n * 13) % 120 else null)
            }
        }
        events += Done("m1", at(3, 21), "bad", mini = true)
        events += Hit("h1", at(6, 19), "steuer", "s1")
        events += Hit("h2", at(4, 18), "steuer", "s2")

        return SaveData(
            quests = quests,
            events = events,
            rewards = listOf(Reward("r1", "Pizza bestellen", 800, "coin"), Reward("r2", "Kinoabend", 1500, "star"), Reward("r3", "Neues Buch", 1200, "book")),
            settings = Settings(chaos = chaos, narrator = "butler"),
            meta = Meta(onboarded = true, seed = 4242L),
        )
    }
}
