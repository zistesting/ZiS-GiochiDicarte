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

    /** Proporzione delle immagini: 819/448 = 1.828. */
    const val RATIO = 1.829f

    /**
     * Misura utile in dp.
     *
     * Si legge dalla Configuration e non da displayMetrics: screenWidthDp e screenHeightDp
     * danno l'area che l'app puo' davvero usare, gia' al netto delle barre di sistema, e in
     * multi-finestra o in finestra ridimensionata seguono la finestra. displayMetrics invece
     * riporta l'intero schermo: le carte venivano calcolate su qualche decina di dp che a
     * schermo non esistono, e in finestra piccola risultavano troppo grandi.
     *
     * Il ripiego su displayMetrics resta per il caso limite in cui la Configuration non sia
     * ancora valorizzata (vale 0 finche' l'activity non e' agganciata).
     */
    private fun widthDp(res: Resources): Float {
        val cfg = res.configuration
        val dm = res.displayMetrics
        val wDp = if (cfg.screenWidthDp > 0) cfg.screenWidthDp.toFloat() else dm.widthPixels / dm.density
        val hDp = if (cfg.screenHeightDp > 0) cfg.screenHeightDp.toFloat() else dm.heightPixels / dm.density
        val byWidth = wDp * 0.21f            // tre carte affiancate in tavola, piu' i margini
        val byHeight = hDp * 0.22f / RATIO   // le due mani piu' il tavolo in mezzo
        return minOf(byWidth, byHeight).coerceIn(64f, 150f)
    }

    fun width(res: Resources): Int = (widthDp(res) * res.displayMetrics.density).toInt()

    fun height(res: Resources): Int = (widthDp(res) * RATIO * res.displayMetrics.density).toInt()
}
