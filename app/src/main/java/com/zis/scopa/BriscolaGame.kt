package com.zis.scopa

/**
 * Briscola for two players (0 = you, 1 = Banco).
 * 40-card deck; 3 cards each; the bottom card of the deck shows the briscola (trump) suit and is
 * drawn last. Card points: A=11, 3=10, Re=4, Cavallo=3, Fante=2, others=0 (total 120, win with 61+).
 * Strength order (high->low): A, 3, Re, Cavallo, Fante, 7, 6, 5, 4, 2.
 */
class BriscolaGame {

    val deck = ArrayDeque<Card>()
    val hands = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val piles = arrayOf(mutableListOf<Card>(), mutableListOf<Card>())
    val trick = mutableListOf<Card>()        // 0 = leader's card, 1 = follower's card

    var briscolaSuit = 0
        private set

    /**
     * La carta di briscola, quella sotto al mazzo. Resta valorizzata per tutta la partita,
     * anche dopo che e' stata pescata: serve per mostrarla mentre scorre l'animazione.
     */
    var trumpCard: Card? = null
        private set

    /** Vero finche' la briscola e' ancora sotto al mazzo, cioe' finche' nessuno l'ha pescata. */
    val trumpInDeck: Boolean get() = deck.isNotEmpty()

    /** Carte pescate nell'ultima presa: servono per mostrarle solo a animazione finita. */
    val lastDrawn = arrayOfNulls<Card>(2)
    var leader = 0
    var turn = 0
    var finished = false

    // ---------------- ultima presa: fotografia e ripristino ----------------

    /** Fotografia completa della mano: tutto cio' che serve per riprendere da quel punto. */
    class State(
        val deck: List<Card>, val hands: List<List<Card>>, val piles: List<List<Card>>,
        val trick: List<Card>, val lastDrawn: List<Card?>, val leader: Int, val turn: Int
    )

    /**
     * Stato della mano all'inizio dell'ultima presa, cioe' quando il mazzo e' finito e
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
        trick.toList(), lastDrawn.toList(), leader, turn
    )

    /** Riporta la mano all'inizio dell'ultima presa. Vero se c'era una fotografia da cui ripartire. */
    fun restoreLastTrick(): Boolean {
        val s = lastTrickState ?: return false
        deck.clear(); deck.addAll(s.deck)
        for (p in 0..1) {
            hands[p].clear(); hands[p].addAll(s.hands[p])
            piles[p].clear(); piles[p].addAll(s.piles[p])
            lastDrawn[p] = s.lastDrawn[p]
        }
        trick.clear(); trick.addAll(s.trick)
        leader = s.leader
        turn = s.turn
        finished = false
        return true
    }

    fun points(c: Card): Int = when (c.value) {
        1 -> 11; 3 -> 10; 10 -> 4; 9 -> 3; 8 -> 2; else -> 0
    }

    fun strength(v: Int): Int = when (v) {
        1 -> 10; 3 -> 9; 10 -> 8; 9 -> 7; 8 -> 6; 7 -> 5; 6 -> 4; 5 -> 3; 4 -> 2; 2 -> 1; else -> 0
    }

    fun newGame(youStart: Boolean) {
        deck.clear(); hands[0].clear(); hands[1].clear()
        piles[0].clear(); piles[1].clear(); trick.clear()
        finished = false
        lastTrickState = null
        deck.addAll(shuffledDeck())
        repeat(3) { hands[0].add(deck.removeFirst()); hands[1].add(deck.removeFirst()) }
        trumpCard = deck.last()
        briscolaSuit = deck.last().suit
        lastDrawn[0] = null; lastDrawn[1] = null
        leader = if (youStart) 0 else 1
        turn = leader
    }

    /** Play a card for the current [turn]. Returns the trick winner (0/1) if the trick is now
     *  complete, or -1 if this was the leading card. */
    fun play(card: Card): Int {
        lastDrawn[0] = null; lastDrawn[1] = null
        hands[turn].remove(card)
        trick.add(card)
        if (trick.size == 1) {
            turn = 1 - turn
            return -1
        }
        val lead = trick[0]
        val follow = trick[1]
        val leadPlayer = leader
        val followPlayer = 1 - leader
        val winner = if (followWins(lead, follow)) followPlayer else leadPlayer
        piles[winner].addAll(trick)
        trick.clear()
        drawFor(winner); drawFor(1 - winner)
        leader = winner
        turn = winner
        if (hands[0].isEmpty() && hands[1].isEmpty()) finished = true
        else if (isLastTrickStart()) lastTrickState = snapshot()
        return winner
    }

    private fun followWins(lead: Card, follow: Card): Boolean {
        if (follow.suit == lead.suit) return strength(follow.value) > strength(lead.value)
        if (follow.suit == briscolaSuit) return true
        return false
    }

    private fun drawFor(p: Int) {
        lastDrawn[p] = if (deck.isNotEmpty()) deck.removeFirst().also { hands[p].add(it) } else null
    }

    fun scoreFor(p: Int): Int = piles[p].sumOf { points(it) }

    // ---- conteggio delle carte ----

