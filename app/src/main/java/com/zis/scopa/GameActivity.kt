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
import androidx.core.view.doOnPreDraw
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

    // Safety net: if no progress happens for a few seconds while input is blocked, resume the game so
    // it can never stay frozen (resumes the Banco, shows the summary, or gives the turn back).
    private val ui = Handler(Looper.getMainLooper())
    private var roundEnding = false
    private var destroyed = false
    private val watchdog = Runnable {
        if (destroyed || !busy || roundEnding) return@Runnable
        if (game.finished) {
            endRound()
        } else if (game.turn == 1) {
            busy = true
            botTurn()
        } else {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
        }
    }

    private fun armWatchdog() {
        ui.removeCallbacks(watchdog)
        if (busy && !roundEnding && !destroyed) ui.postDelayed(watchdog, 3500)
    }

    // Cards scale with screen width so they aren't tiny on tablets (min 76dp on phones).
    private val cardWDp: Float
        get() = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * 0.21f).coerceIn(76f, 200f)
    private val cardW get() = (cardWDp * resources.displayMetrics.density).toInt()
    // Proporzione reale delle immagini (560x1024 = 1.829). Prima era 120/76 = 1.579:
    // il disegno veniva schiacciato del 14% in altezza, visibile soprattutto sulle figure.
    private val cardRatio = 1.829f
    private val cardH get() = (cardWDp * cardRatio * resources.displayMetrics.density).toInt()
    private val centerW get() = cardW
    private val centerH get() = cardH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGameBinding.inflate(layoutInflater)
        setContentView(b.root)
        startMatch()
    }

    override fun onDestroy() {
        // Tutti i passaggi di turno sono differiti: senza questa pulizia un callback puo' partire
        // su un'activity gia' distrutta (tipicamente quando l'utente esce mentre gioca il Banco)
        // e far crashare l'app con BadTokenException al momento di mostrare il dialogo.
        destroyed = true
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** postDelayed sicuro: il blocco non viene eseguito se l'activity nel frattempo e' morta. */
    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    private fun startMatch() {
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
        busy = true
        render()
        b.txtStatus.setText(R.string.your_turn)
        b.root.doOnPreDraw {
            if (destroyed) return@doOnPreDraw
            animateDeal {
                render()
                if (game.turn == 1) {
                    // the Banco leads this round: keep input blocked and let it play
                    b.txtStatus.setText(R.string.bot_turn)
                    post(450) { botTurn() }
                } else {
                    busy = false
                    b.txtStatus.setText(R.string.your_turn)
                }
            }
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

    private fun makeDeckCell(): View {
        val fl = FrameLayout(this)
        val back = CardView(this); back.faceUp = false
        fl.addView(back, FrameLayout.LayoutParams(centerW, centerH))
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
        tp.gravity = Gravity.CENTER
        fl.addView(tv, tp)
        return fl
    }

    private fun render() {
        val yourTurn = game.turn == 0 && !game.finished && !busy

        b.botScore.text = getString(R.string.score_line, game.captured[1].size, game.scope[1], matchBot)
        b.youScore.text = getString(R.string.score_line, game.captured[0].size, game.scope[0], matchYou)

        b.botHand.removeAllViews()
        for (c in game.hands[1]) b.botHand.addView(makeCard(null, false, cardW, cardH))

        b.youHand.removeAllViews()
        for (c in game.hands[0]) {
            val canCapture = game.capturesFor(c.value).isNotEmpty()
            val cv = makeCard(c, true, cardW, cardH, highlight = yourTurn && canCapture)
            cv.setOnClickListener { onPlayerCard(c, cv) }
            b.youHand.addView(cv)
        }

        b.gridCenter.removeAllViews()
        b.gridCenter.columnCount = 4
        if (game.deck.isNotEmpty()) b.gridCenter.addView(makeDeckCell(), gridLp())
        for (c in game.table) {
            val cv = CardView(this); cv.card = c; cv.faceUp = true
            b.gridCenter.addView(cv, gridLp())
        }
        armWatchdog()
    }

    // ---------- player move (with capture chooser) ----------
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
        busy = true
        armWatchdog()
        fromView.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(fromView)
        playAnimated(card, capture, sx, sy, byBot = false)
    }

    private fun botTurn() {
        if (game.finished) { endRound(); return }
        if (game.hands[1].isEmpty()) {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
            return
        }
        val (card, cap) = game.botChoose()
        val src: View = if (b.botHand.childCount > 0) b.botHand.getChildAt(0) else b.botHand
        val (sx, sy) = topLeftInOverlay(src)
        playAnimated(card, cap, sx, sy, byBot = true)
    }

    /** Slide the played card onto the table; if it captures, gather the captured cards under it.
     *  Uses postDelayed (not withEndAction, which can silently fail to fire and freeze the turn). */
    private fun playAnimated(card: Card, capture: List<Card>, sx: Float, sy: Float, byBot: Boolean) {
        val (cx, cy) = tableCenter()
        val tx = cx - cardW / 2f
        val ty = cy - cardH / 2f
        val played = addTempCard(card, true, sx, sy, cardW, cardH)
        played.animate().x(tx).y(ty).setDuration(340).start()
        post(360) {
            if (capture.isEmpty()) {
                b.overlay.removeView(played)
                finishPlay(card, capture, byBot)
            } else {
                gatherCaptured(capture, tx, ty, played) {
                    b.overlay.removeView(played)
                    finishPlay(card, capture, byBot)
                }
            }
        }
    }

    private fun gatherCaptured(capture: List<Card>, tx: Float, ty: Float, played: View, onDone: () -> Unit) {
        val temps = ArrayList<View>()
        for (c in capture) {
            val v = findTableCardView(c)
            val start = if (v != null) {
                val p = topLeftInOverlay(v); v.visibility = View.INVISIBLE; p
            } else Pair(tx, ty)
            temps.add(addTempCard(c, true, start.first, start.second, centerW, centerH))
        }
        played.bringToFront() // captured cards slide UNDER the played card
        var maxEnd = 0L
        for ((i, t) in temps.withIndex()) {
            val destX = tx + (cardW - centerW) / 2f
            val destY = ty + dp(30) + i * dp(3)
            val delay = (i * 70).toLong()
            t.animate().x(destX).y(destY).setStartDelay(delay).setDuration(300).start()
            maxEnd = maxOf(maxEnd, delay + 300)
        }
        post(maxEnd + 40) {
            for (t in temps) b.overlay.removeView(t)
            onDone()
        }
    }

    private fun finishPlay(card: Card, capture: List<Card>, byBot: Boolean) {
        try {
            val scopa = game.play(card, capture)
            afterPlay(scopa, capture, byBot, card)
        } catch (e: Exception) {
            busy = false
            render()
            if (game.finished) endRound() else b.txtStatus.setText(R.string.your_turn)
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

        render()
        if (game.finished) {
            // let the banner play before the round-summary dialog covers it
            busy = true
            if (hasBanner) post(1300) { endRound() } else endRound()
            return
        }
        b.txtStatus.setText(if (byBot) R.string.your_turn else R.string.bot_turn)

        // when new cards were just dealt, keep them hidden until animateDeal flies them in (no pop-in flicker)
        if (game.lastDealt) hideHandsForDeal()

        post(500) {
            if (game.lastDealt) {
                b.root.doOnPreDraw {
                    if (destroyed) return@doOnPreDraw
                    animateDeal {
                        if (byBot) { busy = false; render() } else { botTurn() }
                    }
                }
            } else {
                if (byBot) { busy = false; render() } else { botTurn() }
            }
        }
    }

    /** Hide the cards currently in both hands (used right before a deal animation to avoid a flash). */
    private fun hideHandsForDeal() {
        for (i in 0 until b.botHand.childCount) b.botHand.getChildAt(i).alpha = 0f
        for (i in 0 until b.youHand.childCount) b.youHand.getChildAt(i).alpha = 0f
    }

    // ---------- animation helpers ----------
    private fun topLeftInOverlay(v: View): Pair<Float, Float> {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val o = IntArray(2); b.overlay.getLocationInWindow(o)
        return Pair((loc[0] - o[0]).toFloat(), (loc[1] - o[1]).toFloat())
    }

    private fun centerInOverlay(v: View): Pair<Float, Float> {
        val (x, y) = topLeftInOverlay(v)
        return Pair(x + v.width / 2f, y + v.height / 2f)
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

    private fun findTableCardView(card: Card): View? {
        for (i in 0 until b.gridCenter.childCount) {
            val child = b.gridCenter.getChildAt(i)
            if (child is CardView && child.card == card) return child
        }
        return null
    }

    private fun animateDeal(onDone: () -> Unit = {}) {
        val deckView = if (game.deck.isNotEmpty() && b.gridCenter.childCount > 0) b.gridCenter.getChildAt(0) else null
        if (deckView == null) { onDone(); return }
        val deck = IntArray(2); deckView.getLocationInWindow(deck)
        val views = ArrayList<View>()
        for (i in 0 until b.botHand.childCount) views.add(b.botHand.getChildAt(i))
        for (i in 0 until b.youHand.childCount) views.add(b.youHand.getChildAt(i))
        if (views.isEmpty()) { onDone(); return }
        var delay = 0L
        val step = 80L
        val dur = 260L
        for (v in views) {
            val p = IntArray(2); v.getLocationInWindow(p)
            v.translationX = (deck[0] - p[0]).toFloat()
            v.translationY = (deck[1] - p[1]).toFloat()
            v.alpha = 0f
            v.animate().translationX(0f).translationY(0f).alpha(1f)
                .setStartDelay(delay).setDuration(dur).start()
            delay += step
        }
        post(delay + dur) { onDone() }
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
        tv.animate().scaleX(1.15f).scaleY(1.15f).alpha(1f).setDuration(240).start()
        post(1000) { tv.animate().alpha(0f).setDuration(350).start() }
        post(1400) { b.overlay.removeView(tv) }
    }

    // ---------- end of round / match ----------
    private fun endRound() {
        if (roundEnding || destroyed || isFinishing) return
        roundEnding = true
        ui.removeCallbacks(watchdog)
        busy = true
        render()
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)
        matchYou += you.total
        matchBot += bot.total
        // in parita' sul traguardo non si assegna la partita: si gioca un'altra mano
        val over = (matchYou >= target || matchBot >= target) && matchYou != matchBot

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
