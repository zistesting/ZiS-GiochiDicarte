package com.zis.scopa

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
import androidx.core.view.doOnPreDraw
import com.zis.scopa.databinding.ActivityBriscolaBinding

class BriscolaActivity : AppCompatActivity() {

    private lateinit var b: ActivityBriscolaBinding
    private val game = BriscolaGame()
    private var busy = false
    private var youStartNext = true

    // match (first to N hands wins), N from settings (5 or 11)
    private var matchYou = 0
    private var matchBot = 0
    private var matchTarget = 5

    // Cards scale with screen width so they aren't tiny on tablets (min 76dp on phones).
    private val cardWDp: Float
        get() = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * 0.21f).coerceIn(76f, 200f)
    private val cardW get() = (cardWDp * resources.displayMetrics.density).toInt()
    // Proporzione reale delle immagini (560x1024 = 1.829). Prima era 120/76 = 1.579:
    // il disegno veniva schiacciato del 14% in altezza, visibile soprattutto sulle figure.
    private val cardRatio = 1.829f
    private val cardH get() = (cardWDp * cardRatio * resources.displayMetrics.density).toInt()
    // mazzo e briscola alla stessa dimensione delle carte dei giocatori
    private val smallW get() = cardW
    private val smallH get() = cardH

    // watchdog (never let a turn freeze)
    private val ui = Handler(Looper.getMainLooper())
    private var ending = false
    private var destroyed = false
    private val watchdog = Runnable {
        if (destroyed || !busy || ending) return@Runnable
        if (game.finished) endGame()
        else if (game.turn == 1) { busy = true; botPlay() }
        else { busy = false; render(); b.txtStatus.setText(R.string.your_turn) }
    }
    private fun armWatchdog() { ui.removeCallbacks(watchdog); if (busy && !ending && !destroyed) ui.postDelayed(watchdog, 3500) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBriscolaBinding.inflate(layoutInflater)
        setContentView(b.root)
        startMatch()
    }

    override fun onDestroy() {
        // I turni avanzano con callback differiti: se l'utente esce a meta' mano, senza questa
        // pulizia il callback parte comunque e il dialogo di fine partita fa crashare l'app.
        destroyed = true
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** postDelayed sicuro: il blocco non viene eseguito se l'activity nel frattempo e' morta. */
    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    private fun startMatch() {
        matchTarget = Prefs.briscolaTarget(this)
        matchYou = 0
        matchBot = 0
        youStartNext = true
        startGame()
    }

    private fun startGame() {
        game.newGame(youStart = youStartNext)
        youStartNext = !youStartNext
        ending = false
        busy = true
        render()
        b.root.doOnPreDraw {
            if (destroyed) return@doOnPreDraw
            if (game.turn == 1) {
                b.txtStatus.setText(R.string.bot_turn)
                post(500) { botPlay() }
            } else {
                busy = false
                b.txtStatus.setText(R.string.your_turn)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeCard(card: Card?, faceUp: Boolean, w: Int, h: Int, hl: Boolean, click: ((View) -> Unit)? = null): CardView {
        val cv = CardView(this)
        cv.card = card; cv.faceUp = faceUp; cv.highlight = hl
        val lp = LinearLayout.LayoutParams(w, h)
        lp.marginStart = dp(3); lp.marginEnd = dp(3)
        cv.layoutParams = lp
        if (click != null) cv.setOnClickListener { click(cv) }
        return cv
    }

    private fun render(trickOverride: List<Card>? = null) {
        val yourTurn = game.turn == 0 && !game.finished && !busy

        b.txtMatch.text = getString(R.string.match_line, matchYou, matchBot, matchTarget)
        // il seme di briscola resta sempre leggibile, anche dopo che la carta e' stata pescata
        b.txtBriscola.text = getString(R.string.briscola_is, suitName(game.briscolaSuit))
        b.botScore.text = getString(R.string.bot_points, game.scoreFor(1))
        b.youScore.text = getString(R.string.you_points, game.scoreFor(0))

        b.botHand.removeAllViews()
        for (c in game.hands[1]) b.botHand.addView(makeCard(null, false, cardW, cardH, false))

        b.youHand.removeAllViews()
        for (c in game.hands[0]) {
            b.youHand.addView(makeCard(c, true, cardW, cardH, yourTurn) { cv -> onPlayerCard(c, cv) })
        }

        // deck pile (cards above the briscola) + the face-up briscola card
        b.deckBox.removeAllViews()
        val extra = game.deck.size - 1   // cards on top of the briscola
        if (extra > 0) {
            val fl = FrameLayout(this)
            fl.addView(CardView(this).apply { faceUp = false }, FrameLayout.LayoutParams(smallW, smallH))
            val tv = TextView(this)
            tv.text = extra.toString()
            tv.setTextColor(getColor(R.color.silver)); tv.textSize = 14f
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setBackgroundColor(Color.argb(0xB0, 0, 0, 0))
            tv.setPadding(dp(5), dp(1), dp(5), dp(1))
            val tp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            tp.gravity = Gravity.CENTER
            fl.addView(tv, tp)
            b.deckBox.addView(fl)
        }
        b.briscolaBox.removeAllViews()
        val bc = game.briscolaCard()
        if (bc != null) {
            val cv = CardView(this); cv.card = bc; cv.faceUp = true
            b.briscolaBox.addView(cv, FrameLayout.LayoutParams(smallW, smallH))
        }

        // trick (played cards)
        b.trickRow.removeAllViews()
        val shownTrick = trickOverride ?: game.trick
        for (c in shownTrick) b.trickRow.addView(makeCard(c, true, cardW, cardH, false))

        armWatchdog()
    }

    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        busy = true; armWatchdog()
        fromView.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(fromView)
        playAnimated(card, sx, sy)
    }

    private fun botPlay() {
        if (game.finished) { endGame(); return }
        if (game.hands[1].isEmpty()) { busy = false; render(); b.txtStatus.setText(R.string.your_turn); return }
        val card = game.botChoose()
        val src: View = if (b.botHand.childCount > 0) b.botHand.getChildAt(0) else b.botHand
        val (sx, sy) = topLeftInOverlay(src)
        playAnimated(card, sx, sy)
    }

    private fun playAnimated(card: Card, sx: Float, sy: Float) {
        val (cx, cy) = centerInOverlay(b.trickRow)
        val completing = game.trick.size == 1
        val leaderCard = if (completing) game.trick[0] else null
        val temp = CardView(this); temp.card = card; temp.faceUp = true
        b.overlay.addView(temp, FrameLayout.LayoutParams(cardW, cardH))
        temp.x = sx; temp.y = sy
        temp.animate().x(cx - cardW / 2f).y(cy - cardH / 2f).setDuration(320).start()
        post(340) {
            b.overlay.removeView(temp)
            val winner = game.play(card)
            if (winner == -1) afterLead() else afterComplete(leaderCard!!, card, winner)
        }
    }

    private fun afterLead() {
        render()   // trickRow now shows the single leader card
        if (game.turn == 1) {
            busy = true; b.txtStatus.setText(R.string.bot_turn); post(500) { botPlay() }
        } else {
            busy = false; b.txtStatus.setText(R.string.your_turn)
        }
    }

    private fun afterComplete(lead: Card, follow: Card, winner: Int) {
        render(listOf(lead, follow))   // keep BOTH cards visible during the pause
        b.txtStatus.setText(if (winner == 0) R.string.you_take else R.string.bot_take)
        showBanner(if (winner == 0) getString(R.string.you_take) else getString(R.string.bot_take), winner == 1)
        busy = true; armWatchdog()
        post(1200) {
            render()   // trick cleared, hands refilled
            when {
                game.finished -> endGame()
                game.turn == 1 -> { b.txtStatus.setText(R.string.bot_turn); post(500) { botPlay() } }
                else -> { busy = false; b.txtStatus.setText(R.string.your_turn) }
            }
        }
    }

    private fun endGame() {
        if (ending || destroyed || isFinishing) return
        ending = true
        ui.removeCallbacks(watchdog)
        busy = true
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)
        // award the hand to the winner (a 60-60 draw counts for no one)
        if (you > bot) matchYou++ else if (bot > you) matchBot++
        render()

        val handMsg = when {
            you > bot -> getString(R.string.brisc_win_you, you, bot)
            bot > you -> getString(R.string.brisc_win_bot, bot, you)
            else -> getString(R.string.brisc_draw, you, bot)
        }
        val matchOver = matchYou >= matchTarget || matchBot >= matchTarget

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.play_briscola)
            .setCancelable(false)
        if (matchOver) {
            val matchMsg = if (matchYou > matchBot)
                getString(R.string.brisc_match_win_you, matchYou, matchBot)
            else
                getString(R.string.brisc_match_win_bot, matchBot, matchYou)
            builder.setMessage("$handMsg\n\n$matchMsg")
                .setPositiveButton(R.string.new_match) { _, _ -> startMatch() }
                .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        } else {
            val scoreLine = getString(R.string.match_line, matchYou, matchBot, matchTarget)
            builder.setMessage("$handMsg\n\n$scoreLine")
                .setPositiveButton(R.string.continue_match) { _, _ -> startGame() }
                .setNeutralButton(R.string.new_match) { _, _ -> startMatch() }
                .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        }
        val dlg = builder.show()
        // win notice for the user in gold
        if (you > bot) dlg.findViewById<TextView>(android.R.id.message)?.setTextColor(getColor(R.color.gold))
    }

    private fun suitName(suit: Int): String = when (suit) {
        0 -> getString(R.string.suit_denari)
        1 -> getString(R.string.suit_coppe)
        2 -> getString(R.string.suit_spade)
        else -> getString(R.string.suit_bastoni)
    }

    // ---- animation helpers ----
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

    private fun showBanner(text: String, byBot: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(if (byBot) getColor(R.color.silver) else getColor(R.color.gold))
        tv.textSize = 30f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setShadowLayer(10f, 0f, 3f, Color.BLACK)
        tv.gravity = Gravity.CENTER
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        b.overlay.addView(tv, lp)
        tv.scaleX = 0.5f; tv.scaleY = 0.5f; tv.alpha = 0f
        tv.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(200).start()
        post(800) { tv.animate().alpha(0f).setDuration(300).start() }
        post(1150) { b.overlay.removeView(tv) }
    }
}
