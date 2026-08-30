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
    private var autoPlay = false
    private var showBot = false
    /** Vero mentre scorre l'animazione della presa: le carte appena pescate restano nascoste. */
    private var hideDrawn = false

    /** Come in Scopa: il watchdog interviene solo se il gioco non si e' mosso da solo. */
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
            game.finished -> { busy = true; endGame() }
            game.turn == 1 -> { busy = true; render(); post(250) { botPlay() } }
            busy -> { busy = false; render(); b.txtStatus.setText(R.string.your_turn); maybeAutoPlay() }
            else -> maybeAutoPlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBriscolaBinding.inflate(layoutInflater)
        setContentView(b.root)
        placeCards()
        startMatch()
    }

    /**
     * Sistemazione fissa degli elementi, calcolata una volta sola perche' dipende solo
     * dalla larghezza dello schermo:
     *  - le carte del Banco escono per meta' dal bordo alto (sono coperte, non serve vederle)
     *  - il mazzo esce per meta' dal bordo sinistro
     *  - la briscola resta nascosta per un terzo sotto al mazzo
     */
    private fun placeCards() {
        // se le carte del Banco sono scoperte devono restare tutte visibili
        (b.botHand.layoutParams as LinearLayout.LayoutParams).topMargin = if (showBot) 0 else -cardH / 2
        b.botHand.requestLayout()
        (b.deckRow.layoutParams as FrameLayout.LayoutParams).marginStart = -cardW / 2
        (b.briscolaBox.layoutParams as LinearLayout.LayoutParams).marginStart = -cardW / 3
        b.deckBox.translationZ = 1f   // il mazzo copre la briscola, non il contrario
    }

    override fun onResume() {
        super.onResume()
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        placeCards()
        render()
        maybeAutoPlay()
    }

    /** Modalita' test: il programma gioca anche le carte dell'utente. */
    private fun maybeAutoPlay() {
        if (!autoPlay || busy || destroyed || ending) return
        if (game.finished || game.turn != 0 || game.hands[0].isEmpty()) return
        busy = true
        armWatchdog()
        post(250) {
            if (game.finished || game.turn != 0 || game.hands[0].isEmpty()) { recover(); return@post }
            val card = game.botChoose()
            val view = findHandCardView(card)
            val start = if (view != null) {
                view.visibility = View.INVISIBLE
                topLeftInOverlay(view)
            } else centerInOverlay(b.youHand)
            playAnimated(card, start.first, start.second)
        }
    }

    private fun findHandCardView(card: Card): View? {
        for (i in 0 until b.youHand.childCount) {
            val ch = b.youHand.getChildAt(i)
            if (ch is CardView && ch.card == card) return ch
        }
        return null
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
        val wait = Prefs.pauseRemaining(this)
        if (wait > 0) {
            busy = true
            PauseDialog.show(this, wait, onReady = { beginMatch() }, onLeave = { finish() })
            return
        }
        beginMatch()
    }

    private fun beginMatch() {
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
        moveSeq++
        if (game.turn == 1) {
            b.txtStatus.setText(R.string.bot_turn)
            post(500) { botPlay() }
        } else {
            busy = false
            render()
            b.txtStatus.setText(R.string.your_turn)
            maybeAutoPlay()
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

        b.txtMatch.text = getString(R.string.brisc_match_line, matchYou, matchBot, matchTarget)
        b.botScore.text = getString(R.string.bot_points, game.scoreFor(1))
        b.youScore.text = getString(R.string.you_points, game.scoreFor(0))

        b.botHand.removeAllViews()
        for (c in game.hands[1]) {
            if (hideDrawn && c == game.lastDrawn[1]) continue
            b.botHand.addView(makeCard(if (showBot) c else null, showBot, cardW, cardH, false))
        }

        b.youHand.removeAllViews()
        for (c in game.hands[0]) {
            if (hideDrawn && c == game.lastDrawn[0]) continue
            b.youHand.addView(makeCard(c, true, cardW, cardH, yourTurn) { cv -> onPlayerCard(c, cv) })
        }

        // deck pile (cards above the briscola) + the face-up briscola card
        b.deckBox.removeAllViews()
        // finche' le carte pescate restano nascoste, il contatore non deve calare in anticipo
        val pending = if (hideDrawn) game.lastDrawn.count { it != null } else 0
        val extra = game.deck.size + pending - 1   // cards on top of the briscola
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
            tp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            tp.marginEnd = dp(6)
            fl.addView(tv, tp)
            b.deckBox.addView(fl)
        }
        b.briscolaBox.removeAllViews()
        val bc = if (hideDrawn && game.lastDrawn.any { it != null && it == game.trumpCard })
            game.trumpCard else game.briscolaCard()
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
        if (game.hands[1].isEmpty()) { recover(); return }
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
            moveSeq++
            if (winner == -1) afterLead() else afterComplete(leaderCard!!, card, winner)
        }
    }

    private fun afterLead() {
        render()   // trickRow now shows the single leader card
        if (game.turn == 1) {
            busy = true; b.txtStatus.setText(R.string.bot_turn); post(500) { botPlay() }
        } else {
            busy = false; b.txtStatus.setText(R.string.your_turn); maybeAutoPlay()
        }
    }

    private fun afterComplete(lead: Card, follow: Card, winner: Int) {
        // le carte pescate restano nascoste finche' l'animazione della presa non e' finita
        hideDrawn = true
        render(listOf(lead, follow))   // keep BOTH cards visible during the pause
        b.txtStatus.setText(if (winner == 0) R.string.you_take else R.string.bot_take)
        busy = true; armWatchdog()
        post(500) {
            sweepTrick(winner) {
                hideDrawn = false
                render()   // ora compaiono le carte pescate
                when {
                    game.finished -> endGame()
                    game.turn == 1 -> { b.txtStatus.setText(R.string.bot_turn); post(500) { botPlay() } }
                    else -> { busy = false; b.txtStatus.setText(R.string.your_turn) }
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
        val temps = ArrayList<CardView>()
        for (i in 0 until b.trickRow.childCount) {
            val child = b.trickRow.getChildAt(i) as? CardView ?: continue
            val (x, y) = topLeftInOverlay(child)
            val t = CardView(this)
            t.card = child.card; t.faceUp = true
            b.overlay.addView(t, FrameLayout.LayoutParams(cardW, cardH))
            t.x = x; t.y = y
            child.visibility = View.INVISIBLE
            temps.add(t)
        }
        if (temps.isEmpty()) { onDone(); return }

        val dest = if (winner == 0) b.youHand else b.botHand
        val (cx, cy) = centerInOverlay(dest)
        var last = 0L
        for ((i, t) in temps.withIndex()) {
            val delay = (i * 70).toLong()
            t.animate().x(cx - cardW / 2f).y(cy - cardH / 2f)
                .scaleX(0.5f).scaleY(0.5f).alpha(0f)
                .setStartDelay(delay).setDuration(320).start()
            last = maxOf(last, delay + 320)
        }
        post(last + 40) {
            for (t in temps) b.overlay.removeView(t)
            onDone()
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
        if (matchOver) Prefs.markMatchEnded(this)

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
            val scoreLine = getString(R.string.brisc_match_line, matchYou, matchBot, matchTarget)
            builder.setMessage("$handMsg\n\n$scoreLine")
                .setPositiveButton(R.string.continue_match) { _, _ -> startGame() }
                .setNeutralButton(R.string.new_match) { _, _ -> startMatch() }
                .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        }
        val dlg = builder.show()
        // win notice for the user in gold
        if (you > bot) dlg.findViewById<TextView>(android.R.id.message)?.setTextColor(getColor(R.color.gold))
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

}
