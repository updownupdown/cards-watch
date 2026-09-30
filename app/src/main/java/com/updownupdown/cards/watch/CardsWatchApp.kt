package com.updownupdown.cards.watch

import android.app.Application
import com.updownupdown.cards.watch.data.SaveStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class CardsWatchApp : Application() {
    val saves: SaveStore by lazy { SaveStore(this, CoroutineScope(SupervisorJob())) }
}
