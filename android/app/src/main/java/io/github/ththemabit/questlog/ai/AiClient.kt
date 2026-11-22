package io.github.ththemabit.questlog.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

open class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Antwortlänge plus Anfrage passen nicht in den Kontext des Modells (vLLM lehnt das ab, statt zu kürzen). */
private class ContextLimit(message: String) : AiException(message)

data class ChatMessage(val role: String, val content: String)

/**
 * Spricht die OpenAI-kompatible Chat-Schnittstelle, die Ollama, LM Studio, llama.cpp, vLLM
 * und die meisten Cloud-Anbieter anbieten. Nur java.net, keine zusätzliche Bibliothek.
 */
class AiClient(
    baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 180_000,
) {
    val base: String = normalize(baseUrl)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** „192.168.178.65:11434“ wird zu „http://192.168.178.65:11434/v1“. */
        fun normalize(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val path = s.substringAfter("://").substringAfter('/', "")
            if (path.isEmpty()) s += "/v1"
            return s
        }

        /** Denkende Modelle (z. B. mit <think>…</think>) liefern ihre Überlegungen mit. Die gehören nicht in Texte. */
        fun stripThinking(text: String): String =
            text.replace(Regex("(?s)<think>.*?</think>"), "").replace(Regex("(?s)^.*?</think>"), "").trim()

        /** Beim Streamen: unfertigen Denk-Block ausblenden. */
        fun visible(text: String): String {
            val open = text.lastIndexOf("<think>")
            val close = text.lastIndexOf("</think>")
            return if (open >= 0 && close < open) text.substring(0, open).trim() else stripThinking(text)
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = connectTimeoutMs
        c.readTimeout = readTimeoutMs
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json, text/event-stream")
        if (apiKey.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $apiKey")
        return c
    }

    private fun error(conn: HttpURLConnection, code: Int): AiException {
        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            when (val e = root["error"]) {
                is JsonObject -> e["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> e.contentOrNull
                // ältere vLLM-Versionen: {"object":"error","message":"…"}
                else -> root["message"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull() ?: body.take(160)
        val text = when (code) {
            401, 403 -> "Zugriff verweigert ($code). Stimmt der API-Schlüssel?"
            404 -> "Nicht gefunden (404). Stimmt die Adresse oder der Modellname? $msg".trim()
            else -> "Server antwortet mit $code. $msg".trim()
        }
        val tooLong = code == 400 && msg.contains("context", ignoreCase = true) &&
            (msg.contains("maximum", ignoreCase = true) || msg.contains("exceed", ignoreCase = true))
        return if (tooLong) ContextLimit(text) else AiException(text)
    }

    private fun translate(e: Exception): AiException = when (e) {
        is AiException -> e
        is SocketTimeoutException -> AiException("Zeitüberschreitung: Der Server antwortet nicht.", e)
        is ConnectException -> AiException("Keine Verbindung zu $base. Läuft der Server und ist das Handy im selben Netz?", e)
        is UnknownHostException -> AiException("Adresse unbekannt: $base", e)
        is SSLException -> AiException("Verschlüsselte Verbindung fehlgeschlagen: ${e.message}", e)
        else -> AiException(e.message ?: e.javaClass.simpleName, e)
    }

    /**
     * Führt [block] mit einer Verbindung aus. Wird die Coroutine abgebrochen, trennt ein Wächter
     * die Verbindung, damit kein blockierendes Lesen hängen bleibt.
     */
    private suspend fun <T> connection(url: String, method: String, block: (HttpURLConnection) -> T): T = withContext(Dispatchers.IO) {
        val conn = open(url, method)
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                conn.disconnect()
            }
        }
        try {
            block(conn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ensureActive() // abgebrochen: der Fehler ist nur die Folge davon
            throw translate(e)
        } finally {
            watcher.cancel()
            conn.disconnect()
        }
    }

    /** Liste der Modelle (/models). */
    suspend fun models(): List<String> {
        if (base.isEmpty()) throw AiException("Keine Adresse eingetragen.")
        return connection("$base/models", "GET") { conn ->
            val code = conn.responseCode
            if (code !in 200..299) throw error(conn, code)
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val data = root["data"] as? JsonArray
            val list = data?.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
                ?: (root["models"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
            list.sorted()
        }
    }

    /**
     * Ein Chat-Aufruf. Mit [onDelta] wird gestreamt und der bisher sichtbare Text laufend gemeldet.
     * Liefert den fertigen Text ohne Denk-Blöcke. Ist der Kontext des Modells zu klein für
     * [maxTokens] (vLLM mit kleinem --max-model-len), wird einmal ohne Obergrenze nachgefragt,
     * dann nimmt der Server, was übrig ist.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        maxTokens: Int? = 800,
        temperature: Double = 0.8,
        onDelta: ((String) -> Unit)? = null,
    ): String = try {
        request(messages, maxTokens, temperature, onDelta)
    } catch (e: ContextLimit) {
        if (maxTokens == null) throw AiException(e.message ?: "Kontext zu klein.")
        request(messages, null, temperature, onDelta)
    }

    private suspend fun request(messages: List<ChatMessage>, maxTokens: Int?, temperature: Double, onDelta: ((String) -> Unit)?): String {
        if (base.isEmpty()) throw AiException("Keine Adresse eingetragen.")
        if (model.isBlank()) throw AiException("Kein Modell ausgewählt.")
        val stream = onDelta != null
        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") { messages.forEach { m -> addJsonObject { put("role", m.role); put("content", m.content) } } }
            put("temperature", temperature)
            if (maxTokens != null) put("max_tokens", maxTokens)
            put("stream", stream)
        }
        return connection("$base/chat/completions", "POST") { conn ->
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) throw error(conn, code)
            if (!stream) {
                val body = conn.inputStream.bufferedReader().readText()
                val content = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: throw AiException("Der Server hat keinen Text geliefert.")
                stripThinking(content)
            } else {
                val sb = StringBuilder()
                val reader = conn.inputStream.bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    val l = line.trim()
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = runCatching {
                        json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    }.getOrNull() ?: continue
                    sb.append(delta)
                    onDelta?.invoke(visible(sb.toString()))
                }
                if (sb.isEmpty()) throw AiException("Der Server hat keinen Text geliefert.")
                stripThinking(sb.toString())
            }
        }
    }
}
