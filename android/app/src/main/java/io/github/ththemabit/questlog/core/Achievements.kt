package io.github.ththemabit.questlog.core

/** Zähler, aus denen die Meilensteine (K08) entstehen. Werden beim Nachrechnen mitgeführt. */
class AchStats {
    var xp = 0L
    var activeDays = 0L
    var bestStreak = 0L
    var glutMax = 0L
    var doneFull = 0L
    var minis = 0L
    var bossDefeats = 0L
    var bounties = 0L
    var comboMax = 0L
    var goalsReached = 0L
    var dread5 = 0L
    var pbs = 0L
    var loot = 0L
    var purchases = 0L
    var nightOwl = 0L
    var earlyBird = 0L
    var phoenix = 0L
    var tenInDay = 0L
    var wagerWins = 0L
    var hardcoreWins = 0L
    var dances = 0L
    var shoutMax = 0L
    var geraldBeaten = 0L
    var marriages = 0L
    var curseBroken = 0L
    var backfills = 0L
    var earlyDone = 0L
    var ghostBusts = 0L
    var categoriesWeekMax = 0L
}

data class Achievement(
    val id: String,
    val title: String,
    val text: String,
    val icon: String,
    val target: Long,
    val hidden: Boolean = false,
    val value: (AchStats) -> Long,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("erste_glut", "Erste Glut", "Die erste Quest erledigt.", "flame", 1) { it.doneFull },
        Achievement("funken_1k", "Tausend Funken", "1.000 XP gesammelt.", "spark", 1_000) { it.xp },
        Achievement("funken_10k", "Zehntausend Funken", "10.000 XP gesammelt.", "spark", 10_000) { it.xp },
        Achievement("funken_100k", "Hunderttausend Funken", "100.000 XP gesammelt.", "spark", 100_000) { it.xp },
        Achievement("tage_7", "Eine Woche dabei", "An 7 Tagen etwas geschafft.", "calendar", 7) { it.activeDays },
        Achievement("tage_30", "Mondlauf", "An 30 Tagen etwas geschafft.", "moon", 30) { it.activeDays },
        Achievement("tage_66", "Eingebrannt", "66 aktive Tage. Im Schnitt läuft eine Gewohnheit ab hier von selbst.", "flame", 66) { it.activeDays },
        Achievement("tage_100", "Hundert Tage Chronik", "An 100 Tagen etwas geschafft.", "scroll", 100) { it.activeDays },
        Achievement("tage_365", "Ein Jahr Chronik", "An 365 Tagen etwas geschafft.", "crown", 365) { it.activeDays },
        Achievement("serie_7", "Sieben am Stück", "Eine Serie von 7 Tagen.", "shield", 7) { it.bestStreak },
        Achievement("serie_30", "Unermüdlich", "Eine Serie von 30 Tagen.", "shield", 30) { it.bestStreak },
        Achievement("serie_100", "Ewige Flamme", "Eine Serie von 100 Tagen.", "flame", 100) { it.bestStreak },
        Achievement("glut_1000", "Flächenbrand", "Die Glut über 1.000 gebracht.", "flame", 1_000) { it.glutMax },
        Achievement("vielfalt", "Vielseitig", "Quests aus 5 Kategorien in einer Woche.", "star", 5) { it.categoriesWeekMax },
        Achievement("boss_1", "Erster Boss gefallen", "Einen Boss besiegt.", "skull", 1) { it.bossDefeats },
        Achievement("boss_5", "Drachenzähmung", "Fünf Bosse besiegt.", "skull", 5) { it.bossDefeats },
        Achievement("kopfgeld_5", "Kopfgeldjagd", "Fünf Kopfgelder kassiert.", "target", 5) { it.bounties },
        Achievement("combo_4", "Kettenreaktion", "Vier Quests in Folge, Combo ×1,3.", "chain", 3) { it.comboMax },
        Achievement("ziel_1", "Saisonziel erreicht", "Ein Wochenziel geschafft.", "flag", 1) { it.goalsReached },
        Achievement("ziel_10", "Zehn Saisons", "Zehn Wochenziele geschafft.", "flag", 10) { it.goalsReached },
        Achievement("mini_10", "Kleinvieh", "Zehn Mini-Versionen. Macht auch Mist.", "feather", 10) { it.minis },
        Achievement("dread_5", "Ins Auge des Sturms", "Eine Quest mit Überwindung 5 erledigt.", "skull", 1) { it.dread5 },
        Achievement("pb_5", "Schneller als der Schatten", "Fünf Bestzeiten verbessert.", "hourglass", 5) { it.pbs },
        Achievement("loot_10", "Schatzsucher", "Zehn Beutestücke gefunden.", "chest", 10) { it.loot },
        Achievement("kauf_1", "Gönn dir", "Die erste Belohnung gekauft.", "coin", 1) { it.purchases },
        Achievement("nachteule", "Nachteule", "Eine Quest zwischen Mitternacht und 4 Uhr erledigt.", "moon", 1, hidden = true) { it.nightOwl },
        Achievement("morgengrauen", "Morgengrauen", "Eine Quest vor 6 Uhr morgens erledigt.", "sun", 1, hidden = true) { it.earlyBird },
        Achievement("phoenix", "Phönix", "Nach 7 oder mehr freien Tagen zurückgekommen.", "phoenix", 1, hidden = true) { it.phoenix },
        Achievement("zehn_am_tag", "Zehn an einem Tag", "Zehn Quests an einem einzigen Tag.", "crown", 1, hidden = true) { it.tenInDay },
        Achievement("alles_auf_eine_karte", "Alles auf eine Karte", "Doppelt oder nichts gewonnen.", "coin", 1, hidden = true) { it.wagerWins },
        Achievement("hart_wie_stein", "Hart wie Stein", "Eine Hardcore-Woche mit erreichtem Ziel.", "skull", 1, hidden = true) { it.hardcoreWins },
        Achievement("tanzbein", "Tanzbein", "Eine Tanzpause bestanden.", "boot", 1, hidden = true) { it.dances },
        Achievement("lautstark", "Lautstark", "Ein Kampfschrei mit über 85 Punkten Lautstärke.", "horn", 85, hidden = true) { it.shoutMax },
        Achievement("gerald", "Gerald geschlagen", "Eine Woche mehr geschafft als Gerald.", "crown", 1, hidden = true) { it.geraldBeaten },
        Achievement("hochzeit", "Bis dass der Staub uns scheidet", "Zwei Quests haben geheiratet.", "ring", 1, hidden = true) { it.marriages },
        Achievement("fluchbrecher", "Fluchbrecher", "Den Comic-Sans-Fluch gebrochen.", "scroll", 1, hidden = true) { it.curseBroken },
        Achievement("chronist", "Ehrliche Chronik", "Fünf Quests nachgetragen statt vergessen.", "quill", 5, hidden = true) { it.backfills },
        Achievement("uebereifrig", "Übereifrig", "Dreimal eine Quest zu früh erledigt.", "hourglass", 3, hidden = true) { it.earlyDone },
        Achievement("geisterjaeger", "Geisterjäger", "Eine Woche 500 XP vor dem Geist der Vorwoche.", "ghost", 1, hidden = true) { it.ghostBusts },
    )
}
