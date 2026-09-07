package com.zis.scopa

/**
 * Tabella delle immagini dei due mazzi.
 *
 * Prima le carte si caricavano per nome con Resources.getIdentifier("card_0_1", ...).
 * Quella funzione e' deprecata dall'API 29, fa un lookup per stringa a ogni disegno e
 * soprattutto rende le immagini invisibili allo shrinker delle risorse: serviva un
 * res/raw/keep.xml apposta per impedirgli di buttarle via.
 *
 * Qui gli id sono riferimenti diretti a R.drawable, quindi:
 *  - il costo a runtime e' un accesso a un array,
 *  - se una carta manca il progetto NON compila, invece di mostrare un rettangolo bianco
 *    a partita iniziata,
 *  - lo shrinker vede le risorse usate da solo, quindi keep.xml non serve piu' ed e' stato
 *    tolto, e minifyEnabled/shrinkResources si possono accendere senza rete di sicurezza.
 *
 * Indice: seme * 10 + (valore - 1). Semi: 0 denari, 1 coppe, 2 spade, 3 bastoni.
 * Tre mazzi: le illustrazioni ZiS, le figure tradizionali e le bergamasche.
 */
object Decks {

    private val zis = intArrayOf(
        R.drawable.card_0_1, R.drawable.card_0_2, R.drawable.card_0_3, R.drawable.card_0_4, R.drawable.card_0_5, R.drawable.card_0_6, R.drawable.card_0_7, R.drawable.card_0_8, R.drawable.card_0_9, R.drawable.card_0_10,
        R.drawable.card_1_1, R.drawable.card_1_2, R.drawable.card_1_3, R.drawable.card_1_4, R.drawable.card_1_5, R.drawable.card_1_6, R.drawable.card_1_7, R.drawable.card_1_8, R.drawable.card_1_9, R.drawable.card_1_10,
        R.drawable.card_2_1, R.drawable.card_2_2, R.drawable.card_2_3, R.drawable.card_2_4, R.drawable.card_2_5, R.drawable.card_2_6, R.drawable.card_2_7, R.drawable.card_2_8, R.drawable.card_2_9, R.drawable.card_2_10,
        R.drawable.card_3_1, R.drawable.card_3_2, R.drawable.card_3_3, R.drawable.card_3_4, R.drawable.card_3_5, R.drawable.card_3_6, R.drawable.card_3_7, R.drawable.card_3_8, R.drawable.card_3_9, R.drawable.card_3_10
    )

    private val trad = intArrayOf(
        R.drawable.trad_0_1, R.drawable.trad_0_2, R.drawable.trad_0_3, R.drawable.trad_0_4, R.drawable.trad_0_5, R.drawable.trad_0_6, R.drawable.trad_0_7, R.drawable.trad_0_8, R.drawable.trad_0_9, R.drawable.trad_0_10,
        R.drawable.trad_1_1, R.drawable.trad_1_2, R.drawable.trad_1_3, R.drawable.trad_1_4, R.drawable.trad_1_5, R.drawable.trad_1_6, R.drawable.trad_1_7, R.drawable.trad_1_8, R.drawable.trad_1_9, R.drawable.trad_1_10,
        R.drawable.trad_2_1, R.drawable.trad_2_2, R.drawable.trad_2_3, R.drawable.trad_2_4, R.drawable.trad_2_5, R.drawable.trad_2_6, R.drawable.trad_2_7, R.drawable.trad_2_8, R.drawable.trad_2_9, R.drawable.trad_2_10,
        R.drawable.trad_3_1, R.drawable.trad_3_2, R.drawable.trad_3_3, R.drawable.trad_3_4, R.drawable.trad_3_5, R.drawable.trad_3_6, R.drawable.trad_3_7, R.drawable.trad_3_8, R.drawable.trad_3_9, R.drawable.trad_3_10
    )

    private val berg = intArrayOf(
        R.drawable.berg_0_1, R.drawable.berg_0_2, R.drawable.berg_0_3, R.drawable.berg_0_4, R.drawable.berg_0_5, R.drawable.berg_0_6, R.drawable.berg_0_7, R.drawable.berg_0_8, R.drawable.berg_0_9, R.drawable.berg_0_10,
        R.drawable.berg_1_1, R.drawable.berg_1_2, R.drawable.berg_1_3, R.drawable.berg_1_4, R.drawable.berg_1_5, R.drawable.berg_1_6, R.drawable.berg_1_7, R.drawable.berg_1_8, R.drawable.berg_1_9, R.drawable.berg_1_10,
        R.drawable.berg_2_1, R.drawable.berg_2_2, R.drawable.berg_2_3, R.drawable.berg_2_4, R.drawable.berg_2_5, R.drawable.berg_2_6, R.drawable.berg_2_7, R.drawable.berg_2_8, R.drawable.berg_2_9, R.drawable.berg_2_10,
        R.drawable.berg_3_1, R.drawable.berg_3_2, R.drawable.berg_3_3, R.drawable.berg_3_4, R.drawable.berg_3_5, R.drawable.berg_3_6, R.drawable.berg_3_7, R.drawable.berg_3_8, R.drawable.berg_3_9, R.drawable.berg_3_10
    )

    private fun table(prefix: String): IntArray = when (prefix) {
        Prefs.DECK_TRAD -> trad
        Prefs.DECK_BERG -> berg
        else -> zis
    }

    /** Id dell'immagine di una carta nel mazzo indicato. */
    fun faceId(prefix: String, card: Card): Int = table(prefix)[card.suit * 10 + (card.value - 1)]

    /** Id del dorso del mazzo indicato. */
    fun backId(prefix: String): Int = when (prefix) {
        Prefs.DECK_TRAD -> R.drawable.trad_back
        Prefs.DECK_BERG -> R.drawable.berg_back
        else -> R.drawable.card_back
    }
}
