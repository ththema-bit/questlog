package io.github.ththemabit.questlog.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/*
 * Erzählschicht. Die App funktioniert komplett ohne KI: Alle Texte kommen aus Vorlagen
 * (TemplateStoryteller) und Faustregeln (HeuristicQuestSmith).
 *
 * KI-Naht für später: Eine KI-Variante (eigene API, lokales Modell auf der AI-Box oder
 * ein kleines Modell auf dem Handy) implementiert dieselben Schnittstellen. Weil KI-Antworten
 * dauern, erzeugt sie Texte im Voraus und legt sie in SaveData.storyCache ab. Die Oberfläche
 * fragt immer erst den Cache und fällt sonst auf die Vorlage zurück. Siehe DESIGN.md.
 */

enum class Persona(val key: String, val title: String, val blurb: String) {
    CHRONIST("chronist", "Chronist", "Sachlich und freundlich. Der Standard."),
    OMA("oma", "Enttäuschte Oma", "Nicht böse. Nur enttäuscht."),
    SERGEANT("sergeant", "Drill-Sergeant", "Laut. Sehr laut."),
    BUTLER("butler", "Britischer Butler", "Formvollendet und sehr diskret."),
    TIERFILMER("tierfilmer", "Tierfilmer", "Beobachtet dich in deinem natürlichen Lebensraum.");

    companion object {
        fun of(key: String): Persona = entries.firstOrNull { it.key == key } ?: CHRONIST
    }
}

enum class Moment { MORNING, DAY, EVENING, WINDOW_OPEN, CLOSING_SOON, OVERDUE, DIGEST, COMEBACK, STREAK_RISK, DONE, LEVEL_UP, RECORD }

data class StoryContext(val quest: String = "", val count: Int = 0, val days: Int = 0, val level: Int = 0)

data class Letter(val id: String, val title: String, val sender: String, val body: List<String>, val footer: String)

interface Storyteller {
    fun line(persona: Persona, moment: Moment, ctx: StoryContext, salt: Long = 0): String
    fun saga(recap: Recap, geraldOn: Boolean): List<String>
    fun taxLetter(notice: TaxNotice): Letter
    fun weddingLetter(m: Marriage, first: String, second: String, zoneDate: LocalDate): Letter
    fun credits(boss: Quest, xp: Int, days: Int): List<Pair<String, String>>
    fun bossIntro(boss: Quest): String
}

/** Erzeugt aus Freitext Quest-Entwürfe (Auto-Task-Erstellung). */
interface QuestSmith {
    fun draft(text: String, today: LocalDate): List<QuestDraft>
}

