package com.zis.scopa

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * Restituisce al sistema la memoria delle immagini delle carte quando l'app finisce in background
 * o quando Android segnala che la RAM sta finendo. Le bitmap vengono poi ricaricate al bisogno.
 */
class ZisApp : Application() {

    /**
     * I livelli di onTrimMemory non stanno su un'unica scala di gravita', quindi non si
     * possono trattare con una soglia sola. I due gruppi sono separati:
     *
     *  - RUNNING_MODERATE 5, RUNNING_LOW 10, RUNNING_CRITICAL 15: l'app e' ancora in primo
     *    piano e la RAM sta finendo. Sono i valori piu' bassi, quindi la vecchia condizione
     *    "level >= UI_HIDDEN (20)" non li prendeva mai, proprio nei casi in cui liberare
     *    memoria serve di piu'. Qui la cache si dimezza: le carte a schermo si ridecodificano
     *    al bisogno, le altre lasciano posto.
     *  - UI_HIDDEN 20 e oltre (BACKGROUND 40, MODERATE 60, COMPLETE 80): l'app non e' piu'
     *    visibile, le carte non servono a nessuno e la cache si svuota del tutto.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> CardView.clearCache()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> CardView.trimCache()
        }
    }

    @Deprecated("Deprecato in API 34, serve ancora per i dispositivi meno recenti")
    override fun onLowMemory() {
        super.onLowMemory()
        CardView.clearCache()
    }
}
