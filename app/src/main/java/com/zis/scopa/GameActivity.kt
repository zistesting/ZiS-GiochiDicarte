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
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.zis.scopa.databinding.ActivityGameBinding
import com.zis.scopa.databinding.DialogScoreBinding

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
    private var showBot = false

    /** Tutti i tempi di gioco: a zero quando gioca il programma. */
    private val t = Timing()

    private val ui = Handler(Looper.getMainLooper())
    private var roundEnding = false
    private var destroyed = false

    /** Vero fra onStop e il ritorno in primo piano: vedi onStop e onResume. */
    private var stopped = false

    /**
     * Riferimento all'ultimo dialogo aperto, per chiuderlo in onDestroy.
     * I dialoghi creati con AlertDialog.Builder non si chiudono da soli quando l'activity
     * muore: restano appesi al suo contesto, il log segna WindowLeaked e l'activity non
     * viene liberata. Non ce n'e' mai piu' di uno aperto insieme, quindi basta un campo.
     */
    private var openDialog: AlertDialog? = null

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
            ui.postDelayed(watchdog, 4000)
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
            game.turn == 1 -> { busy = true; render(); post(t.think) { botTurn() } }
            busy -> {
                busy = false
                render()
                b.txtStatus.setText(R.string.your_turn)
                maybeAutoPlay()
            }
            else -> maybeAutoPlay()
        }
    }

    // Misure delle carte: vedi CardSize, dipendono da larghezza e altezza dello schermo.
    private val cardW get() = CardSize.width(resources)
    private val cardH get() = CardSize.height(resources)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGameBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)
        b.btnInfo.setOnClickListener { track(InfoDialog.show(this, R.string.info_title, R.string.rules_scopa)) }
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        t.fast = autoPlay
        CardView.setDeck(Prefs.deck(this))
        placeCards()
        startMatch()
    }

    override fun onResume() {
        super.onResume()
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        t.fast = autoPlay
        CardView.setDeck(Prefs.deck(this))
        placeCards()
        render()
        if (stopped) {
            // Si torna da un onStop: la mossa in corso e' sparita insieme ai callback, quindi
            // si riparte guardando lo stato reale della partita, esattamente come fa il
            // watchdog. Se e' aperto un dialogo, recover() se ne accorge e non tocca nulla.
            stopped = false
            recover()
        } else {
            maybeAutoPlay()
        }
    }

    /**
     * Posizioni fisse, calcolate una volta sola perche' dipendono solo dalle dimensioni
     * dello schermo: mano del Banco per meta' fuori dal bordo alto, mazzo per meta' fuori
     * dal bordo sinistro, griglia del tavolo rientrata cosi' non finisce sopra al mazzo.
     */
    private fun placeCards() {
        // se le carte del Banco sono scoperte devono restare tutte visibili
        (b.botHand.layoutParams as LinearLayout.LayoutParams).topMargin = if (showBot) 0 else -cardH / 2
        b.botHand.requestLayout()
        (b.deckBox.layoutParams as FrameLayout.LayoutParams).marginStart = -cardW / 2
        b.centerBox.setPaddingRelative(cardW / 2, 0, 0, 0)   // Relative: rispetta supportsRtl
    }

    /**
     * Rotazione o ridimensionamento della finestra su un dispositivo grande: l'activity non
     * viene ricreata (vedi configChanges nel manifest), quindi la partita in corso resta viva.
     * Basta ricalcolare le misure delle carte e ridisegnare.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        placeCards()
        render()
    }

    /**
     * Sospende la partita appena la schermata smette di essere visibile.
     *
     * I turni avanzano con postDelayed: senza questa pulizia il Banco continuava a giocare
     * mentre l'utente era altrove e a fine mano il riepilogo si apriva su una schermata che
     * nessuno stava guardando. Con il gioco automatico attivo l'app macinava partite intere
     * in background. Le carte in volo e i cartelli vengono tolti perche' al ritorno non hanno
     * piu' senso: la mossa interrotta viene rifatta da capo.
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

    /** postDelayed sicuro: il blocco non viene eseguito se l'activity nel frattempo e' morta. */
    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    /** Registra il dialogo appena aperto, cosi' onDestroy sa cosa chiudere. */
    private fun track(d: AlertDialog?): AlertDialog? {
        openDialog = d
        return d
    }

    private fun closeDialog() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
    }

    private fun startMatch() {
        val wait = Prefs.pauseRemaining(this)
        if (wait > 0) {
            // Nessuna mano in corso finche' il dialogo resta aperto: senza roundEnding il
            // watchdog scattava dopo 4 secondi e cambiava lo stato dietro alla finestra.
            roundEnding = true
            busy = true
            ui.removeCallbacks(watchdog)
            track(PauseDialog.show(this, wait, onReady = { beginMatch() }, onLeave = { finish() }))
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
        busy = true
        render()
        b.txtStatus.setText(if (game.turn == 1) R.string.bot_turn else R.string.your_turn)
        dealAnimation()
        // Il gioco riparte su un timer fisso, non alla fine dell'animazione: se le viste
        // non fossero ancora misurate l'effetto viene semplicemente saltato, ma la partita
        // parte lo stesso.
        post(t.deal) {
            if (destroyed || roundEnding) return@post
            if (game.turn == 1) {
                botTurn()
            } else {
                busy = false
                render()
                b.txtStatus.setText(R.string.your_turn)
                maybeAutoPlay()
            }
        }
    }

    /**
     * Distribuzione iniziale: mani e tavolo partono dal mazzo e raggiungono il loro posto.
     * Sposta le viste vere con translationX/Y, senza copie nell'overlay: se un render()
     * le ricrea a meta' animazione non resta nulla di sospeso.
     */
    private fun dealAnimation() {
        if (t.fast) return
        b.root.doOnLayout {
            if (destroyed) return@doOnLayout
            if (b.deckBox.width == 0) return@doOnLayout
            val views = ArrayList<View>()
            for (i in 0 until b.botHand.childCount) views.add(b.botHand.getChildAt(i))
            for (i in 0 until b.youHand.childCount) views.add(b.youHand.getChildAt(i))
            for (i in 0 until b.gridCenter.childCount) views.add(b.gridCenter.getChildAt(i))
            val (dx, dy) = topLeftInOverlay(b.deckBox)
            var delay = 0L
            for (v in views) {
                if (v.width == 0) continue
                val (tx, ty) = topLeftInOverlay(v)
                v.alpha = 0f
                v.translationX = dx - tx
                v.translationY = dy - ty
                v.animate().translationX(0f).translationY(0f).alpha(1f)
                    .setStartDelay(delay).setDuration(t.dealDur).start()
                delay += t.dealStep
            }
        }
    }

    /** "1 scopa" ma "2 scope": il singolare non si cava con un %d secco. */
    private fun scopeText(n: Int): String = resources.getQuantityString(R.plurals.scope_n, n, n)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeCard(card: Card?, faceUp: Boolean, w: Int, h: Int): CardView {
        val cv = CardView(this)
        cv.card = card
        cv.faceUp = faceUp
        val lp = LinearLayout.LayoutParams(w, h)
        lp.marginStart = dp(3)
        lp.marginEnd = dp(3)
        cv.layoutParams = lp
        return cv
    }

    private fun gridLp(): GridLayout.LayoutParams {
        val lp = GridLayout.LayoutParams()
        lp.width = cardW
        lp.height = cardH
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        return lp
    }

    /** Disegna il mazzo nel riquadro ancorato a sinistra, col numero di carte rimaste. */
    private fun renderDeck() {
        b.deckBox.removeAllViews()
        if (game.deck.isEmpty()) return
        val back = CardView(this); back.faceUp = false
        b.deckBox.addView(back, FrameLayout.LayoutParams(cardW, cardH))
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
        b.txtMatch.text = getString(R.string.match_line, matchYou, matchBot)
        b.txtMatch.tintByOutcome(matchYou > matchBot)
        b.botScore.text = getString(R.string.bot_points, scopeText(game.scope[1]))
        b.youScore.text = getString(R.string.you_points, scopeText(game.scope[0]))

        b.botHand.removeAllViews()
        for (c in game.hands[1]) b.botHand.addView(makeCard(if (showBot) c else null, showBot, cardW, cardH))

        b.youHand.removeAllViews()
        for (c in game.hands[0]) {
            val cv = makeCard(c, true, cardW, cardH)
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
            track(
                AlertDialog.Builder(this)
                    .setTitle(R.string.choose_capture)
                    .setItems(labels) { _, which -> doPlayerPlay(card, fromView, caps[which]) }
                    .setCancelable(true)
                    .show()
            )
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
        post(t.think) {
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
        val src: View = botHandView(card) ?: b.botHand
        val (sx, sy) = topLeftInOverlay(src)
        playAnimated(card, cap, sx, sy, byBot = true)
    }

    /**
     * Vista da cui deve partire la carta calata dal Banco. Le carte del Banco sono coperte,
     * ma render() le crea nell'ordine della mano, quindi la posizione si ricava dall'indice.
     * Prima partiva sempre quella a sinistra: se il Banco aveva scelto un'altra carta, a
     * sinistra ne spariva una e in mezzo al tavolo ne compariva un'altra.
     */
    private fun botHandView(card: Card): View? {
        val i = game.hands[1].indexOf(card)
        return if (i in 0 until b.botHand.childCount) b.botHand.getChildAt(i) else null
    }

    /**
     * La carta calata scivola al centro del tavolo e resta ferma un istante. Se c'e' una
     * presa, le carte prese la raggiungono infilandosi sotto. Tutti i tempi passano dallo
     * stesso Handler: niente doOnPreDraw e niente animazioni annidate, che erano l'origine
     * dei blocchi. In gioco automatico l'animazione si salta del tutto e si passa subito
     * alla mossa successiva, sempre pero' attraverso l'Handler, cosi' le mosse non si
     * annidano l'una dentro l'altra sullo stack.
     */
    private fun playAnimated(card: Card, capture: List<Card>, sx: Float, sy: Float, byBot: Boolean) {
        if (t.fast) {
            post(0) { finishPlay(card, capture, byBot) }
            return
        }
        val (cx, cy) = tableCenter()
        val tx = cx - cardW / 2f
        val ty = cy - cardH / 2f
        val played = addTempCard(card, true, sx, sy, cardW, cardH)
        played.animate().x(tx).y(ty).setDuration(t.playDur).start()
        post(t.hold) {
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

    /** Le carte prese raggiungono il centro del tavolo e finiscono sotto la carta calata. */
    private fun gatherCaptured(capture: List<Card>, tx: Float, ty: Float, played: View, onDone: () -> Unit) {
        val temps = ArrayList<View>()
        for (c in capture) {
            val v = findTableCardView(c)
            val start = if (v != null) {
                val p = topLeftInOverlay(v); v.visibility = View.INVISIBLE; p
            } else Pair(tx, ty)
            temps.add(addTempCard(c, true, start.first, start.second, cardW, cardH))
        }
        // la carta calata torna in cima: le prese devono scivolarle sotto
        played.bringToFront()
        for ((i, tmp) in temps.withIndex()) {
            tmp.animate().x(tx + dp(7) + i * dp(5)).y(ty + dp(12) + i * dp(5))
                .setDuration(t.gatherDur).start()
        }
        post(t.gather) {
            for (tmp in temps) b.overlay.removeView(tmp)
            onDone()
        }
    }

    private fun findTableCardView(card: Card): View? {
        for (i in 0 until b.gridCenter.childCount) {
            val ch = b.gridCenter.getChildAt(i)
            if (ch is CardView && ch.card == card) return ch
        }
        return null
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
        // in gioco automatico i cartelli si saltano: non c'e' nessuno che li legga
        val hasBanner = events.isNotEmpty() && !t.fast
        if (hasBanner) showBanner(events.joinToString("   "), byBot)

        if (game.finished) {
            busy = true
            render()
            // lascio finire il cartello prima del riepilogo, ma senza bloccare nulla
            if (hasBanner) post(t.banner + 100) { endRound() } else endRound()
            return
        }

        val proceed = {
            if (byBot) {
                busy = false
                render()
                b.txtStatus.setText(R.string.your_turn)
                maybeAutoPlay()
            } else {
                busy = true
                render()
                b.txtStatus.setText(R.string.bot_turn)
                // Il Banco si prende il suo tempo prima di rispondere, come in Briscola.
                // Chiamando botTurn() qui di seguito, la scritta "Gioca il Banco" e la carta
                // del Banco comparivano nello stesso istante.
                post(t.think) { botTurn() }
            }
        }

        if (hasBanner) {
            // il tavolo si aggiorna subito, ma si lascia leggere l'avviso prima di proseguire
            busy = true
            render()
            post(t.banner) { proceed() }
        } else {
            proceed()
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
        post(t.banner - 150) { tv.animate().alpha(0f).setDuration(300).start() }
        post(t.banner + 200) { b.overlay.removeView(tv) }
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
        if (over) {
            Prefs.markMatchEnded(this)
            Prefs.recordMatch(this, Prefs.GAME_SCOPA, matchYou > matchBot)
        }

        val v = DialogScoreBinding.inflate(layoutInflater)
        v.youCarte.text = you.carte.toString();          v.botCarte.text = bot.carte.toString()
        v.youDenari.text = you.denari.toString();        v.botDenari.text = bot.denari.toString()
        v.youSette.text = you.settebello.toString();     v.botSette.text = bot.settebello.toString()
        v.youPrimiera.text = you.primiera.toString();    v.botPrimiera.text = bot.primiera.toString()
        v.youScope.text = you.scope.toString();          v.botScope.text = bot.scope.toString()
        v.youTot.text = you.total.toString();            v.botTot.text = bot.total.toString()
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
            builder.setPositiveButton(R.string.continue_match) { _, _ -> startRound() }
        }
        track(builder.show())
    }
}
