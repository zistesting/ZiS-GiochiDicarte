package com.zis.scopa

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout

/**
 * L'impalcatura comune alle tre schermate che si giocano contro il Banco: Scopa, Briscola e
 * Tresette.
 *
 * PERCHE' ESISTE. Fra BriscolaActivity e TresetteActivity c'erano 424 righe identiche su 576,
 * cioe' il 74%, e trentadue funzioni con lo stesso nome comparivano in tutte tre. Non era una
 * questione di eleganza: il difetto che nasce da li' si e' visto due volte. La guardia del
 * blocco differito del gioco automatico chiamava recover() in Scopa e Briscola e non lo
 * chiamava nel Tresette, dove `busy` restava acceso e a sbloccare la partita finiva per
 * essere il watchdog quattro secondi dopo; e i margini di placeCards erano da correggere in
 * tre punti, ma di punti ne erano stati notati due. Con una copia sola quelle due
 * disuguaglianze non si possono piu' dare, e ogni correzione futura vale per tre schermate
 * invece che per una.
 *
 * COSA STA QUI E COSA NO. Qui sta tutto quello che non dipende dalle regole del gioco: il
 * ciclo di vita, il salvataggio della partita, il watchdog, la fine mano con l'assegnazione
 * dell'incontro e la sua registrazione nelle statistiche, la pausa responsabile, la cornice
 * del riepilogo, il volo delle carte in distribuzione, le coordinate dentro l'overlay. Le
 * regole, il disegno del tavolo e le animazioni proprie di ogni gioco restano nelle tre
 * sottoclassi, che le forniscono attraverso i membri astratti qui sotto.
 *
 * Il **Klondike non c'entra** e resta com'e': e' un solitario, non ha un avversario, non ha
 * ne' turni ne' incontro, e la sua schermata non ha niente in comune con queste tre.
 *
 * ORDINE DI PARTENZA. `rootView`, `overlay` e `statusView` sono getter su `b`, che nelle
 * sottoclassi e' `lateinit`: quindi [setupCommon] va chiamata in onCreate **dopo**
 * setContentView, e nessun metodo di questa classe deve essere chiamato prima. Il costruttore
 * non tocca nessuna vista, per la stessa ragione.
 */
abstract class BotGameActivity : AppCompatActivity() {

    // ---------------------------------------------------------------- viste

    /** La vista radice della schermata, cioe' `b.root`: le serve applySystemBars. */
    protected abstract val rootView: View

    /** Il riquadro trasparente sopra il tavolo, dove volano le copie delle carte. */
    protected abstract val overlay: FrameLayout

    /** La riga di stato sotto il tavolo: "Gioca il Banco", "Presa tua". */
    protected abstract val statusView: TextView

    // ------------------------------------------------------------ identita'

    /** Chiave del salvataggio della partita in corso: una delle costanti di [SavedGame]. */
    protected abstract val saveKey: String

    /** Chiave delle statistiche: una delle costanti `Prefs.GAME_*`. */
    protected abstract val statsKey: String

    /** Il testo delle regole aperto dal pulsante info: una delle `R.string.rules_*`. */
    protected abstract val rulesText: Int

    // ------------------------------------------- lo stato letto dal motore
    //
    // Sono tre domande che i motori sanno rispondere ma con nomi diversi e tipi diversi
    // (scoreFor restituisce un Int in Briscola e Tresette e un oggetto in Scopa). Passano
    // da qui invece di tenere un riferimento al motore nella base: cosi' i tre motori
    // restano quello che sono, cioe' Kotlin puro che non sa niente di Android e di questa
    // gerarchia, e non serve inventare un'interfaccia comune per farceli entrare.

    /** Vero quando la mano e' finita e c'e' solo da fare i conti. */
    protected abstract val gameFinished: Boolean

    /** Vero quando tocca al Banco. */
    protected abstract val isBotTurn: Boolean

    /** Vero quando non hai piu' carte in mano. */
    protected abstract val youHandEmpty: Boolean

    /** Vero se il motore ha conservato la fotografia dell'ultima distribuzione. */
    protected abstract val hasLastDeal: Boolean

    // ------------------------------------------- quello che fa ogni gioco

    /** Ridisegna la schermata leggendo lo stato del motore. */
    protected abstract fun render()

