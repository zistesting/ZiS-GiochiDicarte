package com.zis.scopa

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityPokerBinding

/**
 * La schermata del poker: 5-Card Draw a limite fisso, in due o in quattro.
 *
 * PERCHE' NON ESTENDE [BotGameActivity]. Quella classe e' fatta per i tre giochi di presa:
 * due giocatori che si alternano, un turno per volta, un incontro che va a un traguardo di
 * punti, e uno stato che si riassume in "sta giocando il Banco oppure tocca a te". Il poker
 * non ha niente di tutto questo: ha da due a quattro giocatori, una macchina a stati di giri
 * di puntata, gruzzoli che si esauriscono e giocatori che vengono eliminati. Sta a se' come
 * il Klondike.
 *
 * Di attrezzi in comune se ne duplicano QUATTRO, per undici righe in tutto: [post], [dp],
 * [track] e [closeDialog]. Sono le quattro che hanno bisogno di stato proprio dell'activity
 * - l'Handler, il dialogo aperto - e undici righe non sono i 424 da cui e' nata
 * BotGameActivity. Se un giorno il poker si mettesse ad animare le carte come gli altri
 * servirebbero anche le coordinate nell'overlay e resetCard, e a quel punto conviene
 * estrarre: non prima.
 *
 * IL CALCOLO DELLE PROBABILITA' STA SUL FILO PRINCIPALE, e non e' una disattenzione: e'
 * misurato. Nel caso peggiore - tre avversari che si sono serviti e che hanno puntato, cioe'
 * la condizione piu' rara da campionare - a 1500 campioni costa 16 ms su un computer, quindi
 * forse 40 su un telefono, e si paga UNA VOLTA PER DECISIONE e non a ogni ridisegno. A 6000
 * campioni costerebbe 48 ms e non si potrebbe. In tutta questa applicazione non c'e' un
 * thread, ed e' una scelta: qui non serve romperla.
 */
class PokerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPokerBinding
    private lateinit var game: PokerGame

    private val ui = Handler(Looper.getMainLooper())
    private val t = Timing()
    private var openDialog: AlertDialog? = null
    private var destroyed = false
    private var stopped = false

    /** Vero mentre gioca il Banco: i tocchi non si raccolgono. */
    private var busy = false

    private var giocatori = 2
    private var mostraOdds = false

    /** Gli indici delle carte che hai scelto di cambiare. */
    private val daCambiare = HashSet<Int>()

    /** Le misure delle carte, ricalcolate quando cambia la finestra. */
    private var cardW = 0
    private var cardH = 0
    private var seatW = 0
    private var seatH = 0

    // le tre file dei posti, per indirizzarle con un indice invece che per nome
    private val seatBox by lazy { listOf(b.seat1, b.seat2, b.seat3) }
    private val seatName by lazy { listOf(b.seatName1, b.seatName2, b.seatName3) }
    private val seatHand by lazy { listOf(b.seatHand1, b.seatHand2, b.seatHand3) }
    private val seatInfo by lazy { listOf(b.seatInfo1, b.seatInfo2, b.seatInfo3) }

    // ------------------------------------------------------------- ciclo di vita

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPokerBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)
        // l'overlay e' scenografia: TalkBack non deve leggerci dentro
        b.overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        giocatori = Prefs.pokerGiocatori(this)
        mostraOdds = Prefs.pokerOdds(this)
        CardView.setDeck(Prefs.DECK_FR)          // il poker si gioca col mazzo francese

        b.btnInfo.setOnClickListener {
            val regole = if (Prefs.pokerVariante(this) == Prefs.POKER_HOLDEM)
                R.string.rules_poker_holdem else R.string.rules_poker_draw
            track(InfoDialog.show(this, R.string.info_title, regole))
        }
        b.btnFold.setOnClickListener { mossa(PokerGame.Azione.PASSA) }
        b.btnCheck.setOnClickListener { mossa(PokerGame.Azione.PARIFICA) }
        b.btnRaise.setOnClickListener { mossa(PokerGame.Azione.RILANCIA) }
        b.btnDraw.setOnClickListener { confermaScarto() }
        b.btnNext.setOnClickListener { avantiDopoLaMano() }

        misura()
        game = PokerGame(giocatori)
        if (!restoreState()) iniziaPartita()
    }

    override fun onResume() {
        super.onResume()
        mostraOdds = Prefs.pokerOdds(this)
        CardView.setDeck(Prefs.DECK_FR)
        misura()
        render()
        if (stopped) {
            stopped = false
            // si riparte guardando lo stato reale: la mossa differita e' sparita insieme
            // ai callback, e a rimetterla in moto ci pensa avanti()
            busy = false
            avanti()
        }
    }

    override fun onStop() {
        stopped = true
        saveState()
        ui.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        if (isFinishing) SavedGame.clear(this, SavedGame.POKER)
        ui.removeCallbacksAndMessages(null)
        closeDialog()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        misura()
        render()
    }

    // ---------------------------------------------------------------- la partita

    /** La pausa responsabile all'inizio, come negli altri quattro giochi. */
    private fun iniziaPartita() {
        val attesa = Prefs.pauseRemaining(this)
        if (attesa > 0) {
            busy = true
            track(PauseDialog.show(this, attesa, onReady = { nuovaPartita() }, onLeave = { finish() }))
            return
        }
        nuovaPartita()
    }

    private fun nuovaPartita() {
        game = PokerGame(giocatori)
        nuovaMano()
    }

    private fun nuovaMano() {
        daCambiare.clear()
        game.nuovaMano()
        render()
        avanti()
    }

    /**
     * Il motore della schermata: guarda in che stato e' la mano e fa la cosa che tocca.
     *
     * Non tiene nessuno stato suo su "dove siamo arrivati": lo chiede al motore ogni volta.
     * E' la stessa scelta di recover() negli altri tre giochi, e serve alla stessa cosa:
     * tornare da un onStop, o da qualunque cosa sia andata storta, senza avere due idee
     * diverse di dove sia arrivata la partita.
     */
    private fun avanti() {
        if (destroyed || isFinishing) return
        if (game.manoFinita) { mostraEsito(); return }
        val p = game.turno
        if (p == 0) {
            busy = false
            if (game.fase == PokerGame.SCARTO) chiediScarto() else chiediAzione()
        } else {
            busy = true
            render()
            b.txtStatus.text = getString(R.string.poker_turn_of, nomeDi(p))
            post(t.think) { giocaIlBanco(p) }
        }
    }

    private fun giocaIlBanco(p: Int) {
        if (destroyed || game.manoFinita || game.turno != p) { avanti(); return }
        if (game.fase == PokerGame.SCARTO) {
            val da = PokerBot.scarto(game, p)
            game.scarta(p, da)
            messaggioScarto(p, da.size)
        } else {
            val azione = PokerBot.azione(game, p)
            val daPareggiare = game.daPareggiare(p)
            game.agisci(p, azione)
            b.txtStatus.text = when (azione) {
                PokerGame.Azione.PASSA -> getString(R.string.poker_folded, nomeDi(p))
                PokerGame.Azione.PARIFICA ->
                    if (daPareggiare == 0) getString(R.string.poker_check)
                    else getString(R.string.poker_call, daPareggiare)
                PokerGame.Azione.RILANCIA -> getString(R.string.poker_raise, game.puntataCorrente())
            }
            if (game.fiches[p] == 0 && game.inMano(p))
                b.txtStatus.text = getString(R.string.poker_all_in, nomeDi(p))
        }
        render()
        post(t.trickPause) { avanti() }
    }

    // ------------------------------------------------------------- le tue mosse

    private fun chiediAzione() {
        val azioni = game.azioniLegali(0)
        val daPareggiare = game.daPareggiare(0)
        b.btnFold.visibility = if (PokerGame.Azione.PASSA in azioni) View.VISIBLE else View.GONE
        b.btnCheck.visibility = if (PokerGame.Azione.PARIFICA in azioni) View.VISIBLE else View.GONE
        b.btnRaise.visibility = if (PokerGame.Azione.RILANCIA in azioni) View.VISIBLE else View.GONE
        b.btnCheck.text = if (daPareggiare == 0) getString(R.string.poker_check)
                          else getString(R.string.poker_call, daPareggiare)
        b.btnRaise.text = if (game.livello == 0) getString(R.string.poker_bet, game.puntataCorrente())
                          else getString(R.string.poker_raise, game.puntataCorrente())
        b.buttonsRow.visibility = if (azioni.isEmpty()) View.GONE else View.VISIBLE
        b.btnDraw.visibility = View.GONE
        b.btnNext.visibility = View.GONE
        b.txtStatus.text = ""
        render()
        aggiornaOdds()
        if (azioni.isEmpty()) post(t.trickPause) { avanti() }   // all-in: non c'e' niente da fare
    }

    private fun mossa(azione: PokerGame.Azione) {
        if (busy || game.turno != 0) return
        if (azione !in game.azioniLegali(0)) return
        busy = true
        nascondiPulsanti()
        game.agisci(0, azione)
        render()
        post(t.trickPause) { avanti() }
    }

    private fun chiediScarto() {
        nascondiPulsanti()
        b.btnDraw.visibility = View.VISIBLE
        aggiornaTestoScarto()
        b.txtStatus.text = ""
        render()
        aggiornaOdds()
    }

    private fun aggiornaTestoScarto() {
        b.btnDraw.text = if (daCambiare.isEmpty()) getString(R.string.poker_stand_pat)
                         else getString(R.string.poker_change) + " " + daCambiare.size
    }

    private fun confermaScarto() {
        if (busy || game.fase != PokerGame.SCARTO || game.turno != 0) return
        busy = true
        val scelte = daCambiare.toList()
        daCambiare.clear()
        nascondiPulsanti()
        game.scarta(0, scelte)
        messaggioScarto(0, scelte.size)
        render()
        post(t.trickPause) { avanti() }
    }

    /** Il tocco su una carta della tua mano, durante lo scarto: la alza o la riabbassa. */
    private fun toccoCarta(indice: Int) {
        if (busy || game.fase != PokerGame.SCARTO || game.turno != 0) return
        if (indice in daCambiare) daCambiare.remove(indice)
        else if (daCambiare.size < PokerGame.SCARTO_MAX) daCambiare.add(indice)
        aggiornaTestoScarto()
        render()
    }

    /**
     * La riga di stato dopo uno scarto: quante carte ha cambiato, o che si e' servito.
     *
     * E' l'informazione piu' preziosa del 5-Card Draw e va detta a parole, non solo lasciata
     * indovinare guardando le carte che volano: chi usa TalkBack non le vede volare, e chi
     * sta imparando non sa ancora che quel dato conta.
     */
    private fun messaggioScarto(p: Int, quante: Int) {
        b.txtStatus.text = if (quante == 0) getString(R.string.poker_changed_none, nomeDi(p))
                           else getString(R.string.poker_changed, nomeDi(p), quante)
    }

    private fun nascondiPulsanti() {
        b.buttonsRow.visibility = View.GONE
        b.btnDraw.visibility = View.GONE
        b.btnNext.visibility = View.GONE
    }

    // ---------------------------------------------------------------- fine mano

    private fun mostraEsito() {
        busy = true
        nascondiPulsanti()
        render()
        val righe = ArrayList<String>()
        for (p in 0 until giocatori) if (game.incasso[p] > 0)
            righe.add(getString(R.string.poker_wins, nomeDi(p), game.incasso[p]))
        for (p in 0 until giocatori) if (game.eliminato[p] && game.fiches[p] == 0)
            righe.add(getString(R.string.poker_out, nomeDi(p)))
        b.txtStatus.text = righe.joinToString("   ")
        b.btnNext.visibility = View.VISIBLE
        b.btnNext.text = if (game.partitaFinita) getString(R.string.back_home)
                         else getString(R.string.poker_next_hand)
    }

    private fun avantiDopoLaMano() {
        if (!game.partitaFinita) { nuovaMano(); return }
        // la partita e' chiusa: si registra e si torna al menu. La pausa responsabile
        // scatta qui, cioe' quando resta un solo giocatore con le fiches.
        val vinta = game.vincitore() == 0
        Prefs.recordMatch(this, Prefs.GAME_POKER, vinta)
        Prefs.markMatchEnded(this)
        val dialogo = AlertDialog.Builder(this)
            .setTitle(R.string.round_over)
            .setMessage(getString(R.string.poker_game_over, nomeDi(game.vincitore().coerceAtLeast(0))))
            .setCancelable(false)
            .setPositiveButton(R.string.back_home) { _, _ -> finish() }
            .show()
        track(dialogo)
    }

    // ------------------------------------------------------------------ disegno

    /** Le misure delle carte: dipendono solo dalla larghezza della finestra. */
    private fun misura() {
        val utile = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt() - dp(32)
        cardW = minOf(utile / 5 - dp(6), dp(66))
        cardH = (cardW * 1.4f).toInt()
        seatW = (cardW * 0.52f).toInt()
        seatH = (seatW * 1.4f).toInt()
    }

    private fun render() {
        // ---- il piatto ----
        b.txtPot.text = getString(R.string.poker_pot, game.piatto)
        val laterali = game.piatti.drop(1).filter { it.importo > 0 }
        b.txtSidePots.text = if (game.piatti.size > 1)
            laterali.joinToString("   ") { getString(R.string.poker_side_pot, it.importo) } else ""

        // ---- gli avversari ----
        for (slot in 0..2) {
            val p = giocatoreNelPosto(slot)
            if (p < 0) { seatBox[slot].visibility = View.GONE; continue }
            seatBox[slot].visibility = View.VISIBLE
            seatName[slot].text = nomeDi(p) + "  " + getString(R.string.poker_chips, game.fiches[p])
            seatInfo[slot].text = statoDi(p)
            disegnaManoCoperta(seatHand[slot], p)
        }

        // ---- la tua mano ----
        b.txtYou.text = getString(R.string.poker_you) + "  " +
            getString(R.string.poker_chips, game.fiches[0])
        disegnaManoTua()
    }

    /** L'indice del giocatore che siede nel posto [slot], o -1 se il posto non serve. */
    private fun giocatoreNelPosto(slot: Int): Int = when (giocatori) {
        2 -> if (slot == 1) 1 else -1       // in due l'unico avversario sta al centro
        else -> if (slot < giocatori - 1) slot + 1 else -1
    }

    private fun nomeDi(p: Int): String = when {
        p == 0 -> getString(R.string.poker_you)
        giocatori == 2 -> getString(R.string.poker_bank)
        else -> getString(R.string.poker_opponent, p)
    }

    /** La riga sotto il posto: cosa ha fatto quel giocatore in questa mano. */
    private fun statoDi(p: Int): String = when {
        game.eliminato[p] -> getString(R.string.poker_out, "").trim()
        !game.inMano(p) -> getString(R.string.poker_folded, "").trim()
        game.fiches[p] == 0 -> getString(R.string.poker_all_in, "").trim()
        game.cambiate[p] == 0 -> getString(R.string.poker_changed_none, "").trim()
        game.cambiate[p] > 0 -> getString(R.string.poker_changed, "", game.cambiate[p]).trim()
        game.puntato[p] > 0 -> getString(R.string.poker_chips, game.puntato[p])
        else -> ""
    }

    /**
     * Le carte coperte di un avversario, sovrapposte.
     *
     * Allo showdown si scoprono, ma solo se la mano e' arrivata davvero allo showdown: se
     * hanno passato tutti tranne uno le carte restano coperte, ed e' mezzo poker. Lo dice
     * [PokerGame.carteMostrate], non questa schermata.
     */
    private fun disegnaManoCoperta(riga: LinearLayout, p: Int) {
        val carte = game.mani[p]
        val scoperte = game.carteMostrate && game.inMano(p)
        while (riga.childCount > carte.size) riga.removeViewAt(riga.childCount - 1)
        while (riga.childCount < carte.size)
            riga.addView(CardView(this).apply { french = true },
                         LinearLayout.LayoutParams(seatW, seatH))
        for (i in carte.indices) {
            val cv = riga.getChildAt(i) as CardView
            cv.french = true
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.width = seatW; lp.height = seatH
            // si accavallano: sono dorsi tutti uguali, non si perde niente
            lp.marginStart = if (i == 0) 0 else -seatW / 3
            cv.layoutParams = lp
            cv.alpha = if (game.inMano(p)) 1f else 0.35f
            cv.card = if (scoperte) carte[i] else null
            cv.faceUp = scoperte
        }
    }

    /** La tua mano: cinque carte scoperte, e durante lo scarto si toccano per cambiarle. */
    private fun disegnaManoTua() {
        val carte = game.mani[0]
        val riga = b.youHand
        while (riga.childCount > carte.size) riga.removeViewAt(riga.childCount - 1)
        while (riga.childCount < carte.size)
            riga.addView(CardView(this).apply { french = true },
                         LinearLayout.LayoutParams(cardW, cardH))
        val scegliendo = game.fase == PokerGame.SCARTO && game.turno == 0 && !busy
        for (i in carte.indices) {
            val cv = riga.getChildAt(i) as CardView
            cv.french = true
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.width = cardW; lp.height = cardH
            lp.marginStart = dp(3); lp.marginEnd = dp(3)
            cv.layoutParams = lp
            cv.card = carte[i]
            cv.faceUp = true
            cv.alpha = 1f
            // la carta scelta per il cambio si alza: si vede a colpo d'occhio quante sono
            cv.translationY = if (i in daCambiare) -dp(14).toFloat() else 0f
            if (scegliendo) {
                cv.setOnClickListener { toccoCarta(i) }
            } else {
                cv.setOnClickListener(null)
                cv.isClickable = false
            }
        }
    }

    // ------------------------------------------------- le probabilita' a schermo

    /**
     * Il pannello didattico. Si calcola UNA VOLTA per decisione, qui, e non dentro [render]:
     * render viene chiamata a ogni tocco su una carta, e sedici millisecondi per tocco si
     * sentirebbero.
     */
    private fun aggiornaOdds() {
        if (!mostraOdds || game.mani[0].size < 5) { b.oddsBox.visibility = View.GONE; return }
        b.oddsBox.visibility = View.VISIBLE

        val punteggio = PokerHand.valuta(game.mani[0])
        b.txtOddsHand.text = getString(R.string.odds_hand, nomeCombinazione(punteggio))

        val avversari = (1 until giocatori).count { game.inMano(it) }
        val letti = (1 until giocatori).filter { game.inMano(it) }.map {
            PokerOdds.Avversario(
                cambiate = if (game.fase >= PokerGame.PUNTATE_2) game.cambiate[it].coerceAtLeast(0) else -1,
                haPuntato = game.haPuntato[it]
            )
        }
        // lo stesso modello con cui ragiona il Banco: se fosse diverso, questi numeri
        // insegnerebbero a giocare contro un avversario che non esiste
        val modello = PokerOdds.Modello(
            bluffServito = PokerBot.BLUFF_SERVITO,
            sogliaPuntata = 0.78,
            bluffPuntata = PokerOdds.frequenzaBluff(game.piatto, game.puntataCorrente())
        )
        val e = if (avversari == 0) 1.0
                else PokerOdds.equita(game.mani[0], letti, CAMPIONI_SCHERMO, modello = modello)
        b.txtOddsEquity.text = getString(R.string.odds_equity, Math.round(e * 100).toInt())

        val daPareggiare = game.daPareggiare(0)
        if (daPareggiare > 0) {
            val soglia = PokerOdds.quoteDelPiatto(game.piatto, daPareggiare)
            val ce = Math.round(e * 100).toInt()
            val cs = Math.round(soglia * 100).toInt()
            b.txtOddsCall.text = getString(
                if (e > soglia) R.string.odds_call_good else R.string.odds_call_bad, ce, cs)
        } else {
            b.txtOddsCall.text = getString(R.string.odds_bluff_rate,
                Math.round(PokerOdds.frequenzaBluff(game.piatto, game.puntataCorrente()) * 100).toInt())
        }

        // la lettura dell'avversario: e' l'informazione che il 5-Card Draw regala
        val servito = (1 until giocatori).firstOrNull { game.inMano(it) && game.cambiate[it] == 0 }
        val cambiato = (1 until giocatori).firstOrNull { game.inMano(it) && game.cambiate[it] > 0 }
        b.txtOddsRead.text = when {
            servito != null -> getString(R.string.odds_read_pat)
            cambiato != null -> getString(R.string.odds_read_changed, game.cambiate[cambiato])
            daPareggiare > 0 -> getString(R.string.odds_defend_rate,
                Math.round(PokerOdds.frequenzaDifesa(game.piatto, daPareggiare) * 100).toInt())
            else -> ""
        }
    }

    private fun nomeCombinazione(punteggio: Int): String = getString(
        when (PokerHand.categoria(punteggio)) {
            PokerHand.COPPIA -> R.string.hand_pair
            PokerHand.DOPPIA_COPPIA -> R.string.hand_two_pair
            PokerHand.TRIS -> R.string.hand_trips
            PokerHand.SCALA -> R.string.hand_straight
            PokerHand.COLORE -> R.string.hand_flush
            PokerHand.FULL -> R.string.hand_full
            PokerHand.POKER -> R.string.hand_quads
            PokerHand.SCALA_REALE -> R.string.hand_straight_flush
            else -> R.string.hand_high
        }
    )

    // ------------------------------------------------------------- salvataggio

    private fun saveState() {
        if (game.partitaFinita) return
        val w = SavedGame.Writer()
        // Il numero dei giocatori va PRIMA di tutto il resto, e non in fondo: e' il dato che
        // decide la MISURA di tutte le sezioni che seguono - nove elenchi lunghi quanti sono
        // i giocatori, piu' una mano per ciascuno - quindi va letto prima di leggere qualunque
        // altra cosa. Scritto in fondo non si poteva leggere se non dopo aver gia' letto tutto
        // il resto con la misura sbagliata, e leggere con la misura sbagliata non da' errore:
        // consuma un numero diverso di sezioni e va avanti. Vedi [restoreState].
        w.ints(listOf(giocatori))
        game.save(w)
        SavedGame.write(this, SavedGame.POKER, w)
    }

    /**
     * Riprende la partita salvata, se e' della misura giusta.
     *
     * CAMBIARE IL NUMERO DEI GIOCATORI dalle impostazioni vuol dire cominciare una partita
     * nuova, ed e' giusto cosi': quello che si butta e' una mano, non un incontro. La cosa
     * che NON deve capitare e' rileggere un salvataggio da quattro dentro un motore da due.
     *
     * Perche' era un difetto vero e non una precauzione. Con il numero dei giocatori scritto
     * in fondo, la lettura di un salvataggio da quattro fatta con due consumava quattro
     * sezioni in meno - due mani in meno - e arrivava in fondo sfalsata: il controllo finiva
     * per confrontare il MAZZIERE con il numero dei giocatori, e se il mazziere salvato era
     * 2 il controllo passava. Lo stato accettato era spazzatura, [turno] veniva da un codice
     * di carta fra 0 e 51, e alla prima mossa `fuori[37]` chiudeva l'app.
     *
     * Adesso la prima sezione e' il numero dei giocatori e si legge per prima. Il controllo
     * guarda anche la LUNGHEZZA, e non solo il valore: un salvataggio scritto con il formato
     * vecchio ha in testa l'elenco delle fiches, cioe' due o quattro numeri invece di uno, e
     * viene scartato con certezza invece che per fortuna.
     */
    private fun restoreState(): Boolean {
        val r = SavedGame.read(this, SavedGame.POKER) ?: return false
        try {
            val m = r.ints()
            if (m.size != 1 || m[0] != giocatori) { SavedGame.clear(this, SavedGame.POKER); return false }
            val prova = PokerGame(giocatori)
            prova.load(r)
            game = prova
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.POKER)
            return false
        }
        render()
        avanti()
        return true
    }

    // ---------------------------------------------------------------- attrezzi

    /** postDelayed sicuro: il blocco non parte se l'activity nel frattempo e' morta. */
    private fun post(delayMs: Long, action: () -> Unit) {
        ui.postDelayed({ if (!destroyed && !isFinishing) action() }, delayMs)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun track(d: AlertDialog?): AlertDialog? {
        openDialog = d
        return d
    }

    private fun closeDialog() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
    }

    private companion object {
        /**
         * Campioni per le probabilita' mostrate a schermo. Misurato: nel caso peggiore -
         * tre avversari serviti e che hanno puntato - a 1500 costa 16 ms qui e forse 40 su
         * un telefono, a 6000 ne costerebbe 48 e non basterebbe un fotogramma. L'errore a
         * 1500 sta sotto i due punti percentuali su un numero che si mostra arrotondato.
         */
        const val CAMPIONI_SCHERMO = 1500
    }
}
