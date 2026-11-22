package io.github.ththemabit.questlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ththemabit.questlog.MainActivity
import io.github.ththemabit.questlog.QuestlogApp
import io.github.ththemabit.questlog.R
import io.github.ththemabit.questlog.core.QuestState
import io.github.ththemabit.questlog.core.WindowStatus
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Fmt

/** Homescreen-Widget (F04): die dringendsten Quests, Abhaken mit einem Tipp. */
class QuestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = QuestlogApp.store(context)
        store.refresh()
        val state = store.awaitState()
        provideContent { Content(state) }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { QuestWidget().updateAll(context) }
        }
    }
}

private val stone = Color(0xFF221A13)
private val parchment = Color(0xFFE6CF98)
private val ink = Color(0xFF2A1709)
private val inkSoft = Color(0xFF5B3A1E)
private val gold = Color(0xFFFFE28F)
private val ember = Color(0xFFFFC23D)
private val red = Color(0xFFA62B1C)
private val green = Color(0xFF2F5A22)

@Composable
private fun Content(state: AppState) {
    val game = state.game
    val list = (game.board.overdue + game.board.open).take(5)
    Column(GlanceModifier.fillMaxSize().background(stone).padding(10.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.px_flame), "Glut", GlanceModifier.size(20.dp))
            Spacer(GlanceModifier.width(4.dp))
            Text(Fmt.n(game.player.glut), style = TextStyle(color = ColorProvider(ember), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(10.dp))
            Image(ImageProvider(R.drawable.px_shield), "Serie", GlanceModifier.size(18.dp))
            Spacer(GlanceModifier.width(4.dp))
            Text("${game.player.streak}", style = TextStyle(color = ColorProvider(gold), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("QUESTLOG", style = TextStyle(color = ColorProvider(gold), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (list.isEmpty()) {
            Text("Alles erledigt. Die Glut brennt.", style = TextStyle(color = ColorProvider(gold), fontSize = 13.sp))
        } else {
            LazyColumn {
                items(list, itemId = { it.quest.id.hashCode().toLong() }) { qs -> QuestRow(qs) }
            }
        }
    }
}

@Composable
private fun QuestRow(qs: QuestState) {
    Column {
        Row(
            GlanceModifier.fillMaxWidth().background(parchment).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>(actionParametersOf(questKey to qs.quest.id)))) {
                Text(qs.quest.title, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(Fmt.window(qs), style = TextStyle(color = ColorProvider(if (qs.status == WindowStatus.OVERDUE) red else green), fontSize = 11.sp), maxLines = 1)
            }
            Text("+${qs.preview}", style = TextStyle(color = ColorProvider(inkSoft), fontSize = 12.sp))
            Spacer(GlanceModifier.width(6.dp))
            Image(
                ImageProvider(R.drawable.px_seal), "${qs.quest.title} erledigen",
                GlanceModifier.size(34.dp).clickable(actionRunCallback<CompleteAction>(actionParametersOf(questKey to qs.quest.id))),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
    }
}

val questKey = ActionParameters.Key<String>(MainActivity.EXTRA_QUEST)

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[questKey] ?: return
        val store = QuestlogApp.store(context)
        store.awaitState()
        store.complete(id)
        store.flush()
        QuestWidget.refresh(context)
    }
}

class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}
