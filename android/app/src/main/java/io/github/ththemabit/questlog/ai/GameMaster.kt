package io.github.ththemabit.questlog.ai

import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.HeuristicQuestSmith
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.QuestDraft
import io.github.ththemabit.questlog.core.Recap
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Subtask
import io.github.ththemabit.questlog.core.WindowStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.roundToInt

/** Texte, die der Spielleiter für eine einzelne Quest schreibt. Liegen im storyCache unter „quest-<id>“. */
@Serializable
data class QuestTexts(
    /** Inhalt der Quest beim Schreiben (Titel, Kategorie …). Passt er nicht mehr, wird der Text nicht gezeigt. */
    val sig: String,
    /** Erzähler-Stimme beim Schreiben */
    val persona: String = "",
    val intro: String? = null,
    val victory: List<String> = emptyList(),
    val reminder: String? = null,
    val overdue: String? = null,
    val mini: String? = null,
    val bossIntro: String? = null,
    /** nur das Intro aus dem Gespräch, der Rest folgt im Hintergrund */
    val partial: Boolean = false,
)

@Serializable
data class Briefing(val tag: String, val day: String, val text: String)

data class ChatTurn(
    val fromUser: Boolean,
    val text: String,
    val proposals: List<Proposal> = emptyList(),
    val applied: Boolean = false,
    /** welche Vorschläge übernommen wurden */
    val picked: Set<Int> = emptySet(),
    val error: Boolean = false,
)

sealed class Proposal {
    abstract val label: String

    data class New(val draft: QuestDraft, val intro: String?) : Proposal() {
        override val label get() = draft.title
    }

    data class Change(val questId: String, val before: Quest, val after: Quest, val summary: String) : Proposal() {
        override val label get() = before.title
    }

    data class Archive(val questId: String, val title: String) : Proposal() {
        override val label get() = title
    }
}

data class ForgeReply(val message: String, val proposals: List<Proposal>, val unreadable: Boolean)

/**
 * Der KI-Spielleiter: Prompts und das Auslesen der Antworten. Rechnet nie Punkte aus,
 * das bleibt bei der Engine. Antworten werden streng geprüft und auf gültige Werte begrenzt.
 */
