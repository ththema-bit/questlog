# Questlog: Design

## Grundsätze

1. **Ohne KI vollständig.** Alles läuft lokal mit Vorlagen und Faustregeln. Eine KI macht später das Erzählen schöner und legt Quests aus Freitext klüger an, mehr nicht.
2. **Fair statt fies.** Abzug trifft nur die Glut, nie XP, Level, Meilensteine oder Rekorde. Pausen kosten nichts, ein freier Tag bricht keine Serie. Jede Regel ist im Beipackzettel sichtbar.
3. **Offline und privat.** Kein Konto, kein Tracking, keine Werbung, keine Internet-Berechtigung.
4. **Ein Protokoll, alles nachgerechnet.** Gespeichert werden nur Quests, Ereignisse (erledigt, Teilaufgabe, Kauf, Wette) und Einstellungen. Punkte, Glut, Serien, Rekorde, Beute und Meilensteine rechnet die Engine bei jedem Start Tag für Tag neu aus. Dadurch stimmen Nachtragen und Rückgängig immer, und ein Backup ist eine einzige JSON-Datei.

## Regeln (Stellschrauben in `android/app/src/main/java/.../core/Rules.kt`)

**Punkte pro Quest** = Basis × Wichtigkeit × Überwindung × Timing × Boni + Saisonauftakt

| Teil | Regel |
|---|---|
| Basis | 10 × √Minuten (2 h bringen das Fünffache von 5 min, nicht das 24-fache) |
| Wichtigkeit 1–5 | ×0,70 bis ×1,30 |
| Überwindung 1–5 | ×0,60 bis ×1,40 |
| Timing | zu früh ×0,25 · im Fenster ×1 · überfällig −10 %/Tag, mindestens ×0,5 |
| Serie | +1 % pro Tag, höchstens +30 % |
| Combo | weitere Quest binnen 45 min: ×1,1 / ×1,2 / ×1,3 |
| Kopfgeld | eine Quest pro Tag ×2 (die älteste oder meistgehasste im Fenster) |
| Roulette | ×1,5, wenn binnen einer Stunde erledigt |
| Comeback | erste Quest nach 3+ freien Tagen ×2 |
| Karten | laut Modifier, zusammen höchstens ×2,5 |
| Alle Boni | zusammen höchstens ×5 |
| Saisonauftakt | erste Quest der Woche +5 % des Wochenziels (Wochenziel startet nie bei null) |
| Mini-Version | 10 × √2 × Serie, hält Serie und Glut, setzt das Fenster aber nicht zurück |
| Teilaufgaben | je Anteil × 70 %, die letzte bringt den Rest (30 %), beim Boss abzüglich Zorn |

**Glut:** −15 % pro Tag, jede Quest legt ihre Punkte nach, überfällige Quests ziehen täglich Wichtigkeit × 5 ab. Boden: 10 % der Bestform. Pendelt sich bei etwa dem Siebenfachen der Tagespunkte ein.

**Serie:** zählt aktive Tage. Ein freier Tag ist erlaubt, ab dem zweiten in Folge kostet jeder weitere einen Schutzschild (max. 3, alle 10 Serientage gibt es einen neuen, Beute bringt weitere). Hardcore-Woche: keine Schilde, keine Mini-Versionen, alles ×2.

**Level:** Stufe n braucht 50 · n · (n − 1) XP (Stufe 10 bei 4.500, Stufe 20 bei 19.000). Ränge: Kerze, Laterne, Fackel, Lagerfeuer, Schmiedeesse, Leuchtfeuer, Drachenatem, Sonnenglut, Sternenfeuer. Skills je Kategorie mit 20 · n · (n − 1).

**Wochenziel:** Schnitt der letzten vier Wochen + 10 %, gerundet auf 50, Start bei 300.

**Beute:** 12 % Chance pro Quest (+2 % je Überwindungsstufe über 3), Bosse immer. Schild, Münzbeutel, Glutfunke, Glückskarte (×1,5 einmalig) oder Trophäe (20 Stück, drei selten). Zufall ist deterministisch (aus der Ereignis-ID), damit das Nachrechnen stabil bleibt.

