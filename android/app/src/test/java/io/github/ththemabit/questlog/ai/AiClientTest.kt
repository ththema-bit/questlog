package io.github.ththemabit.questlog.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.Executors

/** Spricht mit einem kleinen Fake-Server, der sich wie Ollama oder LM Studio verhält. */
class AiClientTest {
    private data class Reply(val code: Int, val type: String, val body: String, val delayMs: Long = 0)

    private lateinit var server: HttpServer
    private val requests: MutableList<Triple<String, String, String?>> = Collections.synchronizedList(ArrayList())
    @Volatile private var reply: (String) -> Reply = { Reply(200, "application/json", "{}") }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { ex ->
            val body = ex.requestBody.bufferedReader().readText()
            requests += Triple(ex.requestURI.path, body, ex.requestHeaders.getFirst("Authorization"))
            val r = reply(ex.requestURI.path)
            if (r.delayMs > 0) Thread.sleep(r.delayMs)
            val bytes = r.body.toByteArray()
            ex.responseHeaders.add("Content-Type", r.type)
            runCatching {
                ex.sendResponseHeaders(r.code, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private val address get() = "127.0.0.1:${server.address.port}"

    @Test
    fun normalizeAddsSchemeAndPath() {
        assertEquals("http://192.168.178.65:11434/v1", AiClient.normalize("192.168.178.65:11434"))
        assertEquals("http://192.168.178.65:11434/v1", AiClient.normalize(" http://192.168.178.65:11434/ "))
        assertEquals("https://api.example.com/v1", AiClient.normalize("https://api.example.com/v1/"))
        assertEquals("http://box:8080/api/v1", AiClient.normalize("box:8080/api/v1"))
        assertEquals("", AiClient.normalize("  "))
    }

    @Test
    fun thinkingIsRemoved() {
        assertEquals("Hallo", AiClient.stripThinking("<think>erst überlegen\nlange</think>\nHallo"))
        assertEquals("Hallo", AiClient.stripThinking("überlegt ohne Anfangsmarke</think>Hallo"))
        assertEquals("Vorher", AiClient.visible("Vorher <think>noch am Denken"))
        assertEquals("Antwort", AiClient.visible("<think>fertig</think>Antwort"))
    }

    @Test
    fun listsModelsInBothFormats() = runBlocking {
        reply = { Reply(200, "application/json", """{"object":"list","data":[{"id":"qwen2.5:7b"},{"id":"llama3.2:3b"}]}""") }
        assertEquals(listOf("llama3.2:3b", "qwen2.5:7b"), AiClient(address, "").models())
        assertEquals("/v1/models", requests.last().first)
        reply = { Reply(200, "application/json", """{"models":[{"name":"gemma3:4b"}]}""") }
        assertEquals(listOf("gemma3:4b"), AiClient(address, "").models())
    }

    @Test
    fun chatSendsModelAndKeyAndStripsThinking() = runBlocking {
        reply = { Reply(200, "application/json", """{"choices":[{"message":{"role":"assistant","content":"<think>hm</think>Sei gegrüßt!"}}]}""") }
        val text = AiClient(address, "qwen2.5:7b", apiKey = "geheim").chat(listOf(ChatMessage("system", "S"), ChatMessage("user", "Hallo")), maxTokens = 50)
        assertEquals("Sei gegrüßt!", text)
        val (path, body, auth) = requests.last()
        assertEquals("/v1/chat/completions", path)
        assertTrue(body.contains("\"model\":\"qwen2.5:7b\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"max_tokens\":50"))
        assertTrue(body.contains("\"role\":\"user\",\"content\":\"Hallo\""))
        assertEquals("Bearer geheim", auth)
    }

    @Test
    fun noKeyNoAuthorizationHeader() = runBlocking {
        reply = { Reply(200, "application/json", """{"choices":[{"message":{"content":"ok"}}]}""") }
        AiClient(address, "m").chat(listOf(ChatMessage("user", "x")))
        assertEquals(null, requests.last().third)
    }

    @Test
    fun streamsDeltas() = runBlocking {
        val sse = listOf("<think>hm", "</think>Hallo", " Welt", "!").joinToString("") {
            "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n"
        } + "data: [DONE]\n\n"
        reply = { Reply(200, "text/event-stream", sse) }
        val seen = ArrayList<String>()
        val text = AiClient(address, "m").chat(listOf(ChatMessage("user", "x"))) { seen += it }
        assertEquals("Hallo Welt!", text)
        assertEquals(listOf("", "Hallo", "Hallo Welt", "Hallo Welt!"), seen)
        assertTrue(requests.last().second.contains("\"stream\":true"))
    }

    @Test
    fun explainsErrors() = runBlocking {
        reply = { Reply(401, "application/json", """{"error":{"message":"invalid api key"}}""") }
        expectError("API-Schlüssel") { AiClient(address, "m").chat(listOf(ChatMessage("user", "x"))) }
        reply = { Reply(404, "application/json", """{"error":"model 'xyz' not found"}""") }
        expectError("model 'xyz' not found") { AiClient(address, "xyz").chat(listOf(ChatMessage("user", "x"))) }
        reply = { Reply(500, "text/plain", "kaputt") }
        expectError("500") { AiClient(address, "m").models() }
        reply = { Reply(200, "application/json", """{"choices":[]}""") }
        expectError("keinen Text") { AiClient(address, "m").chat(listOf(ChatMessage("user", "x"))) }
        expectError("Modell") { AiClient(address, "").chat(listOf(ChatMessage("user", "x"))) }
        expectError("Adresse") { AiClient("", "m").models() }
    }

    @Test
    fun explainsMissingServer() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        expectError("Keine Verbindung") { AiClient("127.0.0.1:$port", "m", connectTimeoutMs = 2000).models() }
    }

    @Test
    fun cancelStopsAHangingRequest() = runBlocking {
        reply = { Reply(200, "application/json", """{"choices":[{"message":{"content":"zu spät"}}]}""", delayMs = 4000) }
        val call = async(Dispatchers.Default) { AiClient(address, "m").chat(listOf(ChatMessage("user", "x"))) }
        delay(300)
        val t0 = System.currentTimeMillis()
        call.cancel()
        try {
            call.await()
            fail("hätte abbrechen sollen")
        } catch (e: CancellationException) {
            // richtig so
        }
        val took = System.currentTimeMillis() - t0
        assertTrue("Abbruch dauerte $took ms", took < 1500)
        assertFalse(call.isActive)
    }

    private suspend fun expectError(part: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Fehler erwartet: $part")
        } catch (e: AiException) {
            assertTrue("„${e.message}“ enthält nicht „$part“", e.message!!.contains(part))
        }
    }
}
