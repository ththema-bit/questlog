package io.github.ththemabit.questlog.ai

import io.github.ththemabit.questlog.core.Chaos
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.data.Demo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Prüft die Prompts gegen einen echten Server, z. B. Ollama auf der AI-Box. Ohne Umgebungsvariablen
 * wird der Test übersprungen.
 *
 *     QUESTLOG_AI_URL=192.168.178.65:11434 QUESTLOG_AI_MODEL=qwen2.5:7b ./gradlew :app:testDebugUnitTest --tests '*LiveAiTest'
 *
 * Optional QUESTLOG_AI_KEY für Cloud-Dienste. Was das Modell geliefert hat und was die App daraus
 * liest, steht danach im Testbericht unter app/build/reports/tests/testDebugUnitTest (Standard-Ausgabe).
 */
class LiveAiTest {
    private val url = System.getenv("QUESTLOG_AI_URL")
    private val model = System.getenv("QUESTLOG_AI_MODEL")
    private val key = System.getenv("QUESTLOG_AI_KEY") ?: ""
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val save = Demo.save(today, zone, Chaos(narrators = true))
    private val game = Engine(zone).compute(save, System.currentTimeMillis())
    private val hour = Instant.now().atZone(zone).hour

    @Before
    fun needsServer() = assumeTrue("QUESTLOG_AI_URL und QUESTLOG_AI_MODEL setzen", !url.isNullOrBlank() && !model.isNullOrBlank())

    private fun client() = AiClient(url!!, model!!, key)

    @Test
    fun forgeBuildsQuestsFromText() = runBlocking {
        val history = listOf(ChatTurn(true, "Ich ziehe Ende Dezember um und muss vorher noch die Steuer fertig machen. Außerdem will ich zweimal die Woche joggen und die Fenster kann ich mir sparen."))
        var deltas = 0
        val raw = client().chat(GameMaster.forgeMessages(save, game, history, Persona.BUTLER), maxTokens = 2600, temperature = 0.6) { deltas++ }
        println("---- Rohantwort (gestreamt in $deltas Stücken) ----\n$raw")
        val reply = GameMaster.parseForge(raw, save, today)
        println("---- gelesen ----\n${reply.message}")
        reply.proposals.forEach { println(" • $it") }
        assertFalse("Quest-Liste unleserlich", reply.unreadable)
        assertTrue("keine Vorschläge", reply.proposals.isNotEmpty())
    }

    @Test
    fun questTextsFitTheQuest() = runBlocking {
        for (id in listOf("bad", "steuer")) {
            val q = save.quests.first { it.id == id }
            val raw = client().chat(GameMaster.questTextMessages(q, Persona.OMA), maxTokens = 1400, temperature = 0.85)
            println("---- ${q.title}: Rohantwort ----\n$raw")
            val texts = GameMaster.parseQuestTexts(raw, GameMaster.signature(q), Persona.OMA)
            println("---- gelesen ----\n$texts")
            assertNotNull("Texte für ${q.title} unleserlich", texts)
        }
    }

    @Test
    fun briefingAndSaga() = runBlocking {
        val brief = GameMaster.cleanText(client().chat(GameMaster.briefingMessages(game, Persona.SERGEANT, hour), maxTokens = 900, temperature = 0.9), 360)
        println("---- Briefing ----\n$brief")
        assertTrue("Briefing zu kurz", brief.length >= 12)
        val recap = game.recap
        if (recap != null) {
            val saga = GameMaster.cleanSaga(client().chat(GameMaster.sagaMessages(recap, Persona.CHRONIST, false), maxTokens = 1800, temperature = 0.9))
            println("---- Saga ----\n$saga")
            assertTrue("Saga ohne Absätze", saga.split("\n\n").size >= 2)
        }
    }
}
