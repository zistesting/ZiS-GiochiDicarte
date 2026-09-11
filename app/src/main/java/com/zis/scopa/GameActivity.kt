package com.zis.scopa

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.zis.scopa.databinding.ActivityGameBinding
import com.zis.scopa.databinding.DialogScoreBinding

/**
 * Scopa.
 *
 * Il ciclo di vita, il salvataggio, il watchdog, la fine mano e la cornice del riepilogo
 * stanno in [BotGameActivity], insieme a Briscola e Tresette. Qui resta quello che e' di
 * questo gioco, e non e' poco: la Scopa e' la piu' diversa delle tre.
 *
 *  - il tavolo e' una griglia di carte scoperte, non una presa di due;
 *  - la stessa carta puo' prendere in piu' modi, quindi c'e' un dialogo per scegliere la
 *    presa, e mentre e' aperto la mano puo' essere ricostruita sotto (vedi [doPlayerPlay]);
 *  - le carte prese si raccolgono infilandosi sotto quella calata, con un'animazione che
 *    negli altri due non c'e';
 *  - i cartelli SCOPA e SETTEBELLO, che sono l'unico posto dove il gioco si ferma per far
 *    leggere qualcosa;
 *  - il riepilogo ha sette righe invece di due, quindi usa `DialogScoreBinding` e non
 *    `DialogResultBinding`.
 *
 * L'incontro si conta a punti, quindi [matchOver] non si ridefinisce: il predefinito della
 * base, che in parita' sul traguardo non assegna niente, e' gia' la regola della Scopa.
 */
class GameActivity : BotGameActivity() {

    private lateinit var b: ActivityGameBinding
    private val game = ScopaGame()

    // ---- quello che la base ha bisogno di sapere ----
    override val rootView: View get() = b.root
    override val overlay: FrameLayout get() = b.overlay
    override val statusView: TextView get() = b.txtStatus
    override val saveKey: String get() = SavedGame.SCOPA
    override val statsKey: String get() = Prefs.GAME_SCOPA
    override val rulesText: Int get() = R.string.rules_scopa

