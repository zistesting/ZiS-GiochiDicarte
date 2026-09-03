package com.zis.scopa

/**
 * Tresette in due con il tallone.
 *
 * Dieci carte a testa, venti restano nel tallone. Si cala una carta a testa: chi risponde
 * **deve** rispondere al seme di chi ha aperto, e puo' calare un altro seme solo se quel seme
 * non ce l'ha, ma in quel caso non puo' prendere. Prende chi ha calato la carta piu' alta del
 * seme di apertura. Poi si pesca: prima chi ha preso, poi l'altro, e **la carta pescata si
 * mostra all'avversario**. Finito il tallone si giocano le ultime dieci prese senza pescare.
 *
 * Ordine di presa, dal piu' forte: 3, 2, Asso, Re, Cavallo, Fante, 7, 6, 5, 4.
 *
 * I punti si contano in terzi, per non usare i decimali: l'Asso vale 3 terzi (un punto), il 2,
 * il 3, il Fante, il Cavallo e il Re un terzo ciascuno, dal 4 al 7 niente. L'ultima presa vale
 * un punto, cioe' 3 terzi. In tutto fanno 35 terzi. A fine mano ogni giocatore scarta il
 * proprio resto, e per come sono distribuiti i terzi il totale della mano risulta **sempre
 * esattamente 11 punti**: non capita mai che una mano ne valga 10.
 */
class TresetteGame {

