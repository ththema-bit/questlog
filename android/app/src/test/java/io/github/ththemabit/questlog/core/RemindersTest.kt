package io.github.ththemabit.questlog.core

import io.github.ththemabit.questlog.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RemindersTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val day0 = LocalDate.of(2026, 11, 2)
    private fun at(d: Int, h: Int = 12) = day0.plusDays(d.toLong()).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun quietHoursWrapAroundMidnight() {
        assertTrue(Reminders.quiet(23, 22, 7))
        assertTrue(Reminders.quiet(3, 22, 7))
        assertFalse(Reminders.quiet(7, 22, 7))
        assertFalse(Reminders.quiet(12, 22, 7))
    }

    @Test
    fun windowOpenAndClosingNotes() {
        val q = Quest("bad", "Bad putzen", "bad", minDays = 4, maxDays = 9, createdAt = at(0, 8))
        val save = SaveData(quests = listOf(q), events = listOf(Done("e1", at(0), "bad")))
        val opened = Reminders.pending(save, Engine(zone).compute(save, at(4, 10)), 10)
        assertTrue(opened.any { it.key.startsWith("open-bad") && it.text.contains("Bad putzen") })
        val closing = Reminders.pending(save, Engine(zone).compute(save, at(8, 10)), 10)
        assertTrue(closing.any { it.key.startsWith("closing-bad") })
        val digest = Reminders.pending(save, Engine(zone).compute(save, at(8, 8)), 8)
        assertEquals(1, digest.count { it.key.startsWith("digest") })
    }

    @Test
    fun usesTextsOfTheGameMasterWhenThere() {
        val q = Quest("bad", "Bad putzen", "bad", minDays = 4, maxDays = 9, createdAt = at(0, 8))
        val texts = io.github.ththemabit.questlog.ai.QuestTexts(
            io.github.ththemabit.questlog.ai.GameMaster.signature(q), "chronist",
            intro = "Kalkdrachen!", reminder = "Die Kalkdrachen im Bad regen sich wieder.",
        )
        val base = SaveData(quests = listOf(q), events = listOf(Done("e1", at(0), "bad")))
        val withTexts = base.copy(
            settings = Settings(ai = AiConfig(enabled = true, baseUrl = "box:11434", model = "m")),
            storyCache = mapOf(io.github.ththemabit.questlog.ai.GameMaster.textsKey("bad") to io.github.ththemabit.questlog.ai.GameMaster.encode(texts)),
        )
        val note = Reminders.pending(withTexts, Engine(zone).compute(withTexts, at(4, 10)), 10).first { it.key.startsWith("open-bad") }
        assertEquals("Die Kalkdrachen im Bad regen sich wieder.", note.text)
        // KI aus: wieder die Vorlage
        val off = withTexts.copy(settings = withTexts.settings.copy(ai = withTexts.settings.ai.copy(enabled = false)))
        val plain = Reminders.pending(off, Engine(zone).compute(off, at(4, 10)), 10).first { it.key.startsWith("open-bad") }
        assertTrue(plain.text.contains("Bad putzen"))
    }
}