class TemplateStoryteller : Storyteller {
    private val lines: Map<Persona, Map<Moment, List<String>>> = mapOf(
        Persona.CHRONIST to mapOf(
            Moment.MORNING to listOf("Guten Morgen. Die Chronik ist aufgeschlagen.", "Ein neuer Tag, eine leere Seite."),
            Moment.DAY to listOf("Weiter geht's. Was steht als Nächstes an?", "Die Feder ist gespitzt."),
            Moment.EVENING to listOf("Guten Abend. Noch eine Kleinigkeit vor dem Feierabend?", "Die Kerzen brennen. Zeit für eine letzte Quest."),
            Moment.WINDOW_OPEN to listOf("Das Fenster für „{q}“ ist offen. Jetzt gibt es volle Punkte.", "„{q}“ ist wieder dran. Kein Stress, das Fenster ist offen."),
            Moment.CLOSING_SOON to listOf("„{q}“: Das Fenster schließt morgen.", "Letzte Gelegenheit für volle Punkte: „{q}“ schließt morgen."),
            Moment.OVERDUE to listOf("„{q}“ ist seit {d} Tagen überfällig. Spät ist besser als nie."),
            Moment.DIGEST to listOf("Heute warten {n} Quests. Die wichtigste: „{q}“.", "{n} Quests stehen an. Anfangen würde ich mit „{q}“."),
            Moment.COMEBACK to listOf("Willkommen zurück. Die erste Quest heute zählt doppelt."),
            Moment.STREAK_RISK to listOf("Deine Serie wackelt. Eine Mini-Quest heute reicht, um sie zu halten."),
            Moment.DONE to listOf("Eingetragen.", "Sauber.", "Die Chronik nimmt es zur Kenntnis."),
            Moment.LEVEL_UP to listOf("Stufe {l}. Das Licht wird heller."),
            Moment.RECORD to listOf("Neue Bestmarke. Die alte darf jetzt staunen."),
        ),
        Persona.OMA to mapOf(
            Moment.MORNING to listOf("Guten Morgen! Hast du was gefrühstückt?", "Na, ausgeschlafen? Dann kann's ja losgehen."),
            Moment.DAY to listOf("Na, schon was geschafft heute?", "Ich sag ja nichts. Ich frag nur."),
            Moment.EVENING to listOf("So spät noch wach? Dann kannst du auch noch was erledigen.", "Früher war um diese Zeit die Küche schon sauber."),
            Moment.WINDOW_OPEN to listOf("Kind, „{q}“ wäre jetzt dran. Ich sag ja nur.", "„{q}“ ist wieder fällig. Früher hat man das einfach gemacht."),
            Moment.CLOSING_SOON to listOf("Morgen ist „{q}“ überfällig. Was sollen denn die Nachbarn denken?"),
            Moment.OVERDUE to listOf("„{q}“ liegt seit {d} Tagen. Ich bin nicht böse, nur enttäuscht."),
            Moment.DIGEST to listOf("{n} Sachen heute. Iss vorher was Ordentliches. Anfangen würde ich mit „{q}“."),
            Moment.COMEBACK to listOf("Da bist du ja wieder! Ich hab mir Sorgen gemacht. Die erste Quest zählt doppelt, hab ich so eingerichtet."),
            Moment.STREAK_RISK to listOf("Deine Serie, Kind. Nur eine Kleinigkeit heute, mir zuliebe."),
            Moment.DONE to listOf("Na also. Geht doch.", "Siehst du, war gar nicht so schlimm.", "Das erzähl ich beim Kaffeekranz."),
            Moment.LEVEL_UP to listOf("Stufe {l}! Da wird die Tante Erna aber gucken."),
            Moment.RECORD to listOf("Ein Rekord! Ich hab's immer gewusst."),
        ),
        Persona.SERGEANT to mapOf(
            Moment.MORNING to listOf("Aufstehen! Die Quests warten nicht!", "Guten Morgen, Einheit! Antreten zum Questappell!"),
            Moment.DAY to listOf("Stillgestanden! Was ist der Plan?", "Pause ist was für Bosse, die verlieren!"),
            Moment.EVENING to listOf("Feierabend gibt's erst nach der nächsten Quest!", "Die Nacht ist jung, Einheit!"),
            Moment.WINDOW_OPEN to listOf("„{q}“! JETZT! Das Fenster ist offen, Einheit!", "Ziel erfasst: „{q}“. Ausführen!"),
            Moment.CLOSING_SOON to listOf("Morgen ist „{q}“ überfällig! Ich will das erledigt sehen, bevor ich zu Ende gebrüllt habe!"),
            Moment.OVERDUE to listOf("„{q}“ seit {d} Tagen überfällig! Zwanzig Liegestütze! Oder einfach die Quest!"),
            Moment.DIGEST to listOf("{n} Quests auf dem Plan! Erste Stellung: „{q}“! Abmarsch!"),
            Moment.COMEBACK to listOf("Zurück an der Front! Erste Quest zählt doppelt. Keine Ausreden mehr!"),
            Moment.STREAK_RISK to listOf("Die Serie steht auf der Kippe! Eine Mini-Quest! SOFORT!"),
            Moment.DONE to listOf("Akzeptabel. Weitermachen!", "Nicht schlecht, Einheit!", "Das war Pflicht, nicht Kür!"),
            Moment.LEVEL_UP to listOf("Beförderung! Stufe {l}! Bilde dir nichts darauf ein!"),
            Moment.RECORD to listOf("REKORD! Ich bin … beeindruckt. Weitermachen!"),
        ),
        Persona.BUTLER to mapOf(
            Moment.MORNING to listOf("Guten Morgen. Ihr Questlog ist angerichtet.", "Ich habe mir erlaubt, die Aufgaben des Tages bereitzulegen."),
            Moment.DAY to listOf("Darf ich die nächste Aufgabe reichen?", "Sehr wohl. Wir fahren fort."),
            Moment.EVENING to listOf("Guten Abend. Eine letzte Aufgabe vor dem Kaminfeuer?", "Der Abend ist noch jung, wenn ich das anmerken darf."),
            Moment.WINDOW_OPEN to listOf("Verzeihung. „{q}“ wäre nun angebracht, wenn Sie gestatten."),
            Moment.CLOSING_SOON to listOf("Wenn ich anmerken darf: „{q}“ wird morgen überfällig. Der Tee steht bereit."),
            Moment.OVERDUE to listOf("„{q}“ verweilt seit {d} Tagen im Zustand der Vernachlässigung. Wie bedauerlich."),
            Moment.DIGEST to listOf("Für heute sind {n} Aufgaben vorgesehen, beginnend mit „{q}“. Sehr wohl."),
            Moment.COMEBACK to listOf("Willkommen zurück. Ich habe die Glut warm gehalten. Die erste Quest zählt doppelt."),
            Moment.STREAK_RISK to listOf("Die Serie ist in Gefahr. Eine bescheidene Mini-Quest würde genügen."),
            Moment.DONE to listOf("Vorzüglich.", "Sehr wohl. Ich trage es ein.", "Ausgezeichnet, wenn ich das sagen darf."),
            Moment.LEVEL_UP to listOf("Stufe {l}. Ich habe mir erlaubt, die Fahnen zu hissen."),
            Moment.RECORD to listOf("Ein neuer Rekord. Ich bin, wenn ich das sagen darf, gerührt."),
        ),
        Persona.TIERFILMER to mapOf(
            Moment.MORNING to listOf("Die Sonne geht auf über dem Sofa. Das Exemplar regt sich.", "Ein neuer Morgen in der Wildnis der Wohnung."),
            Moment.DAY to listOf("Mittagszeit. Das Exemplar sammelt Kräfte.", "Wir beobachten weiter. Ganz leise."),
            Moment.EVENING to listOf("Die Dämmerung bricht herein. Jetzt werden die nachtaktiven Quests erledigt."),
            Moment.WINDOW_OPEN to listOf("Hier sehen wir das Exemplar in seinem natürlichen Lebensraum. „{q}“ wäre jetzt dran. Ob es sich erhebt?"),
            Moment.CLOSING_SOON to listOf("Die Zeit wird knapp. Morgen ist „{q}“ überfällig. Spannung in der Savanne."),
            Moment.OVERDUE to listOf("Seit {d} Tagen meidet das Exemplar „{q}“. Ein faszinierendes Verhalten."),
            Moment.DIGEST to listOf("Ein neuer Morgen in der Wildnis. {n} Aufgaben warten. Zuerst: „{q}“."),
            Moment.COMEBACK to listOf("Nach langer Wanderung kehrt das Exemplar zurück. Die erste Quest zählt doppelt."),
            Moment.STREAK_RISK to listOf("Die Serie ist bedroht. Wird das Exemplar reagieren? Eine Mini-Quest würde genügen."),
            Moment.DONE to listOf("Unglaublich. Das hat noch kein Team je gefilmt.", "Ein seltener Moment. Wir halten den Atem an."),
            Moment.LEVEL_UP to listOf("Stufe {l}. Das Exemplar häutet sich und wächst."),
            Moment.RECORD to listOf("Ein Rekord! Die Kamera zittert vor Ehrfurcht."),
        ),
    )

