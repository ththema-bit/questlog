plugins {
    id("com.android.application") version "9.5.1" apply false
    // AGP 9 bringt Kotlin schon mit. Diese Zeile hebt es auf eine aktuelle Version,
    // damit Compose-Compiler, Serialisierung und kotlinx-Bibliotheken zusammenpassen.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    // Screenshot-Tests ohne Gerät: ./gradlew recordPaparazziDebug
    id("app.cash.paparazzi") version "2.0.0-alpha05.1" apply false
}