    /** Sistemazione fissa degli elementi, quella che dipende solo dalle misure dello schermo. */
    protected abstract fun placeCards()

    /** Fa giocare il Banco. */
    protected abstract fun playBot()

    /**
     * La mossa che il gioco automatico fa al posto tuo. Viene chiamata a guardie gia'
     * superate e con `busy` gia' acceso: qui c'e' solo la scelta della carta e la sua
     * animazione.
     */
    protected abstract fun autoPlayMove()

    /** Comincia un incontro nuovo: bersaglio dalle impostazioni, punteggi a zero. */
    protected abstract fun beginMatch()

    /** Distribuisce e mette in moto una mano nuova. */
    protected abstract fun startRound()

    /**
     * Somma al punteggio dell'incontro quello che la mano appena chiusa ha prodotto.
     *
     * E' l'unico punto in cui i tre giochi contano in modo davvero diverso: la Scopa e il
     * Tresette sommano i punti della mano, la Briscola assegna una mano vinta a chi ha fatto
     * piu' di sessanta (e a nessuno se sono sessanta pari).
     */
    protected abstract fun awardHand()

    /** La tabella del riepilogo di fine mano, da mettere dentro la finestra. */
    protected abstract fun buildResultView(over: Boolean): View

    /** Scrive lo stato del motore nel salvataggio. */
    protected abstract fun saveGame(w: SavedGame.Writer)

    /** Rilegge lo stato del motore dal salvataggio. */
    protected abstract fun loadGame(r: SavedGame.Reader)

    /** Riporta il motore all'inizio dell'ultima distribuzione; falso se non e' possibile. */
    protected abstract fun restoreLastDeal(): Boolean

    // ------------------------------------ comportamenti con un predefinito

    /**
     * Azzera lo stato lasciato a meta' dalle animazioni. Vuoto per la Scopa, che non ne ha;
     * Briscola e Tresette lo ridefiniscono per spegnere `hideDrawn`, che se restasse acceso
     * terrebbe fuori dalla mano le carte appena pescate per tutto il resto della partita.
     */
    protected open fun clearAnimationState() {}

    /**
     * Rilegge le impostazioni che possono essere cambiate mentre la schermata era in secondo
     * piano. Il Tresette lo ridefinisce per aggiungere il tempo di esposizione della carta
     * pescata.
     */
    protected open fun readSettings() {
        autoPlay = Prefs.autoPlay(this)
        showBot = Prefs.showBotCards(this)
        t.fast = autoPlay
    }

    /**
     * Vero quando l'incontro e' deciso.
     *
     * Il predefinito e' quello di Scopa e Tresette: sul traguardo in parita' l'incontro non
     * si assegna e si gioca un'altra mano. **La Briscola lo ridefinisce** senza quella
     * condizione, perche' li' si contano mani vinte, una per volta, e arrivare al bersaglio
     * in parita' non e' un caso che possa darsi.
     */
    protected open fun matchOver(): Boolean =
        (matchYou >= matchTarget || matchBot >= matchTarget) && matchYou != matchBot

    // --------------------------------------------------------- stato comune

    /** Tutti i tempi di gioco: a zero quando gioca il programma. */
    protected val t = Timing()

    /** L'unico Handler della schermata: tutte le mosse passano da qui, una alla volta. */
    protected val ui = Handler(Looper.getMainLooper())

    /** Vero mentre una mossa e' in corso: i tocchi non vengono raccolti. */
    protected var busy = false

    /** Vero da quando le carte sono in tavola: prima non c'e' niente da salvare. */
    protected var started = false

    protected var destroyed = false

    /** Vero fra onStop e il ritorno in primo piano: vedi [onStop] e [onResume]. */
    protected var stopped = false

    /** Vero da fine mano finche' non ne comincia un'altra: il gioco non deve muoversi. */
    protected var ending = false

    /**
     * Vero quando i punti della mano sono gia' stati sommati all'incontro e registrati.
     *
     * Serve solo al ripristino, ed e' l'unico dato che non si potrebbe ricavare guardando la
     * partita: a mano finita, lo stato del motore e' identico prima e dopo l'assegnazione dei
     * punti. Senza questo, riprendere una partita chiusa col riepilogo aperto rifarebbe i
     * conti una seconda volta, raddoppiando i punti dell'incontro e la vittoria registrata
     * nelle statistiche.
     */
    protected var roundScored = false

