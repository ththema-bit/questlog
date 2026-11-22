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
- **Icons:** 51 eigene 16×16-Pixelgrafiken als Text in `ui/pixel/IconArt.kt`. App-Icon und Benachrichtigungs-Icon erzeugt `android/tools/gen_assets.py` daraus.
- **Widgets:** zwei Schriftrollen für den Homescreen, die Questrolle (dringendste Quests, Wachssiegel zum Abhaken, Tipp auf die Quest öffnet sie) und die Glutrolle (Glut, Serie, Stufe, Wochenziel, ein Wort vom Spielleiter oder Erzähler). Widgets können weder eigene Schriften noch eigenes Zeichnen, darum zeichnet `widget/WidgetRender.kt` die ganze Rolle als ein Bild in Gerätepixeln (Stäbe, Pergament, Pixelschrift, Icons), und Glance legt nur unsichtbare Tippflächen darüber. Dieselben Bilder sind die Vorschau in der Widget-Auswahl.
- **Schriften (OFL):** Cinzel Decorative (Überschriften), Pixelify Sans (Text), Jersey 10 (Zahlen), Silkscreen (Etiketten), Comic Neue (nur für den Fluch). Überschriften werden ohne Kantenglättung in Kunst-Pixeln gerastert und scharf vergrößert, mit einem Pixel Schlagschatten (`ui/pixel/PixelTitle.kt`). So wird aus einer Fantasy-Antiqua eine Pixel-Überschrift wie in alten Rollenspielen. Die Schrift steht nur in `HeadingRaster.fontRes` und lässt sich dort gegen eine Schrift aus dem kommenden Stylesheet tauschen.
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

Umgesetzt: alle Ideen aus Fundament (F01–F10), Game-Kern (K01–K10), Würze außer W08 und W10, Modifier außer M09, Chaos (C01–C10) und der optionale KI-Spielleiter (siehe unten).

Noch offen:
- **W08 Questreihen** und **M09 Dresscode**: im Brainstorming auf „später“ gesetzt.
- **W10 Koop (WG/Partner):** braucht Sync zwischen Handys. Das Ereignis-Protokoll ist dafür gemacht: Ereignisse mehrerer Handys lassen sich zusammenführen und nachrechnen. Fehlt noch: ein Sync-Weg (z. B. eigener kleiner Server auf der AI-Box oder Datei-Austausch) und Spieler-IDs an Ereignissen.
- **Englische Übersetzung** für den Play Store: Texte liegen gebündelt in Kotlin (`core/Story.kt`, Screens) und müssen dafür in Ressourcen.

## KI-Spielleiter (optional, eingebaut)

Die App läuft komplett ohne KI. Wer will, verbindet sie unter **Optionen → KI-Spielleiter** mit einem eigenen Server. Gesprochen wird die OpenAI-kompatible Schnittstelle (`/v1/models`, `/v1/chat/completions`, mit Streaming), die Ollama, LM Studio, llama.cpp, vLLM und viele Cloud-Anbieter können. Adresse, Modell und bei Cloud-Diensten ein API-Schlüssel werden selbst eingetragen. „Verbindung testen“ listet die Modelle des Servers zum Antippen.

| Funktion (einzeln schaltbar) | Wo | Ohne KI |
|---|---|---|
| **Quests bauen** (Agent) | Kristallkugel oben auf dem Board, „Spielleiter fragen“ in der Schnelleingabe, Kugel in jeder Quest („Mit dem Spielleiter besprechen“) | Schnelleingabe mit Faustregeln |
| **Questtexte** | Einleitung in der Quest, Siegeszeile beim Abhaken, Ermutigung für die Mini-Version, Erinnerung beim Öffnen des Fensters, Boss-Auftritt | Vorlagen |
| **Tagesbriefing** | Begrüßung auf dem Board, Morgen-Übersicht als Benachrichtigung | Erzähler-Zeilen |
| **Heldensaga** | Wochenrückblick, aus den echten Zahlen der Woche | Vorlagen-Saga (Chaos-Kammer) |

Im Gespräch beschreibt man, was ansteht („Ich ziehe Ende Dezember um“). Der Spielleiter antwortet in seiner Stimme und schlägt neue Quests, Änderungen an bestehenden und Archivierungen vor. Jeder Vorschlag hat ein Häkchen, übernommen wird nur, was man bestätigt.

Regeln:

