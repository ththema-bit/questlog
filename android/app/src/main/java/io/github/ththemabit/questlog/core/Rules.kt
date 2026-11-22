package io.github.ththemabit.questlog.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Alle Spielregeln an einem Ort. Zahlen hier ändern = Balance ändern. */
object Rules {
    // Punkte-Formel
    fun base(effortMin: Int): Double = 10.0 * sqrt(effortMin.coerceIn(1, 480).toDouble())
    fun importanceFactor(importance: Int): Double = 1.0 + 0.15 * (importance.coerceIn(1, 5) - 3)
    fun dreadFactor(dread: Int): Double = 1.0 + 0.2 * (dread.coerceIn(1, 5) - 3)
    fun streakFactor(streak: Int): Double = 1.0 + min(streak, 30) / 100.0

    const val TIMING_EARLY = 0.25
    fun overdueFactor(overdueDays: Int): Double = max(0.5, 1.0 - 0.1 * overdueDays)

    const val COMBO_WINDOW_MS = 45L * 60_000
    fun comboFactor(chainIndex: Int): Double = 1.0 + 0.1 * min(chainIndex, 3)

    const val BOUNTY = 2.0
    const val ROULETTE = 1.5
    const val ROULETTE_WINDOW_MS = 60L * 60_000
    const val COMEBACK = 2.0
    const val COMEBACK_DAYS = 3
    const val HARDCORE = 2.0
    const val WEDDING = 1.2
    const val WEDDING_WINDOW_MS = 30L * 60_000
    const val WEDDING_NEEDED = 5
    const val BONUS_CAP = 5.0
    const val MOD_CAP = 2.5
    const val SEASON_START_SHARE = 0.05

    // Mini-Version und Teilaufgaben
    fun miniPoints(streak: Int): Int = (base(2) * streakFactor(streak)).roundToInt()
    const val HIT_SHARE = 0.7
    const val FINISH_SHARE = 0.3

    // Glut
    const val GLUT_DECAY = 0.85
    const val GLUT_FLOOR_SHARE = 0.10
    const val OVERDUE_DRAIN_PER_IMPORTANCE = 5

    // Boss-Zorn: pro Tag ohne Treffer weniger Siegesbeute
    const val BOSS_RAGE_PER_DAY = 0.05
    const val BOSS_RAGE_MAX = 0.4
    const val BOSS_RAGE_GRACE_DAYS = 2

    // Doppelt oder nichts
    fun wagerStake(glut: Int): Int = max(10, (glut * 0.1).roundToInt())
    const val WAGER_DEADLINE_HOUR = 20

    // Schutzschilde
    const val SHIELD_MAX = 3
    const val SHIELD_EVERY_STREAK_DAYS = 10

    // Level-Kurve: Stufe n braucht 50·n·(n−1) XP
    fun levelForXp(xp: Long): Int = floor((1 + sqrt(1 + 4.0 * xp / 50.0)) / 2).toInt().coerceAtLeast(1)
    fun xpForLevel(level: Int): Long = 50L * level * (level - 1)

    // Skills je Kategorie: 20·n·(n−1)
    fun skillLevel(xp: Long): Int = floor((1 + sqrt(1 + 4.0 * xp / 20.0)) / 2).toInt().coerceAtLeast(1)
    fun xpForSkill(level: Int): Long = 20L * level * (level - 1)

    // Münzen: 1 Punkt = 1 Münze
    // Loot
    const val LOOT_BASE_CHANCE = 0.12

    // Wochenziel
    const val GOAL_DEFAULT = 300
    const val GOAL_MIN = 200
    fun weeklyGoal(previousWeeks: List<Int>): Int {
        val weeks = previousWeeks.takeLast(4)
        if (weeks.isEmpty() || weeks.all { it == 0 }) return GOAL_DEFAULT
        val avg = weeks.average() * 1.1
        return max(GOAL_MIN, ((avg / 50.0).roundToInt() * 50))
    }

    // Fluch: so lange überfällig, dass die App in Comic Sans fällt
    fun curseThreshold(quest: Quest): Int = max(3, quest.maxDays ?: 3)

    // Wollmäuse
    val DUST_CATEGORIES = setOf("haushalt", "kueche", "bad", "waesche")
    const val DUST_MAX = 12
}

data class Rank(val minLevel: Int, val name: String, val flavor: String)

object Ranks {
    val all = listOf(
        Rank(1, "Kerze", "Ein kleines Licht in der Dunkelheit"),
        Rank(3, "Laterne", "Trägt das Licht schon durch den Flur"),
        Rank(6, "Fackel", "Leuchtet auch in den Keller"),
        Rank(10, "Lagerfeuer", "Um dich herum wird es warm"),
        Rank(15, "Schmiedeesse", "Hier wird Ordnung geschmiedet"),
        Rank(20, "Leuchtfeuer", "Von weitem zu sehen"),
        Rank(30, "Drachenatem", "Nichts bleibt liegen"),
        Rank(40, "Sonnenglut", "Ein eigener Tagesanbruch"),
        Rank(50, "Sternenfeuer", "Legende der Chronik"),
    )