    /**
     * Carte che il giocatore [p] non ha ancora visto: non sono nella sua mano, non stanno nei
     * due mazzetti delle prese e non sono in tavola.
     *
     * Finche' il mazzo non e' finito sono le carte del mazzo piu' quelle in mano
     * all'avversario. Quando il mazzo e' vuoto sono **esattamente** la mano dell'avversario:
     * le ultime tre prese si giocano quindi a carte scoperte.
     */
    fun unseenBy(p: Int): List<Card> {
        val known = HashSet<Card>(64)
        known.addAll(hands[p]); known.addAll(piles[0]); known.addAll(piles[1]); known.addAll(trick)
        return fullDeck().filter { it !in known }
    }

    // ---- simple bot ----
    fun botChoose(): Card {
        val hand = hands[turn]
        require(hand.isNotEmpty()) { "mano vuota per il giocatore $turn" }
        // Finale a carte note: finito il mazzo, le carte mai viste sono esattamente quelle
        // dell'avversario. Restano al massimo tre prese, quindi invece di stimare si calcola
        // la giocata migliore esplorando tutte le combinazioni: e' li' che si decide la
        // partita, perche' i carichi rimasti valgono da soli decine di punti.
        if (deck.isEmpty()) {
            val theirs = unseenBy(turn)
            if (theirs.size in 1..3) return bestEndgame(hand, theirs)
        }
        return if (trick.isEmpty()) botLead(hand) else botFollow(hand, trick[0])
    }

    /** Carta che porta al saldo migliore nelle prese che restano. */
    private fun bestEndgame(mine: List<Card>, theirs: List<Card>): Card {
        val lead = trick.firstOrNull()
        var best: Card? = null
        var bestScore = Int.MIN_VALUE
        for (c in mine) {
            val rest = mine - c
            val v = if (lead == null) {
                solve(rest, theirs, c, false)
            } else {
                val win = followWins(lead, c)
                val pot = points(lead) + points(c)
                (if (win) pot else -pot) + solve(rest, theirs, null, win)
            }
            if (v > bestScore) { bestScore = v; best = c }
        }
        return best ?: mine.first()
    }

    /**
     * Saldo di punti (mio meno avversario) delle prese ancora da giocare, con gioco perfetto
     * dalle due parti. [lead] e' la carta gia' in tavola, null se tocca aprire; [meToPlay]
     * dice se la prossima carta la gioco io. Con tre carte per parte l'albero ha 36 foglie,
     * quindi si esplora tutto senza tagli.
     */
    private fun solve(mine: List<Card>, theirs: List<Card>, lead: Card?, meToPlay: Boolean): Int {
        if (lead == null) {
            if (mine.isEmpty() || theirs.isEmpty()) return 0
            return if (meToPlay) mine.maxOf { c -> solve(mine - c, theirs, c, false) }
                   else theirs.minOf { c -> solve(mine, theirs - c, c, true) }
        }
        return if (meToPlay) mine.maxOf { c ->
            // rispondo io, quindi chi ha aperto e' l'avversario
            val win = followWins(lead, c)
            val pot = points(lead) + points(c)
            (if (win) pot else -pot) + solve(mine - c, theirs, null, win)
        } else theirs.minOf { c ->
            // risponde lui, quindi chi ha aperto sono io
            val win = followWins(lead, c)
            val pot = points(lead) + points(c)
            (if (win) -pot else pot) + solve(mine, theirs - c, null, !win)
        }
    }

    /**
     * Quanto vale tenersi una carta invece di giocarla: le briscole servono per le prese
     * future, e i carichi (Asso e 3) valgono da soli quasi un decimo della partita.
     */
    private fun keepValue(card: Card): Double =
        (if (card.suit == briscolaSuit) 6.0 + strength(card.value) * 0.5 else 0.0) +
        (if (card.value == 1) 5.0 else if (card.value == 3) 4.0 else 0.0)

    /**
     * Apertura: si cala la carta che costa meno perderla, cioe' i suoi punti piu' quanto
     * varrebbe tenersela.
     *
     * Prima qui c'era una regola secca, "mai aprire di briscola", che pero' con una mano
     * tipo briscola 4 + briscola cavallo + un 3 costringeva a buttare il carico: dieci punti
     * regalati per non calare una briscola che non vale niente. Adesso la briscola bassa
     * entra nel confronto e in quel caso vince lei, mentre nelle mani normali il liscio resta
     * comunque la scelta piu' economica e la briscola si tiene.
     */
    private fun botLead(hand: List<Card>): Card =
        hand.minByOrNull { points(it) + keepValue(it) + strength(it.value) * 0.05 }!!

    private fun botFollow(hand: List<Card>, lead: Card): Card {
        val pot = points(lead)
        return hand.maxByOrNull { evalFollow(it, lead, pot) }!!
    }

    /** Value of playing [card] as the follower. Winning secures the table points (and keeps the card's
     *  own points in our pile); losing hands them to the opponent. Trumps and carichi (Asso/3) carry a
     *  "keep" value, so the bot won't throw a carico away just to hoard a briscola, yet still grabs rich
     *  tricks and prefers winning with a non-briscola when it can. */
    private fun evalFollow(card: Card, lead: Card, pot: Int): Double {
        val wins = followWins(lead, card)
        val cardPts = points(card)
        val keep = keepValue(card)
        val tie = strength(card.value) * 0.02
        return if (wins) (pot + cardPts) - keep * 0.9 - tie
        else -(pot + cardPts) + keep * 0.2 - tie
    }
}
