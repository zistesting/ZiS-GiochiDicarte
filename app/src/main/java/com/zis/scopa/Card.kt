package com.zis.scopa

import android.content.res.Resources

/**
 * Una carta, in tutti e due i mazzi che l'app conosce.
 *
 * Mazzo italiano da 40: semi 0 denari, 1 coppe, 2 spade, 3 bastoni; valori 1..10, dove 8 e'
 * il Fante, 9 il Cavallo, 10 il Re.
 *
 * Mazzo francese da 52: semi 0 cuori, 1 quadri, 2 fiori, 3 picche; valori 1..13, dove 11 e'
 * il Fante, 12 la Donna, 13 il Re.
 *
 * I due mazzi condividono la stessa classe invece di averne una per ciascuno. Il motivo e'
 * che tutto quello che ci gira intorno - il salvataggio, la tabella delle immagini, CardView -
 * lavora su seme e valore e non ha bisogno di sapere altro. A distinguerli e' il gioco che
 * la usa: la Scopa non vedra' mai un valore 13, il Klondike non vedra' mai un mazzo da 40.
 */
data class Card(val suit: Int, val value: Int) {

    val isDenari: Boolean get() = suit == 0
    val isSettebello: Boolean get() = suit == 0 && value == 7

    /**
     * Rosso o nero, e vale solo per il mazzo francese: cuori e quadri sono rossi, fiori e
     * picche neri. Serve al Klondike, dove le colonne si costruiscono a colori alternati.
     */
    val isRed: Boolean get() = suit <= 1

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

    /**
     * Il nome della carta letto da TalkBack, nella lingua dell'app.
     *
     * Prima erano quattro proprieta' di questa classe - suitLabel, frenchSuitLabel,
     * italianName, frenchName - con i semi e le figure scritti in italiano dentro il
     * codice. Era l'unica parte dell'app che non si traduceva cambiando lingua, e non si
     * poteva tradurre: Card non ha un Context, quindi non puo' leggere le risorse.
     *
     * Da qui il parametro: chi chiama ha un Context - CardView e GameActivity - e passa le
     * sue Resources. Il valore e il seme vengono dagli array, la congiunzione da
     * cd_card_name, che in inglese diventa "of": e' esattamente la cosa che concatenando
     * "$valore di $seme" non si poteva ottenere.
     */
    fun nome(res: Resources, french: Boolean): String {
        val semi = res.getStringArray(if (french) R.array.semi_francesi else R.array.semi_italiani)
        val valori = res.getStringArray(if (french) R.array.valori_francesi else R.array.valori_italiani)
        return res.getString(R.string.cd_card_name, valori[value - 1], semi[suit])
    }
}

fun fullDeck(): MutableList<Card> {
    val d = mutableListOf<Card>()
    for (s in 0..3) for (v in 1..10) d.add(Card(s, v))
    return d
}

fun fullFrenchDeck(): MutableList<Card> {
    val d = mutableListOf<Card>()
    for (s in 0..3) for (v in 1..13) d.add(Card(s, v))
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

/** Le 52 carte francesi mescolate, con lo stesso generatore del mazzo italiano. */
fun shuffledFrenchDeck(): MutableList<Card> {
    val d = fullFrenchDeck()
    java.util.Collections.shuffle(d, rng)
    return d
}
