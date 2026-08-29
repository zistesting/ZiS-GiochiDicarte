package com.zis.scopa

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.zis.scopa.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {

    private lateinit var b: ActivityGameBinding
    private val game = ScopaGame()
    private var busy = false

    // match (multi-round) state
    private var target = 11
    private var matchYou = 0
    private var matchBot = 0
    private var youStartNext = true
    private var autoPlay = false

    private val ui = Handler(Looper.getMainLooper())
    private var roundEnding = false
    private var destroyed = false

    /**
     * Contatore delle mosse completate. Il watchdog lo confronta con il valore che aveva
     * quando e' stato armato: se il gioco e' andato avanti da solo non fa nulla, se invece
     * e' fermo interviene. Senza questo confronto il watchdog rischiava di far giocare il
     * Banco una seconda volta mentre una mossa era ancora in corso.
     */
    private var moveSeq = 0
    private var watchdogSeq = -1

    private val watchdog = Runnable {
        if (destroyed || roundEnding) return@Runnable
        if (moveSeq != watchdogSeq) return@Runnable   // il gioco si e' mosso: nulla da fare
        recover()
    }

    /** Armato solo quando il gioco deve muoversi da solo: durante una giocata,
     *  quando tocca al Banco o a fine mano. Se tocca all'utente non serve. */
    private fun armWatchdog() {
        ui.removeCallbacks(watchdog)
        if (destroyed || roundEnding) return
        if (busy || game.finished || game.turn == 1) {
            watchdogSeq = moveSeq
            ui.postDelayed(watchdog, 3000)
        }
    }

    /**
     * Riporta il gioco in moto guardando lo stato reale della partita, qualunque cosa sia
     * andata storta. Prima il blocco catch metteva busy = false lasciando il turno al Banco
     * senza che nessuno lo facesse giocare: la partita restava ferma per sempre e i tocchi
     * dell'utente venivano ignorati perche' game.turn era 1.
     */
    private fun recover() {
        if (destroyed || roundEnding) return
        when {
            game.finished -> { busy = true; render(); endRound() }
            game.turn == 1 -> { busy = true; render(); post(250) { botTurn() } }
            busy -> {
                busy = false
                render()
                b.txtStatus.setText(R.string.your_turn)
                maybeAutoPlay()
            }
            else -> maybeAutoPlay()
        }
    }

    // Cards scale with screen width so they aren't tiny on tablets (min 76dp on phones).
    private val cardWDp: Float
        get() = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * 0.21f).coerceIn(76f, 200f)
    private val cardW get() = (cardWDp * resources.displayMetrics.density).toInt()
    // Proporzione reale delle immagini (560x1024 = 1.829).
    private val cardRatio = 1.829f
    private val cardH get() = (cardWDp * cardRatio * resources.displayMetrics.density).toInt()
    private val centerW get() = cardW
    private val centerH get() = cardH

    /** Quanto resta ferma a schermo la carta appena calata, prima che il tavolo si aggiorni. */
    private val holdMs: Long get() = if (autoPlay) 300L else 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGameBinding.inflate(layoutInflater)
        setContentView(b.root)
        placeCards()
        startMatch()
    }

    override fun onResume() {
        super.onResume()
        autoPlay = Prefs.autoPlay(this)
        maybeAutoPlay()
    }

    /**
     * Posizioni fisse, calcolate una volta sola perche' dipendono solo dalla larghezza
     * dello schermo: mano del Banco per meta' fuori dal bordo alto, mazzo per meta' fuori
     * dal bordo sinistro, griglia del tavolo rientrata cosi' non finisce sopra al mazzo.
     */
    private fun placeCards() {
        (b.botHand.layoutParams as LinearLayout.LayoutParams).topMargin = -cardH / 2
        (b.deckBox.layoutParams as FrameLayout.LayoutParams).marginStart = -cardW / 2
        b.centerBox.setPaddingRelative(cardW / 2, 0, 0, 0)   // Relative: rispetta supportsRtl
    }

    override fun onDestroy() {
        destroyed = true
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** postDelayed sicuro: il blocco non viene eseguito se l'activity nel frattempo e' morta. */
    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    private fun startMatch() {
        val wait = Prefs.pauseRemaining(this)
        if (wait > 0) {
            busy = true
            PauseDialog.show(this, wait, onReady = { beginMatch() }, onLeave = { finish() })
            return
        }
        beginMatch()
    }

    private fun beginMatch() {
        target = Prefs.scoreTarget(this)
        matchYou = 0
        matchBot = 0
        youStartNext = true
        startRound()
    }

    private fun startRound() {
        game.newGame(youStart = youStartNext)
        youStartNext = !youStartNext
        roundEnding = false
        moveSeq++
        if (game.turn == 1) {
            busy = true
            render()
            b.txtStatus.setText(R.string.bot_turn)
            post(400) { botTurn() }
        } else {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
            maybeAutoPlay()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeCard(card: Card?, faceUp: Boolean, w: Int, h: Int, highlight: Boolean = false): CardView {
        val cv = CardView(this)
        cv.card = card
        cv.faceUp = faceUp
        cv.highlight = highlight
        val lp = LinearLayout.LayoutParams(w, h)
        lp.marginStart = dp(3)
        lp.marginEnd = dp(3)
        cv.layoutParams = lp
        return cv
    }

    private fun gridLp(): GridLayout.LayoutParams {
        val lp = GridLayout.LayoutParams()
        lp.width = centerW
        lp.height = centerH
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        return lp
    }

    /** Disegna il mazzo nel riquadro ancorato a sinistra, col numero di carte rimaste. */
    private fun renderDeck() {
        b.deckBox.removeAllViews()
        if (game.deck.isEmpty()) return
        val back = CardView(this); back.faceUp = false
        b.deckBox.addView(back, FrameLayout.LayoutParams(centerW, centerH))
        val tv = TextView(this)
        tv.text = game.deck.size.toString()
        tv.setTextColor(getColor(R.color.silver))
        tv.textSize = 15f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setBackgroundColor(Color.argb(0xB0, 0, 0, 0))
        tv.setPadding(dp(6), dp(1), dp(6), dp(1))
        val tp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        // meta' mazzo e' fuori schermo: il contatore sta a destra, dove si vede
        tp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        tp.marginEnd = dp(6)
        b.deckBox.addView(tv, tp)
    }

    private fun render() {
        val yourTurn = game.turn == 0 && !game.finished && !busy

        b.botScore.text = getString(R.string.score_line, matchBot, game.scope[1])
        b.youScore.text = getString(R.string.score_line, matchYou, game.scope[0])

        b.botHand.removeAllViews()
        for (c in game.hands[1]) b.botHand.addView(makeCard(null, false, cardW, cardH))

        b.youHand.removeAllViews()
        for (c in game.hands[0]) {
            val canCapture = game.capturesFor(c.value).isNotEmpty()
            val cv = makeCard(c, true, cardW, cardH, highlight = yourTurn && canCapture)
            cv.setOnClickListener { onPlayerCard(c, cv) }
            b.youHand.addView(cv)
        }

        renderDeck()
        b.gridCenter.removeAllViews()
        for (c in game.table) {
            val cv = CardView(this); cv.card = c; cv.faceUp = true
            b.gridCenter.addView(cv, gridLp())
        }
        armWatchdog()
    }

    // ---------- mossa dell'utente ----------
    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        val caps = game.capturesFor(card.value)
        if (caps.size > 1) {
            val labels = caps.map { opt -> opt.joinToString(" + ") { it.italianName } }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(R.string.choose_capture)
                .setItems(labels) { _, which -> doPlayerPlay(card, fromView, caps[which]) }
                .setCancelable(true)
                .show()
        } else {
            doPlayerPlay(card, fromView, caps.firstOrNull() ?: emptyList())
        }
    }

    private fun doPlayerPlay(card: Card, fromView: View, capture: List<Card>) {
        if (busy || game.turn != 0 || game.finished) return
        busy = true
        armWatchdog()
        fromView.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(fromView)
        playAnimated(card, capture, sx, sy, byBot = false)
    }

    /** Modalita' test: il programma gioca anche le carte dell'utente. */
    private fun maybeAutoPlay() {
        if (!autoPlay || busy || destroyed || roundEnding) return
        if (game.finished || game.turn != 0 || game.hands[0].isEmpty()) return
        busy = true
        armWatchdog()
        post(250) {
            if (game.finished || game.turn != 0 || game.hands[0].isEmpty()) { recover(); return@post }
            val (card, cap) = game.choose(0)
            val view = findHandCardView(card)
            val start = if (view != null) {
                view.visibility = View.INVISIBLE
                topLeftInOverlay(view)
            } else centerInOverlay(b.youHand)
            playAnimated(card, cap, start.first, start.second, byBot = false)
        }
    }

    private fun findHandCardView(card: Card): View? {
        for (i in 0 until b.youHand.childCount) {
            val ch = b.youHand.getChildAt(i)
            if (ch is CardView && ch.card == card) return ch
        }
        return null
    }

    private fun botTurn() {
        if (destroyed || roundEnding) return
        if (game.finished) { busy = true; endRound(); return }
        if (game.hands[1].isEmpty()) { recover(); return }
        val (card, cap) = game.choose(1)
        val src: View = if (b.botHand.childCount > 0) b.botHand.getChildAt(0) else b.botHand
        val (sx, sy) = topLeftInOverlay(src)
        playAnimated(card, cap, sx, sy, byBot = true)
    }

    /**
     * La carta calata scivola al centro del tavolo e resta ferma mezzo secondo; poi il
     * tavolo si aggiorna di colpo. Le carte prese spariscono subito, senza animazione:
     * era proprio quella catena di animazioni annidate a generare i blocchi.
     */
    private fun playAnimated(card: Card, capture: List<Card>, sx: Float, sy: Float, byBot: Boolean) {
        val (cx, cy) = tableCenter()
        val played = addTempCard(card, true, sx, sy, cardW, cardH)
        played.animate().x(cx - cardW / 2f).y(cy - cardH / 2f).setDuration(240).start()
        post(holdMs) {
            b.overlay.removeView(played)
            finishPlay(card, capture, byBot)
        }
    }

    private fun finishPlay(card: Card, capture: List<Card>, byBot: Boolean) {
        try {
            val scopa = game.play(card, capture)
            moveSeq++
            afterPlay(scopa, capture, byBot, card)
        } catch (e: Exception) {
            moveSeq++
            recover()
        }
    }

    private fun afterPlay(scopa: Boolean, capture: List<Card>, byBot: Boolean, playedCard: Card) {
        val events = mutableListOf<String>()
        if (scopa) events.add("SCOPA!")
        // Settebello secured this turn: either captured from the table, or played as the capturing card.
        val gotSettebello = capture.any { it.isSettebello } || (playedCard.isSettebello && capture.isNotEmpty())
        if (gotSettebello) events.add("SETTEBELLO!")
        if (events.isEmpty() && capture.size >= 3) events.add("Bella presa!")
        val hasBanner = events.isNotEmpty()
        if (hasBanner) showBanner(events.joinToString("   "), byBot)

        if (game.finished) {
            busy = true
            render()
            // lascio finire il cartello prima del riepilogo, ma senza bloccare nulla
            if (hasBanner) post(1100) { endRound() } else endRound()
            return
        }

        if (byBot) {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
            maybeAutoPlay()
        } else {
            busy = true
            render()
            b.txtStatus.setText(R.string.bot_turn)
            botTurn()
        }
    }

    // ---------- animation helpers ----------
    private fun topLeftInOverlay(v: View): Pair<Float, Float> {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val o = IntArray(2); b.overlay.getLocationInWindow(o)
        return Pair((loc[0] - o[0]).toFloat(), (loc[1] - o[1]).toFloat())
    }

    private fun centerInOverlay(v: View): Pair<Float, Float> {
        val (x, y) = topLeftInOverlay(v)
        val w = if (v.width > 0) v.width else cardW
        val h = if (v.height > 0) v.height else cardH
        return Pair(x + w / 2f, y + h / 2f)
    }

    private fun tableCenter(): Pair<Float, Float> = centerInOverlay(b.centerArea)

    private fun addTempCard(card: Card, faceUp: Boolean, x: Float, y: Float, w: Int, h: Int): CardView {
        val cv = CardView(this)
        cv.card = card
        cv.faceUp = faceUp
        b.overlay.addView(cv, FrameLayout.LayoutParams(w, h))
        cv.x = x; cv.y = y
        return cv
    }

    private fun showBanner(text: String, byBot: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(if (byBot) getColor(R.color.silver) else getColor(R.color.gold))
        tv.textSize = 34f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setShadowLayer(10f, 0f, 3f, Color.BLACK)
        tv.gravity = Gravity.CENTER
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER
        b.overlay.addView(tv, lp)
        tv.scaleX = 0.4f; tv.scaleY = 0.4f; tv.alpha = 0f
        tv.animate().scaleX(1.15f).scaleY(1.15f).alpha(1f).setDuration(220).start()
        post(850) { tv.animate().alpha(0f).setDuration(300).start() }
        post(1200) { b.overlay.removeView(tv) }
    }

    // ---------- end of round / match ----------
    private fun endRound() {
        if (roundEnding || destroyed || isFinishing) return
        roundEnding = true
        ui.removeCallbacks(watchdog)
        busy = true
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)
        matchYou += you.total
        matchBot += bot.total
        render()
        // in parita' sul traguardo non si assegna la partita: si gioca un'altra mano
        val over = (matchYou >= target || matchBot >= target) && matchYou != matchBot
        if (over) Prefs.markMatchEnded(this)

        val msg = buildString {
            append("<b>${getString(R.string.you)} ${you.total} &#8211; ${bot.total} ${getString(R.string.bot)}</b><br/><br/>")
            append(row(R.string.pt_carte, you.carte, bot.carte))
            append(row(R.string.pt_denari, you.denari, bot.denari))
            append(row(R.string.pt_settebello, you.settebello, bot.settebello))
            append(row(R.string.pt_primiera, you.primiera, bot.primiera))
            append(row(R.string.pt_scope, you.scope, bot.scope))
            append("<br/>")
            append("<b>").append(getString(R.string.match_line, matchYou, matchBot, target)).append("</b>")
            if (over) {
                append("<br/><br/><b>")
                if (matchYou > matchBot) {
                    append("<font color='#E9C24C'>").append(getString(R.string.match_win_you)).append("</font>")
                } else {
                    append(getString(R.string.match_win_bot))
                }
                append("</b>")
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.round_over)
            .setMessage(HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setCancelable(false)
            .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        if (over) {
            builder.setPositiveButton(R.string.new_match) { _, _ -> startMatch() }
        } else {
            builder.setPositiveButton(R.string.continue_match) { _, _ -> startRound() }
        }
        builder.show()
    }

    private fun row(labelRes: Int, you: Int, bot: Int): String =
        "${getString(labelRes)}: <b>$you</b> &#8211; <b>$bot</b><br/>"
}
