package com.zis.scopa

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * Restituisce al sistema la memoria delle immagini delle carte quando l'app finisce in background
 * o quando Android segnala che la RAM sta finendo. Le bitmap vengono poi ricaricate al bisogno.
 */
class ZisApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            CardView.clearCache()
        }
    }

    @Deprecated("Deprecato in API 34, serve ancora per i dispositivi meno recenti")
    override fun onLowMemory() {
        super.onLowMemory()
        CardView.clearCache()
    }
}