**Münzen:** 1 Punkt = 1 Münze, plus Beute, minus Einkäufe. XP bleiben davon unberührt.

**Modifier-Freischaltung:** Soundtrack (Stufe 2), Telefonjoker (3), Handy weg (4), Drei-Song (5), Blind Date (6), Doppelt oder nichts (7), Hardcore-Woche (8), Sportreporter (9), Tanzpause (10).

## Look

90er-Fantasy-Rollenspiel als eigene Interpretation, keine Kopie eines bestimmten Spiels.

- **Material:** Kerkermauer als Hintergrund, Steinplatten mit Fase und Goldnieten, Pergament mit gerissenen Kanten und Flecken, Schriftrollen mit Holzstäben, rotes Wachssiegel als Abhak-Knopf.
- **Pixel:** Alles wird in ganzzahligen „Kunst-Pixeln“ gezeichnet (`ui/pixel/PixelKit.kt`), damit es auf jedem Display scharf bleibt. Keine Verläufe.
- **Icons:** 50 eigene 16×16-Pixelgrafiken als Text in `ui/pixel/IconArt.kt`. App-Icon, Benachrichtigungs-Icon und Widget-Bilder erzeugt `android/tools/gen_assets.py` daraus.
- **Schriften (OFL):** Jacquard 24 (Pixel-Fraktur, Überschriften), Pixelify Sans (Text), Jersey 10 (Zahlen), Silkscreen (Etiketten), Comic Neue (nur für den Fluch).
- **Klang:** 8-Bit-Klänge werden live synthetisiert (`ui/Fx.kt`), keine Audiodateien.
- **UX:** Wischen oder Siegel antippen zum Abhaken, Beipackzettel mit Rückgängig, Haptik, kompakter Kopf, damit Quests sofort im Bild sind, große Tippflächen (48 dp).

## Psychologie dahinter (Kurzfassung)

| Hebel | Umsetzung |
|---|---|
| Selbstbestimmung (Ryan & Deci) | Quests, Regeln, Karten selbst gewählt, nichts ist Pflicht |
| Hürde senken (Fogg) | Mini-Version, ein Tipp, Widget, Vorlagen, Schnelleingabe |
| Wenn-dann-Pläne (Gollwitzer) | Feld „Wenn-dann“ an jeder Quest |
| Sofortige Belohnung | Beipackzettel, Klang, Haptik, Konfetti |
| Temptation Bundling (Milkman) | Soundtrack-Karte |
| Zielgradient, geschenkter Vorsprung | Fortschrittsbalken überall, Saisonauftakt |
| Verlustaversion ohne Absturz | Glut mit Boden, XP sinken nie, Comeback ×2 |
| Ein Fehltag ist egal (Lally) | Serie bricht erst am zweiten freien Tag |
| Fresh Start (Dai, Milkman, Riis) | Wochen-Saison ab Montag, Rückblick |
| Neugier, Gewöhnung | versteckte Meilensteine, Modifier-Freischaltung, Feiertage |
| Grün trägt, Rot treibt (Octalysis) | Stufen 1–2 fast nur „grüne“ Mechaniken, Verlust und Zufall nur als Würze |

## Stand der Features

Umgesetzt: alle Ideen aus Fundament (F01–F10), Game-Kern (K01–K10), Würze außer W08 und W10, Modifier außer M09, Chaos (C01–C10).

Noch offen:
- **W08 Questreihen** und **M09 Dresscode**: im Brainstorming auf „später“ gesetzt.
- **W10 Koop (WG/Partner):** braucht Sync zwischen Handys. Das Ereignis-Protokoll ist dafür gemacht: Ereignisse mehrerer Handys lassen sich zusammenführen und nachrechnen. Fehlt noch: ein Sync-Weg (z. B. eigener kleiner Server auf der AI-Box oder Datei-Austausch) und Spieler-IDs an Ereignissen.
- **Englische Übersetzung** für den Play Store: Texte liegen gebündelt in Kotlin (`core/Story.kt`, Screens) und müssen dafür in Ressourcen.

