package com.zis.scopa

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.zis.scopa.databinding.ActivityTresetteBinding
import com.zis.scopa.databinding.DialogResultBinding

/**
 * Tresette in due con il tallone.
 *
 * Il ciclo di vita, il salvataggio, il watchdog, la fine mano e la cornice del riepilogo
 * stanno in [BotGameActivity], insieme a Scopa e Briscola. Qui resta quello che e' di questo
 * gioco, che sono le tre differenze che si vedono a schermo:
 *
 *  - la mano e' di dieci carte invece che di tre, quindi le carte sono piu' piccole, su due
 *    file per il giocatore e a ventaglio per il Banco, e ordinate per seme;
 *  - rispondere al seme e' un obbligo, percio' le carte non giocabili sono spente e non
 *    rispondono al tocco;
 *  - la carta pescata si mostra all'avversario, e questo e' un passaggio di gioco vero, non
 *    un abbellimento: da li' passa meta' dell'informazione della partita.
 *
 * L'incontro si conta a punti come nella Scopa, quindi [matchOver] non si ridefinisce: il
 * predefinito della base, che in parita' sul traguardo non assegna niente, e' gia' la regola
 * del Tresette.
 */
class TresetteActivity : BotGameActivity() {

    private lateinit var b: ActivityTresetteBinding
    private val game = TresetteGame()

    /** Vero mentre scorre l'animazione della presa e della pescata: le carte nuove restano fuori. */
    private var hideDrawn = false

    /** Carte in mano: ridotte, perche' sono dieci. In tavola restano quelle piene della base. */
    private val handW get() = CardSize.handWidth(resources)
    private val handH get() = CardSize.handHeight(resources)

    /**
     * Le viste delle due mani, nello stesso ordine in cui sono disegnate. La mano del
     * giocatore sta su due file, quindi ricavare la vista da un indice richiederebbe conti
     * su riga e colonna che si disallineano al primo cambiamento: tenere l'elenco costa
     * niente e non puo' sbagliare.
     */
    private val youViews = ArrayList<CardView>()
    private val botViews = ArrayList<CardView>()

    // ---- quello che la base ha bisogno di sapere ----
    override val rootView: View get() = b.root
    override val overlay: FrameLayout get() = b.overlay
    override val statusView: TextView get() = b.txtStatus
    override val saveKey: String get() = SavedGame.TRESETTE
    override val statsKey: String get() = Prefs.GAME_TRESETTE
    override val rulesText: Int get() = R.string.rules_tresette