    override fun line(persona: Persona, moment: Moment, ctx: StoryContext, salt: Long): String {
        val pool = lines[persona]?.get(moment) ?: lines.getValue(Persona.CHRONIST).getValue(moment)
        val pick = pool[Rng(salt * 7919 + moment.ordinal + persona.ordinal * 31L).nextInt(pool.size)]
        return pick.replace("{q}", ctx.quest).replace("{n}", ctx.count.toString())
            .replace("{d}", ctx.days.toString()).replace("{l}", ctx.level.toString())
    }

    override fun saga(recap: Recap, geraldOn: Boolean): List<String> {
        val kw = recap.weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val rng = Rng(recap.weekStart.toEpochDay())
        val openings = listOf(
            "Höret die Kunde von der $kw. Woche des Jahres!",
            "Es begab sich in der $kw. Woche, dass die Chronik aufgeschlagen ward.",
            "Vernehmt, was in der $kw. Woche geschah, und staunet.",
        )
        val out = ArrayList<String>()
        out += openings[rng.nextInt(openings.size)]
        out += "${recap.doneCount} Quests wurden bezwungen, und ${fmt(recap.xp)} Funken stiegen gen Himmel."
        recap.topQuest?.let { out += "Am häufigsten erklang der Name „${it.first}“, und er brachte ${fmt(it.second)} Punkte ein." }
        recap.bossesDefeated.forEach { out += "Und siehe: Der Boss „$it“ fiel, und die Erde bebte ein wenig." }
        recap.bestDay?.let {
            val wd = it.first.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
            out += "Am $wd aber brannte das Feuer am hellsten: ${fmt(it.second)} Punkte an einem einzigen Tag."
        }
        out += if (recap.glutEnd >= recap.glutStart) "Die Glut wuchs von ${fmt(recap.glutStart)} auf ${fmt(recap.glutEnd)}."
        else "Die Glut sank von ${fmt(recap.glutStart)} auf ${fmt(recap.glutEnd)}, doch sie erlosch nicht."
        out += if (recap.reached) "So ward das Saisonziel von ${fmt(recap.goal)} erfüllt, und die Barden stimmten ihre Lauten."
        else "Das Saisonziel von ${fmt(recap.goal)} blieb fern. Doch am Montag beginnt eine neue Saison."
        if (geraldOn && recap.geraldXp > 0) {
            out += if (recap.xp > recap.geraldXp) "Gerald, der Ewige Zweite, blieb mit ${fmt(recap.geraldXp)} Punkten zurück und behauptete, sein Staubsauger sei kaputt."
            else "Gerald prahlte mit ${fmt(recap.geraldXp)} Punkten. Ob es stimmt, weiß niemand."
        }
        recap.bestMoment?.let { out += "Der hellste Moment der Woche: „${it.questTitle}“, ${fmt(it.points)} Punkte." }
        return out
    }

