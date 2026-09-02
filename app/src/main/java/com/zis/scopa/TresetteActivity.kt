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
        startMatch()
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
        ui.removeCallbacksAndMessages(null)
        b.overlay.removeAllViews()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        ui.removeCallbacksAndMessages(null)
        closeDialog()
        super.onDestroy()
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
            ending = true
            busy = true
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

    private fun makeCard(card: Card?, faceUp: Boolean, w: Int, h: Int): CardView {
        val cv = CardView(this)
        cv.card = card
        cv.faceUp = faceUp
        cv.layoutParams = LinearLayout.LayoutParams(w, h)
        return cv
    }

    /** Ventaglio del Banco: una fila sola di dorsi sovrapposti. */
    private fun addBotFan() {
        b.botHand.removeAllViews()
        botViews.clear()
        val cards = visibleHand(1)
        val step = fanStep(cards.size)
        for ((i, c) in cards.withIndex()) {
            val cv = makeCard(if (showBot) c else null, showBot, handW, handH)
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            // le carte si accavallano portando indietro il margine iniziale di ognuna
            if (i > 0) lp.marginStart = step - handW
            b.botHand.addView(cv, lp)
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
        b.youHand.removeAllViews()
        youViews.clear()
        val cards = visibleHand(0)
        val perRow = if (cards.size > 5) (cards.size + 1) / 2 else cards.size
        var i = 0
        while (i < cards.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.gravity = Gravity.CENTER_HORIZONTAL
            b.youHand.addView(row, rowLp)
            var k = 0
            while (k < perRow && i < cards.size) {
                val c = cards[i]
                val cv = makeCard(c, true, handW, handH)
                val lp = cv.layoutParams as LinearLayout.LayoutParams
                lp.marginStart = dp(2); lp.marginEnd = dp(2); lp.topMargin = dp(2)
                row.addView(cv, lp)
                youViews.add(cv)
                if (c in legal) {
                    cv.alpha = 1f
                    cv.setOnClickListener { onPlayerCard(c, cv) }
                } else {
                    // obbligo di rispondere al seme: la carta si vede ma non si puo' giocare
                    cv.alpha = 0.35f
                }
                i++; k++
            }
        }
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

        // tallone
        b.deckBox.removeAllViews()
        val pending = if (hideDrawn) game.lastDrawn.count { it != null } else 0
        val left = game.deck.size + pending
        if (left > 0) {
            val fl = FrameLayout(this)
            fl.addView(CardView(this).apply { faceUp = false }, FrameLayout.LayoutParams(cardW, cardH))
            val tv = TextView(this)
            tv.text = left.toString()
            tv.setTextColor(getColor(R.color.silver)); tv.textSize = 14f
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setBackgroundColor(Color.argb(0xB0, 0, 0, 0))
            tv.setPadding(dp(5), dp(1), dp(5), dp(1))
            val tp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            tp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            tp.marginEnd = dp(6)
            fl.addView(tv, tp)
            b.deckBox.addView(fl)
        }

        b.trickRow.removeAllViews()
        for (c in (trickOverride ?: game.trick)) {
            val cv = makeCard(c, true, cardW, cardH)
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.marginStart = dp(3); lp.marginEnd = dp(3)
            b.trickRow.addView(cv, lp)
        }

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

    private fun endHand() {
        if (ending || destroyed || isFinishing) return
        ending = true
        ui.removeCallbacks(watchdog)
        busy = true

        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)
        matchYou += you
        matchBot += bot
        render()

        val over = (matchYou >= matchTarget || matchBot >= matchTarget) && matchYou != matchBot
        if (over) Prefs.markMatchEnded(this)

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