object GameMaster {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true; allowComments = true }
    private val smith = HeuristicQuestSmith()
    const val MARK = "###QUESTS"

    fun textsKey(questId: String) = "quest-$questId"
    fun sagaKey(weekStart: LocalDate) = "saga-$weekStart"
    const val BRIEF_KEY = "brief"

    /** Nacht, Morgen, Tag oder Abend: das Briefing wird pro Tagesabschnitt neu geschrieben. */
    fun dayPart(hour: Int): String = when {
        hour < 5 -> "nacht"
        hour < 11 -> "morgen"
        hour < 18 -> "tag"
        else -> "abend"
    }

    /** Wann das Briefing neu geschrieben wird: neuer Tagesabschnitt, andere Stimme oder eine weitere erledigte Quest. */
    fun briefingTag(game: GameState, persona: Persona, hour: Int): String =
        "${game.today}|${dayPart(hour)}|${persona.key}|${game.player.todayCount}"

    fun briefingTag(save: SaveData): String? = briefingOf(save)?.tag

    /** Das letzte Briefing von heute (darf etwas älter sein, bis das neue fertig ist). */
    fun briefing(save: SaveData, today: LocalDate): String? = briefingOf(save)?.takeIf { it.day == today.toString() }?.text

    /** Das Briefing aus dem Morgen von [today], für den Sammelpush. */
    fun morningBriefing(save: SaveData, today: LocalDate): String? =
        briefingOf(save)?.takeIf { it.day == today.toString() && it.tag.split('|').getOrNull(1) == "morgen" }?.text

    private fun briefingOf(save: SaveData): Briefing? =
        save.storyCache[BRIEF_KEY]?.let { runCatching { json.decodeFromString(Briefing.serializer(), it) }.getOrNull() }

    fun encodeBriefing(b: Briefing): String = json.encodeToString(Briefing.serializer(), b)

    fun signature(q: Quest): String = "${q.title}|${q.category}|${q.boss}|${q.subtasks.size}|${q.mini != null}"

    /** Gespeicherte Texte zu einer Quest, egal ob noch aktuell. */
    fun texts(save: SaveData, questId: String): QuestTexts? =
        save.storyCache[textsKey(questId)]?.let { runCatching { json.decodeFromString(QuestTexts.serializer(), it) }.getOrNull() }

    /** Texte, die zur Quest in ihrer jetzigen Form passen. Sonst null, dann gilt die Vorlage. */
    fun textsFor(save: SaveData, q: Quest): QuestTexts? = texts(save, q.id)?.takeIf { it.sig == signature(q) }

    /** Muss der Spielleiter (neu) schreiben? */
    fun needsTexts(save: SaveData, q: Quest, persona: Persona): Boolean {
        val t = texts(save, q.id) ?: return true
        return t.sig != signature(q) || t.persona != persona.key || t.partial
    }

    fun encode(t: QuestTexts): String = json.encodeToString(QuestTexts.serializer(), t)

    private fun voice(p: Persona): String = when (p) {
        Persona.CHRONIST -> "ein freundlicher, leicht verschmitzter Chronist, der den Alltag als Abenteuer erzählt"
        Persona.OMA -> "eine liebevolle Oma, ein bisschen enttäuscht, aber immer stolz"
        Persona.SERGEANT -> "ein lauter Drill-Sergeant mit Herz, der die Person „Einheit“ nennt"
        Persona.BUTLER -> "ein formvollendeter britischer Butler, sehr höflich und sehr diskret"
        Persona.TIERFILMER -> "ein Tierfilmer, der die Person flüsternd als „das Exemplar“ in ihrem natürlichen Lebensraum beschreibt"
    }

    fun system(p: Persona): String = """
        Du bist der Spielleiter von „Questlog“, einer To-do-App im Stil eines Fantasy-Rollenspiels aus den 90ern. Aufgaben aus dem echten Leben sind Quests.
        Du schreibst auf Deutsch und sprichst die Person mit ${if (p == Persona.BUTLER) "„Sie“" else "„du“"} an. Du triffst keine Annahmen über ihr Geschlecht.
        Dein Ton: ${voice(p)}. Kurz, witzig, ermutigend, nie belehrend, nie beschämend. Keine Emojis.
    """.trimIndent()

    // ---------------------------------------------------------------- Agent: Quests bauen

    private fun rhythm(q: Quest): String = when {
        q.minDays == null && q.maxDays == null -> if (q.dueDate != null) "einmalig bis ${q.dueDate}" else "einmalig"
        q.minDays != null && q.maxDays != null -> "alle ${q.minDays} bis ${q.maxDays} Tage"
        q.maxDays != null -> "spätestens alle ${q.maxDays} Tage"
        else -> "frühestens alle ${q.minDays} Tage"
    }

    /**
     * Kurze Kennungen q1, q2 … statt langer IDs, damit kleine Modelle sie zuverlässig zurückgeben.
     * Die Nummer ist die Position im Spielstand. Quests werden nur angehängt oder archiviert, nie
     * entfernt, darum bleibt die Nummer im ganzen Gespräch dieselbe.
     */
    fun questIndex(save: SaveData): List<Pair<String, Quest>> =
        save.quests.mapIndexedNotNull { i, q -> if (q.archived) null else "q${i + 1}" to q }.takeLast(80)

    fun forgeMessages(save: SaveData, game: GameState, history: List<ChatTurn>, persona: Persona): List<ChatMessage> {
        val keys = Categories.all.joinToString(", ") { "\"${it.key}\" (${it.name})" }
        val today = game.today
        val wd = today.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
        val index = questIndex(save)
        val status = index.joinToString("\n") { (k, q) ->
            val qs = game.quest(q.id)
            val st = when (qs?.status) {
                WindowStatus.OVERDUE -> "überfällig seit ${qs.overdueDays} T."
                WindowStatus.EARLY -> "noch zu früh"
                WindowStatus.DONE -> "erledigt"
                WindowStatus.OPEN -> "im Fenster"
                null -> ""
            }
            "$k: ${q.title} (${Categories.of(q.category).name}, ${rhythm(q)}, $st${if (q.boss) ", Boss" else ""})"
        }.ifEmpty { "(noch keine)" }
        val rules = """
            Deine Aufgabe jetzt: Hilf beim Planen. Die Person beschreibt, was ansteht. Du schlägst passende Quests vor, änderst bestehende, archivierst sie oder beantwortest einfach eine Frage.

            Antworte IMMER so:
            1. Zuerst 1 bis 3 Sätze an die Person, im Ton des Spielleiters.
            2. Nur wenn du Quests anlegen, ändern oder archivieren willst: eine Zeile $MARK und danach ein JSON-Array. Nichts nach dem Array.

            Jedes Element im Array ist ein Objekt mit diesen Feldern:
            "title": kurzer Titel, höchstens 40 Zeichen, ohne Emoji
            "category": einer von $keys
            "importance": 1 bis 5 (wie wichtig)
            "effortMin": geschätzte Minuten
            "dread": 1 bis 5 (wie sehr man sie hasst)
            "minDays", "maxDays": bei Wiederkehrendem das Zeitfenster in Tagen (frühestens, spätestens), sonst null
            "dueDate": bei Einmaligem optional ein Termin JJJJ-MM-TT, sonst null
            "mini": eine Zwei-Minuten-Version als kurzer Satz, oder null
            "why": warum es wichtig ist, ein kurzer Satz aus Sicht der Person, oder null
            "subtasks": Liste kurzer Teilaufgaben, nur bei größeren Vorhaben, sonst []
            "boss": true bei großen, verhassten Projekten mit mindestens 3 Teilaufgaben
            "intro": 1 bis 2 Sätze Questtext im Fantasy-Ton, genau zu dieser Aufgabe
            "id": NUR wenn du eine bestehende Quest änderst oder archivierst, ihre Kennung (z. B. "q3"). Beim Ändern nur die geänderten Felder angeben.
            "archive": true, wenn die bestehende Quest mit dieser "id" archiviert werden soll

            Regeln: Wiederkehrendes bekommt immer ein Zeitfenster, minDays kleiner oder gleich maxDays. Realistische Minuten. Lege nichts doppelt an, was es schon gibt. Höchstens 8 Einträge pro Antwort. Erfinde keine Aufgaben, die die Person nicht erwähnt hat, außer sinnvollen Teilaufgaben.

            Beispiel
            Person: Ich muss jede Woche den Müll rausbringen und irgendwann den Keller aufräumen.
            Antwort:
            Zwei Aufträge, frisch aus der Gildenhalle. Der Keller wird ein Boss, der Müll ein treuer Begleiter.
            $MARK
            [{"title":"Müll rausbringen","category":"haushalt","importance":3,"effortMin":5,"dread":2,"minDays":5,"maxDays":7,"dueDate":null,"mini":null,"why":null,"subtasks":[],"boss":false,"intro":"Die Tonne vor dem Tor grollt. Bring ihr ihr Opfer, bevor sie überquillt."},{"title":"Keller aufräumen","category":"haushalt","importance":3,"effortMin":180,"dread":5,"minDays":null,"maxDays":null,"dueDate":null,"mini":"Eine Kiste aussortieren","why":null,"subtasks":["Kisten sichten","Sperrmüll raus","Regal einräumen"],"boss":true,"intro":"In der Tiefe lauert der Kellerdrache aus vergessenen Umzugskartons."}]

            Heute ist $wd, der $today.
            Stufe ${game.player.level} (${game.player.rank.name}), Glut ${game.player.glut}, Serie ${game.player.streak} Tage.
            Bestehende Quests (Kennung: Titel, Kategorie, Rhythmus, Stand):
        """.trimIndent() + "\n" + status
        val msgs = ArrayList<ChatMessage>()
        msgs += ChatMessage("system", system(persona) + "\n\n" + rules)
        for (t in history.takeLast(12)) {
            if (t.error) continue
            if (t.fromUser) msgs += ChatMessage("user", t.text)
            else {
                val note = when {
                    t.proposals.isEmpty() -> ""
                    t.applied -> "\n(Vorgeschlagen: ${t.proposals.joinToString { it.label }}. Übernommen: ${t.proposals.filterIndexed { i, _ -> i in t.picked }.joinToString { it.label }})"
                    else -> "\n(Vorgeschlagen, noch nicht übernommen: ${t.proposals.joinToString { it.label }})"
                }
                msgs += ChatMessage("assistant", t.text + note)
            }
        }
        return msgs
    }

    /** Liest die Antwort des Agenten. Kaputte oder unvollständige Einträge werden repariert oder verworfen. */
    fun parseForge(raw: String, save: SaveData, today: LocalDate): ForgeReply {
        val text = AiClient.stripThinking(raw)
        val idx = text.indexOf(MARK)
        if (idx < 0) {
            // Manche Modelle lassen die Markierung weg und hängen nur JSON an
            val start = text.indexOf('[')
            val found = if (start >= 0) proposals(objectsIn(text.substring(start)), save, today) else emptyList()
            if (found.isEmpty()) return ForgeReply(cleanMessage(text).ifEmpty { "Der Spielleiter nickt." }, emptyList(), unreadable = false)
            return ForgeReply(cleanMessage(text.substring(0, start)).ifEmpty { "Hier sind meine Vorschläge." }, found, unreadable = false)
        }
        val message = cleanMessage(text.substring(0, idx)).ifEmpty { "Hier sind meine Vorschläge." }
        val objects = objectsIn(text.substring(idx + MARK.length))
        return ForgeReply(message, proposals(objects, save, today), unreadable = objects.isEmpty())
    }

    private fun cleanMessage(m: String): String = m.replace("```json", "").replace("```", "").trim().removeSurrounding("\"").trim()

    /**
     * Alle JSON-Objekte der obersten Ebene in [s], jedes für sich gelesen. So überleben die
     * heilen Einträge, wenn ein Modell mittendrin abbricht oder ein Objekt verhunzt.
     */
    fun objectsIn(s: String): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for ((i, ch) in s.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        runCatching { json.parseToJsonElement(s.substring(start, i + 1)) as? JsonObject }.getOrNull()?.let { out += it }
                        start = -1
                    }
                }
            }
        }
        return out
    }

    fun extractObject(s: String): JsonObject? {
        val t = AiClient.stripThinking(s)
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(t.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    private fun JsonObject.int(k: String): Int? {
        val p = this[k] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.contentOrNull?.toIntOrNull() ?: p.doubleOrNull?.roundToInt() ?: p.contentOrNull?.filter { it.isDigit() }?.toIntOrNull()
    }
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.lowercase()?.let { s -> s == "true" || s == "ja" } }
    private fun JsonObject.list(k: String): List<String> = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun cut(s: String?, n: Int): String? = s?.replace(Regex("\\s+"), " ")?.trim()?.let { if (it.length > n) it.take(n - 1).trimEnd() + "…" else it }?.takeIf { it.isNotEmpty() }

    private fun proposals(objects: List<JsonObject>, save: SaveData, today: LocalDate): List<Proposal> {
        val index = questIndex(save).toMap()
        val byLower = save.quests.filter { !it.archived }.associateBy { it.title.lowercase() }
        val out = ArrayList<Proposal>()
        val touched = HashSet<String>()
        for (o in objects.take(12)) {
            val ref = o.str("id")?.lowercase()?.let { if (it.all(Char::isDigit)) "q$it" else it }
            val archive = o.bool("archive") == true
            // Bestehende Quest: über die Kennung, sonst über den genauen Titel
            val existing = ref?.let { index[it] } ?: o.str("title")?.let { byLower[it.lowercase()] }
            if (existing != null) {
                if (!touched.add(existing.id)) continue
                if (archive) {
                    out += Proposal.Archive(existing.id, existing.title)
                    continue
                }
                val after = patch(existing, o, today)
                val summary = diff(existing, after)
                if (summary.isNotEmpty()) out += Proposal.Change(existing.id, existing, after, summary)
                continue
            }
            if (archive) continue // Archivieren, aber unbekannt: lieber nichts tun
            val title = cut(o.str("title"), 60) ?: continue
            if (!touched.add("new:" + title.lowercase())) continue
            out += Proposal.New(draft(o, title, today), cut(o.str("intro"), 320))
        }
        return out.take(8)
    }

    private fun category(o: JsonObject, title: String): String {
        val c = o.str("category")?.lowercase()
        val keys = Categories.all.map { it.key }
        return when {
            c != null && c in keys -> c
            c != null -> Categories.all.firstOrNull { it.name.lowercase() == c }?.key ?: smith.parse(title, LocalDate.now()).category
            else -> smith.parse(title, LocalDate.now()).category
        }
    }

    private fun window(o: JsonObject): Pair<Int?, Int?> {
        var min = o.int("minDays")?.coerceIn(0, 365)
        var max = o.int("maxDays")?.coerceIn(1, 730)
        if (min != null && max != null && min > max) min = max.also { max = min }
        if (min == 0) min = null
        return min to max
    }

    private fun due(o: JsonObject, today: LocalDate): String? =
        o.str("dueDate")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.takeIf { !it.isBefore(today) }?.toString()

    fun draft(o: JsonObject, title: String, today: LocalDate): QuestDraft {
        val (min, max) = window(o)
        val subs = o.list("subtasks").map { cut(it, 60)!! }.take(10)
        return QuestDraft(
            title = title,
            category = category(o, title),
            importance = (o.int("importance") ?: 3).coerceIn(1, 5),
            effortMin = (o.int("effortMin") ?: 15).coerceIn(1, 480),
            dread = (o.int("dread") ?: 3).coerceIn(1, 5),
            minDays = min,
            maxDays = max,
            mini = cut(o.str("mini"), 80),
            why = cut(o.str("why"), 160),
            subtasks = subs,
            boss = o.bool("boss") == true && subs.size >= 2,
            dueDate = if (min == null && max == null) due(o, today) else null,
        )
    }

    private fun patch(q: Quest, o: JsonObject, today: LocalDate): Quest {
        val hasWindow = o.containsKey("minDays") || o.containsKey("maxDays")
        val (min, max) = window(o)
        val subs = o.list("subtasks")
        return q.copy(
            title = cut(o.str("title"), 60) ?: q.title,
            category = if (o.str("category") != null) category(o, q.title) else q.category,
            importance = o.int("importance")?.coerceIn(1, 5) ?: q.importance,
            effortMin = o.int("effortMin")?.coerceIn(1, 480) ?: q.effortMin,
            dread = o.int("dread")?.coerceIn(1, 5) ?: q.dread,
            minDays = if (hasWindow) min else q.minDays,
            maxDays = if (hasWindow) max else q.maxDays,
            dueDate = if (o.containsKey("dueDate")) due(o, today) else q.dueDate,
            mini = cut(o.str("mini"), 80) ?: q.mini,
            why = cut(o.str("why"), 160) ?: q.why,
            subtasks = if (subs.isNotEmpty()) subs.take(10).mapIndexed { i, t -> q.subtasks.getOrNull(i)?.takeIf { it.title == t } ?: Subtask("${q.id}-ai-$i-${t.hashCode()}", cut(t, 60)!!) } else q.subtasks,
            boss = o.bool("boss")?.let { it && (if (subs.isNotEmpty()) subs.size else q.subtasks.size) >= 2 } ?: q.boss,
        )
    }

    private fun diff(a: Quest, b: Quest): String = buildList {
        if (a.title != b.title) add("Titel „${b.title}“")
        if (a.category != b.category) add("Kategorie ${Categories.of(b.category).name}")
        if (a.minDays != b.minDays || a.maxDays != b.maxDays) add(rhythm(b))
        if (a.importance != b.importance) add("Wichtigkeit ${b.importance}")
        if (a.dread != b.dread) add("Überwindung ${b.dread}")
        if (a.effortMin != b.effortMin) add("${b.effortMin} min")
        if (a.dueDate != b.dueDate) add(b.dueDate?.let { "Termin $it" } ?: "ohne Termin")
        if (a.mini != b.mini) add("Mini: ${b.mini}")
        if (a.why != b.why) add("Warum geändert")
        if (a.subtasks.map { it.title } != b.subtasks.map { it.title }) add("${b.subtasks.size} Teilaufgaben")
        if (a.boss != b.boss) add(if (b.boss) "wird Boss" else "kein Boss mehr")
    }.joinToString(" · ")

    // ---------------------------------------------------------------- Texte je Quest

    fun questTextMessages(q: Quest, persona: Persona): List<ChatMessage> {
        val details = buildList {
            add("Kategorie ${Categories.of(q.category).name}")
            add(rhythm(q))
            add("${q.effortMin} Minuten")
            add("Wichtigkeit ${q.importance}/5")
            add("Überwindung ${q.dread}/5")
            if (q.boss) add("Boss mit Teilaufgaben: " + q.subtasks.joinToString(", ") { it.title })
            else if (q.subtasks.isNotEmpty()) add("Teilaufgaben: " + q.subtasks.joinToString(", ") { it.title })
            q.why?.let { add("Warum: $it") }
            q.mini?.let { add("Mini-Version: $it") }
        }.joinToString(", ")
        val user = """
            Quest: „${q.title}“ ($details)
            Schreibe Texte genau für diese Quest. Antworte nur mit einem JSON-Objekt mit diesen Feldern:
            "intro": 2 bis 3 Sätze wie von einem Questgeber, konkret zu dieser Aufgabe,
            "victory": Liste mit 3 kurzen Siegeszeilen (je höchstens 90 Zeichen) für den Moment des Abhakens,
            "reminder": 1 Satz Erinnerung, wenn das Zeitfenster aufgeht,
            "overdue": 1 Satz, wenn die Quest überfällig ist, freundlich und nicht beschämend,
            "mini": ${if (q.mini != null) "1 Satz Ermutigung für die Mini-Version" else "null"},
            "bossIntro": ${if (q.boss) "1 bis 2 Sätze, wie der Boss auftritt" else "null"}
        """.trimIndent()
        return listOf(ChatMessage("system", system(persona)), ChatMessage("user", user))
    }

    fun parseQuestTexts(raw: String, sig: String, persona: Persona): QuestTexts? {
        val o = objectsIn(AiClient.stripThinking(raw)).firstOrNull { it.containsKey("intro") || it.containsKey("victory") }
            ?: extractObject(raw) ?: return null
        val t = QuestTexts(
            sig = sig,
            persona = persona.key,
            intro = cut(o.str("intro"), 360),
            victory = o.list("victory").mapNotNull { cut(it, 120) }.take(5),
            reminder = cut(o.str("reminder"), 180),
            overdue = cut(o.str("overdue"), 180),
            mini = cut(o.str("mini"), 160),
            bossIntro = cut(o.str("bossIntro"), 260),
        )
        return t.takeIf { it.intro != null || it.victory.isNotEmpty() }
    }

    // ---------------------------------------------------------------- Tagesbriefing & Saga

    fun briefingMessages(game: GameState, persona: Persona, hour: Int): List<ChatMessage> {
        val p = game.player
        val facts = buildList {
            add("Tageszeit: " + when (dayPart(hour)) { "nacht" -> "Nacht"; "morgen" -> "Morgen"; "tag" -> "Tag"; else -> "Abend" })
            if (p.todayCount > 0) add("Heute schon erledigt: ${p.todayCount} Quests, ${p.todayXp} Punkte")
            add("Stufe ${p.level} (${p.rank.name}), Glut ${p.glut}, Serie ${p.streak} Tage${if (p.streakAtRisk) " (wackelt, heute zählt)" else ""}")
            if (p.comebackReady) add("Rückkehr nach einer Pause: die erste Quest heute zählt doppelt")
            game.board.overdue.take(3).forEach { add("Überfällig seit ${it.overdueDays} Tagen: ${it.quest.title}") }
            game.board.open.take(4).forEach { add("Im Fenster${it.closesIn?.let { c -> ", schließt in $c Tagen" } ?: ""}: ${it.quest.title}") }
            game.board.bosses.firstOrNull()?.let { add("Boss: ${it.quest.title} (${it.bossHpLeft} von ${it.bossHpMax} Treffern übrig)") }
            game.bountyQuestId?.let { id -> game.quest(id)?.let { add("Kopfgeld ×2 heute auf: ${it.quest.title}") } }
            add("Wochenziel: ${game.season.xp} von ${game.season.goal} Punkten")
            game.holiday?.let { add("Heute ist ${it.name}") }
        }.joinToString("\n")
        val user = """
            Schreibe ein kurzes Tagesbriefing für das Questboard: 2 bis 3 Sätze, höchstens 300 Zeichen.
            Nenne höchstens zwei konkrete Quests. Nur der Text, ohne Anführungszeichen, ohne Aufzählung, ohne JSON.
            Fakten:
        """.trimIndent() + "\n" + facts
        return listOf(ChatMessage("system", system(persona)), ChatMessage("user", user))
    }

    fun cleanText(raw: String, max: Int): String =
        AiClient.stripThinking(raw).replace("```", "").trim().removeSurrounding("\"").removeSurrounding("„", "“").trim().let { cut(it, max) ?: "" }

    fun sagaMessages(recap: Recap, persona: Persona, geraldOn: Boolean): List<ChatMessage> {
        val kw = recap.weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val facts = buildList {
            add("Woche: ${kw}. Kalenderwoche")
            add("Punkte: ${recap.xp}, Wochenziel ${recap.goal} (${if (recap.reached) "erreicht" else "nicht erreicht"})")
            add("Aktive Tage: ${recap.activeDays} von 7, erledigte Quests: ${recap.doneCount}")
            recap.bestDay?.let { add("Bester Tag: ${it.first.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))} mit ${it.second} Punkten") }
            recap.topQuest?.let { add("Meiste Punkte brachte: ${it.first} (${it.second})") }
            recap.bossesDefeated.forEach { add("Besiegter Boss: $it") }
            add("Glut: von ${recap.glutStart} auf ${recap.glutEnd}")
            add("Vorwoche: ${recap.prevXp} Punkte")
            if (geraldOn) add("Rivale Gerald: ${recap.geraldXp} Punkte")
            recap.bestMoment?.let { add("Hellster Moment: ${it.questTitle} mit ${it.points} Punkten") }
        }.joinToString("\n")
        val user = """
            Erzähle die vergangene Woche als kurze Heldensaga im Stil einer alten Chronik: 5 bis 7 Absätze, jeder 1 bis 2 Sätze.
            Nutze nur diese Fakten und erfinde keine Zahlen. Der letzte Absatz feiert den hellsten Moment.
            Nur der Text, Absätze durch Leerzeilen getrennt, ohne Überschrift.
            Fakten:
        """.trimIndent() + "\n" + facts
        return listOf(ChatMessage("system", system(persona)), ChatMessage("user", user))
    }

    fun cleanSaga(raw: String): String {
        val lines = AiClient.stripThinking(raw).replace("```", "").lines().map { it.trim().trimStart('#', '*').trim() }
        var blocks = lines.joinToString("\n").split(Regex("\\n\\s*\\n")).map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
        // Manche Modelle trennen Absätze nur mit einem Zeilenumbruch
        if (blocks.size < 2) blocks = lines.filter { it.isNotEmpty() }
        return blocks.take(9).joinToString("\n\n") { cut(it, 400)!! }
    }
}