## KI als Bonus (geplant, nicht eingebaut)

Die Naht ist schon da: `core/Story.kt` definiert `Storyteller` (Begrüßung, Erinnerungen, Saga, Briefe, Abspann) und `QuestSmith` (Freitext → Quests). Heute stecken dahinter `TemplateStoryteller` und `HeuristicQuestSmith`. Eine KI-Variante implementiert dieselben Schnittstellen.

Weil Antworten dauern, erzeugt die KI Texte vorab (z. B. die Saga am Sonntagabend) und legt sie in `SaveData.storyCache` ab. Die Oberfläche fragt immer zuerst den Cache und fällt sonst auf die Vorlage zurück. So bleibt die App schnell und funktioniert auch, wenn die KI gerade nicht erreichbar ist.

Drei Wege, alle optional:

1. **Eigene API:** eine OpenAI-kompatible Schnittstelle deckt lokale Server (Ollama, LM Studio, llama.cpp auf der AI-Box) und die meisten Cloud-Anbieter ab, die Anthropic-API kommt als eigener Adapter dazu. Nutzer tragen Adresse und Schlüssel selbst ein.
2. **Modell auf dem Handy:** ein kleines, quantisiertes Modell (etwa 0,5 bis 2 Milliarden Parameter) über MediaPipe LLM Inference oder llama.cpp. Gut für kurze Texte, langsamer bei der Saga.
3. **Eigenes Modell auf der AI-Box trainieren:** ein kleines Basismodell per LoRA auf Questlog-Aufgaben feintunen (Freitext → Quest-JSON, Wochenwerte → Saga, Persona-Zeilen), Trainingsdaten synthetisch aus den Vorlagen und der Engine erzeugen, dann als GGUF bzw. `.task`-Datei fürs Handy exportieren. Welches Basismodell, zum Zeitpunkt des Trainings neu entscheiden.

Was die KI freischalten soll: bessere Saga, Erzähler, Boss-Intros und Abspann, klügere Schnelleingabe (Rhythmus, Teilaufgaben, Kategorie aus Kontext), Vorschläge für passende Mini-Versionen. Die Spielregeln selbst rechnet weiter die Engine, nie die KI.

Datenschutz: Die Basis-App bleibt ohne Internet-Berechtigung. KI per API am besten als eigene Build-Variante oder ausdrücklicher Opt-in mit Hinweis, dass Quest-Titel an den gewählten Anbieter gehen. Dafür braucht der Play Store dann eine Datenschutzerklärung mit diesem Punkt.

## Play Store (Checkliste)

1. **App-ID** `io.github.ththemabit.questlog` (nach dem ersten Upload nicht mehr änderbar).
2. **Signatur:** Upload-Schlüssel erzeugen, `keystore.properties` anlegen (siehe `android/README.md`), nie committen.
3. **Bundle bauen:** `gradlew bundleRelease` (Play nimmt AAB).
4. **Datenschutzerklärung:** nötig, weil die App das Mikrofon anfragen kann (Kampfschrei). Inhalt: keine Daten verlassen das Gerät, Mikrofon nur für die Lautstärke-Messung, nichts wird aufgenommen oder gespeichert.
5. **Datensicherheit (Formular):** keine Daten erhoben oder geteilt.
6. **Preis:** kostenpflichtig oder kostenlos mit Kauf einer Vollversion; Werbung würde den Belohnungsmoment stören und passt nicht zum Konzept.
7. **Store-Grafiken:** Screenshots liefern die Paparazzi-Tests (`android/app/src/test/snapshots/images`), Icon-Grafik aus `tools/gen_assets.py`.
8. **Übersetzung:** mindestens Englisch für mehr Reichweite.
9. **Vor dem Release** einmal auf echten Geräten prüfen: Benachrichtigungen (Android 13+ fragt), Widget, Tanzpause (Sensor), Kampfschrei (Mikrofon), Backup-Export/Import.