    /**
     * Contatore delle mosse completate. Il watchdog lo confronta con il valore che aveva
     * quando e' stato armato: se il gioco e' andato avanti da solo non fa nulla, se invece
     * e' fermo interviene. Senza questo confronto il watchdog rischiava di far giocare il
     * Banco una seconda volta mentre una mossa era ancora in corso.
     */
    protected var moveSeq = 0

    private var watchdogSeq = -1

    /**
     * Riferimento all'ultimo dialogo aperto, per chiuderlo in onDestroy.
     * I dialoghi creati con AlertDialog.Builder non si chiudono da soli quando l'activity
     * muore: restano appesi al suo contesto, il log segna WindowLeaked e l'activity non
     * viene liberata. Non ce n'e' mai piu' di uno aperto insieme, quindi basta un campo.
     */
    private var openDialog: AlertDialog? = null

    protected var autoPlay = false
    protected var showBot = false

    // ---- incontro ----
    /** Il bersaglio dell'incontro. Non ha un valore sensato finche' non passa da
     *  [beginMatch] o da [restoreState], e fino a quel momento nessuno lo legge. */
    protected var matchTarget = 0
    protected var matchYou = 0
    protected var matchBot = 0
    protected var youStartNext = true

    // ---- rigioca le ultime carte: cosa serve per riportare indietro l'orologio ----
    /** Punteggi della partita prima che la mano appena finita venisse sommata. */
    protected var matchBeforeEnd = Pair(0, 0)

    /** Esito registrato nelle statistiche a fine partita (null se la partita non e' finita). */
    protected var recordedWin: Boolean? = null

    /** Valore di last_match_end prima di questa fine partita, per rimetterlo se si rigioca. */
    protected var prevMatchEnd = 0L

    // Misure delle carte in tavola: vedi CardSize, dipendono da larghezza e altezza dello
    // schermo. Il Tresette ne aggiunge una seconda, piu' piccola, per le dieci carte in mano.
    protected val cardW get() = CardSize.width(resources)
    protected val cardH get() = CardSize.height(resources)

    // ------------------------------------------------------------ watchdog

    private val watchdog = Runnable {
        if (destroyed || ending) return@Runnable
        if (moveSeq != watchdogSeq) return@Runnable   // il gioco si e' mosso: nulla da fare
        recover()
    }

    /**
     * Armato solo quando il gioco deve muoversi da solo: durante una giocata, quando tocca
     * al Banco o a fine mano. Se tocca all'utente non serve, perche' non c'e' niente da
     * aspettare.
     */
    protected fun armWatchdog() {
        ui.removeCallbacks(watchdog)
        if (destroyed || ending) return
        if (busy || gameFinished || isBotTurn) {
            watchdogSeq = moveSeq
            ui.postDelayed(watchdog, WATCHDOG_MS)
        }
    }

    /**
     * Riporta il gioco in moto guardando lo stato reale della partita, qualunque cosa sia
     * andata storta. Prima un blocco catch metteva busy = false lasciando il turno al Banco
     * senza che nessuno lo facesse giocare: la partita restava ferma per sempre e i tocchi
     * dell'utente venivano ignorati perche' il turno non era il loro.
     */
    protected fun recover() {
        if (destroyed || ending) return
        when {
            gameFinished -> { busy = true; render(); endRound() }
            isBotTurn -> { busy = true; render(); post(t.think) { playBot() } }
            busy -> { busy = false; render(); clearStatus(); maybeAutoPlay() }
            else -> maybeAutoPlay()
        }
    }