    override fun taxLetter(notice: TaxNotice): Letter {
        val from = notice.weekStart
        val to = from.plusDays(6)
        val df = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val az = "QL-${from.year}-${from.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}-${(Rng.hash(from.toString()) and 0xfff).toString(16).uppercase()}"
        return Letter(
            id = notice.id,
            title = "Bescheid über die Festsetzung der Faulheitssteuer",
            sender = "Finanzamt für Ordnung · Abteilung Glut",
            body = listOf(
                "Aktenzeichen: $az",
                "Festsetzungszeitraum: ${from.format(df)} bis ${to.format(df)}",
                "Sehr geehrte steuerpflichtige Person,",
                "für den oben genannten Zeitraum wird die Faulheitssteuer wie folgt festgesetzt:",
                "Abkühlung an Tagen ohne Quest (15 % je Tag): ${fmt(notice.decay)} Glut",
                "Säumniszuschlag für überfällige Quests: ${fmt(notice.drain)} Glut",
                "Festgesetzter Betrag: ${fmt(notice.total)} Glut",
                "Der Betrag wurde bereits einbehalten. Eine Erstattung erfolgt nicht. Eine Verrechnung mit künftigen Quests ist ausdrücklich erwünscht.",
            ),
            footer = "Rechtsbehelfsbelehrung: Gegen diesen Bescheid kann innerhalb eines Monats Widerspruch eingelegt werden, vorzugsweise durch das Erledigen einer Quest. Dieser Bescheid wurde maschinell erstellt und ist ohne Unterschrift gültig.",
        )
    }

