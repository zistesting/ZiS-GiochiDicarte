package com.zis.scopa

import android.content.res.Resources

/**
 * Dimensione delle carte a schermo, uguale per Scopa e Briscola.
 *
 * La misura dipende sia dalla larghezza sia dall'altezza dello schermo. Calcolandola solo
 * sulla larghezza, su un tablet in orizzontale una carta diventerebbe cosi' alta che le due
 * mani non ci starebbero piu': serve da quando, con targetSdk 36, i dispositivi grandi
 * ignorano il blocco in verticale e l'app puo' ritrovarsi in orizzontale o in una finestra.
 */
object CardSize {

    /** Proporzione delle immagini: 1024/560 = 1.829 (le attuali 320x585 danno 1.828). */
    const val RATIO = 1.829f

    private fun widthDp(res: Resources): Float {
        val dm = res.displayMetrics
        val wDp = dm.widthPixels / dm.density
        val hDp = dm.heightPixels / dm.density
        val byWidth = wDp * 0.21f            // tre carte affiancate in tavola, piu' i margini
        val byHeight = hDp * 0.22f / RATIO   // le due mani piu' il tavolo in mezzo
        return minOf(byWidth, byHeight).coerceIn(64f, 150f)
    }

    fun width(res: Resources): Int = (widthDp(res) * res.displayMetrics.density).toInt()

    fun height(res: Resources): Int = (widthDp(res) * RATIO * res.displayMetrics.density).toInt()
}
