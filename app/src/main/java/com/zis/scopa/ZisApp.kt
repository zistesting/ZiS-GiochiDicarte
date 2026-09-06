package com.zis.scopa

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * Restituisce al sistema la memoria delle immagini delle carte quando l'app finisce in background
 * o quando Android segnala che la RAM sta finendo. Le bitmap vengono poi ricaricate al bisogno.
 */
class ZisApp : Application() {

    /**
     * ATTENZIONE ai livelli: non sono una scala unica, e soprattutto non arrivano piu' tutti.
     *
     * Da API 34 (Android 14) il sistema NON notifica piu' le app di RUNNING_MODERATE (5),
     * RUNNING_LOW (10), RUNNING_CRITICAL (15), MODERATE (60) e COMPLETE (80): sono deprecati
     * proprio con la nota "Apps are not notified of this level since API level 34". Su un
     * telefono moderno arrivano quindi solo UI_HIDDEN (20) e BACKGROUND (40), cioe' i due casi
     * in cui l'app non e' piu' visibile e la cache si puo' svuotare del tutto.
     *
     * Il ramo dei RUNNING_* resta perche' il minSdk e' 24: su Android 13 e precedenti quelle
     * segnalazioni arrivano ancora, ed e' li' che dimezzare la cache serve davvero. Non serve
     * invece andare a leggere ActivityManager.getMyMemoryState() per rimpiazzarli: su Android
     * 14+ la LruCache si autoregola gia' (e' tarata su 1/8 della heap) e interrogare lo stato
     * della memoria a ogni fotogramma costerebbe piu' di quello che farebbe risparmiare.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> CardView.clearCache()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> CardView.trimCache()
        }
    }

    /**
     * Deprecata e mai piu' chiamata da API 34 in su, ma su Android 13 e precedenti arriva
     * ancora ed e' l'ultimo avviso prima che il processo venga ucciso: la teniamo finche'
     * il minSdk resta 24.
     */
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        CardView.clearCache()
    }
}