    override fun weddingLetter(m: Marriage, first: String, second: String, zoneDate: LocalDate): Letter = Letter(
        id = "hochzeit-${m.firstId}-${m.secondId}",
        title = "Vermählungsanzeige",
        sender = "Standesamt der Chronik",
        body = listOf(
            "Wir freuen uns, die Vermählung von „$first“ und „$second“ bekannt zu geben.",
            "Nach ${m.count} gemeinsamen Auftritten binnen einer halben Stunde haben die beiden beschlossen, fortan gemeinsam durchs Leben zu gehen.",
            "Als Hochzeitsgeschenk erhält „$second“ einen Ehebonus von ×1,2, wenn es innerhalb von 30 Minuten nach „$first“ erledigt wird.",
            "Von Geschenken bitten wir abzusehen. Erledigte Quests sind jederzeit willkommen.",
        ),
        footer = "Der Hochzeitstag wird jährlich am ${zoneDate.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))} begangen.",
    )

    override fun credits(boss: Quest, xp: Int, days: Int): List<Pair<String, String>> {
        val tool = mapOf(
            "haushalt" to "der Staubsauger", "kueche" to "der Schwamm", "bad" to "die Klobürste",
            "waesche" to "der Wäschekorb", "sport" to "die Isomatte", "lernen" to "der Textmarker",
            "arbeit" to "die Tastatur", "papierkram" to "der Locher", "selbst" to "die Wärmflasche",
            "sozial" to "das Telefon", "garten" to "die Gießkanne", "sonstiges" to "ein Klebezettel",
        )
        val catering = listOf("eine halbe Tüte Chips", "kalter Kaffee", "drei Kekse aus der Notreserve", "Leitungswasser, stilvoll serviert", "ein Apfel, aus Prinzip")
        val rng = Rng(boss.id)
        return buildList {
            add("" to "Ein Questlog-Original")
            add("" to "Nach einer wahren Begebenheit")
            add("Regie" to "Du")
            add("Drehbuch" to "Du, ein bisschen widerwillig")
            add("In der Titelrolle" to boss.title)
            boss.subtasks.forEach { add("Gefallen" to it.title) }
            add("Nebenrolle" to (tool[boss.category] ?: "ein Klebezettel"))
            add("Catering" to catering[rng.nextInt(catering.size)])
            add("Spezialeffekte" to "${fmt(xp)} Funken Glut")
            add("Drehzeit" to if (days <= 1) "ein Tag" else "$days Tage")
            add("" to "Bei den Dreharbeiten kamen nur Staubkörner zu Schaden.")
            add("" to "ENDE")
        }
    }

    override fun bossIntro(boss: Quest): String {
        val n = boss.subtasks.size
        return "Ein Boss erhebt sich: „${boss.title}“. $n ${if (n == 1) "Treffer" else "Treffer"} bis zum Sieg."
    }

    private fun fmt(n: Int): String = String.format(Locale.GERMANY, "%,d", n)
}

