package com.zis.scopa

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.zis.scopa.databinding.ActivityBriscolaBinding
import com.zis.scopa.databinding.DialogResultBinding

/**
 * Briscola in due col tallone.
 *
 * Il ciclo di vita, il salvataggio, il watchdog, la fine mano e la cornice del riepilogo
 * stanno in [BotGameActivity], insieme a Scopa e Tresette. Qui resta solo quello che e' di
 * questo gioco:
 *
 *  - la briscola coricata sotto il mazzo, che si vede finche' nel tallone ci sono carte;
 *  - la presa a due carte, con la pausa a carte scoperte e il volo verso il mazzetto di chi
 *    ha vinto;
 *  - l'incontro che si conta a **mani vinte** e non a punti, da cui il [matchOver] proprio.
 */
class BriscolaActivity : BotGameActivity() {

    private lateinit var b: ActivityBriscolaBinding
    private val game = BriscolaGame()

    /** Vero mentre scorre l'animazione della presa: le carte appena pescate restano nascoste. */
    private var hideDrawn = false

    // ---- quello che la base ha bisogno di sapere ----
    override val rootView: View get() = b.root
    override val overlay: FrameLayout get() = b.overlay
    override val statusView: TextView get() = b.txtStatus
    override val saveKey: String get() = SavedGame.BRISCOLA
    override val statsKey: String get() = Prefs.GAME_BRISCOLA
    override val rulesText: Int get() = R.string.rules_briscola