    fun forLevel(level: Int): Rank = all.last { level >= it.minLevel }
    fun next(level: Int): Rank? = all.firstOrNull { it.minLevel > level }
}

data class Category(val key: String, val name: String, val icon: String)

object Categories {
    val all = listOf(
        Category("haushalt", "Haushalt", "broom"),
        Category("kueche", "Küche", "pot"),
        Category("bad", "Bad", "drop"),
        Category("waesche", "Wäsche", "shirt"),
        Category("sport", "Sport", "sword"),
        Category("lernen", "Lernen", "book"),
        Category("arbeit", "Arbeit", "anvil"),
        Category("papierkram", "Papierkram", "quill"),
        Category("selbst", "Selbstfürsorge", "heart"),
        Category("sozial", "Freunde & Familie", "mug"),
        Category("garten", "Garten", "leaf"),
        Category("sonstiges", "Sonstiges", "star"),
    )
    private val byKey = all.associateBy { it.key }
    fun of(key: String): Category = byKey[key] ?: all.last()
}

/** Modifier-Karten (M01–M10). Dresscode steht auf später. */
data class Modifier(
    val key: String,
    val name: String,
    val factor: Double,
    val unlockLevel: Int,
    val icon: String,
    val rule: String,
    val kind: Kind,
) {
    enum class Kind { HONOR, TIMER, FOCUS, SENSOR, RANDOM, WAGER, WEEK, LOOT }
}

object Modifiers {
    const val SOUNDTRACK = "soundtrack"
    const val TELEFON = "telefon"
    const val HANDYWEG = "handyweg"
    const val DREISONG = "dreisong"
    const val BLINDDATE = "blinddate"
    const val WAGER = "doppelt"
    const val HARDCORE = "hardcore"
    const val REPORTER = "reporter"
    const val TANZ = "tanz"
    const val JOKER = "joker"

    const val DREISONG_LIMIT_SEC = 630

    val all = listOf(
        Modifier(SOUNDTRACK, "Soundtrack", 1.1, 2, "note", "Musik, Podcast oder Hörbuch nur während der Quest.", Modifier.Kind.HONOR),
        Modifier(TELEFON, "Telefonjoker", 1.2, 3, "talk", "Erledige die Quest, während du mit jemandem telefonierst.", Modifier.Kind.HONOR),
        Modifier(HANDYWEG, "Handy weg", 1.3, 4, "moon", "Bildschirm aus bis zum Ende. Wer die App verlässt, verliert den Bonus.", Modifier.Kind.FOCUS),
        Modifier(DREISONG, "Drei-Song-Challenge", 1.5, 5, "hourglass", "Fertig, bevor drei Songs durch sind: 10:30 Minuten.", Modifier.Kind.TIMER),
        Modifier(BLINDDATE, "Blind Date", 1.5, 6, "dice", "Die Chronik wählt, du siehst die Quest erst nach dem Annehmen.", Modifier.Kind.RANDOM),
        Modifier(WAGER, "Doppelt oder nichts", 2.0, 7, "coin", "Glut-Einsatz: heute vor 20 Uhr erledigt = doppelt zurück.", Modifier.Kind.WAGER),
        Modifier(HARDCORE, "Hardcore-Woche", 2.0, 8, "skull", "Eine Woche ohne Schutzschilde und Mini-Versionen. Alles zählt doppelt.", Modifier.Kind.WEEK),
        Modifier(REPORTER, "Sportreporter", 1.3, 9, "horn", "Kommentiere dich selbst laut wie live im Stadion.", Modifier.Kind.HONOR),
        Modifier(TANZ, "Tanzpause", 1.5, 10, "boot", "Zehn Sekunden tanzen, der Bewegungssensor prüft mit.", Modifier.Kind.SENSOR),
        Modifier(JOKER, "Glückskarte", 1.5, 1, "clover", "Aus der Beute: einmalig ×1,5 auf eine Quest.", Modifier.Kind.LOOT),
    )
    private val byKey = all.associateBy { it.key }
    fun of(key: String): Modifier? = byKey[key]

    /** Modifier, die man direkt an eine Quest hängen kann (ohne Wette, Woche, Zufall). */
    val attachable = listOf(SOUNDTRACK, TELEFON, REPORTER)

    fun factorFor(mods: List<String>, durationSec: Int?): Double {
        var f = 1.0
        for (key in mods.distinct()) {
            val m = of(key) ?: continue
            if (m.kind == Modifier.Kind.WAGER || m.kind == Modifier.Kind.WEEK) continue
            if (key == DREISONG && (durationSec == null || durationSec > DREISONG_LIMIT_SEC)) continue
            f *= m.factor
        }
        return min(f, Rules.MOD_CAP)
    }
}
