package com.zis.scopa

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.zis.scopa.databinding.ActivityTresetteBinding
import com.zis.scopa.databinding.DialogResultBinding

/**
 * Tresette in due con il tallone. Struttura identica a Briscola, con tre differenze che si
 * vedono a schermo:
 *
 *  - la mano e' di dieci carte invece che di tre, quindi le carte sono piu' piccole,
 *    sovrapposte e ordinate per seme;
 *  - rispondere al seme e' un obbligo, percio' le carte non giocabili sono spente e non
 *    rispondono al tocco;
 *  - la carta pescata si mostra all'avversario, e questo e' un passaggio di gioco vero, non
 *    un abbellimento: da li' passa meta' dell'informazione della partita.
 */
class TresetteActivity : AppCompatActivity() {

    private lateinit var b: ActivityTresetteBinding
    private val game = TresetteGame()
    private var busy = false
    private var youStartNext = true

    // incontro: si sommano i punti mano dopo mano fino al bersaglio (21 o 31)
    private var matchYou = 0
    private var matchBot = 0
    private var matchTarget = 21

    private val t = Timing()

    /** Carte in tavola: misura piena. */
    private val cardW get() = CardSize.width(resources)
    private val cardH get() = CardSize.height(resources)

    /** Carte in mano: ridotte, perche' sono dieci. */
    private val handW get() = CardSize.handWidth(resources)
    private val handH get() = CardSize.handHeight(resources)

    private val ui = Handler(Looper.getMainLooper())
    private var ending = false

    /**
     * Vero quando i punti della mano sono gia' stati sommati all'incontro e registrati.
     *
     * Serve solo al ripristino, ed e' l'unico dato che non si potrebbe ricavare guardando la
     * partita: a mano finita, lo stato del motore e' identico prima e dopo l'assegnazione dei
     * punti. Senza questo, riprendere una partita chiusa col riepilogo aperto rifarebbe i
     * conti una seconda volta, raddoppiando i punti dell'incontro e la vittoria registrata
     * nelle statistiche.
     */
    private var roundScored = false

    /** Vero da quando le carte sono in tavola: prima non c'e' niente da salvare. */
    private var started = false
    private var destroyed = false
    private var stopped = false
    private var openDialog: AlertDialog? = null
    private var autoPlay = false
    private var showBot = false

    /** Vero mentre scorre l'animazione della presa e della pescata: le carte nuove restano fuori. */
    private var hideDrawn = false

    /**
     * Le viste delle due mani, nello stesso ordine in cui sono disegnate. La mano del
     * giocatore sta su due file, quindi ricavare la vista da un indice richiederebbe conti
     * su riga e colonna che si disallineano al primo cambiamento: tenere l'elenco costa
     * niente e non puo' sbagliare.
     */
    private val youViews = ArrayList<CardView>()
    private val botViews = ArrayList<CardView>()

    private var moveSeq = 0
    private var watchdogSeq = -1

    // ---- rigioca le ultime carte: cosa serve per riportare indietro l'orologio ----
    /** Punteggi della partita prima che la mano appena finita venisse sommata. */
    private var matchBeforeEnd = Pair(0, 0)
    /** Esito registrato nelle statistiche a fine partita (null se la partita non e' finita). */
    private var recordedWin: Boolean? = null
    /** Valore di last_match_end prima di questa fine partita, per rimetterlo se si rigioca. */
    private var prevMatchEnd = 0L

    /**
     * Annulla la registrazione fatta a fine partita: la partita torna aperta, quindi
     * l'esito esce dalle statistiche e la pausa fra le partite non parte.
     */
    private fun undoMatchRecord(gameKey: String) {
        recordedWin?.let {
            Prefs.unrecordMatch(this, gameKey, it)
            Prefs.setLastMatchEnd(this, prevMatchEnd)
            recordedWin = null
        }
    }

    private val watchdog = Runnable {
        if (destroyed || ending) return@Runnable
        if (moveSeq != watchdogSeq) return@Runnable
        recover()
    }

