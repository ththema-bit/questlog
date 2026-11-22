package io.github.ththemabit.questlog.ai

import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.SaveCodec
import io.github.ththemabit.questlog.data.Demo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Kleine lokale Modelle antworten oft unsauber. Hier wird geprüft, was davon heil ankommt. */
class GameMasterTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
    private val save = Demo.save(today, zone)
    private val game = Engine(zone).compute(save, now)

    @Test
    fun readsNewQuestsAndClampsValues() {
        val raw = """
            <think>Die Person zieht um. Ich plane zwei Quests.</think>
            Ein Umzug! Die Gilde schickt dir zwei Aufträge.
            ###QUESTS
            ```json
            [{"title":"Umzugskartons besorgen","category":"sonstiges","importance":9,"effortMin":"30 min","dread":0,"minDays":null,"maxDays":null,"dueDate":"2026-11-28","mini":"Beim Supermarkt fragen","subtasks":[],"boss":false,"intro":"Kartons, so weit das Auge reicht."},
             {"title":"Umzug","category":"Haushalt","importance":5,"effortMin":900,"dread":5,"minDays":7,"maxDays":3,"subtasks":["Packen","Transporter mieten","Schlüssel abgeben"],"boss":true,"intro":"Der Umzugsdrache erwacht."},]
            ```
        """.trimIndent()
        val r = GameMaster.parseForge(raw, save, today)
        assertEquals("Ein Umzug! Die Gilde schickt dir zwei Aufträge.", r.message)
        assertFalse(r.unreadable)
        assertEquals(2, r.proposals.size)
        val boxes = r.proposals[0] as Proposal.New
        assertEquals(5, boxes.draft.importance)
        assertEquals(30, boxes.draft.effortMin)
        assertEquals(1, boxes.draft.dread)
        assertEquals("2026-11-28", boxes.draft.dueDate)
        assertEquals("Beim Supermarkt fragen", boxes.draft.mini)
        assertEquals("Kartons, so weit das Auge reicht.", boxes.intro)
        val move = r.proposals[1] as Proposal.New
        assertEquals("haushalt", move.draft.category)
        assertEquals(480, move.draft.effortMin)
        assertEquals(3, move.draft.minDays)
        assertEquals(7, move.draft.maxDays)
        assertTrue(move.draft.boss)
        assertEquals(listOf("Packen", "Transporter mieten", "Schlüssel abgeben"), move.draft.subtasks)
    }

    @Test
    fun changesAndArchivesExistingQuests() {
        val raw = """
            Erledigt, die Schriftrollen sind neu geschrieben.
            ###QUESTS
            [{"id":"q1","minDays":5,"maxDays":10},{"id":"q7","archive":true},{"id":"q99","archive":true},{"title":"Staubsaugen","importance":4},{"id":"12","title":"Steuererklärung","dueDate":"2026-12-15"}]
        """.trimIndent()
        val r = GameMaster.parseForge(raw, save, today)
        assertEquals(4, r.proposals.size)
        val bath = r.proposals[0] as Proposal.Change
        assertEquals("bad", bath.questId)
        assertEquals(5, bath.after.minDays)
        assertEquals(10, bath.after.maxDays)
        assertEquals("alle 5 bis 10 Tage", bath.summary)
        assertEquals("fenster", (r.proposals[1] as Proposal.Archive).questId)
        val vacuum = r.proposals[2] as Proposal.Change
        assertEquals("saugen", vacuum.questId)
        assertEquals(4, vacuum.after.importance)
        val tax = r.proposals[3] as Proposal.Change
        assertEquals("steuer", tax.questId)
        assertEquals("2026-12-15", tax.after.dueDate)
        assertTrue(tax.after.boss)
        assertEquals(5, tax.after.subtasks.size)
    }

    @Test
    fun unchangedOrDuplicateEntriesAreDropped() {
        val raw = "Gut.\n###QUESTS\n[{\"title\":\"Dehnen\"},{\"title\":\"Zahnseide\"},{\"title\":\"zahnseide\"}]"
        val r = GameMaster.parseForge(raw, save, today)
        assertEquals(listOf("Zahnseide"), r.proposals.map { it.label })
    }

    @Test
    fun salvagesCutOffAnswers() {
        val raw = "Hier!\n###QUESTS\n[{\"title\":\"Zimmerpflanzen umtopfen\",\"category\":\"garten\",\"effortMin\":40,\"intro\":\"Die {Wurzeln} drängen.\"},{\"title\":\"Keller auf"
        val r = GameMaster.parseForge(raw, save, today)
        assertEquals(1, r.proposals.size)
        val plant = r.proposals[0] as Proposal.New
        assertEquals("garten", plant.draft.category)
        assertEquals("Die {Wurzeln} drängen.", plant.intro)
        assertFalse(r.unreadable)
    }

    @Test
    fun findsListWithoutMarker() {
        val raw = "Gern! Hier deine Quests:\n```json\n[{\"title\":\"Zahnarzt anrufen\",\"importance\":4}]\n```"
        val r = GameMaster.parseForge(raw, save, today)
        assertEquals("Gern! Hier deine Quests:", r.message)
        val dentist = r.proposals.single() as Proposal.New
        assertEquals("selbst", dentist.draft.category)
        assertEquals(4, dentist.draft.importance)
    }

    @Test
    fun plainAnswerHasNoProposals() {
        val text = "Nimm dir zuerst das Bad vor [das Fenster schließt bald]."
        val r = GameMaster.parseForge(text, save, today)
        assertEquals(text, r.message)
        assertTrue(r.proposals.isEmpty())
        assertFalse(r.unreadable)
    }

    @Test
    fun brokenListIsReported() {
        val r = GameMaster.parseForge("Moment.\n###QUESTS\nnicht mal JSON", save, today)
        assertEquals("Moment.", r.message)
        assertTrue(r.unreadable)
        assertTrue(r.proposals.isEmpty())
    }

    @Test
    fun questNumbersStayPutWhenSomethingIsArchived() {
        val archived = save.copy(quests = save.quests.map { if (it.id == "saugen") it.copy(archived = true) else it })
        val idx = GameMaster.questIndex(archived).toMap()
        assertEquals("bad", idx["q1"]!!.id)
        assertNull(idx["q2"])
        assertEquals("spuele", idx["q3"]!!.id)
        // Archivieren über eine alte Nummer trifft keine falsche Quest
        val r = GameMaster.parseForge("Weg damit.\n###QUESTS\n[{\"id\":\"q2\",\"archive\":true}]", archived, today)
        assertTrue(r.proposals.isEmpty())
    }

    @Test
    fun forgePromptListsQuestsWithoutStrayIndent() {
        val msgs = GameMaster.forgeMessages(save, game, listOf(ChatTurn(true, "Was soll ich tun?")), Persona.CHRONIST)
        val sys = msgs.first().content
        assertEquals("system", msgs.first().role)
        assertTrue(sys.contains("q1: Bad putzen"))
        assertTrue(sys.contains("q12: Steuererklärung"))
        assertTrue(sys.contains(GameMaster.MARK))
        assertFalse(sys.lines().any { it.startsWith("    ") })
        assertEquals(ChatMessage("user", "Was soll ich tun?"), msgs.last())
        assertTrue(GameMaster.system(Persona.BUTLER).contains("„Sie“"))
    }

    @Test
    fun readsQuestTexts() {
        val q = save.quests.first { it.id == "bad" }
        val raw = "<think>…</think>Klar, hier:\n{\"intro\":\"Im Bad wuchern Kalkdrachen.\",\"victory\":[\"Glänzt!\",\"Der Drache weicht.\",\"Sauber!\"],\"reminder\":\"Das Bad ruft.\",\"overdue\":\"Das Bad wartet geduldig.\",\"mini\":\"Ein Waschbecken ist auch ein Sieg.\",\"bossIntro\":null}"
        val t = GameMaster.parseQuestTexts(raw, GameMaster.signature(q), Persona.OMA)!!
        assertEquals("Im Bad wuchern Kalkdrachen.", t.intro)
        assertEquals(3, t.victory.size)
        assertNull(t.bossIntro)
        assertEquals("oma", t.persona)
        val withTexts = save.copy(storyCache = mapOf(GameMaster.textsKey("bad") to GameMaster.encode(t)))
        assertEquals(t, GameMaster.textsFor(withTexts, q))
        // Nach dem Umbenennen passt der Text nicht mehr und wird neu geschrieben
        assertNull(GameMaster.textsFor(withTexts, q.copy(title = "Badezimmer schrubben")))
        assertFalse(GameMaster.needsTexts(withTexts, q, Persona.OMA))
        assertTrue(GameMaster.needsTexts(withTexts, q, Persona.BUTLER))
        assertTrue(GameMaster.needsTexts(withTexts.copy(storyCache = mapOf(GameMaster.textsKey("bad") to GameMaster.encode(t.copy(partial = true)))), q, Persona.OMA))
        assertNull(GameMaster.parseQuestTexts("Leider kann ich das nicht.", "x", Persona.CHRONIST))
    }

    @Test
    fun briefingBelongsToToday() {
        val tag = GameMaster.briefingTag(game, Persona.CHRONIST, 9)
        val b = Briefing(tag, today.toString(), "Guten Morgen, das Bad wartet.")
        val s = save.copy(storyCache = mapOf(GameMaster.BRIEF_KEY to GameMaster.encodeBriefing(b)))
        assertEquals("Guten Morgen, das Bad wartet.", GameMaster.briefing(s, today))
        assertEquals("Guten Morgen, das Bad wartet.", GameMaster.morningBriefing(s, today))
        assertNull(GameMaster.briefing(s, today.plusDays(1)))
        assertEquals(tag, GameMaster.briefingTag(s))
        val evening = save.copy(storyCache = mapOf(GameMaster.BRIEF_KEY to GameMaster.encodeBriefing(b.copy(tag = GameMaster.briefingTag(game, Persona.CHRONIST, 19)))))
        assertNull(GameMaster.morningBriefing(evening, today))
        assertEquals("nacht", GameMaster.dayPart(3))
        assertEquals("abend", GameMaster.dayPart(20))
    }

    @Test
    fun sagaGetsParagraphs() {
        assertEquals(listOf("Es war einmal.", "Dann kam das Bad."), GameMaster.cleanSaga("```\nEs war einmal.\n\nDann kam das Bad.\n```").split("\n\n"))
        assertEquals(3, GameMaster.cleanSaga("Höret!\nDas Bad glänzte.\nUnd die Glut stieg.").split("\n\n").size)
        assertEquals("Sei gegrüßt.", GameMaster.cleanText("<think>x</think>„Sei gegrüßt.“", 100))
    }

    @Test
    fun keyStaysOutOfBackups() {
        val s = save.copy(settings = save.settings.copy(ai = AiConfig(enabled = true, baseUrl = "box:11434", model = "m", apiKey = "sk-geheim")))
        val exported = SaveCodec.encodePretty(SaveCodec.forExport(s))
        assertFalse(exported.contains("sk-geheim"))
        val back = SaveCodec.keepKey(SaveCodec.decode(exported), s)
        assertEquals("sk-geheim", back.settings.ai.apiKey)
        assertEquals("m", back.settings.ai.model)
        assertTrue(back.settings.ai.ready)
    }
}
