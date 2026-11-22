package io.github.ththemabit.questlog.core

import java.time.LocalDate
import java.time.YearMonth

/** Alles, was die Oberfläche anzeigt. Wird von der Engine aus dem Spielstand berechnet. */
data class GameState(
    val now: Long,
    val today: LocalDate,
    val player: PlayerState,
    val quests: List<QuestState>,
    val board: Board,
    val results: Map<String, DoneResult>,
    val days: Map<LocalDate, DayStat>,
    val weeks: Map<LocalDate, Int>,
    val months: Map<YearMonth, Int>,
    val season: Season,
    val recap: Recap?,
    val records: Records,
    val achievements: List<AchievementState>,
    val skills: List<SkillState>,
    val inventory: Inventory,
    val purchases: List<Buy>,
    val lootLog: List<Pair<Long, Loot>>,
    val bountyQuestId: String?,
    val holiday: Holiday?,
    val gerald: GeraldState,
    val curse: Curse?,
    val dust: Int,
    val marriages: List<Marriage>,
    val taxNotices: List<TaxNotice>,
    val wager: WagerState?,
    val paused: Boolean,
    val hardcore: Boolean,
    val firstDay: LocalDate,
    private val snapshot: ScoreSnapshot,
) {
    private val questById = quests.associateBy { it.quest.id }
    fun quest(id: String): QuestState? = questById[id]

    /** Punkte-Vorschau für „jetzt erledigen“ mit bestimmten Karten. */
    fun preview(questId: String, mods: List<String> = emptyList(), durationSec: Int? = null, roulette: Boolean = false): ScoreResult? {
        val qs = questById[questId] ?: return null
        return snapshot.preview(qs, mods, durationSec, roulette)
    }
}

data class PlayerState(
    val xp: Long,
    val level: Int,
    val levelStartXp: Long,
    val nextLevelXp: Long,
    val rank: Rank,
    val nextRank: Rank?,
    val glut: Int,
    val glutBest: Int,
    val glutFloor: Int,
    val streak: Int,
    /** heute ist noch nichts passiert und gestern auch nicht: heute zählt */
    val streakAtRisk: Boolean,
    val bestStreak: Int,
    val shields: Int,
    val coins: Int,
    val comebackReady: Boolean,
    val comboIndex: Int,
    val comboUntil: Long?,
    val todayXp: Int,
    val todayCount: Int,
    val activeDays: Int,
) {
    val levelProgress: Float
        get() = if (nextLevelXp <= levelStartXp) 1f else ((xp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)
}

enum class WindowStatus { EARLY, OPEN, OVERDUE, DONE }

data class QuestState(
    val quest: Quest,
    val status: WindowStatus,
    val daysSince: Int,
    val opensIn: Int?,
    val closesIn: Int?,
    val overdueDays: Int,
    val lastDone: Long?,
    val doneCount: Int,
    val bestSec: Int?,
    val avgSec: Int?,
    val hitsThisCycle: Set<String>,
    val bossHpLeft: Int,
    val bossHpMax: Int,
    val bossRage: Double,
    /** 0 = sauber, 1..3 = Staub, Spinnweben, Risse */
    val dust: Int,
    val isBounty: Boolean,
    val cursed: Boolean,
    val partnerId: String?,
    val preview: Int,
    val miniPreview: Int,
    val urgency: Double,
    val history: List<String>,
)

data class Board(
    val overdue: List<QuestState>,
    val open: List<QuestState>,
    val bosses: List<QuestState>,
    val early: List<QuestState>,
    val done: List<QuestState>,
)

data class Line(val label: String, val value: String)

data class ScoreResult(val points: Int, val lines: List<Line>, val capped: Boolean)

data class DoneResult(
    val eventId: String,
    val questId: String,
    val questTitle: String,
    val at: Long,
    val points: Int,
    val lines: List<Line>,
    val mini: Boolean,
    val hit: Boolean,
    val finished: Boolean,
    val bossDefeated: Boolean,
    val loot: Loot?,
    val glutBefore: Int,
    val glutAfter: Int,
    val wagerWon: Int?,
    val newPb: Boolean,
    val early: Boolean,
)

data class DayStat(val xp: Int, val count: Int, val mini: Int, val byCategory: Map<String, Int>)

data class Season(
    val weekStart: LocalDate,
    val xp: Int,
    val goal: Int,
    val ghostXp: Int,
    val daysLeft: Int,
    val hardcore: Boolean,
    val lastWeekXp: Int,
) {
    val progress: Float get() = if (goal <= 0) 0f else (xp.toFloat() / goal).coerceIn(0f, 1f)
    val ghostDelta: Int get() = xp - ghostXp
}

data class Recap(
    val weekStart: LocalDate,
    val xp: Int,
    val goal: Int,
    val activeDays: Int,
    val doneCount: Int,
    val bestDay: Pair<LocalDate, Int>?,
    val topQuest: Pair<String, Int>?,
    val bestMoment: DoneResult?,
    val glutStart: Int,
    val glutEnd: Int,
    val prevXp: Int,
    val tax: Int,
    val geraldXp: Int,
    val categories: Map<String, Int>,
    val bossesDefeated: List<String>,
    val newRecord: Boolean,
) {
    val reached: Boolean get() = xp >= goal
}

data class Records(
    val bestDay: Pair<LocalDate, Int>?,
    val bestWeek: Pair<LocalDate, Int>?,
    val bestMonth: Pair<YearMonth, Int>?,
    val longestStreak: Int,
    val highestGlut: Pair<LocalDate, Int>?,
    val mostQuestsDay: Pair<LocalDate, Int>?,
    val fastest: List<Triple<String, String, Int>>,
)

data class AchievementState(val def: Achievement, val unlockedAt: Long?, val progress: Pair<Long, Long>?)

data class SkillState(val category: Category, val xp: Long, val level: Int, val levelStartXp: Long, val nextLevelXp: Long) {
    val progress: Float
        get() = if (nextLevelXp <= levelStartXp) 1f else ((xp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)
}

enum class LootKind { SHIELD, COINS, GLUT, JOKER, TROPHY }

data class Loot(val kind: LootKind, val amount: Int, val name: String, val rare: Boolean)

data class Inventory(val shields: Int, val jokers: Int, val trophies: List<Pair<String, Long>>)

data class GeraldState(val todayXp: Int, val weekXp: Int, val message: String, val lie: Boolean, val beatenWeeks: Int)

data class Curse(val questId: String, val questTitle: String, val overdueDays: Int)

data class Marriage(val firstId: String, val secondId: String, val since: Long, val count: Int)

data class TaxNotice(val weekStart: LocalDate, val decay: Int, val drain: Int) {
    val total: Int get() = decay + drain
    val id: String get() = "steuer-$weekStart"
}

data class WagerState(val eventId: String, val questId: String, val stake: Int, val deadline: Long, val resolved: Boolean, val won: Boolean)
