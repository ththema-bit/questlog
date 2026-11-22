package io.github.ththemabit.questlog.core

import kotlinx.serialization.json.Json

/** JSON-Format der Speicherdatei und des Backups. Unbekannte Felder werden ignoriert (vorwärtskompatibel). */
object SaveCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "t"
        prettyPrint = false
    }

    private val pretty = Json(json) { prettyPrint = true }

    fun encode(save: SaveData): String = json.encodeToString(SaveData.serializer(), save)
    fun encodePretty(save: SaveData): String = pretty.encodeToString(SaveData.serializer(), save)
    fun decode(text: String): SaveData = json.decodeFromString(SaveData.serializer(), text)

    /** Für Sicherungen: ein API-Schlüssel gehört nicht in eine Datei, die herumgereicht wird. */
    fun forExport(save: SaveData): SaveData = save.copy(settings = save.settings.copy(ai = save.settings.ai.copy(apiKey = "")))

    /** Beim Einspielen: Hat die Sicherung keinen Schlüssel, bleibt der eigene. */
    fun keepKey(imported: SaveData, current: SaveData): SaveData {
        val key = imported.settings.ai.apiKey.ifBlank { current.settings.ai.apiKey }
        return imported.copy(settings = imported.settings.copy(ai = imported.settings.ai.copy(apiKey = key)))
    }
}
