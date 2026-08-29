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

    val valueLabel: String
        get() = when (value) {
            1 -> "A"
            8 -> "F"
            9 -> "C"
            10 -> "R"
            else -> value.toString()
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
