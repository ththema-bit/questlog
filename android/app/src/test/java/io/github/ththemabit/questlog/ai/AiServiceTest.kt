package io.github.ththemabit.questlog.ai

import com.sun.net.httpserver.HttpServer
import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.SaveCodec
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.core.Subtask
import io.github.ththemabit.questlog.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Der ganze Weg einmal durch: Spielstand auf der Platte, KI-Dienst im Hintergrund,
 * ein Fake-Server, der wie eine KI-Box antwortet.
 */
class AiServiceTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val calls = AtomicInteger()
    private val forge = """Ein Umzug! Hier deine Aufträge.
###QUESTS
[{"title":"Umzugskartons besorgen","category":"sonstiges","importance":4,"effortMin":30,"dread":2,"dueDate":null,"intro":"Kartons, so weit das Auge reicht."},{"id":"q1","maxDays":5}]"""

    @Before
    fun start() {
        dir = Files.createTempDirectory("questlog-ai").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { ex ->
            calls.incrementAndGet()
            val body = ex.requestBody.bufferedReader().readText()
            val stream = body.contains("\"stream\":true")
            val content = when {
                ex.requestURI.path.endsWith("/models") -> null
                body.contains("Tagesbriefing") -> "Ein neuer Tag in der Gildenhalle. Das Bad wartet."
                body.contains("Schreibe Texte genau") ->
                    """{"intro":"Ein Auftrag, wie für dich gemacht.","victory":["Geschafft!","Die Chronik jubelt.","Sauber!"],"reminder":"Es ist wieder so weit.","overdue":"Kein Stress, es wartet.","mini":null,"bossIntro":"Der Boss baut sich auf."}"""
                body.contains("Heldensaga") -> "Höret!\n\nDie Woche war groß."
                else -> forge
            }
            val (type, text) = when {
                content == null -> "application/json" to """{"data":[{"id":"fake-7b"}]}"""
                stream -> "text/event-stream" to content.chunked(12).joinToString("") { part ->
                    "data: " + kotlinx.serialization.json.buildJsonObject {
                        put("choices", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.buildJsonObject {
                                put("delta", kotlinx.serialization.json.buildJsonObject { put("content", kotlinx.serialization.json.JsonPrimitive(part)) })
                            })
                        })
                    } + "\n\n"
                } + "data: [DONE]\n\n"
                else -> "application/json" to kotlinx.serialization.json.buildJsonObject {
                    put("choices", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("message", kotlinx.serialization.json.buildJsonObject { put("content", kotlinx.serialization.json.JsonPrimitive(content)) })
                        })
                    })
                }.toString()
            }
            val bytes = text.toByteArray()
            ex.responseHeaders.add("Content-Type", type)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun seed(ai: AiConfig) {
        val created = System.currentTimeMillis() - 3 * 86_400_000L
        val save = SaveData(
            quests = listOf(
                Quest("bad", "Bad putzen", "bad", minDays = 4, maxDays = 9, createdAt = created),
                Quest("keller", "Keller entrümpeln", "haushalt", boss = true, createdAt = created,
                    subtasks = listOf(Subtask("k1", "Kisten"), Subtask("k2", "Regal"), Subtask("k3", "Sperrmüll"))),
            ),
            settings = Settings(ai = ai),
            meta = Meta(onboarded = true, seed = 1),
        )
        File(dir, "questlog.json").writeText(SaveCodec.encode(save))
    }

    private suspend fun until(what: String, ms: Long = 10_000, check: () -> Boolean) {
        try {
            withTimeout(ms) { while (!check()) delay(40) }
        } catch (e: Exception) {
            throw AssertionError("Nicht eingetreten: $what", e)
        }
    }

    @Test
    fun writesTextsInTheBackgroundAndBuildsQuestsInConversation() = runBlocking {
        seed(AiConfig(enabled = true, baseUrl = "127.0.0.1:${server.address.port}", model = "fake-7b"))
        val store = Store(dir, zone)
        val ai = AiService(store, zone)
        try {
            store.awaitState()
            // Im Hintergrund passiert nichts, solange die App nicht sichtbar ist
            delay(300)
            assertEquals(0, calls.get())
            ai.setForeground(true)
            until("Briefing und Questtexte") {
                val s = store.snapshot()!!
                s.quests.all { GameMaster.textsFor(s, it) != null } && GameMaster.briefing(s, store.state.value!!.game.today) != null
            }
            val s = store.snapshot()!!
            assertEquals("Ein Auftrag, wie für dich gemacht.", GameMaster.textsFor(s, s.quests[0])!!.intro)
            assertEquals("Der Boss baut sich auf.", GameMaster.textsFor(s, s.quests[1])!!.bossIntro)
            until("Status bereit") { ai.status.value.phase == AiPhase.IDLE }
            assertTrue(ai.status.value.text.contains("2 von 2"))

            // Gespräch: Vorschläge kommen, werden übernommen
            ai.send("Ich ziehe um und das Bad soll öfter dran sein.")
            until("Antwort") { ai.chat.value.size == 2 && ai.live.value == null }
            val reply = ai.chat.value[1]
            assertFalse(reply.error)
            assertEquals("Ein Umzug! Hier deine Aufträge.", reply.text)
            assertEquals(2, reply.proposals.size)
            assertEquals(2, ai.apply(1, setOf(0, 1)))
            assertTrue(ai.chat.value[1].applied)
            assertEquals(0, ai.apply(1, setOf(0, 1)))
            val after = store.snapshot()!!
            assertEquals(5, after.quests.first { it.id == "bad" }.maxDays)
            val boxes = after.quests.first { it.title == "Umzugskartons besorgen" }
            assertEquals("Kartons, so weit das Auge reicht.", GameMaster.textsFor(after, boxes)!!.intro)
            // Der Rest der Texte für die neue Quest folgt im Hintergrund, das Intro aus dem Gespräch bleibt
            until("volle Texte für die neue Quest") { GameMaster.texts(store.snapshot()!!, boxes.id)?.partial == false }
            val full = GameMaster.texts(store.snapshot()!!, boxes.id)!!
            assertEquals("Kartons, so weit das Auge reicht.", full.intro)
            assertEquals(3, full.victory.size)
        } finally {
            ai.close()
        }
    }

    @Test
    fun missingServerOnlyChangesTheStatus() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        seed(AiConfig(enabled = true, baseUrl = "127.0.0.1:$port", model = "fake-7b"))
        val store = Store(dir, zone)
        val ai = AiService(store, zone)
        try {
            ai.setForeground(true)
            until("offline") { ai.status.value.phase == AiPhase.OFFLINE }
            assertNotNull(ai.status.value.error)
            assertTrue(ai.status.value.error!!.contains("Keine Verbindung"))
            // Spielstand unberührt, nichts im Cache
            assertTrue(store.snapshot()!!.storyCache.isEmpty())
            // Im Gespräch kommt der Fehler als Antwort an, nichts stürzt ab
            ai.send("Hallo?")
            until("Fehlerantwort") { ai.chat.value.size == 2 && ai.live.value == null }
            assertTrue(ai.chat.value[1].error)
        } finally {
            ai.close()
        }
    }

    @Test
    fun offMeansNoTraffic() = runBlocking {
        seed(AiConfig(enabled = false, baseUrl = "127.0.0.1:${server.address.port}", model = "fake-7b"))
        val store = Store(dir, zone)
        val ai = AiService(store, zone)
        try {
            ai.setForeground(true)
            ai.send("Hallo?")
            delay(600)
            assertEquals(0, calls.get())
            assertEquals(AiPhase.OFF, ai.status.value.phase)
            assertTrue(ai.chat.value.isEmpty())
        } finally {
            ai.close()
        }
    }
}