    override val gameFinished: Boolean get() = game.finished
    override val isBotTurn: Boolean get() = game.turn == 1
    override val youHandEmpty: Boolean get() = game.hands[0].isEmpty()
    override val hasLastDeal: Boolean get() = game.lastDealState != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTresetteBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Dopo setContentView: rootView e overlay sono getter su b, che e' lateinit.
        setupCommon(b.btnInfo)
        // Se c'e' una partita lasciata a meta' si riprende quella, altrimenti se ne comincia una.
        if (!restoreState()) startMatch()
    }

    /**
     * Il tallone esce per meta' dal bordo sinistro e la mano del Banco per meta' da quello
     * alto: sono carte coperte, non serve vederle intere, e lo spazio guadagnato va al tavolo.
     */
    override fun placeCards() {
        // Come in Scopa e Briscola: i margini si riassegnano, non si modificano in posto.
        // setLayoutParams chiama requestLayout(), mutare i campi dell'oggetto che la vista
        // sta gia' usando no - e appoggiarsi al render() che segue vuol dire dipendere da
        // un ordine di chiamate scritto in un altro metodo.
        val bot = b.botHand.layoutParams as FrameLayout.LayoutParams
        bot.topMargin = if (showBot) 0 else -handH / 2
        b.botHand.layoutParams = bot
        val deck = b.deckBox.layoutParams as FrameLayout.LayoutParams
        deck.marginStart = -cardW / 2
        b.deckBox.layoutParams = deck
    }

    /**
     * Se ci si ferma a meta' dell'animazione della presa o della pescata, `hideDrawn`
     * resterebbe acceso e le carte appena pescate resterebbero fuori dalla mano per il resto
     * della partita.
     */
    override fun clearAnimationState() {
        hideDrawn = false
    }

    /** Il tempo di esposizione della carta pescata e' un'impostazione solo di questo gioco. */
    override fun readSettings() {
        super.readSettings()
        t.drawShowMs = Prefs.drawShowSeconds(this) * 1000L
    }

    // ---------------------------------------------------------- l'incontro

    override fun beginMatch() {
        matchTarget = Prefs.tresetteTarget(this)
        matchYou = 0
        matchBot = 0
        youStartNext = true
        ending = false
        startRound()
    }

    override fun startRound() {
        game.newGame(youStartNext)
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

    /** Nel Tresette l'incontro si somma mano dopo mano fino al bersaglio (21 o 31 punti). */
    override fun awardHand() {
        matchYou += game.scoreFor(0)
        matchBot += game.scoreFor(1)
    }

    // ------------------------------------------------------------- disegno

    /** Larghezza utile in pixel, al netto dei bordi della schermata. */
    private val usableW: Int
        get() = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt() - dp(24)

    /**
     * Quanto avanza da una carta all'altra nel ventaglio del Banco.
     *
     * Sono dorsi tutti uguali, quindi possono sovrapporsi quanto serve senza perdere niente.
     * Su uno schermo largo il passo arriva alla larghezza piena e le carte si separano.
     */
    private fun fanStep(n: Int): Int {
        if (n <= 1) return handW + dp(3)
        return minOf(handW + dp(3), (usableW - handW) / (n - 1))
    }

    /** La mano si tiene ordinata per seme e, dentro il seme, dalla carta piu' forte. */
    private fun sorted(cards: List<Card>): List<Card> =
        cards.sortedWith(compareBy({ it.suit }, { -game.strength(it.value) }))

    /** Le carte della mano di [p] nell'ordine in cui compaiono a schermo. */
    private fun visibleHand(p: Int): List<Card> =
        sorted(game.hands[p]).filter { !(hideDrawn && it == game.lastDrawn[p]) }

    // ---------- riuso delle viste ----------
    //
    // Prima render() faceva removeAllViews() e ricostruiva tutto: qui sono fino a venticinque
    // CardView (dieci in mano, dieci al Banco, due in tavola, il tallone) buttate e riallocate
    // a ogni chiamata, e render() viene chiamata piu' volte per presa. CardView era gia'
    // scritta per essere riusata, ma nessuno la riusava.
    //
    // L'unica attenzione: le animazioni lasciano stato sulla vista (visibility a INVISIBLE,
    // translation, alpha, scale). Prima lo azzerava la ricostruzione, ora lo azzera resetCard,
    // che sta nella base. L'alpha in particolare va rimessa a mano DOPO resetCard, perche'
    // qui vale 0,35 sulle carte non giocabili.

    /** Misure e margini di una carta riusata. */
    private fun sizeCard(cv: CardView, w: Int, h: Int, side: Int, top: Int, startOverride: Int? = null) {
        val lp = cv.layoutParams as LinearLayout.LayoutParams
        lp.width = w
        lp.height = h
        lp.marginStart = startOverride ?: side
        lp.marginEnd = side
        lp.topMargin = top
        cv.layoutParams = lp
    }

    /** Aggiunge o toglie carte in coda finche' il contenitore non ne ha [n]. */
    private fun fitCards(row: LinearLayout, n: Int, w: Int, h: Int) {
        while (row.childCount > n) row.removeViewAt(row.childCount - 1)
        while (row.childCount < n) row.addView(CardView(this), LinearLayout.LayoutParams(w, h))
    }

    /** Ventaglio del Banco: una fila sola di dorsi sovrapposti. */
    private fun addBotFan() {
        val cards = visibleHand(1)
        val step = fanStep(cards.size)
        fitCards(b.botHand, cards.size, handW, handH)
        botViews.clear()
        for (i in cards.indices) {
            val cv = b.botHand.getChildAt(i) as CardView
            // le carte si accavallano portando indietro il margine iniziale di ognuna
            sizeCard(cv, handW, handH, side = 0, top = 0, startOverride = if (i > 0) step - handW else 0)
            resetCard(cv)
            cv.card = if (showBot) cards[i] else null
            cv.faceUp = showBot
            botViews.add(cv)
        }
    }

    /**
     * Mano del giocatore su due file da cinque, senza sovrapposizioni: ogni carta si vede
     * per intero. Le file si tengono pari (dieci carte fanno 5 e 5, nove fanno 5 e 4) e da
     * cinque carte in giu' si passa a una fila sola, cosi' non resta una riga vuota a
     * mangiarsi lo spazio del tavolo.
     */
    private fun addYouHand(legal: Set<Card>) {
        val cards = visibleHand(0)
        val perRow = if (cards.size > 5) (cards.size + 1) / 2 else cards.size
        val rows = if (cards.isEmpty()) 0 else (cards.size + perRow - 1) / perRow

        while (b.youHand.childCount > rows) b.youHand.removeViewAt(b.youHand.childCount - 1)
        while (b.youHand.childCount < rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.gravity = Gravity.CENTER_HORIZONTAL
            b.youHand.addView(row, rowLp)
        }

        youViews.clear()
        var i = 0
        for (r in 0 until rows) {
            val row = b.youHand.getChildAt(r) as LinearLayout
            val n = minOf(perRow, cards.size - i)
            fitCards(row, n, handW, handH)
            for (k in 0 until n) {
                val cv = row.getChildAt(k) as CardView
                sizeCard(cv, handW, handH, side = dp(2), top = dp(2))
                resetCard(cv)
                val c = cards[i]
                cv.card = c
                cv.faceUp = true
                val giocabile = c in legal
                // playable non cambia il disegno: cambia quello che TalkBack legge, perche'
                // chi non vede l'alpha non ha altro modo di sapere che la carta e' fuori gioco
                cv.playable = giocabile
                if (giocabile) {
                    cv.setOnClickListener { onPlayerCard(c, cv) }
                } else {
                    // obbligo di rispondere al seme: la carta si vede ma non si puo' giocare
                    cv.alpha = 0.35f
                    cv.setOnClickListener(null)
                    cv.isClickable = false
                }
                youViews.add(cv)
                i++
            }
        }
    }

    /** Carte della presa in corso, al centro del tavolo. */
    private fun renderTrick(cards: List<Card>) {
        fitCards(b.trickRow, cards.size, cardW, cardH)
        for (i in cards.indices) {
            val cv = b.trickRow.getChildAt(i) as CardView
            sizeCard(cv, cardW, cardH, side = dp(3), top = 0)
            resetCard(cv)
            cv.card = cards[i]
            cv.faceUp = true
        }
    }

    /**
     * Il tallone col numero delle carte rimaste. Il disegno del mazzetto e del contatore sta
     * nella base, che lo condivide con la Briscola; qui resta il conto.
     */
    private fun renderDeck() {
        // finche' le carte pescate restano nascoste, il contatore non deve calare in anticipo
        val pending = if (hideDrawn) game.lastDrawn.count { it != null } else 0
        val left = game.deck.size + pending
        // Qui la soglia e' zero e non uno: sotto al tallone non c'e' nessuna briscola
        // coricata, quindi quando il mazzo e' vuoto non resta niente da mostrare.
        renderDeckBox(b.deckBox, left, pileVisible = left > 0)
    }

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
        b.botScore.text = getString(R.string.bot_points, formatThirds(game.thirdsFor(1)))
        b.youScore.text = getString(R.string.you_points, formatThirds(game.thirdsFor(0)))

        addBotFan()

        // Le carte si spengono solo quando tocca davvero a te rispondere: mentre gioca il
        // Banco la mano resta com'e', altrimenti lampeggerebbe a ogni presa.
        val legal: Set<Card> =
            if (!busy && !game.finished && game.turn == 0) game.legalMoves(0).toSet() else game.hands[0].toSet()
        addYouHand(legal)

        renderDeck()
        renderTrick(trickOverride ?: game.trick)

        armWatchdog()
    }

    /** "3", "3 e 1/3", "3 e 2/3": i terzi si vedono, ma il punteggio che conta e' quello intero. */
    private fun formatThirds(thirds: Int): String {
        val whole = thirds / 3
        return when (thirds % 3) {
            1 -> getString(R.string.tre_points_third, whole)
            2 -> getString(R.string.tre_points_twothirds, whole)
            else -> whole.toString()
        }
    }

    /** Le venti carte partono dal tallone e raggiungono la loro mano. */
    private fun dealAnimation() {
        // dagli elenchi e non dai figli: la mano del giocatore e' annidata in due file
        dealFrom(b.deckBox) { ArrayList<View>(botViews + youViews) }
    }

    // ------------------------------------------------------------ giocate

    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        if (!game.isLegal(0, card)) return       // rete di sicurezza: l'obbligo di seme vale sempre
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
        val v = youHandView(card)
        val (sx, sy) = topLeftInOverlay(v ?: b.youHand)
        v?.visibility = View.INVISIBLE
        playAnimated(card, sx, sy)
    }

    /** Vista da cui parte la carta calata: l'elenco segue l'ordine in cui e' disegnata la mano. */
    private fun botHandView(card: Card): View? = botViews.getOrNull(visibleHand(1).indexOf(card))

    private fun youHandView(card: Card): View? = youViews.getOrNull(visibleHand(0).indexOf(card))

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
        // la carta parte piccola come in mano e cresce arrivando in tavola
        temp.scaleX = handW.toFloat() / cardW; temp.scaleY = temp.scaleX
        temp.animate().x(cx - cardW / 2f).y(cy - cardH / 2f).scaleX(1f).scaleY(1f)
            .setDuration(t.playDur).start()
        post(t.playDur + 20) {
            b.overlay.removeView(temp)
            resolve()
        }
    }

    private fun afterLead() {
        if (isBotTurn) {
            busy = true
            render()
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
        hideDrawn = true
        busy = true
        renderWith(listOf(lead, follow))
        statusView.setText(if (winner == 0) R.string.you_take else R.string.bot_take)
        armWatchdog()
        post(t.trickPause) {
            sweepTrick(b.trickRow, if (winner == 0) b.youHandBox else b.botHandBox) {
                showDraw {
                    hideDrawn = false
                    when {
                        game.finished -> { render(); endRound() }
                        isBotTurn -> {
                            render()
                            statusView.setText(R.string.bot_turn)
                            post(t.trickPause) { playBot() }
                        }
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
    }

    /**
     * Le due carte pescate si mostrano scoperte in mezzo al tavolo prima di entrare nelle
     * mani. Non e' un abbellimento: la regola vuole che la carta pescata sia vista anche
     * dall'avversario, ed e' cosi' che il giocatore umano puo' tenere il conto come lo tiene
     * il Banco. Restano ferme mezzo secondo, quel tanto che basta per leggerle.
     */
    private fun showDraw(onDone: () -> Unit) {
        val drawn = listOf(0, 1).mapNotNull { p -> game.lastDrawn[p]?.let { p to it } }
        if (t.fast || drawn.isEmpty()) { onDone(); return }

        val (dx, dy) = topLeftInOverlay(b.deckBox)
        val (cx, cy) = centerInOverlay(b.trickRow)
        val temps = ArrayList<Pair<Int, CardView>>()
        val span = cardW + dp(10)
        for ((k, pc) in drawn.withIndex()) {
            val (owner, card) = pc
            val tmp = CardView(this); tmp.card = card; tmp.faceUp = true
            b.overlay.addView(tmp, FrameLayout.LayoutParams(cardW, cardH))
            tmp.x = dx; tmp.y = dy
            val targetX = cx - cardW / 2f + (k - (drawn.size - 1) / 2f) * span
            tmp.animate().x(targetX).y(cy - cardH / 2f).setDuration(t.playDur).start()
            temps.add(owner to tmp)
        }
        post(t.playDur + t.drawShow) {
            var last = 0L
            for ((owner, tmp) in temps) {
                val dest = if (owner == 0) b.youHandBox else b.botHandBox
                val (hx, hy) = centerInOverlay(dest)
                tmp.animate().x(hx - cardW / 2f).y(hy - cardH / 2f)
                    .scaleX(0.5f).scaleY(0.5f).alpha(0f)
                    .setDuration(t.sweepDur).start()
                last = maxOf(last, t.sweepDur)
            }
            post(last + 40) {
                for ((_, tmp) in temps) b.overlay.removeView(tmp)
                onDone()
            }
        }
    }

    // --------------------------------------------------------- fine mano

    /**
     * La tabella del riepilogo: la stessa della Scopa e della Briscola, con partita e
     * incontro incolonnati, cosi' passando da un gioco all'altro il riepilogo sta sempre
     * allo stesso posto.
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