    override val gameFinished: Boolean get() = game.finished
    override val isBotTurn: Boolean get() = game.turn == 1
    override val youHandEmpty: Boolean get() = game.hands[0].isEmpty()
    override val hasLastDeal: Boolean get() = game.lastDealState != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBriscolaBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Dopo setContentView: rootView e overlay sono getter su b, che e' lateinit.
        setupCommon(b.btnInfo)
        // Se c'e' una partita lasciata a meta' si riprende quella, altrimenti se ne comincia una.
        if (!restoreState()) startMatch()
    }

    /**
     * Sistemazione fissa degli elementi, calcolata una volta sola perche' dipende solo
     * dalle dimensioni dello schermo:
     *  - le carte del Banco escono per meta' dal bordo alto (sono coperte, non serve vederle)
     *  - il mazzo esce per meta' dal bordo sinistro
     *  - la briscola resta nascosta per un terzo sotto al mazzo
     */
    override fun placeCards() {
        // I margini si RIASSEGNANO e non si modificano in posto: setLayoutParams chiama
        // requestLayout() da sola, mutare i campi dell'oggetto esistente no. Prima
        // funzionava solo perche' il render() che segue cambia comunque qualche misura e
        // il layout lo chiede lui - una dipendenza da un ordine di chiamate che sta in un
        // altro metodo. La briscola era gia' scritta cosi' qui sotto; queste due no.
        // se le carte del Banco sono scoperte devono restare tutte visibili
        val bot = b.botHand.layoutParams as LinearLayout.LayoutParams
        bot.topMargin = if (showBot) 0 else -cardH / 2
        b.botHand.layoutParams = bot
        val row = b.deckRow.layoutParams as FrameLayout.LayoutParams
        row.marginStart = -cardW / 2
        b.deckRow.layoutParams = row

        // La briscola sta coricata sotto il mazzo, quindi il suo riquadro ha le misure
        // scambiate: largo quanto e' alta una carta, alto quanto e' larga.
        val lp = b.briscolaBox.layoutParams as LinearLayout.LayoutParams
        lp.width = cardH
        lp.height = cardW
        lp.gravity = Gravity.CENTER_VERTICAL
        // Il centro della briscola cade sul bordo destro del mazzo, quindi ne sporge fuori
        // esattamente meta'. Centrandola invece sul mazzo (come nel primo tentativo) oltre il
        // bordo ne restava solo un quinto: la carta c'era ma non si leggeva. La meta' che
        // resta nascosta va sotto il mazzo e fuori dal bordo sinistro dello schermo.
        lp.marginStart = -cardH / 2
        b.briscolaBox.layoutParams = lp

        // La briscola coricata esce dai confini sia del suo riquadro sia della riga: senza
        // questo verrebbe tagliata. A ritagliare resta il riquadro centrale, che e' quello
        // che deve tenerla dentro il bordo dello schermo.
        b.briscolaBox.clipChildren = false
        b.deckRow.clipChildren = false

        b.deckBox.translationZ = 1f   // il mazzo copre la briscola, non il contrario
    }

    /**
     * Se ci si ferma a meta' dell'animazione della presa, hideDrawn resterebbe acceso e le
     * carte appena pescate sparirebbero dalla mano per il resto della partita.
     */
    override fun clearAnimationState() {
        hideDrawn = false
    }

    /**
     * Nella Briscola l'incontro si conta a mani vinte, una per volta: arrivare al bersaglio
     * in parita' non e' un caso che possa darsi, quindi qui non c'e' la condizione sulla
     * parita' che hanno Scopa e Tresette.
     */
    override fun matchOver(): Boolean = matchYou >= matchTarget || matchBot >= matchTarget

    // ---------------------------------------------------------- l'incontro

    override fun beginMatch() {
        matchTarget = Prefs.briscolaTarget(this)
        matchYou = 0
        matchBot = 0
        youStartNext = true
        startRound()
    }

    override fun startRound() {
        game.newGame(youStart = youStartNext)
        youStartNext = !youStartNext
        ending = false
        roundScored = false
        started = true
        hideDrawn = false
        busy = true
        render()
        moveSeq++
        statoTurno()
        dealAnimation()
        // come in Scopa: il gioco riparte su un timer fisso, non alla fine dell'animazione
        post(t.deal) {
            if (destroyed || ending) return@post
            if (isBotTurn) {
                playBot()
            } else {
                busy = false
                render()
                clearStatus()
                maybeAutoPlay()
            }
        }
    }

    /** Le sei carte partono dal mazzo e raggiungono la loro mano. */
    private fun dealAnimation() {
        dealFrom(b.deckBox) {
            val views = ArrayList<View>()
            for (i in 0 until b.botHand.childCount) views.add(b.botHand.getChildAt(i))
            for (i in 0 until b.youHand.childCount) views.add(b.youHand.getChildAt(i))
            views
        }
    }

    /** A sessanta pari la mano non va a nessuno. */
    override fun awardHand() {
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)
        if (you > bot) matchYou++ else if (bot > you) matchBot++
    }

    // ------------------------------------------------------------- disegno

    /** Allinea una fila di carte al contenuto voluto, riusando le viste gia' presenti. */
    private fun syncRow(row: LinearLayout, cards: List<Card>, faceUp: Boolean, clickable: Boolean) {
        while (row.childCount > cards.size) row.removeViewAt(row.childCount - 1)
        while (row.childCount < cards.size) {
            val lp = LinearLayout.LayoutParams(cardW, cardH)
            lp.marginStart = dp(3); lp.marginEnd = dp(3)
            row.addView(CardView(this), lp)
        }
        for (i in cards.indices) {
            val cv = row.getChildAt(i) as CardView
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; cv.layoutParams = lp
            }
            resetCard(cv)
            val c = cards[i]
            cv.card = if (faceUp) c else null
            cv.faceUp = faceUp
            if (clickable) {
                cv.setOnClickListener { onPlayerCard(c, cv) }
            } else {
                cv.setOnClickListener(null)
                cv.isClickable = false
            }
        }
    }

    // Mazzo e briscola: create una volta sola, poi si aggiornano.
    private var deckBack: CardView? = null
    private var deckCount: TextView? = null
    private var trumpView: CardView? = null

    override fun render() = renderWith(null)

    /**
     * Il disegno del tavolo. [trickOverride] tiene in vista entrambe le carte della presa
     * durante la pausa a carte scoperte, quando il motore ha gia' svuotato la presa.
     *
     * Non e' piu' render(trickOverride: List<Card>? = null) come prima, perche' una funzione
     * con parametro predefinito non implementa un abstract fun render(): alla base serve una
     * funzione senza parametri, e quella qui sopra e' la sua unica riga.
     */
    private fun renderWith(trickOverride: List<Card>?) {
        b.botScore.text = getString(R.string.bot_points, game.scoreFor(1).toString())
        b.youScore.text = getString(R.string.you_points, game.scoreFor(0).toString())

        syncRow(b.botHand, visibleHand(1), faceUp = showBot, clickable = false)
        syncRow(b.youHand, visibleHand(0), faceUp = true, clickable = true)

        renderDeck()
        renderTrump()

        // trick (played cards)
        syncRow(b.trickRow, trickOverride ?: game.trick, faceUp = true, clickable = false)

        armWatchdog()
    }

    /** Le carte della mano di [p] nell'ordine in cui compaiono a schermo. */
    private fun visibleHand(p: Int): List<Card> =
        game.hands[p].filter { !(hideDrawn && it == game.lastDrawn[p]) }

    /** Mazzo: le carte sopra la briscola, col numero rimasto. */
    private fun renderDeck() {
        if (deckBack == null) {
            val fl = FrameLayout(this)
            val back = CardView(this).apply { faceUp = false }
            fl.addView(back, FrameLayout.LayoutParams(cardW, cardH))
            val tv = TextView(this)
            tv.setTextColor(getColor(R.color.silver)); tv.textSize = 14f
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setBackgroundColor(Color.argb(0xB0, 0, 0, 0))
            tv.setPadding(dp(5), dp(1), dp(5), dp(1))
            val tp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
            tp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            tp.marginEnd = dp(6)
            fl.addView(tv, tp)
            b.deckBox.addView(fl)
            deckBack = back
            deckCount = tv
        }
        // finche' le carte pescate restano nascoste, il contatore non deve calare in anticipo
        val pending = if (hideDrawn) game.lastDrawn.count { it != null } else 0

        // Le carte che restano da pescare, BRISCOLA COMPRESA.
        //
        // Prima qui c'era un - 1: il numero contava le sole coperte impilate SOPRA la
        // briscola, che non era nel conto perche' si vede da se', coricata di fianco. Con due
        // carte in tutto il numero diceva percio' 1, e sopra un mazzo un numero vuol dire
        // quante carte restano - tanto che la prossima presa ne fa pescare due, la coperta a
        // chi vince e la briscola a chi perde. Cosi' e' anche lo stesso conto della Scopa
        // (deck.size) e del Tresette (deck.size + pending), ed e' l'unico che rende vera la
        // descrizione per TalkBack, cd_deck, che dice "Mazzo, %d carte": chi non vede la
        // briscola sporgere non ha modo di accorgersi che ce n'e' una in piu'.
        val left = game.deck.size + pending

        // La visibilita' del mazzetto coperto NON cambia rispetto a prima, in nessuno stato
        // raggiungibile: il mazzo si svuota due carte per volta, perche' a ogni presa pescano
        // tutti e due, quindi le carte che restano sono sempre in numero pari e lo stato "c'e'
        // solo la briscola" non si da'. Il mazzetto compare finche' il mazzo non e' vuoto e
        // sparisce quando lo e', esattamente come col - 1. Il confronto e' con 1 e non con 0
        // per essere una rete: se un giorno si pescasse una carta per volta, il numero non
        // arriverebbe a dire "1" avendo in vista la sola briscola - e lo direbbe anche male,
        // perche' cd_deck e' una frase unica al plurale.
        val visible = if (left > 1) View.VISIBLE else View.GONE
        deckBack?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; it.layoutParams = lp
            }
        }
        deckCount?.let {
            it.text = left.toString()
            it.contentDescription =
                if (left > 0) getString(R.string.cd_deck, left) else getString(R.string.cd_deck_empty)
        }
        (deckBack?.parent as? View)?.visibility = visible
    }

    /**
     * La briscola si vede finche' sta sotto al mazzo; nella presa in cui viene pescata resta
     * a schermo fino alla fine dell'animazione, poi compare nella mano di chi l'ha presa.
     */
    private fun renderTrump() {
        if (trumpView == null) {
            val cv = CardView(this)
            cv.faceUp = true
            // Ruotata in senso orario, non antiorario: cosi' la meta' che resta in vista
            // e' quella ALTA della carta. Girandola dall'altra parte si vedrebbe la meta'
            // bassa, cioe' il cartiglio vuoto, e la briscola sarebbe piu' difficile da
            // riconoscere con un'occhiata.
            cv.rotation = 90f
            val clp = FrameLayout.LayoutParams(cardW, cardH)
            clp.gravity = Gravity.CENTER
            b.briscolaBox.addView(cv, clp)
            trumpView = cv
        }
        val trumpVisible = game.trumpInDeck ||
            (hideDrawn && game.lastDrawn.any { it != null && it == game.trumpCard })
        trumpView?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; it.layoutParams = lp
            }
            it.card = game.trumpCard
            it.rotation = 90f
            it.visibility = if (trumpVisible && game.trumpCard != null) View.VISIBLE else View.GONE
        }
    }

    // ------------------------------------------------------------ giocate

    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        busy = true; armWatchdog()
        fromView.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(fromView)
        playAnimated(card, sx, sy)
    }

    override fun playBot() {
        if (destroyed || ending) return
        if (game.finished) { busy = true; endRound(); return }
        if (game.hands[1].isEmpty()) { recover(); return }
        val card = game.botChoose()
        val src: View = botHandView(card) ?: b.botHand
        val (sx, sy) = topLeftInOverlay(src)
        playAnimated(card, sx, sy)
    }

    override fun autoPlayMove() {
        val card = game.botChoose()
        val view = findHandCardView(card)
        val start = if (view != null) {
            view.visibility = View.INVISIBLE
            topLeftInOverlay(view)
        } else centerInOverlay(b.youHand)
        playAnimated(card, start.first, start.second)
    }

    private fun findHandCardView(card: Card): View? {
        for (i in 0 until b.youHand.childCount) {
            val ch = b.youHand.getChildAt(i)
            if (ch is CardView && ch.card == card) return ch
        }
        return null
    }

    /**
     * Vista da cui deve partire la carta calata dal Banco. Le carte del Banco sono coperte,
     * ma render() le crea nell'ordine della mano, quindi la posizione si ricava dall'indice.
     * Il salto delle carte appena pescate va ripetuto qui identico a render(), altrimenti
     * l'indice slitta di uno. Prima partiva sempre quella a sinistra e, se il Banco aveva
     * scelto un'altra carta, a sinistra ne spariva una e in tavola ne compariva un'altra.
     */
    private fun botHandView(card: Card): View? {
        val i = visibleHand(1).indexOf(card)
        return if (i in 0 until b.botHand.childCount) b.botHand.getChildAt(i) else null
    }

    /**
     * In gioco automatico l'animazione si salta del tutto e si passa subito alla mossa
     * successiva, sempre pero' attraverso l'Handler: cosi' le mosse non si annidano l'una
     * dentro l'altra sullo stack e i tocchi dell'utente continuano a essere raccolti.
     */
    private fun playAnimated(card: Card, sx: Float, sy: Float) {
        val completing = game.trick.size == 1
        val leaderCard = if (completing) game.trick[0] else null
        val resolve = {
            val winner = game.play(card)
            moveSeq++
            if (winner == -1) afterLead() else afterComplete(leaderCard!!, card, winner)
        }
        if (t.fast) {
            post(0) { resolve() }
            return
        }
        val (cx, cy) = centerInOverlay(b.trickRow)
        val temp = CardView(this); temp.card = card; temp.faceUp = true
        b.overlay.addView(temp, FrameLayout.LayoutParams(cardW, cardH))
        temp.x = sx; temp.y = sy
        temp.animate().x(cx - cardW / 2f).y(cy - cardH / 2f).setDuration(t.playDur).start()
        post(t.playDur + 20) {
            b.overlay.removeView(temp)
            resolve()
        }
    }

    private fun afterLead() {
        if (isBotTurn) {
            busy = true
            render()   // trickRow now shows the single leader card
            statusView.setText(R.string.bot_turn)
            post(t.trickPause) { playBot() }
        } else {
            busy = false
            render()
            clearStatus()
            maybeAutoPlay()
        }
    }

    private fun afterComplete(lead: Card, follow: Card, winner: Int) {
        // le carte pescate restano nascoste finche' l'animazione della presa non e' finita
        hideDrawn = true
        busy = true
        renderWith(listOf(lead, follow))   // keep BOTH cards visible during the pause
        statusView.setText(if (winner == 0) R.string.you_take else R.string.bot_take)
        armWatchdog()
        post(t.trickPause) {
            sweepTrick(winner) {
                hideDrawn = false
                when {
                    game.finished -> { render(); endRound() }
                    isBotTurn -> {
                        render()   // ora compaiono le carte pescate
                        statusView.setText(R.string.bot_turn)
                        post(t.trickPause) { playBot() }
                    }
                    // busy PRIMA di render(), come in Scopa: cosi' la schermata viene
                    // ridisegnata gia' nello stato "tocca a te"
                    else -> {
                        busy = false
                        render()
                        clearStatus()
                        maybeAutoPlay()
                    }
                }
            }
        }
    }

    /**
     * Le due carte della presa scivolano verso chi ha vinto la mano: in alto se ha preso
     * il Banco, in basso se hai preso tu. Rimpicciolendosi e sfumando danno l'idea della
     * carta che finisce nel mazzetto delle prese.
     */
    private fun sweepTrick(winner: Int, onDone: () -> Unit) {
        if (t.fast) { onDone(); return }
        val temps = ArrayList<CardView>()
        for (i in 0 until b.trickRow.childCount) {
            val child = b.trickRow.getChildAt(i) as? CardView ?: continue
            val (x, y) = topLeftInOverlay(child)
            val tmp = CardView(this)
            tmp.card = child.card; tmp.faceUp = true
            b.overlay.addView(tmp, FrameLayout.LayoutParams(cardW, cardH))
            tmp.x = x; tmp.y = y
            child.visibility = View.INVISIBLE
            temps.add(tmp)
        }
        if (temps.isEmpty()) { onDone(); return }

        val dest = if (winner == 0) b.youHand else b.botHand
        val (cx, cy) = centerInOverlay(dest)
        var last = 0L
        for ((i, tmp) in temps.withIndex()) {
            val delay = i * t.sweepStep
            tmp.animate().x(cx - cardW / 2f).y(cy - cardH / 2f)
                .scaleX(0.5f).scaleY(0.5f).alpha(0f)
                .setStartDelay(delay).setDuration(t.sweepDur).start()
            last = maxOf(last, delay + t.sweepDur)
        }
        post(last + 40) {
            for (tmp in temps) b.overlay.removeView(tmp)
            onDone()
        }
    }

    // --------------------------------------------------------- fine mano

    /**
     * La tabella del riepilogo: la stessa della Scopa con due righe invece di sette, perche'
     * qui la partita e' un numero solo e non una somma di quattro punti. Colonne, caratteri e
     * allineamento sono gli stessi, cosi' passando da un gioco all'altro il riepilogo sta
     * sempre allo stesso posto.
     */
    override fun buildResultView(over: Boolean): View {
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)

        val v = DialogResultBinding.inflate(layoutInflater)
        v.youHand.text = you.toString();                 v.botHand.text = bot.toString()
        v.youMatch.text = matchYou.toString();           v.botMatch.text = matchBot.toString()

        // Le due righe prendono il colore di chi e' avanti, ma su due conti diversi: la
        // Partita guarda questa mano, l'Incontro la serie. Possono risultare di colori
        // opposti, ed e' giusto cosi'.
        for (tv in listOf(v.lblHand, v.youHand, v.botHand)) tv.tintByScore(you, bot)
        for (tv in listOf(v.lblMatch, v.youMatch, v.botMatch)) tv.tintByScore(matchYou, matchBot)
        if (over) {
            v.txtWinner.text = if (matchYou > matchBot) getString(R.string.match_win_you)
                               else getString(R.string.match_win_bot)
            v.txtWinner.tintByOutcome(matchYou > matchBot)
            v.txtWinner.visibility = View.VISIBLE
        }
        return v.root
    }

    // ------------------------------------------- salvataggio (parte motore)

    override fun saveGame(w: SavedGame.Writer) = game.save(w)

    override fun loadGame(r: SavedGame.Reader) = game.load(r)

    override fun restoreLastDeal(): Boolean = game.restoreLastDeal()
}