    private fun armWatchdog() {
        ui.removeCallbacks(watchdog)
        if (destroyed || ending) return
        if (busy || game.finished || game.turn == 1) {
            watchdogSeq = moveSeq
            ui.postDelayed(watchdog, 4000)
        }
    }

    private fun recover() {
        if (destroyed || ending) return
        when {
            game.finished -> { busy = true; render(); endHand() }
            game.turn == 1 -> { busy = true; render(); post(t.think) { botPlay() } }
            busy -> { busy = false; render(); b.txtStatus.setText(R.string.your_turn); maybeAutoPlay() }
            else -> maybeAutoPlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTresetteBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)
        b.btnInfo.setOnClickListener { track(InfoDialog.show(this, R.string.info_title, R.string.rules_tresette)) }
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        t.fast = autoPlay
        t.drawShowMs = Prefs.drawShowSeconds(this) * 1000L
        CardView.setDeck(Prefs.deck(this))
        placeCards()
        // Se c'e' una partita lasciata a meta' si riprende quella, altrimenti se ne comincia una.
        if (!restoreState()) startMatch()
    }

    /**
     * Il tallone esce per meta' dal bordo sinistro e la mano del Banco per meta' da quello
     * alto: sono carte coperte, non serve vederle intere, e lo spazio guadagnato va al tavolo.
     */
    private fun placeCards() {
        (b.botHand.layoutParams as FrameLayout.LayoutParams).topMargin = if (showBot) 0 else -handH / 2
        b.botHand.requestLayout()
        (b.deckBox.layoutParams as FrameLayout.LayoutParams).marginStart = -cardW / 2
    }

    override fun onResume() {
        super.onResume()
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        t.fast = autoPlay
        t.drawShowMs = Prefs.drawShowSeconds(this) * 1000L
        CardView.setDeck(Prefs.deck(this))
        placeCards()
        if (stopped) {
            // Si torna da un onStop: hideDrawn va rimesso a posto, altrimenti le carte
            // appena pescate resterebbero fuori dalla mano per il resto della partita.
            stopped = false
            hideDrawn = false
            render()
            recover()
        } else {
            render()
            maybeAutoPlay()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        placeCards()
        render()
    }

    /**
     * Sospende la partita quando la schermata non e' piu' visibile: senza, il Banco
     * continuerebbe a giocare da solo in background.
     */
    override fun onStop() {
        stopped = true
        // Prima di tutto il resto: onStop e' l'ultimo momento garantito prima che Android
        // possa uccidere il processo, e lo stato qui e' ancora coerente.
        saveState()
        ui.removeCallbacksAndMessages(null)
        b.overlay.removeAllViews()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        // Uscire dal Menu o col tasto indietro e' una scelta: la partita si butta. Chiudere
        // l'app non passa di qui, quindi in quel caso il salvataggio resta.
        if (isFinishing) SavedGame.clear(this, SavedGame.TRESETTE)
        ui.removeCallbacksAndMessages(null)
        closeDialog()
        super.onDestroy()
    }

    // ---------------- salvataggio e ripristino ----------------

    private fun saveState() {
        if (!started) return
        val w = SavedGame.Writer()
        game.save(w)
        w.ints(listOf(matchTarget, matchYou, matchBot, if (youStartNext) 1 else 0))
        w.ints(listOf(matchBeforeEnd.first, matchBeforeEnd.second))
        w.int(when (recordedWin) { true -> 1; false -> 0; null -> -1 })
        w.long(prevMatchEnd)
        w.bool(roundScored)
        SavedGame.write(this, SavedGame.TRESETTE, w)
    }

    /**
     * Vero se c'era una partita da riprendere. Le sezioni si rileggono nello stesso ordine in
     * cui saveState le ha scritte; se il testo non torna si butta il salvataggio e si
     * ricomincia, che e' meglio che ripartire da uno stato a meta'.
     */
    private fun restoreState(): Boolean {
        val r = SavedGame.read(this, SavedGame.TRESETTE) ?: return false
        try {
            game.load(r)
            val m = r.ints()
            matchTarget = m[0]; matchYou = m[1]; matchBot = m[2]; youStartNext = m[3] == 1
            val mb = r.ints(); matchBeforeEnd = Pair(mb[0], mb[1])
            recordedWin = when (r.int()) { 1 -> true; 0 -> false; else -> null }
            prevMatchEnd = r.long()
            roundScored = r.bool()
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.TRESETTE)
            return false
        }
        started = true
        moveSeq++
        hideDrawn = false
        render()
        if (roundScored) {
            // La mano era gia' chiusa e i punti gia' assegnati: si rimette solo il riepilogo.
            resumeRoundDialog()
        } else {
            // busy a true fa prendere a recover() il ramo che ripulisce la mossa interrotta
            // e restituisce il turno, esattamente come al ritorno da un onStop.
            busy = true
            recover()
        }
        return true
    }

    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    private fun track(d: AlertDialog?): AlertDialog? {
        openDialog = d
        return d
    }

