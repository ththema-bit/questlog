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
}
