package com.zis.scopa

// Italian 40-card deck. Suits: 0 Denari, 1 Coppe, 2 Spade, 3 Bastoni. Values 1..10 (8 Fante, 9 Cavallo, 10 Re).
data class Card(val suit: Int, val value: Int) {

    val isDenari: Boolean get() = suit == 0
    val isSettebello: Boolean get() = suit == 0 && value == 7

    // Prime value used for the "primiera" point.
    val prime: Int
        get() = when (value) {
            7 -> 21
            6 -> 18
            1 -> 16
            5 -> 15
            4 -> 14
            3 -> 13
            2 -> 12
            else -> 10 // 8 Fante, 9 Cavallo, 10 Re
        }

    val suitLabel: String
        get() = when (suit) {
            0 -> "denari"
            1 -> "coppe"
            2 -> "spade"
            else -> "bastoni"
        }

    val italianName: String
        get() {
            val v = when (value) {
                1 -> "Asso"
                8 -> "Fante"
                9 -> "Cavallo"
                10 -> "Re"
                else -> value.toString()
            }
            return "$v di $suitLabel"
        }
}

fun fullDeck(): MutableList<Card> {
    val d = mutableListOf<Card>()
    for (s in 0..3) for (v in 1..10) d.add(Card(s, v))
    return d
}

/**
 * Unico generatore casuale dell'app, usato sia da Scopa sia da Briscola.
 *
 * Perche' SecureRandom e non il Random normale: java.util.Random tiene uno stato interno
 * di 48 bit, cioe' 281.000 miliardi di partenze possibili. Sembrano tante, ma le mescolate
 * diverse di 40 carte sono 40 fattoriale, un numero con 48 cifre: con Random la stragrande
 * maggioranza delle disposizioni non uscirebbe mai, nemmeno per sbaglio. SecureRandom invece
 * pesca entropia dal sistema operativo a ogni chiamata, senza uno stato limitato, quindi ogni
 * disposizione delle 40 carte e' realmente raggiungibile.
 */
private val rng: java.util.Random = java.security.SecureRandom()

/**
 * Mazzo completo gia' mescolato. Collections.shuffle e' un Fisher-Yates: ogni carta ha la
 * stessa probabilita' di finire in ogni posizione, senza le distorsioni dei metodi "a caso"
 * scritti a mano (per esempio scambiare due carte a caso N volte, che non e' uniforme).
 */
fun shuffledDeck(): MutableList<Card> {
    val d = fullDeck()
    java.util.Collections.shuffle(d, rng)
    return d
}