    private fun closeDialog() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
    }

    // ---------------- avvio ----------------

    private fun startMatch() {
        matchTarget = Prefs.tresetteTarget(this)
        matchYou = 0
        matchBot = 0
        youStartNext = true
        val wait = Prefs.pauseRemaining(this)
        if (wait > 0) {
            // Nessuna mano in corso finche' il dialogo resta aperto. Il removeCallbacks non e'
            // ridondante: era l'unico punto in cui questa schermata si comportava diversamente
            // da Scopa e Briscola, e "i tre giochi fanno la stessa cosa" e' proprio l'invariante
            // su cui si regge tutta questa parte del codice.
            ending = true
            busy = true
            ui.removeCallbacks(watchdog)
            track(PauseDialog.show(this, wait, onReady = { beginMatch() }, onLeave = { finish() }))
        } else {
            beginMatch()
        }
    }

    private fun beginMatch() {
        ending = false
        startHand()
    }

    private fun startHand() {
        game.newGame(youStartNext)
        youStartNext = !youStartNext
        ending = false
        roundScored = false
        started = true
        hideDrawn = false
        busy = true
        render()
        moveSeq++
        b.txtStatus.setText(if (game.turn == 1) R.string.bot_turn else R.string.your_turn)
        dealAnimation()
        post(t.deal) {
            if (destroyed || ending) return@post
            if (game.turn == 1) {
                botPlay()
            } else {
                busy = false
                render()
                b.txtStatus.setText(R.string.your_turn)
                maybeAutoPlay()
            }
        }
    }

    private fun maybeAutoPlay() {
        if (!autoPlay || destroyed || ending) return
        if (busy || game.finished || game.turn != 0) return
        if (game.hands[0].isEmpty()) return
        busy = true
        armWatchdog()
        post(t.think) {
            if (destroyed || ending || game.finished || game.turn != 0) return@post
            if (game.hands[0].isEmpty()) { recover(); return@post }
            val card = game.botChoose()
            val v = youHandView(card)
            val (sx, sy) = topLeftInOverlay(v ?: b.youHand)
            v?.visibility = View.INVISIBLE
            playAnimated(card, sx, sy)
        }
    }

    // ---------------- disegno ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
    // translation, alpha, scale). Prima lo azzerava la ricostruzione, ora lo azzera resetCard.
    // L'alpha in particolare va rimessa a mano DOPO resetCard, perche' qui vale 0,35 sulle
    // carte non giocabili.

    /** Rimette una vista riusata nello stato "appena creata". */
    private fun resetCard(cv: CardView) {
        cv.animate().cancel()
        cv.visibility = View.VISIBLE
        cv.alpha = 1f
        cv.translationX = 0f
        cv.translationY = 0f
        cv.scaleX = 1f
        cv.scaleY = 1f
        cv.rotation = 0f
    }

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

    // Il tallone: creato una volta sola, poi cambia solo il numero sopra.
    private var deckBack: CardView? = null
    private var deckCount: TextView? = null

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
        val pending = if (hideDrawn) game.lastDrawn.count { it != null } else 0
        val left = game.deck.size + pending
        deckBack?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; it.layoutParams = lp
            }
        }
        deckCount?.let {
            it.text = left.toString()
            // il numero da solo non dice niente a TalkBack
            it.contentDescription =
                if (left > 0) getString(R.string.cd_deck, left) else getString(R.string.cd_deck_empty)
        }
        (deckBack?.parent as? View)?.visibility = if (left > 0) View.VISIBLE else View.GONE
    }

    private fun render(trickOverride: List<Card>? = null) {
        b.txtMatch.text = getString(R.string.match_line, matchYou, matchBot)
        b.txtMatch.tintByOutcome(matchYou > matchBot)
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

    private fun dealAnimation() {
        if (t.fast) return
        b.root.doOnLayout {
            if (destroyed) return@doOnLayout
            if (b.deckBox.width == 0) return@doOnLayout
            // dagli elenchi e non dai figli: la mano del giocatore e' annidata in due file
            val views = ArrayList<View>(botViews + youViews)
            val (dx, dy) = topLeftInOverlay(b.deckBox)
            var delay = 0L
            for (v in views) {
                if (v.width == 0) continue
                val (tx, ty) = topLeftInOverlay(v)
                val keep = v.alpha
                v.alpha = 0f
                v.translationX = dx - tx
                v.translationY = dy - ty
                v.animate().translationX(0f).translationY(0f).alpha(keep)
                    .setStartDelay(delay).setDuration(t.dealDur).start()
                delay += t.dealStep
            }
        }
    }

    // ---------------- giocate ----------------

    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        if (!game.isLegal(0, card)) return       // rete di sicurezza: l'obbligo di seme vale sempre
        busy = true; armWatchdog()
        fromView.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(fromView)
        playAnimated(card, sx, sy)
    }

    private fun botPlay() {
        if (destroyed || ending) return
        if (game.finished) { busy = true; endHand(); return }
        if (game.hands[1].isEmpty()) { recover(); return }
        val card = game.botChoose()
        val src: View = botHandView(card) ?: b.botHand
        val (sx, sy) = topLeftInOverlay(src)
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
        if (game.turn == 1) {
            busy = true
            render()
            b.txtStatus.setText(R.string.bot_turn)
            post(t.trickPause) { botPlay() }
        } else {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
            maybeAutoPlay()
        }
    }

    private fun afterComplete(lead: Card, follow: Card, winner: Int) {
        hideDrawn = true
        busy = true
        render(listOf(lead, follow))
        b.txtStatus.setText(if (winner == 0) R.string.you_take else R.string.bot_take)
        armWatchdog()
        post(t.trickPause) {
            sweepTrick(winner) {
                showDraw {
                    hideDrawn = false
                    when {
                        game.finished -> { render(); endHand() }
                        game.turn == 1 -> {
                            render()
                            b.txtStatus.setText(R.string.bot_turn)
                            post(t.trickPause) { botPlay() }
                        }
                        else -> {
                            busy = false
                            render()
                            b.txtStatus.setText(R.string.your_turn)
                            maybeAutoPlay()
                        }
                    }
                }
            }
        }
    }

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

        val dest = if (winner == 0) b.youHandBox else b.botHandBox
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

    // ---------------- fine mano ----------------


    /**
     * Riporta la mano all'inizio delle ultime carte (vedi la fotografia nel motore), rimette
     * i punteggi della partita com'erano e fa ripartire il gioco da li'. Il Banco non tira
     * a caso: le sue carte saranno le stesse, cambia solo quello che decidi tu.
     */
    private fun replayLastDeal() {
        if (!game.restoreLastDeal()) return
        matchYou = matchBeforeEnd.first
        matchBot = matchBeforeEnd.second
        undoMatchRecord(Prefs.GAME_TRESETTE)
        ending = false
        roundScored = false
        moveSeq++
        busy = true
        b.txtStatus.setText(if (game.turn == 1) R.string.bot_turn else R.string.your_turn)
        // recover() legge lo stato reale: fa giocare il Banco se tocca a lui, altrimenti
        // libera la mano per la tua giocata
        recover()
    }

    /** In parita' sul traguardo non si assegna l'incontro: si gioca un'altra mano. */
    private fun matchOver(): Boolean =
        (matchYou >= matchTarget || matchBot >= matchTarget) && matchYou != matchBot

    /**
     * Fine mano: assegna i punti, li registra se l'incontro e' finito, e mostra il riepilogo.
     *
     * L'assegnazione e la finestra sono separate perche' il ripristino ha bisogno solo della
     * seconda: riprendendo una mano chiusa col riepilogo aperto, i punti sono gia' stati dati
     * e rifarli significherebbe raddoppiarli.
     */
    private fun endHand() {
        if (ending || destroyed || isFinishing) return
        ending = true
        ui.removeCallbacks(watchdog)
        busy = true

        matchBeforeEnd = Pair(matchYou, matchBot)
        matchYou += game.scoreFor(0)
        matchBot += game.scoreFor(1)

        val over = matchOver()
        recordedWin = null
        if (over) {
            prevMatchEnd = Prefs.lastMatchEnd(this)
            Prefs.markMatchEnded(this)
            recordedWin = matchYou > matchBot
            Prefs.recordMatch(this, Prefs.GAME_TRESETTE, matchYou > matchBot)
        }
        roundScored = true
        render()
        showRoundDialog(over)
    }

    /** Rimette il riepilogo dopo un ripristino, senza toccare il punteggio. */
    private fun resumeRoundDialog() {
        ending = true
        busy = true
        render()
        showRoundDialog(matchOver())
    }

    /**
     * Il riepilogo della mano. I punti si rileggono dal motore invece di essere passati come
     * parametri: a mano finita lo stato non cambia piu', quindi il conto e' lo stesso sia che
     * si arrivi da endHand sia da un ripristino, e non c'e' un secondo posto da tenere
     * allineato.
     */
    private fun showRoundDialog(over: Boolean) {
        if (destroyed || isFinishing) return
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)

        // Stessa impaginazione della Scopa e della Briscola. Il totale della mano e' sempre
        // 11, quindi qui il pareggio di mano non esiste e bastano i due casi.
        val v = DialogResultBinding.inflate(layoutInflater)
        v.txtHand.text = if (you > bot) getString(R.string.hand_you, you, bot)
                         else getString(R.string.hand_bot, bot, you)
        v.txtHand.tintByOutcome(you > bot)
        v.txtMatch.text = getString(R.string.match_line, matchYou, matchBot)
        v.txtMatch.tintByOutcome(matchYou > matchBot)
        if (over) {
            v.txtWinner.text = if (matchYou > matchBot) getString(R.string.match_win_you)
                               else getString(R.string.match_win_bot)
            v.txtWinner.tintByOutcome(matchYou > matchBot)
            v.txtWinner.visibility = View.VISIBLE
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.round_over)
            .setView(v.root)
            .setCancelable(false)
            .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        if (over) {
            builder.setPositiveButton(R.string.new_match) { _, _ -> startMatch() }
        } else {
            builder.setPositiveButton(R.string.continue_match) { _, _ -> startHand() }
        }
        if (game.lastDealState != null) {
            builder.setNeutralButton(getString(R.string.replay_last_deal, game.lastDealCards)) { _, _ -> replayLastDeal() }
        }
        track(builder.show())
    }

    // ---------------- coordinate ----------------

    private fun topLeftInOverlay(v: View): Pair<Float, Float> {
        val a = IntArray(2); v.getLocationInWindow(a)
        val o = IntArray(2); b.overlay.getLocationInWindow(o)
        return Pair((a[0] - o[0]).toFloat(), (a[1] - o[1]).toFloat())
    }

    private fun centerInOverlay(v: View): Pair<Float, Float> {
        val (x, y) = topLeftInOverlay(v)
        return Pair(x + v.width / 2f, y + v.height / 2f)
    }
}
