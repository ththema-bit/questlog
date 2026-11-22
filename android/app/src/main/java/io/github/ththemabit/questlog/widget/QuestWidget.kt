package io.github.ththemabit.questlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import io.github.ththemabit.questlog.MainActivity
import io.github.ththemabit.questlog.QuestlogApp
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.data.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull

/*
 * Homescreen-Widgets als Schriftrollen. Das Bild zeichnet WidgetRender, hier liegen nur
 * unsichtbare Tippflächen darüber. Der Spielstand wird während der Sitzung beobachtet, damit
 * ein Tipp aufs Siegel sofort sichtbar wird.
 */

/** Spielstand-Änderungen, ohne das Weiterticken der Uhr (das würde die Bilder ständig neu zeichnen). */
private fun changes(store: Store): Flow<AppState> =
    store.state.filterNotNull().distinctUntilChangedBy { it.save to it.game.today }

/** Questrolle (F04): die dringendsten Quests, abhaken mit dem Wachssiegel. */
class QuestWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = QuestlogApp.store(context)
        store.refresh()
        val first = store.awaitState()
        val render = WidgetRender(context)
        provideContent {
            val state by remember { changes(store) }.collectAsState(first)
            val size = LocalSize.current
            QuestScrollContent(render, render.questScroll(state, render.px(size.width), render.px(size.height)))
        }
    }

    companion object {
        /** Beide Widgets neu zeichnen. */
        suspend fun refresh(context: Context) {
            runCatching { QuestWidget().updateAll(context) }
            runCatching { GlutWidget().updateAll(context) }
        }
    }
}

@Composable
private fun QuestScrollContent(r: WidgetRender, s: WidgetRender.QuestScroll) {
    val openApp = actionStartActivity<MainActivity>()
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(s.bitmap), ContentScale.FillBounds)) {
        Column(GlanceModifier.fillMaxSize()) {
            Box(GlanceModifier.fillMaxWidth().height(r.dpOf(s.headerHeight)).clickable(openApp).semantics { contentDescription = s.summary }) {}
            for (row in s.rows) {
                Row(GlanceModifier.fillMaxWidth().height(r.dpOf(s.rowHeight))) {
                    Box(
                        GlanceModifier.defaultWeight().fillMaxHeight()
                            .clickable(actionStartActivity<MainActivity>(actionParametersOf(questKey to row.id)))
                            .semantics { contentDescription = row.label },
                    ) {}
                    Box(
                        GlanceModifier.width(r.dpOf(s.sealZone)).fillMaxHeight()
                            .clickable(actionRunCallback<CompleteAction>(actionParametersOf(questKey to row.id)))
                            .semantics { contentDescription = "${row.title} erledigen" },
                    ) {}
                }
            }
            Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(openApp)) {}
        }
    }
}

/** Glutrolle: Glut, Serie, Stufe, Wochenziel und ein Wort vom Spielleiter oder Erzähler. */
class GlutWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = QuestlogApp.store(context)
        store.refresh()
        val first = store.awaitState()
        val render = WidgetRender(context)
        provideContent {
            val state by remember { changes(store) }.collectAsState(first)
            val size = LocalSize.current
            val s = render.glutScroll(state, render.px(size.width), render.px(size.height))
            Box(
                GlanceModifier.fillMaxSize().background(ImageProvider(s.bitmap), ContentScale.FillBounds)
                    .clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = s.summary },
            ) {}
        }
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

class GlutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlutWidget()
}
