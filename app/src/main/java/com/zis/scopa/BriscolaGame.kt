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

    // ---- simple bot ----
    fun botChoose(): Card {
        val hand = hands[turn]
        require(hand.isNotEmpty()) { "mano vuota per il giocatore $turn" }
        return if (trick.isEmpty()) botLead(hand) else botFollow(hand, trick[0])
    }

    private fun botLead(hand: List<Card>): Card {
        val nonBrisc = hand.filter { it.suit != briscolaSuit }
        val pool = if (nonBrisc.isNotEmpty()) nonBrisc else hand
        return pool.minByOrNull { points(it) * 100 + strength(it.value) }!!
    }

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
        val keep = (if (card.suit == briscolaSuit) 6.0 + strength(card.value) * 0.5 else 0.0) +
                (if (card.value == 1) 5.0 else if (card.value == 3) 4.0 else 0.0)
        val tie = strength(card.value) * 0.02
        return if (wins) (pot + cardPts) - keep * 0.9 - tie
        else -(pot + cardPts) + keep * 0.2 - tie
    }
}
