package io.github.ththemabit.questlog.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val engine = Engine(zone)
    private val day0: LocalDate = LocalDate.of(2026, 11, 2) // ein Montag

    private fun at(day: Int, hour: Int = 12, minute: Int = 0): Long =
        day0.plusDays(day.toLong()).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun quest(id: String, min: Int? = 4, max: Int? = 9, effort: Int = 20, imp: Int = 3, dread: Int = 3, cat: String = "haushalt", subtasks: List<Subtask> = emptyList(), boss: Boolean = false) =
        Quest(id = id, title = id, category = cat, importance = imp, effortMin = effort, dread = dread, minDays = min, maxDays = max,
            subtasks = subtasks, boss = boss, createdAt = at(0, 8))

    private fun done(id: String, q: String, t: Long, mini: Boolean = false, mods: List<String> = emptyList()) = Done(id, t, q, mini = mini, mods = mods)

    @Test
    fun scoringMatchesConceptExample() {
        val q = quest("bad", effort = 20, imp = 4, dread = 4)
        val s = Scoring.compute(
            ScoreInput(q, WindowStatus.OPEN, 0, streak = 12, chainIndex = 0, bounty = false, roulette = false, comeback = false,
                mods = listOf(Modifiers.SOUNDTRACK), durationSec = null, joker = false, holiday = null, wedding = false,
                hardcore = false, seasonBonus = 0)
        )
        assertEquals(76, s.points)
    }

    @Test
    fun windowMovesThroughEarlyOpenOverdue() {
        val q = quest("bad")
        val save = SaveData(quests = listOf(q), events = listOf(done("e1", "bad", at(0))))
        assertEquals(WindowStatus.EARLY, engine.compute(save, at(2)).quest("bad")!!.status)
        assertEquals(2, engine.compute(save, at(2)).quest("bad")!!.opensIn)
        val open = engine.compute(save, at(6)).quest("bad")!!
        assertEquals(WindowStatus.OPEN, open.status)
        assertEquals(3, open.closesIn)
        val late = engine.compute(save, at(11)).quest("bad")!!
        assertEquals(WindowStatus.OVERDUE, late.status)
        assertEquals(2, late.overdueDays)
    }

    @Test
    fun newQuestIsOpenImmediately() {
        val state = engine.compute(SaveData(quests = listOf(quest("bad"))), at(0, 9))
        assertEquals(WindowStatus.OPEN, state.quest("bad")!!.status)
    }

    @Test
    fun tooEarlyGivesQuarterAndResetsWindow() {
        val q = quest("bad", min = 4, max = 9)
        val save = SaveData(quests = listOf(q), events = listOf(done("e1", "bad", at(0)), done("e2", "bad", at(1))))
        val s = engine.compute(save, at(1, 13))
        val r = s.results.getValue("e2")
        assertTrue(r.early)
        assertTrue(r.lines.any { it.label == "Zu früh" && it.value == "×0,25" })
        assertEquals(WindowStatus.EARLY, s.quest("bad")!!.status)
    }

    @Test
    fun glutDecaysFifteenPercentPerDay() {
        val q = quest("x", min = null, max = null, effort = 100, imp = 3, dread = 3)
        val save = SaveData(quests = listOf(q), events = listOf(done("e1", "x", at(0))))
        val first = engine.compute(save, at(0, 13))
        val pts = first.results.getValue("e1").points
        assertEquals(pts, first.player.glut)
        val next = engine.compute(save, at(1, 13))
        assertEquals(Math.round(pts * 0.85).toInt(), next.player.glut)
        assertEquals(pts.toLong(), next.player.xp)
    }

    @Test
    fun glutNeverFallsBelowTenPercentOfBest() {
        val q = quest("x", min = null, max = null, effort = 100)
        val save = SaveData(quests = listOf(q), events = listOf(done("e1", "x", at(0))))
        val s = engine.compute(save, at(40))
        val best = s.player.glutBest
        assertTrue(s.player.glut >= (best * 0.1).toInt())
        assertTrue(s.player.glut <= (best * 0.1).toInt() + 1)
    }

    @Test
    fun overdueQuestsDrainGlutByImportance() {
        val big = quest("big", min = null, max = null, effort = 400)
        val late = quest("late", min = 1, max = 2, imp = 4)
        val save = SaveData(quests = listOf(big, late), events = listOf(done("e1", "big", at(0)), done("e2", "late", at(0, 13))))
        val s = engine.compute(save, at(7, 13))
        // ab Tag 3 überfällig: Tage 3, 4, 5 und 6 enden überfällig, je Wichtigkeit 4 × 5 Glut
        assertEquals(80, s.taxNotices.first().drain)
        assertEquals(WindowStatus.OVERDUE, s.quest("late")!!.status)
    }

    @Test
    fun oneMissedDayKeepsTheStreak() {
        val daily = quest("x", min = 0, max = 1)
        val save = SaveData(quests = listOf(daily), events = listOf(done("e1", "x", at(0)), done("e2", "x", at(1)), done("e3", "x", at(3))))
        val s = engine.compute(save, at(3, 13))
        assertEquals(3, s.player.streak)
        assertEquals(1, s.player.shields)
    }

    @Test
    fun twoMissedDaysUseAShieldThenBreak() {
        val daily = quest("x", min = 0, max = 1)
        val withShield = SaveData(quests = listOf(daily), events = listOf(done("e1", "x", at(0)), done("e2", "x", at(3))))
        // Tag 1 und 2 frei: Tag 2 kostet den Startschild
        val s = engine.compute(withShield, at(3, 13))
        assertEquals(2, s.player.streak)
        assertEquals(0, s.player.shields)
        val noShield = SaveData(quests = listOf(daily), events = listOf(done("e1", "x", at(0)), done("e2", "x", at(4))))
        val b = engine.compute(noShield, at(4, 13))
        assertEquals(1, b.player.streak)
    }

    @Test
    fun comebackDoublesFirstQuestAfterThreeFreeDays() {
        val daily = quest("x", min = 0, max = 30)
        val save = SaveData(quests = listOf(daily), events = listOf(done("e1", "x", at(0)), done("e2", "x", at(5))))
        val s = engine.compute(save, at(5, 13))
        assertTrue(s.results.getValue("e2").lines.any { it.label == "Comeback" })
        assertFalse(s.results.getValue("e1").lines.any { it.label == "Comeback" })
    }

    @Test
    fun miniKeepsStreakButNotTheWindow() {
        val q = quest("spuele", min = 1, max = 2, effort = 10)
        val save = SaveData(quests = listOf(q), events = listOf(done("e1", "spuele", at(0)), done("e2", "spuele", at(4), mini = true)))
        val s = engine.compute(save, at(4, 13))
        assertEquals(WindowStatus.OVERDUE, s.quest("spuele")!!.status)
        assertTrue(s.results.getValue("e2").mini)
        assertTrue(s.player.todayXp > 0)
    }

    @Test
    fun subtasksAddUpAndFinishTheBoss() {
        val subs = listOf(Subtask("a", "A"), Subtask("b", "B"), Subtask("c", "C"))
        val boss = quest("keller", min = null, max = null, effort = 120, subtasks = subs, boss = true)
        val events = listOf(
            Hit("h1", at(0, 10), "keller", "a"),
            Hit("h2", at(0, 11), "keller", "b"),
            Hit("h3", at(0, 12), "keller", "c"),
        )
        val s = engine.compute(SaveData(quests = listOf(boss), events = events), at(0, 13))
        val last = s.results.getValue("h3")
        assertTrue(last.finished)
        assertTrue(last.bossDefeated)
        assertNotNull("Boss gibt immer Beute", last.loot)
        assertEquals(WindowStatus.DONE, s.quest("keller")!!.status)
        assertTrue(s.achievements.first { it.def.id == "boss_1" }.unlockedAt != null)
    }

    @Test
    fun undoIsJustRemovingTheEvent() {
        val q = quest("x", min = null, max = null)
        val save = SaveData(quests = listOf(q.copy(minDays = 0, maxDays = 3)), events = listOf(done("e1", "x", at(0)), done("e2", "x", at(0, 15))))
        val before = engine.compute(save, at(0, 16)).player.xp
        val after = engine.compute(save.copy(events = save.events.dropLast(1)), at(0, 16)).player.xp
        assertTrue(after < before)
        assertEquals(engine.compute(save.copy(events = save.events.take(1)), at(0, 16)).player.xp, after)
    }

    @Test
    fun pausedDaysFreezeWindowAndGlut() {
        val q = quest("bad", min = 4, max = 9)
        val save = SaveData(
            quests = listOf(q), events = listOf(done("e1", "bad", at(0))),
            pauses = listOf(PauseRange(day0.plusDays(1).toString(), day0.plusDays(20).toString())),
        )
        val s = engine.compute(save, at(21, 13))
        // nur Tag 21 zählt: ein Tag seit dem Erledigen
        assertEquals(1, s.quest("bad")!!.daysSince)
        assertEquals(WindowStatus.EARLY, s.quest("bad")!!.status)
        val pts = s.results.getValue("e1").points
        assertEquals(Math.round(pts * 0.85).toInt(), s.player.glut)
    }

    @Test
    fun wagerWinsDoubleAndLosesAfterDeadline() {
        val big = quest("big", min = null, max = null, effort = 200)
        val target = quest("t", min = 0, max = 5)
        val deadline = at(0, 20)
        val base = listOf(done("e1", "big", at(0, 9)), Wager("w1", at(0, 10), "t", deadline))
        val won = engine.compute(SaveData(quests = listOf(big, target), events = base + done("e2", "t", at(0, 11))), at(0, 21))
        assertEquals(true, won.wager?.won)
        assertNotNull(won.results.getValue("e2").wagerWon)
        val lost = engine.compute(SaveData(quests = listOf(big, target), events = base), at(0, 21))
        assertEquals(false, lost.wager?.won)
        assertEquals(true, lost.wager?.resolved)
    }

    @Test
    fun levelsFollowTheCurve() {
        assertEquals(1, Rules.levelForXp(0))
        assertEquals(1, Rules.levelForXp(99))
        assertEquals(2, Rules.levelForXp(100))
        assertEquals(3, Rules.levelForXp(300))
        assertEquals(10, Rules.levelForXp(4500))
    }

    @Test
    fun seasonGoalAndStartBonus() {
        val q = quest("x", min = null, max = null)
        val s = engine.compute(SaveData(quests = listOf(q.copy(minDays = 0, maxDays = 3)), events = listOf(done("e1", "x", at(0)))), at(0, 13))
        assertEquals(300, s.season.goal)
        assertTrue(s.results.getValue("e1").lines.any { it.label == "Saisonauftakt" && it.value == "+15" })
    }

    @Test
    fun bountyDoublesOncePerDay() {
        val a = quest("a", min = 0, max = 3, dread = 5)
        val s = engine.compute(SaveData(quests = listOf(a), events = listOf(done("e1", "a", at(0, 10)), done("e2", "a", at(0, 11)))), at(0, 12))
        assertTrue(s.results.getValue("e1").lines.any { it.label == "Kopfgeld" })
        assertFalse(s.results.getValue("e2").lines.any { it.label == "Kopfgeld" })
    }

    @Test
    fun comboBuildsWithinFortyFiveMinutes() {
        val a = quest("a", min = 0, max = 3)
        val b = quest("b", min = 0, max = 3)
        val c = quest("c", min = 0, max = 3)
        val s = engine.compute(SaveData(quests = listOf(a, b, c), events = listOf(
            done("e1", "a", at(0, 10, 0)), done("e2", "b", at(0, 10, 30)), done("e3", "c", at(0, 12, 0)),
        )), at(0, 13))
        assertTrue(s.results.getValue("e2").lines.any { it.label == "Combo 2" })
        assertFalse(s.results.getValue("e3").lines.any { it.label.startsWith("Combo") })
    }

    @Test
    fun firstQuestUnlocksFirstAchievement() {
        val q = quest("a", min = 0, max = 3)
        val s = engine.compute(SaveData(quests = listOf(q), events = listOf(done("e1", "a", at(0)))), at(0, 13))
        assertNotNull(s.achievements.first { it.def.id == "erste_glut" }.unlockedAt)
        assertNull(s.achievements.first { it.def.id == "funken_10k" }.unlockedAt)
    }

    @Test
    fun saveDataRoundTrip() {
        val q = quest("a", subtasks = listOf(Subtask("s1", "Eins")))
        val save = SaveData(
            quests = listOf(q),
            events = listOf(done("e1", "a", at(0), mods = listOf("soundtrack")), Hit("h1", at(1), "a", "s1"), Buy("b1", at(2), "r1", "Pizza", 800), Wager("w1", at(3), "a", at(3, 20))),
            rewards = listOf(Reward("r1", "Pizza", 800)),
            pauses = listOf(PauseRange("2026-12-01", null)),
        )
        val back = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(save, back)
    }

    @Test
    fun questSmithUnderstandsCommonPhrases() {
        val smith = HeuristicQuestSmith()
        val today = LocalDate.of(2026, 11, 22)
        val bad = smith.parse("Bad putzen alle 7 Tage 20 min nervig", today)
        assertEquals("Bad putzen", bad.title)
        assertEquals("bad", bad.category)
        assertEquals(7, bad.maxDays)
        assertEquals(4, bad.minDays)
        assertEquals(20, bad.effortMin)
        assertEquals(4, bad.dread)
        val keller = smith.parse("Keller: Regal, Kisten, Fahrrad", today)
        assertTrue(keller.boss)
        assertEquals(3, keller.subtasks.size)
        assertEquals("Keller", keller.title)
        val steuer = smith.parse("Steuer bis 30.11. !!", today)
        assertEquals("2026-11-30", steuer.dueDate)
        assertEquals(5, steuer.importance)
        assertEquals("papierkram", steuer.category)
        val weekly = smith.parse("Müll rausbringen wöchentlich", today)
        assertEquals(7, weekly.maxDays)
    }

    @Test
    fun fullMoonComesOncePerLunarMonth() {
        val nov = (1..30).map { LocalDate.of(2026, 11, it) }.filter { Holidays.isFullMoon(it) }
        assertTrue(nov.size in 1..2)
    }

    @Test
    fun storytellerFillsPlaceholders() {
        val t = TemplateStoryteller()
        val line = t.line(Persona.OMA, Moment.OVERDUE, StoryContext(quest = "Bad putzen", days = 3))
        assertTrue(line.contains("Bad putzen"))
        assertTrue(line.contains("3"))
    }
}