/**
 * Schnelleingabe ohne KI: eine Zeile = eine Quest. Erkennt Rhythmus („alle 3 Tage“, „wöchentlich“),
 * Dauer („30 min“, „1 h“), Wichtigkeit („!“, „wichtig“), Unlust („nervig“, „hasse“), Termine
 * („bis Freitag“, „bis 30.11.“) und Teilaufgaben nach Doppelpunkt („Keller: Regal, Kisten“ wird ein Boss).
 */
class HeuristicQuestSmith : QuestSmith {
    private val catWords = listOf(
        "bad" to listOf("bad", "klo", "toilette", "dusche", "badewanne", "waschbecken", "fliesen"),
        "kueche" to listOf("küche", "spül", "abwasch", "kochen", "kühlschrank", "herd", "ofen", "geschirr"),
        "waesche" to listOf("wäsche", "waschen", "bügeln", "bett beziehen", "betten", "socken"),
        "haushalt" to listOf("saugen", "staub", "putzen", "wischen", "aufräumen", "müll", "fenster", "boden", "keller", "dachboden"),
        "sport" to listOf("sport", "laufen", "joggen", "gym", "training", "yoga", "rad", "schwimmen", "liegestütz", "dehnen"),
        "lernen" to listOf("lernen", "lesen", "vokabel", "kurs", "üben", "studium", "buch", "kapitel"),
        "papierkram" to listOf("steuer", "rechnung", "post", "formular", "amt", "vertrag", "versicherung", "brief", "überweis"),
        "arbeit" to listOf("arbeit", "projekt", "mail", "meeting", "bericht", "präsentation", "backup"),
        "selbst" to listOf("arzt", "zahn", "meditation", "schlaf", "wasser", "friseur", "vitamin", "pause"),
        "sozial" to listOf("anrufen", "treffen", "geburtstag", "mama", "papa", "oma", "opa", "freunde", "freund"),
        "garten" to listOf("garten", "pflanzen", "gießen", "rasen", "unkraut", "balkon", "hecke"),
    )

    override fun draft(text: String, today: LocalDate): List<QuestDraft> =
        text.lines().map { it.trim().trimStart('-', '*', '•').trim() }.filter { it.length >= 2 }.map { parse(it, today) }

