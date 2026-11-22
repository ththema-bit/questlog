package io.github.ththemabit.questlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ai.AiPhase
import io.github.ththemabit.questlog.ai.Probe
import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.LocalAi
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ChipRow
import io.github.ththemabit.questlog.ui.components.ParchSection
import io.github.ththemabit.questlog.ui.components.PixelField
import io.github.ththemabit.questlog.ui.components.Toggle
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Server(val name: String, val port: Int?, val hint: String)

private val servers = listOf(
    Server("Ollama", 11434, "Ollama: Auf der Box OLLAMA_HOST=0.0.0.0 setzen, damit das Handy sie im Heimnetz erreicht. Port 11434."),
    Server("LM Studio", 1234, "LM Studio: Server starten und „Serve on Local Network“ einschalten. Port 1234."),
    Server("llama.cpp", 8080, "llama.cpp: llama-server mit --host 0.0.0.0 starten. Port 8080."),
    Server("vLLM", 8000, "vLLM: vllm serve lauscht auf Port 8000. Mit --api-key gestartet? Dann den Schlüssel unten eintragen. Das Modell heißt wie beim Start (oder --served-model-name)."),
    Server("Cloud", null, "Cloud: die volle Adresse des Anbieters (mit /v1) und den API-Schlüssel eintragen."),
)

/** Setzt in einer Adresse den Port, z. B. für die Server-Knöpfe. */
fun withPort(address: String, port: Int): String {
    val a = address.trim()
    if (a.isEmpty()) return a
    val scheme = if ("://" in a) a.substringBefore("://") + "://" else ""
    val rest = a.substringAfter("://")
    val host = rest.substringBefore('/').substringBefore(':')
    val path = rest.substringAfter('/', "")
    return "$scheme$host:$port" + if (path.isEmpty()) "" else "/$path"
}

/** Optionen für den KI-Spielleiter. Aus heißt: die App läuft mit Vorlagen und Faustregeln. */
@Composable
fun AiSection(state: AppState, act: Actions, nav: Nav, ui: UiController, preview: Probe? = null) {
    val ai = LocalAi.current
    val fx = LocalFx.current
    val cfg = state.save.settings.ai
    val status by ai.status.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(cfg.baseUrl) }
    var model by remember { mutableStateOf(cfg.model) }
    var key by remember { mutableStateOf(cfg.apiKey) }
    var hint by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(11434) }
    var probe by remember { mutableStateOf(preview) }
    var testing by remember { mutableStateOf(false) }
    val edited: (AiConfig) -> AiConfig = { it.copy(baseUrl = url.trim(), model = model.trim(), apiKey = key.trim()) }
    // Eingaben kurz nach dem Tippen speichern, nicht bei jedem Buchstaben
    LaunchedEffect(url, model, key) {
        delay(700)
        val next = edited(cfg)
        if (next != cfg) act.settings { it.copy(ai = edited(it.ai)) }
    }
    fun set(block: (AiConfig) -> AiConfig) = act.settings { it.copy(ai = block(it.ai)) }

    ParchSection("KI-Spielleiter", icon = "orb") {
        Toggle(cfg.enabled, { v -> set { it.copy(enabled = v) } }, "KI-Spielleiter",
            "Optional. Deine eigene KI baut Quests im Gespräch und schreibt Texte, die genau zu jeder Aufgabe passen. Ohne KI läuft alles mit Vorlagen.", icon = "orb")
        if (!cfg.enabled) return@ParchSection
        T("SERVER", Q.type.label, color = Pal.inkSoft)
        ChipRow(servers, { s -> if (s.port != null) url.contains(":${s.port}") else url.startsWith("https://") }, { it.name }) { s ->
            if (s.port != null) {
                url = withPort(url, s.port)
                port = s.port
            }
            hint = s.hint
            fx.tap()
        }
        hint?.let { T(it, Q.type.small, color = Pal.inkSoft) }
        PixelField(url, { url = it }, "z. B. 192.168.178.65:$port", label = "Adresse", url = true)
        PixelField(model, { model = it }, "z. B. qwen2.5:7b", label = "Modell", url = true)
        probe?.models?.takeIf { it.isNotEmpty() }?.let { models ->
            ChipRow(models.take(16), { it == model }, { it }) { model = it; fx.tap() }
        }
        PixelField(key, { key = it }, "nur wenn der Server einen verlangt", label = "API-Schlüssel", secret = true)
        PixelButton(if (testing) "Teste …" else "Verbindung testen", {
            testing = true
            probe = null
            val c = edited(cfg)
            act.settings { it.copy(ai = edited(it.ai)) }
            scope.launch {
                probe = ai.probe(c)
                testing = false
                if (probe?.ok == true) fx.done() else fx.undo()
            }
        }, Modifier.fillMaxWidth(), kind = Btn.MANA, icon = "orb", enabled = !testing && url.isNotBlank())
        probe?.let { T(it.message, Q.type.small, color = if (it.ok) Pal.leaf2 else Pal.blood1) }
        if (cfg.ready && probe == null) {
            val (text, color) = when (status.phase) {
                AiPhase.OFFLINE -> "Nicht erreichbar: ${status.error ?: ""}" to Pal.blood1
                AiPhase.OFF -> status.text to Pal.inkSoft
                else -> status.text to Pal.inkSoft
            }
            T(text, Q.type.small, color = color)
        }

        T("DER SPIELLEITER DARF", Q.type.label, color = Pal.inkSoft)
        Toggle(cfg.forge, { v -> set { it.copy(forge = v) } }, "Quests bauen", "Im Gespräch Quests anlegen, ändern und archivieren. Übernommen wird nur, was du abhakst.", icon = "anvil")
        Toggle(cfg.questTexts, { v -> set { it.copy(questTexts = v) } }, "Questtexte schreiben", "Einleitung, Siegeszeilen, Erinnerungen und Boss-Auftritte für jede Quest.", icon = "scroll")
        Toggle(cfg.briefing, { v -> set { it.copy(briefing = v) } }, "Tagesbriefing", "Begrüßung auf dem Board und Morgen-Übersicht, passend zu deinem Tag.", icon = "sun")
        Toggle(cfg.saga, { v -> set { it.copy(saga = v) } }, "Heldensaga", "Erzählt den Wochenrückblick als Sage aus deinen echten Zahlen.", icon = "book")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cfg.forge) PixelButton("Gespräch", { nav.go(Route.Master()) }, Modifier.weight(1f), kind = Btn.STONE, icon = "talk", enabled = cfg.ready)
            PixelButton("Neu schreiben", {
                ui.show(Overlay.Confirm(
                    "Alle Texte neu schreiben?",
                    "Questtexte, Briefing und Saga werden verworfen und neu geschrieben. Bis dahin gelten die Vorlagen.",
                    "Neu schreiben",
                ) { ai.rewriteAll(); ui.toast("Der Spielleiter schreibt alles neu.") })
            }, Modifier.weight(1f), kind = Btn.STONE, icon = "quill", enabled = cfg.ready)
        }
        T("Die KI rechnet nie Punkte aus und ändert nichts ohne dein Häkchen. Questlog spricht nur mit dieser Adresse. Der API-Schlüssel kommt nicht mit in Sicherungen.", Q.type.small, color = Pal.inkFaint)
    }
}