    /** Il tallone: le venti carte da cui si pesca. */
    val deck = ArrayDeque<Card>()
    val hands = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val piles = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())

    /** Carte gia' calate nella presa in corso: una se qualcuno ha aperto, vuota fra una presa e l'altra. */
    val trick = mutableListOf<Card>()

    /** Ultima carta pescata da ciascuno, per mostrarla a schermo. */
    val lastDrawn = arrayOfNulls<Card>(2)

    /**
     * Carte che l'avversario ha pescato mostrandole e che ha ancora in mano.
     * `seenInHandOf[p]` sono le carte che il giocatore p **sa** essere in mano all'altro.
     * Serve al Banco per giocare informato prima che il tallone finisca.
     */
    val seenInHandOf = arrayOf(mutableSetOf<Card>(), mutableSetOf<Card>())

    var leader = 0
        private set
    var turn = 0
        private set
    var finished = false
        private set

    /** Chi ha vinto l'ultima presa della mano: vale un punto in piu'. */
    var lastTrickWinner = -1
        private set

    // ---------------- ultima presa: fotografia e ripristino ----------------

    /** Fotografia completa della mano: tutto cio' che serve per riprendere da quel punto. */
    class State(
        val deck: List<Card>, val hands: List<List<Card>>, val piles: List<List<Card>>,
        val trick: List<Card>, val lastDrawn: List<Card?>, val seen: List<Set<Card>>,
        val leader: Int, val turn: Int, val lastTrickWinner: Int
    )

    /**
     * Stato della mano all'inizio dell'ultima presa, cioe' quando il tallone e' finito e
     * ciascuno ha in mano la sua ultima carta, prima che venga calata la prima delle due.
     * A fine mano permette di rigiocare quell'ultima giocata. Il Banco non tira a caso,
     * quindi le sue carte saranno le stesse: cambia solo quello che decidi tu.
     */
    var lastTrickState: State? = null
        private set

    private fun isLastTrickStart(): Boolean =
        deck.isEmpty() && trick.isEmpty() && hands[0].size == 1 && hands[1].size == 1

    private fun snapshot() = State(
        deck.toList(), listOf(hands[0].toList(), hands[1].toList()),
        listOf(piles[0].toList(), piles[1].toList()),
        trick.toList(), lastDrawn.toList(),
        listOf(seenInHandOf[0].toSet(), seenInHandOf[1].toSet()),
        leader, turn, lastTrickWinner
    )

    /** Riporta la mano all'inizio dell'ultima presa. Vero se c'era una fotografia da cui ripartire. */
    fun restoreLastTrick(): Boolean {
        val s = lastTrickState ?: return false
        deck.clear(); deck.addAll(s.deck)
        for (p in 0..1) {
            hands[p].clear(); hands[p].addAll(s.hands[p])
            piles[p].clear(); piles[p].addAll(s.piles[p])
            lastDrawn[p] = s.lastDrawn[p]
            seenInHandOf[p].clear(); seenInHandOf[p].addAll(s.seen[p])
        }
        trick.clear(); trick.addAll(s.trick)
        leader = s.leader
        turn = s.turn
        lastTrickWinner = s.lastTrickWinner
        finished = false
        return true
    }

    fun newGame(youStart: Boolean) {
        val d = shuffledDeck()
        deck.clear(); deck.addAll(d.subList(20, 40))
        hands[0].clear(); hands[0].addAll(d.subList(0, 10))
        hands[1].clear(); hands[1].addAll(d.subList(10, 20))
        piles[0].clear(); piles[1].clear()
        trick.clear()
        lastDrawn[0] = null; lastDrawn[1] = null
        seenInHandOf[0].clear(); seenInHandOf[1].clear()
        leader = if (youStart) 0 else 1
        turn = leader
        finished = false
        lastTrickWinner = -1
        lastTrickState = null
    }

    /** Forza di presa: 3 e' la piu' alta, 4 la piu' bassa. */
    fun strength(value: Int): Int = when (value) {
        3 -> 10; 2 -> 9; 1 -> 8; 10 -> 7; 9 -> 6; 8 -> 5; 7 -> 4; 6 -> 3; 5 -> 2; else -> 1
    }

    /** Valore della carta in terzi di punto. */
    fun thirds(c: Card): Int = when (c.value) {
        1 -> 3                      // Asso: un punto intero
        2, 3, 8, 9, 10 -> 1         // 2, 3, Fante, Cavallo, Re: un terzo
        else -> 0                   // dal 4 al 7: niente
    }

    /**
     * Carte che il giocatore [p] puo' calare adesso. Se ha aperto l'avversario e p possiede
     * quel seme, deve rispondere: e' un obbligo, non una convenienza.
     */
    fun legalMoves(p: Int): List<Card> {
        val hand = hands[p]
        if (trick.isEmpty()) return hand.toList()
        val same = hand.filter { it.suit == trick[0].suit }
        return if (same.isNotEmpty()) same else hand.toList()
    }

    fun isLegal(p: Int, card: Card): Boolean = legalMoves(p).contains(card)

    /** Vero se la carta di risposta batte quella di apertura. */
    fun followWins(lead: Card, follow: Card): Boolean =
        follow.suit == lead.suit && strength(follow.value) > strength(lead.value)

    /**
     * Cala una carta. Restituisce -1 se ha solo aperto la presa, altrimenti chi l'ha vinta.
     */
    fun play(card: Card): Int {
        val p = turn
        hands[p].remove(card)
        seenInHandOf[1 - p].remove(card)   // se era una carta vista, adesso e' in tavola
        trick.add(card)

        if (trick.size == 1) {
            turn = 1 - p
            return -1
        }

        val lead = trick[0]
        val winner = if (followWins(lead, trick[1])) 1 - leader else leader
        piles[winner].addAll(trick)
        trick.clear()
        lastTrickWinner = winner

        lastDrawn[0] = null; lastDrawn[1] = null
        for (q in intArrayOf(winner, 1 - winner)) {   // pesca prima chi ha preso
            if (deck.isNotEmpty()) {
                val c = deck.removeFirst()
                hands[q].add(c)
                lastDrawn[q] = c
                seenInHandOf[1 - q].add(c)            // la carta pescata si mostra all'avversario
            }
        }

        leader = winner
        turn = winner
        if (hands[0].isEmpty() && hands[1].isEmpty()) finished = true
        else if (isLastTrickStart()) lastTrickState = snapshot()
        return winner
    }

    /** Punti in terzi del giocatore [p], ultima presa compresa. */
    fun thirdsFor(p: Int): Int {
        var t = piles[p].sumOf { thirds(it) }
        if (finished && lastTrickWinner == p) t += 3
        return t
    }

    /** Punti interi: il resto si scarta, come vuole il gioco. */
    fun scoreFor(p: Int): Int = thirdsFor(p) / 3

    /** Il resto scartato, da mostrare nel riepilogo (0, 1 o 2 terzi). */
    fun remainderFor(p: Int): Int = thirdsFor(p) % 3

    // ---------------- conteggio delle carte ----------------

    /**
     * Carte di cui il giocatore [p] non sa dove sono: ne' in mano sua, ne' in tavola, ne' nei
     * due mazzetti delle prese. Sono il tallone piu' la parte della mano avversaria che non ha
     * mai visto. Quando il tallone e' finito sono **esattamente** la mano dell'avversario.
     */
    fun unseenBy(p: Int): List<Card> {
        val known = HashSet<Card>(64)
        known.addAll(hands[p]); known.addAll(piles[0]); known.addAll(piles[1]); known.addAll(trick)
        return fullDeck().filter { it !in known }
    }

    /** Vero se nessuna carta ignota batte [c] nel suo seme: da li' in poi e' una presa sicura. */
    fun isMaster(p: Int, c: Card, unseen: List<Card>): Boolean =
        unseen.none { it.suit == c.suit && strength(it.value) > strength(c.value) }

    // ---------------- il Banco ----------------

    /** Vero se la presa che si sta giocando e' l'ultima della mano: vale un punto in piu'. */
    private fun isLastTrick(p: Int): Boolean =
        deck.isEmpty() && hands[p].size == 1 && hands[1 - p].isEmpty()

    /**
     * Scelta del Banco (e del giocatore umano quando e' attivo il gioco automatico).
     *
     * Finito il tallone restano poche carte e la mano dell'avversario e' nota, quindi si
     * calcola la giocata migliore esplorando l'albero. Sopra le [EXACT_MAX] carte l'albero
     * esplode, percio' li' resta l'euristica: tanto e' il finale che decide la mano.
     */
    fun botChoose(): Card {
        val p = turn
        require(hands[p].isNotEmpty()) { "mano vuota per il giocatore $p" }
        if (deck.isEmpty() && hands[p].size <= EXACT_MAX) {
            exactChoice(p)?.let { return it }
        }
        return heuristic(p)
    }

    /** Quanto vale tenersi una carta invece di calarla. */
    private fun keepValue(c: Card, unseen: List<Card>): Double {
        val higher = unseen.count { it.suit == c.suit && strength(it.value) > strength(c.value) }
        // il valore in punti che perderei, piu' il valore di controllo: una carta che nessuno
        // puo' piu' battere e' una presa sicura piu' avanti
        return thirds(c) + if (higher == 0) 2.0 else maxOf(0.0, 1.2 - 0.4 * higher)
    }

    /**
     * Euristica per quando il tallone non e' ancora finito.
     *
     * Le carte ignote si dividono in due: quelle che l'avversario ha pescato scoperte e ha
     * ancora in mano ([seenInHandOf]) sono una certezza, tutte le altre stanno nel tallone
     * o nella parte della sua mano che non ho mai visto. Prima le due categorie si
     * confondevano in un'unica probabilita': una carta vista pescare contava come una
     * carta qualsiasi del tallone, e il Banco usciva tranquillo con un Re di coppe pur
     * avendo visto l'avversario pescare il 3 di coppe.
     */
    private fun heuristic(p: Int): Card {
        val unseen = unseenBy(p)
        val known = seenInHandOf[p]
        val unknown = unseen.filter { it !in known }
        // carte in mano all'avversario che non ho mai visto
        val hiddenSlots = maxOf(0, hands[1 - p].size - known.size)
        val legal = legalMoves(p)

        if (trick.isNotEmpty()) {
            // Rispondo, quindi so gia' come finisce la presa: niente da indovinare.
            val lead = trick[0]
            val bonus = if (isLastTrick(p)) 3 else 0
            var best = legal.first(); var bestScore = -1e9
            for (c in legal) {
                val pot = thirds(lead) + thirds(c) + bonus
                val swing = if (followWins(lead, c)) pot.toDouble() else -pot.toDouble()
                val s = swing - keepValue(c, unseen) * 0.9 - strength(c.value) * 0.02
                if (s > bestScore) { bestScore = s; best = c }
            }
            return best
        }

        // Apro io. Se ho una carta che nessuno puo' battere, esco di li': prendo di sicuro e
        // resto di mano. Altrimenti esco con la carta che rischia di costarmi meno.
        var best = legal.first(); var bestScore = -1e9
        for (c in legal) {
            val higherKnown = known.count { it.suit == c.suit && strength(it.value) > strength(c.value) }
            val higherUnknown = unknown.count { it.suit == c.suit && strength(it.value) > strength(c.value) }
            val s = if (higherKnown == 0 && higherUnknown == 0) {
                // presa sicura: prendo la mia carta piu' quella che l'avversario deve calare.
                // Se so che ha carte di questo seme, calera' la piu' economica fra quelle;
                // altrimenti stimo con la piu' economica fra le ignote del seme.
                val knownSuit = known.filter { it.suit == c.suit }
                val cheapest = (if (knownSuit.isNotEmpty()) knownSuit else unknown.filter { it.suit == c.suit })
                    .minOfOrNull { thirds(it) } ?: 0
                6.0 + thirds(c) + cheapest
            } else {
                // una carta piu' alta vista in mano sua e' una certezza, non una probabilita'
                val risk = if (higherKnown > 0) 1.0
                           else minOf(1.0, higherUnknown.toDouble() * hiddenSlots / maxOf(1, unknown.size))
                -(thirds(c) * 2.0 + 0.5) * risk - 0.03 * strength(c.value)
            }
            if (s > bestScore) { bestScore = s; best = c }
        }
        return best
    }

    // ---------------- finale a carte note ----------------

    /**
     * Oltre questo numero di carte in mano l'albero del finale diventa troppo grande per
     * risolverlo mentre il giocatore aspetta. Il valore e' misurato, non scelto a occhio.
     */
    private val EXACT_MAX = 7

    /** Tetto di nodi, come rete di sicurezza: se scatta si torna all'euristica. */
    private class Budget(var left: Int) { var aborted = false }

    /**
     * "Infinito" della ricerca. Non si usano Int.MIN_VALUE e Int.MAX_VALUE perche' la
     * finestra alfa-beta viene spostata di qualche terzo di punto a ogni presa, e sottrarre
     * da Int.MIN_VALUE fa ribaltare il segno. I punti di una mano sono al massimo 35 terzi,
     * quindi un milione e' infinito quanto basta.
     */
    private val INF = 1_000_000

    private fun legalFrom(hand: List<Card>, lead: Card?): List<Card> {
        if (lead == null) return hand
        val same = hand.filter { it.suit == lead.suit }
        return if (same.isNotEmpty()) same else hand
    }

    private fun exactChoice(p: Int): Card? {
        val theirs = unseenBy(p)
        if (theirs.isEmpty()) return null
        val mine = hands[p].toList()
        val lead = trick.firstOrNull()
        val budget = Budget(400_000)
        var best: Card? = null
        var bestScore = -INF
        for (c in legalFrom(mine, lead)) {
            val rest = mine - c
            val v = if (lead == null) {
                solve(rest, theirs, c, false, -INF, INF, budget)
            } else {
                val win = followWins(lead, c)
                val pot = thirds(lead) + thirds(c)
                val last = rest.isEmpty() && theirs.isEmpty()
                val gain = if (win) pot + (if (last) 3 else 0) else 0
                gain + solve(rest, theirs, null, win, -INF, INF, budget)
            }
            if (budget.aborted) return null
            if (v > bestScore) { bestScore = v; best = c }
        }
        return best
    }

    /**
     * Terzi di punto che il Banco porta a casa dalle prese che restano, con gioco perfetto
     * dalle due parti. Massimizzare i propri terzi e' esattamente la cosa giusta: il totale
     * della mano e' fisso, quindi la differenza di punti interi cresce insieme ai terzi.
     */
    private fun solve(
        mine: List<Card>, theirs: List<Card>, lead: Card?, meToPlay: Boolean,
        alphaIn: Int, betaIn: Int, budget: Budget
    ): Int {
        if (lead == null && mine.isEmpty() && theirs.isEmpty()) return 0
        if (--budget.left < 0) { budget.aborted = true; return 0 }

        var alpha = alphaIn
        var beta = betaIn

        if (lead == null) {
            // apre chi tocca
            if (meToPlay) {
                var best = -INF
                for (c in legalFrom(mine, null)) {
                    val v = solve(mine - c, theirs, c, false, alpha, beta, budget)
                    if (budget.aborted) return best
                    if (v > best) best = v
                    if (best > alpha) alpha = best
                    if (beta <= alpha) break
                }
                return best
            }
            var best = INF
            for (c in legalFrom(theirs, null)) {
                val v = solve(mine, theirs - c, c, true, alpha, beta, budget)
                if (budget.aborted) return best
                if (v < best) best = v
                if (best < beta) beta = best
                if (beta <= alpha) break
            }
            return best
        }

        if (meToPlay) {
            // rispondo io: chi ha aperto e' l'avversario
            var best = -INF
            for (c in legalFrom(mine, lead)) {
                val rest = mine - c
                val win = followWins(lead, c)
                val pot = thirds(lead) + thirds(c)
                val last = rest.isEmpty() && theirs.isEmpty()
                val gain = if (win) pot + (if (last) 3 else 0) else 0
                // Il valore del nodo e' gain + valore del figlio, quindi la finestra alfa-beta
                // va passata al figlio spostata di -gain. Senza lo spostamento il figlio
                // potava su soglie sbagliate e restituiva un limite al posto del valore vero:
                // in un finale su quindici il Banco sceglieva una carta non ottima.
                val v = gain + solve(rest, theirs, null, win, alpha - gain, beta - gain, budget)
                if (budget.aborted) return best
                if (v > best) best = v
                if (best > alpha) alpha = best
                if (beta <= alpha) break
            }
            return best
        }
        // risponde lui: chi ha aperto sono io
        var best = INF
        for (c in legalFrom(theirs, lead)) {
            val rest = theirs - c
            val oppWins = followWins(lead, c)
            val pot = thirds(lead) + thirds(c)
            val last = mine.isEmpty() && rest.isEmpty()
            val gain = if (oppWins) 0 else pot + (if (last) 3 else 0)
            // stesso spostamento della finestra del ramo qui sopra
            val v = gain + solve(mine, rest, null, !oppWins, alpha - gain, beta - gain, budget)
            if (budget.aborted) return best
            if (v < best) best = v
            if (best < beta) beta = best
            if (beta <= alpha) break
        }
        return best
    }
}
