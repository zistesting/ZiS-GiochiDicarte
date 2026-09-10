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
                b.txtStatus.text = ""
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
        // Se c'e' una partita lasciata a meta' si riprende quella, altrimenti se ne comincia una.
        if (!restoreState()) startMatch()
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
        if (isFinishing) SavedGame.clear(this, SavedGame.SCOPA)
        ui.removeCallbacksAndMessages(null)
        closeDialog()
        super.onDestroy()
    }

    // ---------------- salvataggio e ripristino ----------------

    private fun saveState() {
        if (!started) return
        val w = SavedGame.Writer()
        game.save(w)
        w.ints(listOf(target, matchYou, matchBot, if (youStartNext) 1 else 0))
        w.ints(listOf(matchBeforeEnd.first, matchBeforeEnd.second))
        w.int(when (recordedWin) { true -> 1; false -> 0; null -> -1 })
        w.long(prevMatchEnd)
        w.bool(roundScored)
        SavedGame.write(this, SavedGame.SCOPA, w)
    }

    /**
     * Vero se c'era una partita da riprendere. Le sezioni si rileggono nello stesso ordine in
     * cui saveState le ha scritte; se il testo non torna si butta il salvataggio e si
     * ricomincia, che e' meglio che ripartire da uno stato a meta'.
     */
    private fun restoreState(): Boolean {
        val r = SavedGame.read(this, SavedGame.SCOPA) ?: return false
        try {
            game.load(r)
            val m = r.ints()
            target = m[0]; matchYou = m[1]; matchBot = m[2]; youStartNext = m[3] == 1
            val mb = r.ints(); matchBeforeEnd = Pair(mb[0], mb[1])
            recordedWin = when (r.int()) { 1 -> true; 0 -> false; else -> null }
            prevMatchEnd = r.long()
            roundScored = r.bool()
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.SCOPA)
            return false
        }
        started = true
        moveSeq++
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
        roundScored = false
        started = true
        moveSeq++
        busy = true
        render()
        statoTurno()
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
                b.txtStatus.text = ""
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

    // ---------- riuso delle viste ----------
    //
    // Prima render() faceva removeAllViews() e ricostruiva tutto: fino a una quindicina di
    // CardView buttate e riallocate a ogni chiamata, e render() viene chiamata piu' volte per
    // ogni giocata. CardView era gia' scritta per essere riusata (i setter di card e faceUp
    // chiamano invalidate()), ma nessuno la riusava.
    //
    // Adesso le viste restano e si aggiornano le proprieta'. L'unica cosa a cui stare attenti
    // e' che le animazioni lasciano dietro di se' dello stato sulla vista (visibility a
    // INVISIBLE, translation, alpha): prima lo azzerava la ricostruzione, ora va rimesso a
    // mano in resetCard, altrimenti una carta resta invisibile o spostata per sempre.

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

    private fun newCard(): CardView = CardView(this)

    /** Allinea una fila (mano) al contenuto voluto, riusando le viste gia' presenti. */
    private fun syncHand(row: LinearLayout, cards: List<Card>, faceUp: Boolean, clickable: Boolean) {
        while (row.childCount > cards.size) row.removeViewAt(row.childCount - 1)
        while (row.childCount < cards.size) {
            val lp = LinearLayout.LayoutParams(cardW, cardH)
            lp.marginStart = dp(3)
            lp.marginEnd = dp(3)
            row.addView(newCard(), lp)
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

    /** Come syncHand, ma per la griglia del tavolo. */
    private fun syncTable(cards: List<Card>) {
        val grid = b.gridCenter
        while (grid.childCount > cards.size) grid.removeViewAt(grid.childCount - 1)
        while (grid.childCount < cards.size) {
            val lp = GridLayout.LayoutParams()
            lp.width = cardW
            lp.height = cardH
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            grid.addView(newCard(), lp)
        }
        for (i in cards.indices) {
            val cv = grid.getChildAt(i) as CardView
            val lp = cv.layoutParams as GridLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; cv.layoutParams = lp
            }
            resetCard(cv)
            cv.card = cards[i]
            cv.faceUp = true
        }
    }

    // Le due viste del mazzo si creano una volta sola e restano: cambia solo il numero sopra.
    private var deckBack: CardView? = null
    private var deckCount: TextView? = null

    /** Disegna il mazzo nel riquadro ancorato a sinistra, col numero di carte rimaste. */
    private fun renderDeck() {
        if (deckBack == null) {
            val back = CardView(this)
            back.faceUp = false
            b.deckBox.addView(back, FrameLayout.LayoutParams(cardW, cardH))
            deckBack = back

            val tv = TextView(this)
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
            deckCount = tv
        }
        val n = game.deck.size
        val visible = if (n > 0) View.VISIBLE else View.GONE
        deckBack?.let {
            val lp = it.layoutParams as FrameLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH; it.layoutParams = lp
            }
            it.visibility = visible
        }
        deckCount?.let {
            it.text = n.toString()
            // il numero da solo non dice niente a TalkBack
            it.contentDescription =
                if (n > 0) getString(R.string.cd_deck, n) else getString(R.string.cd_deck_empty)
            it.visibility = visible
        }
    }

    private fun render() {
        b.botScore.text = getString(R.string.bot_points, scopeText(game.scope[1]))
        b.youScore.text = getString(R.string.you_points, scopeText(game.scope[0]))

        syncHand(b.botHand, game.hands[1], faceUp = showBot, clickable = false)
        syncHand(b.youHand, game.hands[0], faceUp = true, clickable = true)

        renderDeck()
        syncTable(game.table)
        armWatchdog()
    }

    // ---------- mossa dell'utente ----------
    private fun onPlayerCard(card: Card, fromView: View) {
        if (busy || game.turn != 0 || game.finished) return
        val caps = game.capturesFor(card.value)
        if (caps.size > 1) {
            val labels = caps.map { opt -> opt.joinToString(" + ") { it.nome(resources, false) } }.toTypedArray()
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
        // La vista si rilegge adesso e non si usa quella del tocco: se in mezzo c'e' stato
        // un onStop (dialogo "Scegli la presa" aperto, utente in home e ritorno) render()
        // ha ricreato la mano e fromView e' una vista staccata, con coordinate a caso.
        val src = findHandCardView(card) ?: fromView
        busy = true
        armWatchdog()
        src.visibility = View.INVISIBLE
        val (sx, sy) = topLeftInOverlay(src)
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
        if (scopa) events.add(getString(R.string.banner_scopa))
        // Settebello secured this turn: either captured from the table, or played as the capturing card.
        val gotSettebello = capture.any { it.isSettebello } || (playedCard.isSettebello && capture.isNotEmpty())
        if (gotSettebello) events.add(getString(R.string.banner_settebello))
        if (events.isEmpty() && capture.size >= 3) events.add(getString(R.string.banner_nice_take))
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
                b.txtStatus.text = ""
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


    /**
     * Riporta la mano all'inizio delle ultime carte (vedi la fotografia nel motore), rimette
     * i punteggi della partita com'erano e fa ripartire il gioco da li'. Il Banco non tira
     * a caso: le sue carte saranno le stesse, cambia solo quello che decidi tu.
     */
    private fun replayLastDeal() {
        if (!game.restoreLastDeal()) return
        matchYou = matchBeforeEnd.first
        matchBot = matchBeforeEnd.second
        undoMatchRecord(Prefs.GAME_SCOPA)
        roundEnding = false
        roundScored = false
        moveSeq++
        busy = true
        statoTurno()
        // recover() legge lo stato reale: fa giocare il Banco se tocca a lui, altrimenti
        // libera la mano per la tua giocata
        recover()
    }

    // ---------- end of round / match ----------
    /** In parita' sul traguardo non si assegna la partita: si gioca un'altra mano. */
    private fun matchOver(): Boolean =
        (matchYou >= target || matchBot >= target) && matchYou != matchBot

    /**
     * Fine mano: assegna i punti, li registra se l'incontro e' finito, e mostra il riepilogo.
     *
     * L'assegnazione e la finestra sono separate perche' il ripristino ha bisogno solo della
     * seconda: riprendendo una partita chiusa col riepilogo aperto, i punti sono gia' stati
     * dati e rifarli significherebbe raddoppiarli.
     */
    private fun endRound() {
        if (roundEnding || destroyed || isFinishing) return
        roundEnding = true
        ui.removeCallbacks(watchdog)
        busy = true
        matchBeforeEnd = Pair(matchYou, matchBot)
        matchYou += game.scoreFor(0).total
        matchBot += game.scoreFor(1).total
        val over = matchOver()
        recordedWin = null
        if (over) {
            prevMatchEnd = Prefs.lastMatchEnd(this)
            Prefs.markMatchEnded(this)
            recordedWin = matchYou > matchBot
            Prefs.recordMatch(this, Prefs.GAME_SCOPA, matchYou > matchBot)
        }
        roundScored = true
        render()
        showRoundDialog(over)
    }

    /** Rimette il riepilogo dopo un ripristino, senza toccare il punteggio. */
    private fun resumeRoundDialog() {
        roundEnding = true
        busy = true
        render()
        showRoundDialog(matchOver())
    }

    /**
     * Il riepilogo della mano. I punteggi si rileggono dal motore invece di essere passati
     * come parametri: a mano finita lo stato non cambia piu', quindi il conto e' lo stesso
     * sia che si arrivi da endRound sia da un ripristino, e non c'e' un secondo posto in cui
     * tenerli allineati.
     */
    private fun showRoundDialog(over: Boolean) {
        if (destroyed || isFinishing) return
        val you = game.scoreFor(0)
        val bot = game.scoreFor(1)

        val v = DialogScoreBinding.inflate(layoutInflater)
        v.youCarte.text = you.carte.toString();          v.botCarte.text = bot.carte.toString()
        v.youDenari.text = you.denari.toString();        v.botDenari.text = bot.denari.toString()
        v.youSette.text = you.settebello.toString();     v.botSette.text = bot.settebello.toString()
        v.youPrimiera.text = you.primiera.toString();    v.botPrimiera.text = bot.primiera.toString()
        v.youScope.text = you.scope.toString();          v.botScope.text = bot.scope.toString()
        v.youTot.text = you.total.toString();            v.botTot.text = bot.total.toString()
        v.youMatch.text = matchYou.toString();           v.botMatch.text = matchBot.toString()

        // Le due righe riassuntive prendono il colore di chi e' avanti, ma su due conti
        // diversi: il Totale guarda questa partita, l'Incontro la serie. Possono quindi
        // risultare di colori opposti, ed e' giusto cosi': hai vinto la partita ma sei
        // ancora sotto nell'incontro, o viceversa.
        for (t in listOf(v.lblTot, v.youTot, v.botTot)) t.tintByScore(you.total, bot.total)
        for (t in listOf(v.lblMatch, v.youMatch, v.botMatch)) t.tintByScore(matchYou, matchBot)
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
        if (game.lastDealState != null) {
            builder.setNeutralButton(R.string.replay_last_deal) { _, _ -> replayLastDeal() }
        }
        val dialog = builder.show()
        // I pulsanti dei dialoghi sono in maiuscolo per impostazione del tema, e "RIGIOCA
        // L'ULTIMA MANO" tutto maiuscolo non ci sta: veniva troncato in "RIGIOCA L'ULTIMA
        // MA...". Spegnendo il maiuscolo solo su questo pulsante il testo entra per intero e
        // resta la parola RIGIOCA in evidenza, che e' quella che conta.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isAllCaps = false
        track(dialog)
    }
    /**
     * La riga di stato per il turno: VUOTA quando tocca a te.
     *
     * "Tocca a te: gioca una carta" non informava nessuno - a gioco fermo tocca sempre a te -
     * e la riga della partita ("Incontro: Tu x - x Banco") portava via spazio al tavolo per un
     * dato che il riepilogo di fine partita mostra comunque, alla voce Incontro.
     *
     * La riga pero' NON e' stata tolta, perche' ci passano due cose che ovvie non sono: che
     * sta giocando il Banco, e chi ha preso la mano. E nel layout ha android:lines="1", cioe'
     * resta alta una riga anche da vuota: se collassasse, il tavolo salterebbe su e giu' di
     * venti punti a ogni cambio di turno, che e' peggio del testo che si e' tolto.
     */
    private fun statoTurno() {
        if (game.turn == 1) b.txtStatus.setText(R.string.bot_turn) else b.txtStatus.text = ""
    }

}
