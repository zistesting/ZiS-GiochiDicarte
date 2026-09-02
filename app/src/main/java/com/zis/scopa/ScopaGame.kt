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

    val deck = ArrayDeque<Card>()
    val table = mutableListOf<Card>()
    val hands = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val captured = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val scope = intArrayOf(0, 0)
    var lastCapturer = -1
    var turn = 0
    var finished = false

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
            deck.addAll(shuffledDeck())
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
    fun capturesFor(value: Int): List<List<Card>> = capturesOn(table, value)

    /** Come capturesFor, ma su un tavolo qualsiasi: serve alla ricerca dell'ultima mano. */
    private fun capturesOn(t: List<Card>, value: Int): List<List<Card>> {
        val singles = t.filter { it.value == value }
        if (singles.isNotEmpty()) return singles.map { listOf(it) }
        val res = mutableListOf<List<Card>>()
        val n = t.size
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) < 2) continue
            var sum = 0
            val sub = ArrayList<Card>()
            for (i in 0 until n) if ((mask shr i) and 1 == 1) { sum += t[i].value; sub.add(t[i]) }
            if (sum == value) res.add(sub)
        }
        return res
    }

    /** Play a card for the current player. Returns true if it was a scopa. */
    fun play(card: Card, capture: List<Card>): Boolean {
        val p = turn
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

    // ---------------- conteggio delle carte ----------------

    /**
     * Carte che il giocatore [p] non ha ancora visto: non sono nella sua mano, non sono in
     * tavola e non stanno nei due mucchi delle prese.
     *
     * Finche' il mazzo non e' finito sono le carte del mazzo piu' quelle in mano
     * all'avversario. Quando il mazzo e' vuoto sono **esattamente** la mano dell'avversario:
     * da li' in poi l'ultima mano si gioca a carte note.
     */
    fun unseenBy(p: Int): List<Card> {
        val known = HashSet<Card>(64)
        known.addAll(hands[p]); known.addAll(table)
        known.addAll(captured[0]); known.addAll(captured[1])
        return fullDeck().filter { it !in known }
    }

    /**
     * Sceglie carta e presa per il giocatore indicato. Usata dal Banco e, quando e' attivo
     * il gioco automatico, anche per le carte dell'utente.
     *
     * Nell'ultima mano, a mazzo finito, calcola la giocata migliore invece di stimarla: le
     * carte mai viste sono quelle dell'avversario, restano al massimo sei giocate e l'albero
     * si esplora tutto. E' li' che si decidono le cose che pesano di piu': non regalare una
     * scopa, fare l'ultima presa (chi la fa si porta via il tavolo) e chiudere primiera e
     * denari. Fuori dall'ultima mano resta l'euristica: prima le prese buone (settebello,
     * denari, sette, scopa), altrimenti la carta che aiuta meno l'avversario.
     */
    fun choose(player: Int): Pair<Card, List<Card>> {
        val hand = hands[player]
        require(hand.isNotEmpty()) { "mano vuota per il giocatore $player" }

        if (deck.isEmpty() && hands[1 - player].isNotEmpty()) {
            exactChoice(player)?.let { return it }
        }

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
        val card = hand.minByOrNull { it.value + noCaptureRisk(player, it) } ?: hand.first()
        return Pair(card, emptyList())
    }

    // ---------------- ultima mano a carte note ----------------

    /** Tetto di nodi, per non bloccare mai la schermata. In pratica non si avvicina nemmeno:
     *  su ventimila ricerche il massimo osservato e' stato 221 nodi, la mediana 10. */
    private class Budget(var left: Int) { var aborted = false }

    /** Migliore giocata dell'ultima mano, o null se la ricerca e' stata interrotta. */
    private fun exactChoice(p: Int): Pair<Card, List<Card>>? {
        val budget = Budget(60_000)
        var best: Pair<Card, List<Card>>? = null
        var bestScore = Int.MIN_VALUE
        for (card in hands[p].toList()) {
            val caps = capturesOn(table, card.value).ifEmpty { listOf(emptyList<Card>()) }
            for (cap in caps) {
                val h = arrayOf(hands[0].toMutableList(), hands[1].toMutableList())
                val t = table.toMutableList()
                val c = arrayOf(captured[0].toMutableList(), captured[1].toMutableList())
                val sc = scope.copyOf()
                var lc = lastCapturer
                h[p].remove(card)
                if (cap.isNotEmpty()) {
                    for (x in cap) t.remove(x)
                    c[p].add(card); c[p].addAll(cap); lc = p
                    if (t.isEmpty() && (h[0].isNotEmpty() || h[1].isNotEmpty())) sc[p]++
                } else t.add(card)
                val v = solve(p, h, t, c, sc, lc, 1 - p, Int.MIN_VALUE, Int.MAX_VALUE, budget)
                if (budget.aborted) return null
                if (v > bestScore) { bestScore = v; best = Pair(card, cap) }
            }
        }
        return best
    }

    /**
     * Saldo di punti (giocatore [me] meno avversario) a fine mano, con gioco perfetto dalle
     * due parti. Il taglio alfa-beta serve: senza, l'albero e' cento volte piu' grande.
     */
    private fun solve(
        me: Int,
        h: Array<MutableList<Card>>, t: MutableList<Card>,
        c: Array<MutableList<Card>>, sc: IntArray,
        lastCap: Int, turnNow: Int,
        alphaIn: Int, betaIn: Int, budget: Budget
    ): Int {
        if (h[turnNow].isEmpty()) {
            // fine mano: le carte rimaste in tavola vanno all'ultimo che ha preso
            val c0 = c[0].toMutableList(); val c1 = c[1].toMutableList()
            if (lastCap >= 0 && t.isNotEmpty()) (if (lastCap == 0) c0 else c1).addAll(t)
            val mine = if (me == 0) c0 else c1
            val other = if (me == 0) c1 else c0
            return scoreOf(mine, other, sc[me]).total - scoreOf(other, mine, sc[1 - me]).total
        }
        if (--budget.left < 0) { budget.aborted = true; return 0 }

        var alpha = alphaIn
        var beta = betaIn
        val maximizing = turnNow == me
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (card in h[turnNow].toList()) {
            val caps = capturesOn(t, card.value).ifEmpty { listOf(emptyList<Card>()) }
            for (cap in caps) {
                val nh = arrayOf(h[0].toMutableList(), h[1].toMutableList())
                val nt = t.toMutableList()
                val nc = arrayOf(c[0].toMutableList(), c[1].toMutableList())
                val nsc = sc.copyOf()
                var nl = lastCap
                nh[turnNow].remove(card)
                if (cap.isNotEmpty()) {
                    for (x in cap) nt.remove(x)
                    nc[turnNow].add(card); nc[turnNow].addAll(cap); nl = turnNow
                    if (nt.isEmpty() && (nh[0].isNotEmpty() || nh[1].isNotEmpty())) nsc[turnNow]++
                } else nt.add(card)
                val v = solve(me, nh, nt, nc, nsc, nl, 1 - turnNow, alpha, beta, budget)
                if (budget.aborted) return best
                if (maximizing) {
                    if (v > best) best = v
                    if (best > alpha) alpha = best
                } else {
                    if (v < best) best = v
                    if (best < beta) beta = best
                }
                if (beta <= alpha) return best
            }
        }
        return best
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

    /**
     * Quanto e' pericoloso lasciare il tavolo com'e' dopo aver calato [card].
     *
     * Il tavolo si puo' spazzare solo prendendolo tutto, quindi il pericolo esiste se il
     * totale sta fra 1 e 10. Ma la carta che serve all'avversario deve anche essere ancora
     * in giro: se sono gia' uscite tutte e quattro le carte di quel valore il rischio e'
     * zero, e prima il Banco lo evitava lo stesso. Il peso cresce man mano che le carte
     * ignote diminuiscono, cioe' quando e' sempre piu' probabile che l'avversario ce l'abbia
     * davvero in mano.
     */
    private fun noCaptureRisk(player: Int, card: Card): Double {
        val newSum = table.sumOf { it.value } + card.value
        if (newSum !in 1..10) return 0.0
        val hidden = unseenBy(player)
        if (hidden.isEmpty()) return 0.0
        val matches = hidden.count { it.value == newSum }
        if (matches == 0) return 0.0
        val prob = (matches.toDouble() * hands[1 - player].size / hidden.size).coerceAtMost(1.0)
        return 8.0 * prob
    }

    private fun isLastCardOfGame(): Boolean =
        hands[0].isEmpty() && hands[1].isEmpty() && deck.isEmpty()

    // ---------------- scoring ----------------
    data class Score(val carte: Int, val denari: Int, val settebello: Int, val primiera: Int, val scope: Int) {
        val total: Int get() = carte + denari + settebello + primiera + scope
    }

    fun scoreFor(p: Int): Score = scoreOf(captured[p], captured[1 - p], scope[p])

    /** Punteggio a partire da due mucchi qualsiasi: lo usa anche la ricerca dell'ultima mano. */
    private fun scoreOf(mine: List<Card>, other: List<Card>, scopeCount: Int): Score {
        val carte = if (mine.size > other.size) 1 else 0
        val denari = if (mine.count { it.isDenari } > other.count { it.isDenari }) 1 else 0
        val settebello = if (mine.any { it.isSettebello }) 1 else 0
        val primiera = if (primieraValue(mine) > primieraValue(other)) 1 else 0
        return Score(carte, denari, settebello, primiera, scopeCount)
    }

    private fun primieraValue(cards: List<Card>): Int {
        var total = 0
        for (s in 0..3) total += cards.filter { it.suit == s }.maxOfOrNull { it.prime } ?: 0
        return total
    }
}
