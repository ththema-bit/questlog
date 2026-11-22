package io.github.ththemabit.questlog

import android.app.Application
import android.content.Context
import io.github.ththemabit.questlog.data.Store
import io.github.ththemabit.questlog.notify.Reminders
import io.github.ththemabit.questlog.widget.QuestWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class QuestlogApp : Application() {
    val store: Store by lazy { Store(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Reminders.setup(this)
        // Widget nachziehen, wenn sich der Spielstand ändert (gebündelt, höchstens alle 2 s)
        scope.launch {
            store.state.filterNotNull().distinctUntilChangedBy { it.save }.drop(1).debounce(2000).collect {
                QuestWidget.refresh(this@QuestlogApp)
            }
        }
    }

    companion object {
        fun store(context: Context): Store = (context.applicationContext as QuestlogApp).store
    }
}
