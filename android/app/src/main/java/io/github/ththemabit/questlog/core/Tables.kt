package io.github.ththemabit.questlog.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** Deterministischer Zufall: gleiche Saat = gleiches Ergebnis, damit das Nachrechnen stabil bleibt. */
class Rng(seed: Long) {
    private var state = seed

    constructor(text: String) : this(hash(text))

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    fun nextInt(bound: Int): Int = if (bound <= 1) 0 else ((nextLong() ushr 1) % bound).toInt()

    companion object {
        fun hash(text: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in text) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}

/** C09: erfundene Feiertage. Kategorie null = gilt für alles. */
data class Holiday(
    val month: Int,
    val day: Int,
    val name: String,
    val text: String,
    val category: String?,
    val mult: Double,
    val titleKeyword: String? = null,
) {
    fun appliesTo(q: Quest): Boolean = when {
        titleKeyword != null -> q.title.contains(titleKeyword, ignoreCase = true)
        category == null -> true
        else -> q.category == category
    }
}

object Holidays {
    val all = listOf(
        Holiday(1, 9, "Tag des abgeschmückten Baums", "Lametta, Kugeln, Nadeln: alles muss raus. Haushalt zählt doppelt.", "haushalt", 2.0),
        Holiday(1, 23, "Fest der gefüllten Wasserflasche", "Selbstfürsorge zählt doppelt.", "selbst", 2.0),
        Holiday(2, 11, "Gedenktag des verschollenen Kugelschreibers", "Papierkram zählt doppelt. Der Kuli taucht trotzdem nicht auf.", "papierkram", 2.0),
        Holiday(2, 27, "Tag der Fussel", "Wäsche zählt dreifach.", "waesche", 3.0),
        Holiday(3, 14, "Tag des Frühjahrsputz-Vorglühens", "Haushalt zählt doppelt.", "haushalt", 2.0),
        Holiday(3, 30, "Tag der unterschätzten Zahnseide", "Selbstfürsorge zählt doppelt.", "selbst", 2.0),
        Holiday(4, 17, "Hochfest der Topfpflanze", "Garten zählt dreifach.", "garten", 3.0),
        Holiday(5, 9, "Tag der verlorenen Socke", "Wäsche zählt dreifach. Vielleicht findet sich eine.", "waesche", 3.0),
        Holiday(5, 28, "Kühlschrank-Gedenktag", "Küche zählt doppelt, für alles, was hinten links lebt.", "kueche", 2.0),
        Holiday(6, 12, "Fest des offenen Fensters", "Haushalt zählt doppelt, Lüften inklusive.", "haushalt", 2.0),
        Holiday(6, 25, "Tag des Muskelkaters", "Sport zählt doppelt.", "sport", 2.0),
        Holiday(7, 8, "Feiertag der vollen Spülmaschine", "Küche zählt dreifach.", "kueche", 3.0),
        Holiday(7, 26, "Tag des ungelesenen Buches", "Lernen zählt doppelt.", "lernen", 2.0),
        Holiday(8, 13, "Fest der angerufenen Großeltern", "Freunde & Familie zählt dreifach.", "sozial", 3.0),
        Holiday(8, 30, "Tag des vergessenen Wäscheständers", "Wäsche zählt doppelt.", "waesche", 2.0),
        Holiday(9, 16, "Tag der sauberen Fuge", "Bad zählt dreifach. Die alte Zahnbürste weiß, wofür.", "bad", 3.0),
        Holiday(10, 4, "Erntedank der Kartoffelschalen", "Küche zählt doppelt.", "kueche", 2.0),
        Holiday(10, 31, "Nacht der Wollmäuse", "Haushalt zählt doppelt. Sie kommen unter dem Bett hervor.", "haushalt", 2.0),
        Holiday(11, 19, "Tag des abgehefteten Briefes", "Papierkram zählt dreifach.", "papierkram", 3.0),
        Holiday(12, 6, "Tag des geputzten Stiefels", "Alles zählt anderthalbfach.", null, 1.5),
        Holiday(12, 21, "Längste Nacht der Glut", "Alles zählt anderthalbfach. Halt das Feuer am Leben.", null, 1.5),
        Holiday(12, 28, "Tag zwischen den Jahren", "Sonstiges zählt doppelt.", "sonstiges", 2.0),
    )

    private val fullMoon = Holiday(0, 0, "Vollmond", "Fensterputzen zählt heute doppelt.", null, 2.0, titleKeyword = "fenster")

    fun on(day: LocalDate): Holiday? =
        all.firstOrNull { it.month == day.monthValue && it.day == day.dayOfMonth }
            ?: if (isFullMoon(day)) fullMoon else null

    fun matching(day: LocalDate, q: Quest): Holiday? = on(day)?.takeIf { it.appliesTo(q) }

    /** Vollmond näherungsweise über den synodischen Monat ab einem bekannten Neumond. */
    fun isFullMoon(day: LocalDate): Boolean {
        val ref = LocalDateTime.of(2000, 1, 6, 18, 14).toEpochSecond(ZoneOffset.UTC) / 86400.0
        val noon = day.atTime(12, 0).toEpochSecond(ZoneOffset.UTC) / 86400.0
        val synodic = 29.530588853
        val age = ((noon - ref) % synodic + synodic) % synodic
        return abs(age - synodic / 2) < 0.5
    }
}

data class Trophy(val name: String, val rare: Boolean)

object Trophies {
    val all = listOf(
        Trophy("Goldener Besen", false), Trophy("Silberne Socke", false), Trophy("Rostiger Schlüssel", false),
        Trophy("Mondsteinkrümel", false), Trophy("Sanduhr ohne Sand", false), Trophy("Ewige Kerze", false),
        Trophy("Gnomenmütze", false), Trophy("Federkiel des Chronisten", false), Trophy("Kristallschwamm", false),
        Trophy("Zwergenhammer", false), Trophy("Elfenseife", false), Trophy("Verzauberte Wäscheklammer", false),
        Trophy("Goldener Kochlöffel", false), Trophy("Eulenfeder", false), Trophy("Laterne des Kellers", false),
        Trophy("Rune der Pünktlichkeit", false), Trophy("Trollzahn", false),
        Trophy("Phönixfeder", true), Trophy("Krone der Ordnung", true), Trophy("Drachenschuppe", true),
    )

    fun pick(rng: Rng): Trophy {
        val rare = rng.nextInt(100) < 15
        val pool = all.filter { it.rare == rare }
        return pool[rng.nextInt(pool.size)]
    }
}
