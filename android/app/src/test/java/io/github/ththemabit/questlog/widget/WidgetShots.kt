package io.github.ththemabit.questlog.widget

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.ththemabit.questlog.ai.Briefing
import io.github.ththemabit.questlog.ai.GameMaster
import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.data.Demo
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die Schriftrollen-Widgets ohne Gerät. Mit QUESTLOG_WRITE_PREVIEWS=1 schreibt der Test außerdem
 * die Vorschaubilder für die Widget-Auswahl nach res/drawable-nodpi.
 */
class WidgetShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000), theme = "android:Theme.Material.NoActionBar")

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()

    private fun state(save: SaveData) = AppState(save, Engine(zone).compute(save, now))

    private fun preview(b: Bitmap, name: String) {
        if (System.getenv("QUESTLOG_WRITE_PREVIEWS").isNullOrBlank()) return
        File("src/main/res/drawable-nodpi/$name").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun scrolls() {
        val r = WidgetRender(paparazzi.context)
        val demo = state(Demo.save(today, zone))
        val big = r.questScroll(demo, r.px(340.dp), r.px(236.dp))
        val small = r.questScroll(demo, r.px(300.dp), r.px(120.dp))
        val glut = r.glutScroll(demo, r.px(170.dp), r.px(170.dp))
        // Glutrolle mit Tagesbriefing vom KI-Spielleiter
        val base = Demo.save(today, zone)
        val brief = Briefing(GameMaster.briefingTag(demo.game, Persona.CHRONIST, 18), today.toString(), "Guten Abend. Das Bad wartet seit drei Tagen, und für Lea fehlt noch ein Geschenk.")
        val withAi = state(base.copy(
            settings = base.settings.copy(ai = AiConfig(enabled = true, baseUrl = "box:11434", model = "m")),
            storyCache = mapOf(GameMaster.BRIEF_KEY to GameMaster.encodeBriefing(brief)),
        ))
        val glutAi = r.glutScroll(withAi, r.px(250.dp), r.px(170.dp))
        // Alles erledigt: nur Quests, die heute schon dran waren
        val done = state(Demo.save(today, zone).let { s -> s.copy(quests = s.quests.filter { it.id == "dehnen" || it.id == "vokabeln" }) })
        val empty = r.questScroll(done, r.px(300.dp), r.px(150.dp))
        preview(big.bitmap, "widget_preview_quests.png")
        preview(glut.bitmap, "widget_preview_glut.png")
        paparazzi.snapshot {
            Column(Modifier.fillMaxSize().background(Color(0xFF2E4A62)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (b in listOf(big.bitmap, small.bitmap, glut.bitmap, glutAi.bitmap, empty.bitmap)) {
                    Image(b.asImageBitmap(), null, filterQuality = FilterQuality.None)
                }
            }
        }
    }
}