    /**
     * Modalita' test: il programma gioca anche le carte dell'utente.
     *
     * La guardia sta qui una volta sola. Quella del blocco differito chiama [recover], che e'
     * la cosa importante: se nel frattempo la situazione e' cambiata, `busy` va rimesso a
     * posto da qualcuno, e se non lo fa questa riga lo fa il watchdog quattro secondi dopo.
     * E' esattamente la differenza che c'era fra le tre schermate: il Tresette qui tornava
     * indietro senza chiamarlo.
     *
     * `ending` e' ripetuto dentro al blocco differito, e non per simmetria: se la mano si
     * chiude o si apre un dialogo fra il post e la sua esecuzione, qui non si deve giocare
     * nessuna carta. [recover] in quel caso non fa niente di proposito - a finestra aperta il
     * gioco sta fermo - e `busy` resta acceso, che e' giusto: a rimetterlo a posto sara' chi
     * chiude la finestra. Delle tre schermate era il Tresette a fare cosi'; le altre due
     * avrebbero giocato la carta, e in quel caso giocarla e' la cosa sbagliata.
     */
    protected fun maybeAutoPlay() {
        if (!autoPlay || busy || destroyed || ending) return
        if (gameFinished || isBotTurn || youHandEmpty) return
        busy = true
        armWatchdog()
        post(t.think) {
            if (ending || gameFinished || isBotTurn || youHandEmpty) { recover(); return@post }
            autoPlayMove()
        }
    }

    // ------------------------------------------------------ ciclo di vita

    /**
     * La parte di onCreate uguale nelle tre schermate. Va chiamata **dopo**
     * setContentView, perche' [rootView] e [overlay] sono getter su un campo `lateinit`.
     */
    protected fun setupCommon(infoButton: View) {
        applySystemBars(rootView)
        infoButton.setOnClickListener { track(InfoDialog.show(this, R.string.info_title, rulesText)) }
        readSettings()
        CardView.setDeck(Prefs.deck(this))
        placeCards()
    }

