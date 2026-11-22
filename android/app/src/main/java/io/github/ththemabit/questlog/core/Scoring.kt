package io.github.ththemabit.questlog.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Alles, was in die Punkte einer erledigten Quest eingeht. */
data class ScoreInput(
    val quest: Quest,
    val status: WindowStatus,
    val overdueDays: Int,
    val streak: Int,
    val chainIndex: Int,
    val bounty: Boolean,
    val roulette: Boolean,
    val comeback: Boolean,
    val mods: List<String>,
    val durationSec: Int?,
    val joker: Boolean,
    val holiday: Holiday?,
    val wedding: Boolean,
    val hardcore: Boolean,
    val seasonBonus: Int,
)

/** Ergebnis: Punkte, „roh“ ohne Saisonbonus (für Teilaufgaben) und der Beipackzettel. */
data class Scored(val points: Int, val raw: Double, val lines: List<Line>, val capped: Boolean) {
    fun asResult() = ScoreResult(points, lines, capped)
}

object Scoring {
    fun compute(i: ScoreInput): Scored {
        val q = i.quest
        val lines = ArrayList<Line>()
        val base = Rules.base(q.effortMin)
        lines += Line("Aufwand ${q.effortMin} min", base.roundToInt().toString())
        val fw = Rules.importanceFactor(q.importance)
        lines += Line("Wichtigkeit ${q.importance}", x(fw))
        val fu = Rules.dreadFactor(q.dread)
        lines += Line("Überwindung ${q.dread}", x(fu))
        val ft = when (i.status) {
            WindowStatus.EARLY -> Rules.TIMING_EARLY
            WindowStatus.OVERDUE -> Rules.overdueFactor(i.overdueDays)
            else -> 1.0
        }
        lines += Line(timingLabel(q, i.status, i.overdueDays), x(ft))

        var bonus = 1.0
        fun add(label: String, f: Double) {
            bonus *= f
            lines += Line(label, x(f))
        }
        if (i.streak > 0) add("Serie Tag ${i.streak}", Rules.streakFactor(i.streak))
        if (i.chainIndex > 0) add("Combo ${i.chainIndex + 1}", Rules.comboFactor(i.chainIndex))
        if (i.bounty) add("Kopfgeld", Rules.BOUNTY)
        if (i.roulette) add("Roulette", Rules.ROULETTE)
        if (i.comeback) add("Comeback", Rules.COMEBACK)
        val fm = Modifiers.factorFor(i.mods, i.durationSec)
        val names = i.mods.mapNotNull { Modifiers.of(it) }.filter { it.kind != Modifier.Kind.WAGER && it.kind != Modifier.Kind.WEEK }
        if (names.isNotEmpty()) {
            val missedSong = Modifiers.DREISONG in i.mods && (i.durationSec == null || i.durationSec > Modifiers.DREISONG_LIMIT_SEC)
            val shown = names.filterNot { missedSong && it.key == Modifiers.DREISONG }.joinToString(", ") { it.name }
            if (shown.isNotEmpty()) add("Karten: $shown", fm)
            if (missedSong) lines += Line("Drei-Song-Challenge verpasst", "×1,00")
        }
        if (i.joker) add("Glückskarte", 1.5)
        i.holiday?.let { add("Feiertag: ${it.name}", it.mult) }
        if (i.wedding) add("Ehebonus", Rules.WEDDING)
        if (i.hardcore) add("Hardcore-Woche", Rules.HARDCORE)
        var capped = false
        if (bonus > Rules.BONUS_CAP) {
            bonus = Rules.BONUS_CAP
            capped = true
            lines += Line("Deckel für Boni", "max ×5")
        }
        val raw = base * fw * fu * ft * bonus
        if (i.seasonBonus > 0) lines += Line("Saisonauftakt", "+${i.seasonBonus}")
        val points = max(1, raw.roundToInt()) + i.seasonBonus
        return Scored(points, raw, lines, capped)
    }

    fun timingLabel(q: Quest, status: WindowStatus, overdue: Int): String = when (status) {
        WindowStatus.EARLY -> "Zu früh"
        WindowStatus.OVERDUE -> "$overdue ${if (overdue == 1) "Tag" else "Tage"} überfällig"
        else -> if (q.recurring) "Im Fenster" else "Pünktlich"
    }

    fun x(f: Double): String = "×" + String.format(Locale.GERMANY, "%.2f", f)
}

/** Werte zum Zeitpunkt „jetzt“, damit die Oberfläche Punkte vorab zeigen kann. */
class ScoreSnapshot(
    private val streakIfDoneNow: Int,
    private val chainIndexNow: Int,
    private val bountyId: String?,
    private val comebackNow: Boolean,
    private val hardcoreNow: Boolean,
    private val seasonBonusNow: Int,
    private val holidayFor: (Quest) -> Holiday?,
    private val weddingFor: (String) -> Boolean,
    private val jokers: Int,
) {
    fun preview(qs: QuestState, mods: List<String>, durationSec: Int?, roulette: Boolean): ScoreResult {
        val input = ScoreInput(
            quest = qs.quest,
            status = qs.status,
            overdueDays = qs.overdueDays,
            streak = streakIfDoneNow,
            chainIndex = chainIndexNow,
            bounty = qs.isBounty,
            roulette = roulette,
            comeback = comebackNow,
            mods = mods.filter { it != Modifiers.JOKER },
            durationSec = durationSec,
            joker = Modifiers.JOKER in mods && jokers > 0,
            holiday = holidayFor(qs.quest),
            wedding = weddingFor(qs.quest.id),
            hardcore = hardcoreNow,
            seasonBonus = seasonBonusNow,
        )
        return Scoring.compute(input).asResult()
    }

    fun mini(): Int = Rules.miniPoints(streakIfDoneNow) * (if (comebackNow) 2 else 1)

    fun full(qs: QuestState): Int = preview(qs, emptyList(), null, false).points

    val bounty: String? get() = bountyId
}