- **Die KI rechnet nie Punkte aus.** Sie liefert nur Titel, Kategorie, Werte, Zeitfenster, Teilaufgaben und Texte. Alles wird geprüft und auf gültige Werte begrenzt (Wichtigkeit 1–5, Aufwand bis 8 h, Fenster sortiert, Termine nicht in der Vergangenheit).
- **Die Oberfläche wartet nie auf die KI.** Texte entstehen im Hintergrund, solange die App offen ist (`ai/AiService.kt`), und liegen in `SaveData.storyCache`. Fehlt ein Text oder passt er nach einer Änderung (neuer Titel, andere Kategorie) nicht mehr zur Quest, gilt die Vorlage, bis der neue fertig ist. Reihenfolge: Briefing, gerade geöffnete Quest, Saga, dann überfällige, offene und übrige Quests.
- Das Briefing wird neu geschrieben, wenn ein neuer Tagesabschnitt beginnt (Nacht, Morgen, Tag, Abend), die Erzähler-Stimme wechselt oder eine Quest erledigt wurde. Bis dahin bleibt das letzte von heute stehen.
- **Server weg** (unterwegs, Box aus): Der Dienst versucht es mit wachsendem Abstand neu (20 s bis 15 min), die App läuft mit Vorlagen weiter. Im Gespräch kommt eine verständliche Fehlermeldung.
- **Unsaubere Antworten** kleiner Modelle werden abgefangen: Denk-Blöcke (`<think>`), Code-Zäune, Kommas am Ende, fehlende Markierung, abgebrochene Listen. `ai/GameMaster.kt` liest jedes Objekt einzeln und rettet, was heil ist. Bestehende Quests haben im Gespräch feste Kennungen (`q1`, `q2` … nach Position im Spielstand), damit kleine Modelle sie zuverlässig treffen.
- **Datenschutz:** Ohne KI verlässt nichts das Handy. Mit KI gehen Quest-Titel und -Einstellungen, Spielwerte (Stufe, Glut, Serie, Wochenpunkte) und das Gespräch an den eingetragenen Server, sonst nichts. Der API-Schlüssel kommt nicht in Sicherungen. Klartext-HTTP ist erlaubt (`res/xml/network_security_config.xml`), weil Server im Heimnetz meist kein HTTPS haben.

Code: `ai/AiClient.kt` (HTTP, Streaming, Fehlermeldungen), `ai/GameMaster.kt` (Prompts und Auslesen), `ai/AiService.kt` (Hintergrund und Gespräch), `ui/screens/Master.kt` (Gespräch), `ui/screens/AiOptions.kt` (Einstellungen). Tests gegen einen Fake-Server: `AiClientTest`, `GameMasterTest`, `AiServiceTest`. Gegen den echten Server: `LiveAiTest` (siehe `android/README.md`).

**AI-Box einrichten (Ollama):** Ollama so starten, dass es im Heimnetz lauscht (`OLLAMA_HOST=0.0.0.0`), ein Modell laden (`ollama pull …`), in der App die Adresse der Box mit Port 11434 eintragen und „Verbindung testen“.

**AI-Box mit vLLM:** `vllm serve <Modell> --host 0.0.0.0` (Port 8000), in der App „vLLM“ wählen und die Adresse der Box eintragen. Das Modell heißt in der Liste so wie beim Start (oder wie `--served-model-name`). Mit `--api-key` gestartet: denselben Schlüssel in der App eintragen. vLLM lehnt Anfragen ab, deren Antwortlänge nicht mehr in den Kontext passt (`--max-model-len`); die App fragt dann automatisch noch einmal ohne Längenlimit. Gut geeignet sind Modelle mit etwa 3 bis 14 Milliarden Parametern, die ordentlich Deutsch schreiben. Denkende Modelle funktionieren auch, brauchen aber länger.

Später, weiterhin optional:

1. **Modell auf dem Handy:** ein kleines, quantisiertes Modell über MediaPipe LLM Inference oder llama.cpp. Es implementiert dieselbe Schnittstelle (`AiPort`) oder läuft als lokaler Server.
2. **Eigenes Modell auf der AI-Box trainieren:** ein kleines Basismodell per LoRA auf Questlog feintunen (Freitext → Quest-JSON, Wochenwerte → Saga, Stimmen). Trainingsdaten lassen sich aus den Prompts in `GameMaster.kt`, den Vorlagen und geprüften Antworten erzeugen, Export als GGUF.
3. **Eigener Anthropic-Adapter**, falls die OpenAI-kompatible Schnittstelle eines Anbieters nicht reicht.

## Play Store (Checkliste)

1. **App-ID** `io.github.ththemabit.questlog` (nach dem ersten Upload nicht mehr änderbar).
2. **Signatur:** Upload-Schlüssel erzeugen, `keystore.properties` anlegen (siehe `android/README.md`), nie committen.
3. **Bundle bauen:** `gradlew bundleRelease` (Play nimmt AAB).
4. **Datenschutzerklärung:** nötig, weil die App das Mikrofon anfragen kann (Kampfschrei) und Internet nutzt (KI-Spielleiter). Inhalt: Ohne KI verlassen keine Daten das Gerät. Mikrofon nur für die Lautstärke-Messung, nichts wird aufgenommen oder gespeichert. Mit eingeschaltetem KI-Spielleiter gehen Quest-Titel, Quest-Einstellungen, Spielwerte und das Gespräch an den Server, den die Person selbst einträgt (eigener Rechner oder gewählter Cloud-Anbieter); Questlog selbst betreibt keinen Server.
5. **Datensicherheit (Formular):** ohne KI keine Daten erhoben oder geteilt. Den optionalen Versand an den selbst eingetragenen KI-Server im Formular angeben.
6. **Preis:** kostenpflichtig oder kostenlos mit Kauf einer Vollversion; Werbung würde den Belohnungsmoment stören und passt nicht zum Konzept.
7. **Store-Grafiken:** Screenshots liefern die Paparazzi-Tests (`android/app/src/test/snapshots/images`), Icon-Grafik aus `tools/gen_assets.py`.
8. **Übersetzung:** mindestens Englisch für mehr Reichweite.
9. **Vor dem Release** einmal auf echten Geräten prüfen: Benachrichtigungen (Android 13+ fragt), Widget, Tanzpause (Sensor), Kampfschrei (Mikrofon), Backup-Export/Import, KI-Spielleiter mit einem Server im Heimnetz und ohne Netz.