    fun parse(raw: String, today: LocalDate): QuestDraft {
        var line = raw
        val lower = raw.lowercase(Locale.GERMAN)
        var subtasks = emptyList<String>()
        var boss = false
        val colon = line.indexOf(':')
        if (colon > 0 && colon < line.length - 1) {
            val parts = line.substring(colon + 1).split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                subtasks = parts
                boss = parts.size >= 3
                line = line.substring(0, colon)
            }
        }
        var minD: Int? = null
        var maxD: Int? = null
        Regex("""alle\s+(\d+)\s*(?:-|bis)\s*(\d+)\s+tage?""").find(lower)?.let {
            minD = it.groupValues[1].toInt(); maxD = it.groupValues[2].toInt()
        } ?: Regex("""alle\s+(\d+)\s+tage?""").find(lower)?.let {
            val n = it.groupValues[1].toInt(); maxD = n; minD = maxOf(1, (n * 0.6).toInt())
        } ?: Regex("""alle\s+(\d+)\s+wochen?""").find(lower)?.let {
            val n = it.groupValues[1].toInt() * 7; maxD = n; minD = maxOf(1, (n * 0.7).toInt())
        } ?: run {
            when {
                "täglich" in lower || "jeden tag" in lower -> { minD = 1; maxD = 1 }
                "jeden zweiten tag" in lower -> { minD = 2; maxD = 2 }
                "zweiwöchentlich" in lower || "alle zwei wochen" in lower -> { minD = 10; maxD = 14 }
                "wöchentlich" in lower || "jede woche" in lower || "einmal die woche" in lower -> { minD = 5; maxD = 7 }
                "monatlich" in lower || "jeden monat" in lower || "einmal im monat" in lower -> { minD = 25; maxD = 31 }
            }
        }
        var effort = 15
        Regex("""(\d+)\s*(?:min|minuten)\b""").find(lower)?.let { effort = it.groupValues[1].toInt() }
        Regex("""(\d+(?:[.,]\d)?)\s*(?:h|std|stunden?)\b""").find(lower)?.let {
            effort = (it.groupValues[1].replace(',', '.').toDouble() * 60).toInt()
        }
        val importance = when {
            "!!" in raw || "sehr wichtig" in lower || "dringend" in lower -> 5
            "!" in raw || "wichtig" in lower -> 4
            "unwichtig" in lower || "irgendwann" in lower -> 2
            else -> 3
        }
        val dread = when {
            listOf("hasse", "horror", "grauen", "albtraum").any { it in lower } -> 5
            listOf("nervig", "lästig", "keine lust", "ugh").any { it in lower } -> 4
            listOf("macht spaß", "gerne", "gern").any { it in lower } -> 2
            else -> 3
        }
        var due: String? = null
        Regex("""bis\s+(\d{1,2})\.(\d{1,2})\.?(\d{2,4})?""").find(lower)?.let {
            val d = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val y = it.groupValues[3].takeIf { s -> s.isNotEmpty() }?.toInt()?.let { yy -> if (yy < 100) 2000 + yy else yy } ?: today.year
            runCatching { LocalDate.of(y, m, d) }.getOrNull()?.let { date -> due = (if (date.isBefore(today) && it.groupValues[3].isEmpty()) date.plusYears(1) else date).toString() }
        }
        if (due == null) {
            val days = listOf("montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag")
            Regex("""bis\s+(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|morgen|heute)""").find(lower)?.let {
                val w = it.groupValues[1]
                due = when (w) {
                    "heute" -> today
                    "morgen" -> today.plusDays(1)
                    else -> {
                        val target = days.indexOf(w) + 1
                        var d = today.plusDays(1)
                        while (d.dayOfWeek.value != target) d = d.plusDays(1)
                        d
                    }
                }.toString()
            }
        }
        val category = catWords.firstOrNull { (_, words) -> words.any { it in lower } }?.first ?: "sonstiges"
        val title = cleanTitle(line)
        return QuestDraft(
            title = title.ifBlank { raw.trim() }, category = category, importance = importance, effortMin = effort.coerceIn(1, 480),
            dread = dread, minDays = minD, maxDays = maxD, subtasks = subtasks, boss = boss,
            dueDate = if (minD == null && maxD == null) due else null,
        )
    }

    private fun cleanTitle(s: String): String {
        var t = s
        val patterns = listOf(
            """alle\s+\d+\s*(?:-|bis)\s*\d+\s+tage?""", """alle\s+\d+\s+(?:tage?|wochen?)""", """täglich""", """jeden\s+(?:zweiten\s+)?tag""",
            """(?:zwei)?wöchentlich""", """jede\s+woche""", """einmal\s+(?:die\s+woche|im\s+monat)""", """alle\s+zwei\s+wochen""",
            """monatlich""", """jeden\s+monat""", """\d+\s*(?:min|minuten)\b""", """\d+(?:[.,]\d)?\s*(?:h|std|stunden?)\b""",
            """bis\s+\d{1,2}\.\d{1,2}\.?(?:\d{2,4})?""", """bis\s+(?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|morgen|heute)""",
            """sehr\s+wichtig""", """unwichtig""", """wichtig""", """dringend""", """keine\s+lust""",
            """nervig""", """lästig""", """hasse""", """horror""", """grauen""", """albtraum""", """\bugh\b""",
            """macht\s+spaß""", """\bgerne?\b""", """irgendwann""",
        )
        for (p in patterns) t = t.replace(Regex(p, RegexOption.IGNORE_CASE), " ")
        t = t.replace("!", " ").replace(Regex("""\s+"""), " ").trim().trim(',', '-', '.')
        return t.replaceFirstChar { it.uppercase() }
    }
}
