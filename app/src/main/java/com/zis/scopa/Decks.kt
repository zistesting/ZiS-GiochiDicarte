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
 * Cinque mazzi: le illustrazioni ZiS, le piacentine, le bergamasche, le napoletane e il
 * mazzo francese, che e' l'unico da 52 carte e serve solo al Klondike.
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

    private val nap = intArrayOf(
        R.drawable.nap_0_1, R.drawable.nap_0_2, R.drawable.nap_0_3, R.drawable.nap_0_4, R.drawable.nap_0_5, R.drawable.nap_0_6, R.drawable.nap_0_7, R.drawable.nap_0_8, R.drawable.nap_0_9, R.drawable.nap_0_10,
        R.drawable.nap_1_1, R.drawable.nap_1_2, R.drawable.nap_1_3, R.drawable.nap_1_4, R.drawable.nap_1_5, R.drawable.nap_1_6, R.drawable.nap_1_7, R.drawable.nap_1_8, R.drawable.nap_1_9, R.drawable.nap_1_10,
        R.drawable.nap_2_1, R.drawable.nap_2_2, R.drawable.nap_2_3, R.drawable.nap_2_4, R.drawable.nap_2_5, R.drawable.nap_2_6, R.drawable.nap_2_7, R.drawable.nap_2_8, R.drawable.nap_2_9, R.drawable.nap_2_10,
        R.drawable.nap_3_1, R.drawable.nap_3_2, R.drawable.nap_3_3, R.drawable.nap_3_4, R.drawable.nap_3_5, R.drawable.nap_3_6, R.drawable.nap_3_7, R.drawable.nap_3_8, R.drawable.nap_3_9, R.drawable.nap_3_10
    )

    /**
     * Il mazzo francese: 52 carte, tredici valori per seme invece di dieci.
     *
     * Sta in una tabella a parte e non nella stessa delle altre perche' cambia l'indice:
     * qui e' seme * 13 + (valore - 1). Mescolarli in un'unica tabella significherebbe
     * lasciare tre buchi per seme nei mazzi italiani, cioe' dodici caselle vuote in cui
     * prima o poi qualcuno finirebbe per sbaglio.
     *
     * Semi: 0 cuori, 1 quadri, 2 fiori, 3 picche. Valori 1..13 (11 Fante, 12 Donna, 13 Re).
     */
    private val fr = intArrayOf(
        R.drawable.fr_0_1, R.drawable.fr_0_2, R.drawable.fr_0_3, R.drawable.fr_0_4, R.drawable.fr_0_5, R.drawable.fr_0_6, R.drawable.fr_0_7, R.drawable.fr_0_8, R.drawable.fr_0_9, R.drawable.fr_0_10, R.drawable.fr_0_11, R.drawable.fr_0_12, R.drawable.fr_0_13,
        R.drawable.fr_1_1, R.drawable.fr_1_2, R.drawable.fr_1_3, R.drawable.fr_1_4, R.drawable.fr_1_5, R.drawable.fr_1_6, R.drawable.fr_1_7, R.drawable.fr_1_8, R.drawable.fr_1_9, R.drawable.fr_1_10, R.drawable.fr_1_11, R.drawable.fr_1_12, R.drawable.fr_1_13,
        R.drawable.fr_2_1, R.drawable.fr_2_2, R.drawable.fr_2_3, R.drawable.fr_2_4, R.drawable.fr_2_5, R.drawable.fr_2_6, R.drawable.fr_2_7, R.drawable.fr_2_8, R.drawable.fr_2_9, R.drawable.fr_2_10, R.drawable.fr_2_11, R.drawable.fr_2_12, R.drawable.fr_2_13,
        R.drawable.fr_3_1, R.drawable.fr_3_2, R.drawable.fr_3_3, R.drawable.fr_3_4, R.drawable.fr_3_5, R.drawable.fr_3_6, R.drawable.fr_3_7, R.drawable.fr_3_8, R.drawable.fr_3_9, R.drawable.fr_3_10, R.drawable.fr_3_11, R.drawable.fr_3_12, R.drawable.fr_3_13
    )

    private fun table(prefix: String): IntArray = when (prefix) {
        Prefs.DECK_TRAD -> trad
        Prefs.DECK_BERG -> berg
        Prefs.DECK_NAP -> nap
        Prefs.DECK_FR -> fr
        else -> zis
    }

    /**
     * Id dell'immagine di una carta nel mazzo indicato.
     *
     * A decidere la tabella non e' solo il prefisso: **e' francese anche una carta con
     * valore sopra il 10**, qualunque cosa dica il prefisso. Il mazzo in uso e' uno stato
     * statico condiviso da tutte le schermate (CardView.setDeck), e se una vista del
     * Klondike venisse disegnata un istante dopo che un altro gioco ha rimesso un mazzo
     * italiano, l'indice 3*10+12 cadrebbe fuori da una tabella di quaranta elementi e
     * l'app si chiuderebbe con ArrayIndexOutOfBounds. Oggi non capita, perche' dal
     * Klondike non si passa a un altro gioco senza distruggere l'activity; ma dipende da
     * un ordine di chiamate lontano da qui, e la protezione costa un confronto.
     */
    fun faceId(prefix: String, card: Card): Int =
        if (prefix == Prefs.DECK_FR || card.value > 10) fr[card.suit * 13 + (card.value - 1)]
        else table(prefix)[card.suit * 10 + (card.value - 1)]

    /** Id del dorso del mazzo indicato. */
    fun backId(prefix: String): Int = when (prefix) {
        Prefs.DECK_FR -> R.drawable.fr_back
        Prefs.DECK_TRAD -> R.drawable.trad_back
        Prefs.DECK_BERG -> R.drawable.berg_back
        Prefs.DECK_NAP -> R.drawable.nap_back
        else -> R.drawable.card_back
    }
}
