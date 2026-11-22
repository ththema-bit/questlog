package io.github.ththemabit.questlog.core

/** F07: fertige Quest-Pakete für den schnellen Start. Werte sind Vorschläge, alles bleibt anpassbar. */
data class QuestDraft(
    val title: String,
    val category: String,
    val importance: Int = 3,
    val effortMin: Int = 15,
    val dread: Int = 3,
    val minDays: Int? = null,
    val maxDays: Int? = null,
    val mini: String? = null,
    val why: String? = null,
    val subtasks: List<String> = emptyList(),
    val boss: Boolean = false,
    val dueDate: String? = null,
) {
    fun toQuest(id: String, createdAt: Long, subId: (Int) -> String): Quest = Quest(
        id = id, title = title.trim(), category = category, importance = importance.coerceIn(1, 5),
        effortMin = effortMin.coerceIn(1, 480), dread = dread.coerceIn(1, 5), minDays = minDays, maxDays = maxDays,
        dueDate = dueDate, mini = mini, why = why,
        subtasks = subtasks.mapIndexed { i, t -> Subtask(subId(i), t.trim()) },
        boss = boss, createdAt = createdAt,
    )
}

data class TemplatePack(val key: String, val name: String, val icon: String, val text: String, val quests: List<QuestDraft>)

object Templates {
    val packs = listOf(
        TemplatePack(
            "haushalt", "Haushalt", "broom", "Die Klassiker, damit die Wohnung nicht kippt.",
            listOf(
                QuestDraft("Staubsaugen", "haushalt", 3, 20, 3, 3, 7, mini = "Nur den Flur"),
                QuestDraft("Bad putzen", "bad", 4, 20, 4, 4, 9, mini = "Nur das Waschbecken"),
                QuestDraft("Küche wischen", "kueche", 3, 10, 2, 2, 4, mini = "Nur die Arbeitsfläche"),
                QuestDraft("Spülmaschine ausräumen", "kueche", 3, 5, 3, 1, 2, mini = "Nur das Besteck"),
                QuestDraft("Wäsche waschen", "waesche", 3, 15, 2, 3, 7, mini = "Eine Maschine anstellen"),
                QuestDraft("Müll rausbringen", "haushalt", 3, 5, 2, 2, 4),
                QuestDraft("Betten beziehen", "waesche", 2, 15, 3, 10, 14),
                QuestDraft("Kühlschrank ausmisten", "kueche", 2, 20, 4, 14, 30, mini = "Ein Fach"),
                QuestDraft("Fenster putzen", "haushalt", 2, 45, 4, 30, 60, mini = "Ein Fenster"),
                QuestDraft("Pflanzen gießen", "garten", 3, 5, 1, 2, 4),
            ),
        ),
        TemplatePack(
            "sport", "Sport", "sword", "Bewegung mit Rhythmus statt schlechtem Gewissen.",
            listOf(
                QuestDraft("Laufen gehen", "sport", 4, 30, 4, 2, 4, mini = "Zehn Minuten um den Block"),
                QuestDraft("Krafttraining", "sport", 4, 40, 4, 2, 4, mini = "Eine Übung, drei Sätze"),
                QuestDraft("Dehnen", "sport", 3, 10, 2, 1, 2, mini = "Zwei Minuten Nacken und Rücken"),
                QuestDraft("Spaziergang", "sport", 3, 20, 1, 1, 2),
            ),
        ),
        TemplatePack(
            "lernen", "Lernen", "book", "Wissen sammeln in kleinen Portionen.",
            listOf(
                QuestDraft("Vokabeln wiederholen", "lernen", 3, 15, 3, 1, 2, mini = "Fünf Karten"),
                QuestDraft("Ein Kapitel lesen", "lernen", 3, 30, 2, 1, 3, mini = "Zwei Seiten"),
                QuestDraft("Projekt-Session", "lernen", 4, 60, 4, 2, 4, mini = "Datei öffnen und eine Zeile schreiben"),
            ),
        ),
        TemplatePack(
            "selbst", "Selbstfürsorge", "heart", "Die Sachen, die man immer zuerst streicht.",
            listOf(
                QuestDraft("Zahnseide", "selbst", 3, 2, 2, 1, 2),
                QuestDraft("Meditation", "selbst", 3, 10, 2, 1, 2, mini = "Drei tiefe Atemzüge"),
                QuestDraft("Früh ins Bett", "selbst", 4, 5, 3, 1, 3),
                QuestDraft("Jemanden anrufen", "sozial", 3, 20, 2, 5, 10, mini = "Eine Nachricht schreiben"),
            ),
        ),
        TemplatePack(
            "papierkram", "Papierkram", "quill", "Briefe, Rechnungen und der eine Boss.",
            listOf(
                QuestDraft("Post sortieren", "papierkram", 3, 10, 3, 5, 10, mini = "Nur öffnen"),
                QuestDraft("Kontoauszüge prüfen", "papierkram", 3, 15, 3, 14, 30),
                QuestDraft("Backup machen", "arbeit", 4, 15, 3, 30, 60),
                QuestDraft(
                    "Steuererklärung", "papierkram", 5, 180, 5, boss = true,
                    subtasks = listOf("Belege sammeln", "Lohnsteuerbescheinigung finden", "Werbungskosten eintragen", "Sonderausgaben eintragen", "Abschicken"),
                ),
            ),
        ),
    )
}
