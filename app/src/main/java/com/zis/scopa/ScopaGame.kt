package com.zis.scopa

/**
 * Scopa engine for 2 players: 0 = you, 1 = bot ("Banco").
 * Rules: deal 3 to each + 4 on the table; on your turn play one card; if a single table card has the
 * same value you must take it (single takes priority), otherwise you may take a combination summing to
 * the card value; clearing the table is a "scopa" (except on the very last card of the game). Hands are
 * refilled 3 at a time when both are empty; at the end the remaining table cards go to the last player
 * who captured. Scoring: carte, denari, settebello, primiera, plus each scopa.
 */
class ScopaGame {

    /**
     * SecureRandom invece del Random condiviso di Collections.shuffle(): stessa qualita'
     * statistica ma seme preso dall'entropia di sistema, quindi nessun dubbio sul fatto
     * che due partite avviate a distanza ravvicinata partano da stati simili.
     */
    private val rng = java.security.SecureRandom()

    val deck = ArrayDeque<Card>()
    val table = mutableListOf<Card>()
    val hands = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val captured = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val scope = intArrayOf(0, 0)
    var lastCapturer = -1
    var turn = 0
    var finished = false
    var lastDealt = false

    fun newGame(youStart: Boolean = true) {
        captured[0].clear(); captured[1].clear()
        scope[0] = 0; scope[1] = 0
        lastCapturer = -1
        finished = false

        // Rimescola se esce una mano tutta dello stesso valore (capita nello 0,4% dei casi
        // ma e' fastidiosa perche' toglie ogni scelta) o se in tavola finiscono tre o piu' re,
        // situazione che la regola tradizionale gia' prevede di rifare.
        var tries = 0
        do {
            deck.clear(); table.clear()
            hands[0].clear(); hands[1].clear()
            val d = fullDeck()
            java.util.Collections.shuffle(d, rng)
            deck.addAll(d)
            repeat(3) { hands[0].add(deck.removeFirst()); hands[1].add(deck.removeFirst()) }
            repeat(4) { table.add(deck.removeFirst()) }
            tries++
        } while (tries < 20 && (allSameValue(hands[0]) || allSameValue(hands[1]) || tooManyKings(table)))

        turn = if (youStart) 0 else 1
    }

    private fun allSameValue(h: List<Card>): Boolean =
        h.size >= 3 && h.distinctBy { it.value }.size == 1

    private fun tooManyKings(t: List<Card>): Boolean = t.count { it.value == 10 } >= 3

    /** All capture options for a card of the given value. Single-card matches take priority; if none,
     *  every combination (size >= 2) of table cards summing to the value. */
    fun capturesFor(value: Int): List<List<Card>> {
        val singles = table.filter { it.value == value }
        if (singles.isNotEmpty()) return singles.map { listOf(it) }
        val res = mutableListOf<List<Card>>()
        val n = table.size
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) < 2) continue
            var sum = 0
            val sub = ArrayList<Card>()
            for (i in 0 until n) if ((mask shr i) and 1 == 1) { sum += table[i].value; sub.add(table[i]) }
            if (sum == value) res.add(sub)
        }
        return res
    }

    /** The best capture for a card (used to auto-resolve the player's move and the bot's move). */
    fun bestCaptureFor(card: Card): List<Card> {
        val caps = capturesFor(card.value)
        if (caps.isEmpty()) return emptyList()
        return caps.maxByOrNull { evalCapture(card, it) } ?: caps.first()
    }

    /** Play a card for the current player. Returns true if it was a scopa. */
    fun play(card: Card, capture: List<Card>): Boolean {
        val p = turn
        lastDealt = false
        hands[p].remove(card)
        var scopa = false
        if (capture.isNotEmpty()) {
            for (c in capture) table.remove(c)
            captured[p].add(card)
            captured[p].addAll(capture)
            lastCapturer = p
            if (table.isEmpty() && !isLastCardOfGame()) {
                scope[p]++
                scopa = true
            }
        } else {
            table.add(card)
        }
        if (hands[0].isEmpty() && hands[1].isEmpty() && deck.isNotEmpty()) {
            repeat(3) {
                if (deck.isNotEmpty()) hands[0].add(deck.removeFirst())
                if (deck.isNotEmpty()) hands[1].add(deck.removeFirst())
            }
            lastDealt = true
        }
        if (hands[0].isEmpty() && hands[1].isEmpty() && deck.isEmpty()) {
            if (lastCapturer >= 0 && table.isNotEmpty()) {
                captured[lastCapturer].addAll(table)
                table.clear()
            }
            finished = true
        } else {
            turn = 1 - p
        }
        return scopa
    }

    fun botChoose(): Pair<Card, List<Card>> = choose(1)

    /**
     * Sceglie carta e presa per il giocatore indicato. Usata dal Banco e, quando e' attivo
     * il gioco automatico, anche per le carte dell'utente.
     * Preferisce le prese (settebello, denari, sette, scopa), altrimenti cala la carta che
     * aiuta meno l'avversario.
     */
    fun choose(player: Int): Pair<Card, List<Card>> {
        val hand = hands[player]
        require(hand.isNotEmpty()) { "mano vuota per il giocatore $player" }
        var bestCard: Card? = null
        var bestCap: List<Card> = emptyList()
        var bestScore = Int.MIN_VALUE
        for (card in hand) {
            val caps = capturesFor(card.value)
            if (caps.isEmpty()) continue
            for (cap in caps) {
                val s = evalCapture(card, cap)
                if (s > bestScore) { bestScore = s; bestCard = card; bestCap = cap }
            }
        }
        if (bestCard != null) return Pair(bestCard!!, bestCap)
        // no capture: play the card that least helps the opponent (low value, avoid easy sums)
        val card = hand.minByOrNull { it.value + noCaptureRisk(it) } ?: hand.first()
        return Pair(card, emptyList())
    }

    private fun evalCapture(card: Card, cap: List<Card>): Int {
        var s = 0
        if (cap.size == table.size) s += 40 // clears the table (likely a scopa)
        for (c in cap + card) {
            if (c.isSettebello) s += 20
            if (c.isDenari) s += 3
            if (c.value == 7) s += 4
            if (c.value == 6) s += 2
            s += 1
        }
        return s
    }

    private fun noCaptureRisk(card: Card): Int {
        val newSum = table.sumOf { it.value } + card.value
        return if (newSum in 1..10) 6 else 0 // leaving a table the opponent can sweep is risky
    }

    private fun isLastCardOfGame(): Boolean =
        hands[0].isEmpty() && hands[1].isEmpty() && deck.isEmpty()

    // ---------------- scoring ----------------
    data class Score(val carte: Int, val denari: Int, val settebello: Int, val primiera: Int, val scope: Int) {
        val total: Int get() = carte + denari + settebello + primiera + scope
    }

    fun scoreFor(p: Int): Score {
        val mine = captured[p]
        val other = captured[1 - p]
        val carte = if (mine.size > other.size) 1 else 0
        val denari = if (mine.count { it.isDenari } > other.count { it.isDenari }) 1 else 0
        val settebello = if (mine.any { it.isSettebello }) 1 else 0
        val primiera = if (primieraValue(mine) > primieraValue(other)) 1 else 0
        return Score(carte, denari, settebello, primiera, scope[p])
    }

    private fun primieraValue(cards: List<Card>): Int {
        var total = 0
        for (s in 0..3) total += cards.filter { it.suit == s }.maxOfOrNull { it.prime } ?: 0
        return total
    }
}