    override fun onResume() {
        super.onResume()
        readSettings()
        CardView.setDeck(Prefs.deck(this))
        placeCards()
        if (stopped) {
            // Si torna da un onStop: la mossa in corso e' sparita insieme ai callback, quindi
            // si riparte guardando lo stato reale della partita, esattamente come fa il
            // watchdog. Se e' aperto un dialogo, recover() se ne accorge e non tocca nulla.
            stopped = false
            clearAnimationState()
            render()
            recover()
        } else {
            render()
            maybeAutoPlay()
        }
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
     * in background. Le carte in volo vengono tolte: al ritorno la mossa interrotta viene
     * rifatta da capo.
     */
    override fun onStop() {
        stopped = true
        // Prima di tutto il resto: onStop e' l'ultimo momento garantito prima che Android
        // possa uccidere il processo, e lo stato qui e' ancora coerente.
        saveState()
        ui.removeCallbacksAndMessages(null)
        overlay.removeAllViews()
        super.onStop()
    }

    override fun onDestroy() {
        // I turni avanzano con callback differiti: se l'utente esce a meta' mano, senza questa
        // pulizia il callback parte comunque e il dialogo di fine partita fa crashare l'app.
        destroyed = true
        // Uscire dal Menu o col tasto indietro e' una scelta: la partita si butta. Chiudere
        // l'app non passa di qui, quindi in quel caso il salvataggio resta.
        if (isFinishing) SavedGame.clear(this, saveKey)
        ui.removeCallbacksAndMessages(null)
        closeDialog()
        super.onDestroy()
    }

    // ------------------------------------------- salvataggio e ripristino

    protected fun saveState() {
        if (!started) return
        val w = SavedGame.Writer()
        saveGame(w)
        w.ints(listOf(matchTarget, matchYou, matchBot, if (youStartNext) 1 else 0))
        w.ints(listOf(matchBeforeEnd.first, matchBeforeEnd.second))
        w.int(when (recordedWin) { true -> 1; false -> 0; null -> -1 })
        w.long(prevMatchEnd)
        w.bool(roundScored)
        SavedGame.write(this, saveKey, w)
    }

    /**
     * Vero se c'era una partita da riprendere. Le sezioni si rileggono nello stesso ordine in
     * cui [saveState] le ha scritte; se il testo non torna si butta il salvataggio e si
     * ricomincia, che e' meglio che ripartire da uno stato a meta'.
     */
    protected fun restoreState(): Boolean {
        val r = SavedGame.read(this, saveKey) ?: return false
        try {
            loadGame(r)
            val m = r.ints()
            matchTarget = m[0]; matchYou = m[1]; matchBot = m[2]; youStartNext = m[3] == 1
            val mb = r.ints(); matchBeforeEnd = Pair(mb[0], mb[1])
            recordedWin = when (r.int()) { 1 -> true; 0 -> false; else -> null }
            prevMatchEnd = r.long()
            roundScored = r.bool()
        } catch (e: Exception) {
            SavedGame.clear(this, saveKey)
            return false
        }
        started = true
        moveSeq++
        clearAnimationState()
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

    // -------------------------------------------------- inizio dell'incontro

    /**
     * La pausa responsabile fra un incontro e l'altro.
     *
     * `ending` e `busy` restano accesi finche' il dialogo e' aperto: senza, il watchdog
     * scattava dopo quattro secondi e cambiava lo stato dietro alla finestra.
     *
     * Il punteggio dell'incontro si azzera in [beginMatch] e non qui: con l'avviso aperto la
     * partita appena finita e' ancora quella salvata da onStop, con roundScored acceso, e se
     * i punteggi fossero gia' a zero mettendo l'app in secondo piano e riaprendola il
     * riepilogo tornerebbe a schermo con lo stato del motore giusto e i punti a 0 - 0.
     */
    protected fun startMatch() {
        val wait = Prefs.pauseRemaining(this)
        if (wait > 0) {
            ending = true
            busy = true
            ui.removeCallbacks(watchdog)
            track(PauseDialog.show(this, wait, onReady = { beginMatch() }, onLeave = { finish() }))
            return
        }
        beginMatch()
    }

    // ---------------------------------------------------------- fine mano

    /**
     * Fine mano: assegna la partita, la registra se l'incontro e' finito, e mostra il
     * riepilogo.
     *
     * L'assegnazione e la finestra sono separate perche' il ripristino ha bisogno solo della
     * seconda: riprendendo una mano chiusa col riepilogo aperto, i punti sono gia' stati dati
     * e rifarli significherebbe contarli due volte.
     */
    protected fun endRound() {
        if (ending || destroyed || isFinishing) return
        ending = true
        ui.removeCallbacks(watchdog)
        busy = true
        matchBeforeEnd = Pair(matchYou, matchBot)
        awardHand()
        val over = matchOver()
        recordedWin = null
        if (over) {
            prevMatchEnd = Prefs.lastMatchEnd(this)
            Prefs.markMatchEnded(this)
            recordedWin = matchYou > matchBot
            Prefs.recordMatch(this, statsKey, matchYou > matchBot)
        }
        roundScored = true
        render()
        showRoundDialog(over)
    }

    /** Rimette il riepilogo dopo un ripristino, senza toccare il punteggio. */
    protected fun resumeRoundDialog() {
        ending = true
        busy = true
        render()
        showRoundDialog(matchOver())
    }

    /**
     * La cornice del riepilogo di fine mano: titolo, pulsanti, e dentro la tabella che il
     * gioco costruisce in [buildResultView]. I punteggi si rileggono dal motore invece di
     * essere passati come parametri: a mano finita lo stato non cambia piu', quindi il conto
     * e' lo stesso sia che si arrivi da [endRound] sia da un ripristino, e non c'e' un
     * secondo posto da tenere allineato.
     */
    protected fun showRoundDialog(over: Boolean) {
        if (destroyed || isFinishing) return
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.round_over)
            .setView(buildResultView(over))
            .setCancelable(false)
            .setNegativeButton(R.string.back_home) { _, _ -> finish() }
        if (over) {
            builder.setPositiveButton(R.string.new_match) { _, _ -> startMatch() }
        } else {
            // Niente "Nuovo incontro" a meta' incontro: era l'unico modo di ricominciare
            // saltando la pausa di un minuto. Restano continua e menu.
            builder.setPositiveButton(R.string.continue_match) { _, _ -> startRound() }
        }
        if (hasLastDeal) {
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
     * Riporta la mano all'inizio delle ultime carte (vedi la fotografia nel motore), rimette
     * i punteggi della partita com'erano e fa ripartire il gioco da li'. Il Banco non tira
     * a caso: le sue carte saranno le stesse, cambia solo quello che decidi tu.
     */
    protected fun replayLastDeal() {
        if (!restoreLastDeal()) return
        matchYou = matchBeforeEnd.first
        matchBot = matchBeforeEnd.second
        undoMatchRecord()
        ending = false
        roundScored = false
        moveSeq++
        busy = true
        statoTurno()
        // recover() legge lo stato reale: fa giocare il Banco se tocca a lui, altrimenti
        // libera la mano per la tua giocata
        recover()
    }

    /**
     * Annulla la registrazione fatta a fine partita: la partita torna aperta, quindi
     * l'esito esce dalle statistiche e la pausa fra le partite non parte.
     */
    private fun undoMatchRecord() {
        recordedWin?.let {
            Prefs.unrecordMatch(this, statsKey, it)
            Prefs.setLastMatchEnd(this, prevMatchEnd)
            recordedWin = null
        }
    }

    // ------------------------------------------------------------- attrezzi

    /** postDelayed sicuro: il blocco non viene eseguito se l'activity nel frattempo e' morta. */
    protected fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Registra il dialogo appena aperto, cosi' onDestroy sa cosa chiudere. */
    protected fun track(d: AlertDialog?): AlertDialog? {
        openDialog = d
        return d
    }

    protected fun closeDialog() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
    }

    /**
     * Rimette una vista riusata nello stato "appena creata".
     *
     * render() non ricostruisce il tavolo da zero: aggiunge o toglie solo le CardView che
     * servono e aggiorna quelle che restano. Le animazioni pero' lasciano stato sulla vista
     * (visibility a INVISIBLE, translation, alpha, scale): prima lo azzerava la
     * ricostruzione, ora lo azzera questo metodo, altrimenti una carta resta invisibile o
     * spostata per sempre. L'alpha delle carte spente del Tresette va rimessa a mano
     * **dopo**, perche' qui torna a 1.
     */
    protected fun resetCard(cv: CardView) {
        cv.animate().cancel()
        cv.visibility = View.VISIBLE
        cv.alpha = 1f
        cv.translationX = 0f
        cv.translationY = 0f
        cv.scaleX = 1f
        cv.scaleY = 1f
        cv.rotation = 0f
    }

    /**
     * Distribuzione iniziale: le carte partono dal mazzo e raggiungono il loro posto.
     * Sposta le viste vere con translationX/Y, senza copie nell'overlay: se un render() le
     * ricrea a meta' animazione non resta nulla di sospeso.
     *
     * Le viste arrivano come **lambda** e non come elenco gia' fatto, perche' vanno lette
     * dentro doOnLayout: nel Tresette la mano del giocatore sta su due file e l'elenco lo
     * riempie render(), quindi valutarlo un istante prima darebbe le viste sbagliate o
     * nessuna.
     *
     * L'alpha di partenza si conserva e si rimette al valore che aveva: le carte spente
     * dall'obbligo di seme stanno a 0,35, e riportarle a 1 le accenderebbe tutte.
     */
    protected fun dealFrom(deckBox: View, views: () -> List<View>) {
        if (t.fast) return
        rootView.doOnLayout {
            if (destroyed) return@doOnLayout
            if (deckBox.width == 0) return@doOnLayout
            val (dx, dy) = topLeftInOverlay(deckBox)
            var delay = 0L
            for (v in views()) {
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

    // ---------------------------------------------------------- coordinate

    /** Angolo in alto a sinistra di [v] nel sistema di riferimento dell'overlay. */
    protected fun topLeftInOverlay(v: View): Pair<Float, Float> {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val o = IntArray(2); overlay.getLocationInWindow(o)
        return Pair((loc[0] - o[0]).toFloat(), (loc[1] - o[1]).toFloat())
    }

    /**
     * Centro di [v] nell'overlay. Se la vista non e' ancora stata misurata si ripiega sulle
     * misure di una carta: meglio un centro approssimato che un volo verso l'angolo.
     */
    protected fun centerInOverlay(v: View): Pair<Float, Float> {
        val (x, y) = topLeftInOverlay(v)
        val w = if (v.width > 0) v.width else cardW
        val h = if (v.height > 0) v.height else cardH
        return Pair(x + w / 2f, y + h / 2f)
    }

    // ------------------------------------------------------- riga di stato

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
    protected fun statoTurno() {
        if (isBotTurn) statusView.setText(R.string.bot_turn) else statusView.text = ""
    }

    protected fun clearStatus() {
        statusView.text = ""
    }

    private companion object {
        /** Quanto il watchdog aspetta prima di guardare se la partita si e' piantata. */
        const val WATCHDOG_MS = 4000L
    }
}
