package com.updownupdown.cards.watch.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Redraws the tile and complication once the app has been updated. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) refreshSurfaces(context)
    }
}
