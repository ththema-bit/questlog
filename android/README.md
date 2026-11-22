# Questlog – Android-App

Native App in Kotlin mit Jetpack Compose. Kein WebView, keine Werbung, kein Tracking. Die Internet-Berechtigung braucht nur der optionale KI-Spielleiter; er verbindet sich ausschließlich mit dem Server, der unter Optionen eingetragen ist.

## Bauen und ausprobieren

Voraussetzungen: JDK 17 oder neuer und das Android-SDK (Plattform 36). Am einfachsten Android Studio installieren und diesen Ordner (`Questlog/android`) als Projekt öffnen.

- Test-Version bauen: `build.bat` (Windows) oder `./gradlew assembleDebug`. Die Datei liegt danach in `app/build/outputs/apk/debug/app-debug.apk`.
- Aufs Handy: in Android Studio auf „Run“, oder die APK aufs Handy kopieren und öffnen. Die Test-Version hat die App-ID `….questlog.debug` und lässt sich neben der Play-Store-Version installieren.

## Tests

- `./gradlew testDebugUnitTest` – Engine-Tests (Punkte, Fenster, Glut, Serien, Pause, Wetten, Bosse, Schnelleingabe, Erinnerungen), KI-Tests gegen einen Fake-Server (Client, Streaming, Abbruch, Auslesen unsauberer Antworten, Hintergrund-Dienst von Anfang bis Ende) und Screenshot-Tests.
- KI gegen den echten Server prüfen (sonst übersprungen):

  ```bash
  QUESTLOG_AI_URL=192.168.178.65:11434 QUESTLOG_AI_MODEL=qwen2.5:7b ./gradlew :app:testDebugUnitTest --tests '*LiveAiTest'
  ```

  Unter Windows:

  ```bat
  set QUESTLOG_AI_URL=192.168.178.65:11434
  set QUESTLOG_AI_MODEL=qwen2.5:7b
  gradlew :app:testDebugUnitTest --tests "*LiveAiTest"
  ```

  Adresse und Modell an die eigene Box anpassen, für Cloud-Dienste zusätzlich `QUESTLOG_AI_KEY`. Was das Modell geantwortet hat und was die App daraus liest, steht im Testbericht `app/build/reports/tests/testDebugUnitTest/index.html`.
- `./gradlew recordPaparazziDebug` – Screenshots aller wichtigen Screens neu erzeugen (Paparazzi, ohne Gerät). Sie liegen in `app/src/test/snapshots/images` und dienen auch als Store-Screenshots.
- `./gradlew verifyPaparazziDebug` – prüft, ob sich die Optik ungewollt verändert hat.

## Aufbau

| Ordner (`app/src/main/java/io/github/ththemabit/questlog/`) | Inhalt |
|---|---|
| `core/` | Reines Kotlin ohne Android: Datenmodell, Regeln, Engine (rechnet alles aus dem Ereignis-Protokoll nach), Meilensteine, Vorlagen, Feiertage, Erzähler und Schnelleingabe |
| `ai/` | Optionaler KI-Spielleiter: Client für OpenAI-kompatible Server, Prompts und Auslesen der Antworten, Hintergrund-Dienst und Gespräch |
| `data/` | Speichern (JSON, atomar, mit Sicherungskopie), Aktionen, Demo-Spielstand |
| `ui/pixel/` | Pixel-Baukasten: Rahmen, Pergament, Schriftrollen, Knöpfe, Balken, Icons |
| `ui/theme/` | Farben und Schriften |
| `ui/screens/`, `ui/components/` | Screens und Bausteine |
| `notify/` | Erinnerungen (WorkManager) und Aktionen aus der Benachrichtigung |
| `widget/` | Homescreen-Widget (Glance) |

`tools/gen_assets.py` erzeugt App-Icon, Benachrichtigungs-Icon und Widget-Bilder aus den Pixel-Icons in `ui/pixel/IconArt.kt` (Python mit Pillow).

Der Spielstand liegt in `files/questlog.json` im App-Speicher. Backup und Einspielen gehen über Optionen → Sicherung.

## Versionen

Alle Bibliotheken bauen gegen Android-SDK 36 wie die anderen Projekte im Repo. Neuere Compose- und Core-Versionen verlangen bereits SDK 37; beim Umstieg `compileSdk` und die Versionen in `app/build.gradle.kts` gemeinsam anheben.

## Vor der Veröffentlichung

1. **Signatur-Schlüssel erzeugen** (einmalig, gut aufbewahren, niemals committen):

   ```bat
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   Danach `keystore.properties` in diesem Ordner anlegen:

   ```properties
   storeFile=upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

2. `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen.
3. `./gradlew bundleRelease` bauen und `app/build/outputs/bundle/release/app-release.aab` in der Play Console hochladen.
4. Den Rest der Checkliste (Datenschutzerklärung, Datensicherheit, Grafiken) beschreibt `../DESIGN.md`.
