package io.github.ththemabit.questlog

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.QuestlogRoot
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.StoreActions

class MainActivity : ComponentActivity() {
    private val openQuest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        openQuest.value = intent?.getStringExtra(EXTRA_QUEST)
        val store = QuestlogApp.store(this)
        val act = StoreActions(store)
        setContent {
            val state by store.state.collectAsStateWithLifecycle()
            val nav = remember { Nav() }
            val target = openQuest.value
            LaunchedEffect(target, state != null) {
                if (target != null && state != null) {
                    nav.go(Route.Detail(target))
                    openQuest.value = null
                }
            }
            QuestlogRoot(state, act, nav)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_QUEST)?.let { openQuest.value = it }
    }

    override fun onResume() {
        super.onResume()
        QuestlogApp.store(this).refresh()
    }

    companion object {
        const val EXTRA_QUEST = "questId"
    }
}