    override val gameFinished: Boolean get() = game.finished
    override val isBotTurn: Boolean get() = game.turn == 1
    override val youHandEmpty: Boolean get() = game.hands[0].isEmpty()
    override val hasLastDeal: Boolean get() = game.lastDealState != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGameBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Dopo setContentView: rootView e overlay sono getter su b, che e' lateinit.
        setupCommon(b.btnInfo)
        // Se c'e' una partita lasciata a meta' si riprende quella, altrimenti se ne comincia una.
        if (!restoreState()) startMatch()
    }

    /**
     * Posizioni fisse, calcolate una volta sola perche' dipendono solo dalle dimensioni
     * dello schermo: mano del Banco per meta' fuori dal bordo alto, mazzo per meta' fuori
     * dal bordo sinistro, griglia del tavolo rientrata cosi' non finisce sopra al mazzo.
     *
     * I margini si RIASSEGNANO, non si modificano in posto.
     *
     * `(view.layoutParams as X).topMargin = n` cambia il valore dentro l'oggetto che la
     * vista sta gia' usando, e nessuno se ne accorge: setLayoutParams chiama
     * requestLayout() da sola, mutare i campi no. Finora funzionava per un effetto
     * collaterale - subito dopo placeCards() arriva sempre un render() che cambia la
     * misura di qualche carta, e quello il layout lo chiede - ma e' una dipendenza da un
     * ordine di chiamate che sta in un altro metodo: il giorno che render() non cambia
     * nessuna misura (stesse carte, stesse dimensioni, per esempio tornando dalle
     * impostazioni dopo aver solo spento "mostra carte del Banco") la mano del Banco
     * resterebbe dov'era. Costa una riga, e il difetto non puo' piu' ripresentarsi.
     */
    override fun placeCards() {
        // se le carte del Banco sono scoperte devono restare tutte visibili
        val bot = b.botHand.layoutParams as LinearLayout.LayoutParams
        bot.topMargin = if (showBot) 0 else -cardH / 2
        b.botHand.layoutParams = bot
        val deck = b.deckBox.layoutParams as FrameLayout.LayoutParams
        deck.marginStart = -cardW / 2
        b.deckBox.layoutParams = deck
        b.centerBox.setPaddingRelative(cardW / 2, 0, 0, 0)   // Relative: rispetta supportsRtl
    }

    // ---------------------------------------------------------- l'incontro

    override fun beginMatch() {
        matchTarget = Prefs.scoreTarget(this)
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
        moveSeq++
        busy = true
        render()
        statoTurno()
        dealAnimation()
        // Il gioco riparte su un timer fisso, non alla fine dell'animazione: se le viste
        // non fossero ancora misurate l'effetto viene semplicemente saltato, ma la partita
        // parte lo stesso.
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

    /** Mani e tavolo partono dal mazzo e raggiungono il loro posto. */
    private fun dealAnimation() {
        dealFrom(b.deckBox) {
            val views = ArrayList<View>()
            for (i in 0 until b.botHand.childCount) views.add(b.botHand.getChildAt(i))
            for (i in 0 until b.youHand.childCount) views.add(b.youHand.getChildAt(i))
            for (i in 0 until b.gridCenter.childCount) views.add(b.gridCenter.getChildAt(i))
            views
        }
    }

    /** Nella Scopa l'incontro si somma mano dopo mano fino al bersaglio (11, 16 o 21 punti). */
    override fun awardHand() {
        matchYou += game.scoreFor(0).total
        matchBot += game.scoreFor(1).total
    }

    // ------------------------------------------------------------- disegno

    /** "1 scopa" ma "2 scope": il singolare non si cava con un %d secco. */
    private fun scopeText(n: Int): String = resources.getQuantityString(R.plurals.scope_n, n, n)

    // ---------- riuso delle viste ----------
    //
    // Prima render() faceva removeAllViews() e ricostruiva tutto: fino a una quindicina di
    // CardView buttate e riallocate a ogni chiamata, e render() viene chiamata piu' volte per
    // ogni giocata. CardView era gia' scritta per essere riusata (i setter di card e faceUp
    // chiamano invalidate()), ma nessuno la riusava.
    //
    // Adesso le viste restano e si aggiornano le proprieta'. L'unica cosa a cui stare attenti
    // e' che le animazioni lasciano dietro di se' dello stato sulla vista (visibility a
    // INVISIBLE, translation, alpha): prima lo azzerava la ricostruzione, ora lo azzera
    // resetCard, che sta nella base.

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

    override fun render() {
        b.botScore.text = getString(R.string.bot_points, scopeText(game.scope[1]))
        b.youScore.text = getString(R.string.you_points, scopeText(game.scope[0]))

        syncHand(b.botHand, game.hands[1], faceUp = showBot, clickable = false)
        syncHand(b.youHand, game.hands[0], faceUp = true, clickable = true)

        renderDeck()
        syncTable(game.table)
        armWatchdog()
    }

    // ------------------------------------------------------------ giocate

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

    override fun autoPlayMove() {
        val (card, cap) = game.choose(0)
        val view = findHandCardView(card)
        val start = if (view != null) {
            view.visibility = View.INVISIBLE
            topLeftInOverlay(view)
        } else centerInOverlay(b.youHand)
        playAnimated(card, cap, start.first, start.second, byBot = false)
    }

    private fun findHandCardView(card: Card): View? {
        for (i in 0 until b.youHand.childCount) {
            val ch = b.youHand.getChildAt(i)
            if (ch is CardView && ch.card == card) return ch
        }
        return null
    }

    override fun playBot() {
        if (destroyed || ending) return
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
                clearStatus()
                maybeAutoPlay()
            } else {
                busy = true
                render()
                statusView.setText(R.string.bot_turn)
                // Il Banco si prende il suo tempo prima di rispondere, come in Briscola.
                // Chiamando playBot() qui di seguito, la scritta "Gioca il Banco" e la carta
                // del Banco comparivano nello stesso istante.
                post(t.think) { playBot() }
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

    // ---------- attrezzi delle animazioni ----------

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

    // --------------------------------------------------------- fine mano

    /**
     * La tabella del riepilogo: sette righe, perche' nella Scopa la partita e' una somma di
     * quattro punti piu' le scope, e non un numero solo come negli altri due. Colonne,
     * caratteri e allineamento sono gli stessi, cosi' passando da un gioco all'altro il
     * riepilogo sta sempre allo stesso posto.
     */
    override fun buildResultView(over: Boolean): View {
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
        for (tv in listOf(v.lblTot, v.youTot, v.botTot)) tv.tintByScore(you.total, bot.total)
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
