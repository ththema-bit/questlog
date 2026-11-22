package io.github.ththemabit.questlog.ai

import io.github.ththemabit.questlog.core.AiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AiPhase { OFF, IDLE, WORKING, OFFLINE }

data class AiStatus(
    val phase: AiPhase = AiPhase.OFF,
    val text: String = "Aus",
    /** Speicher-Schlüssel des Texts, der gerade entsteht (z. B. quest-<id>) */
    val working: String? = null,
    val error: String? = null,
)

/** Antwort, die gerade hereinkommt. [listing]: der Spielleiter schreibt schon die Quest-Liste. */
data class LiveReply(val text: String, val listing: Boolean = false)

/** Ergebnis von „Verbindung testen“. */
data class Probe(val ok: Boolean, val message: String, val models: List<String> = emptyList())

/**
 * Was die Oberfläche vom KI-Spielleiter braucht. Die App nutzt [AiService],
 * Vorschauen und Tests [NoAi] oder eine Attrappe. Ohne KI ist alles hier wirkungslos.
 */
interface AiPort {
    val status: StateFlow<AiStatus>
    /** Das laufende Gespräch mit dem Spielleiter */
    val chat: StateFlow<List<ChatTurn>>
    /** Antwort, die gerade hereinkommt; null = es wird nichts geschrieben */
    val live: StateFlow<LiveReply?>

    fun send(text: String)
    fun stop()
    fun clearChat()
    /** Übernimmt die ausgewählten Vorschläge einer Antwort. Gibt die Zahl der Änderungen zurück. */
    fun apply(turn: Int, picks: Set<Int>): Int
    /** Diese Quest bitte als Nächstes mit Texten versorgen (z. B. weil sie gerade offen ist). */
    fun focus(questId: String)
    /** Alle Texte verwerfen und neu schreiben lassen. */
    fun rewriteAll()
    suspend fun probe(cfg: AiConfig): Probe
}

object NoAi : AiPort {
    override val status: StateFlow<AiStatus> = MutableStateFlow(AiStatus())
    override val chat: StateFlow<List<ChatTurn>> = MutableStateFlow(emptyList())
    override val live: StateFlow<LiveReply?> = MutableStateFlow(null)
    override fun send(text: String) {}
    override fun stop() {}
    override fun clearChat() {}
    override fun apply(turn: Int, picks: Set<Int>) = 0
    override fun focus(questId: String) {}
    override fun rewriteAll() {}
    override suspend fun probe(cfg: AiConfig) = Probe(false, "Nicht verfügbar.")
}
