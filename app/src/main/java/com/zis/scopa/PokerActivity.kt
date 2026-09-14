package com.zis.scopa

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
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
 * BotGameActivity. Da quando il poker distribuisce le carte volando si e' aggiunta
 * [distribuisci], che e' la cugina di BotGameActivity.dealFrom: venti righe, e nemmeno
 * identiche, perche' qui non serve niente del resto di quel corredo - le coordinate
 * nell'overlay, resetCard - visto che il poker non fa volare le carte giocate. Estrarre
 * vorrebbe dire mettere le mani in tre giochi collaudati per risparmiare venti righe.
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

    /**
     * Le misure delle carte, ricalcolate quando cambia la finestra.
     *
     * UNA MISURA SOLA per tutte le carte del tavolo, tue e degli avversari. Prima quelle
     * degli avversari erano al 52%: tre file di carte piccole in cima allo schermo, che
     * facevano sembrare gli avversari lontani invece che seduti allo stesso tavolo.
     */
    private var cardW = 0
    private var cardH = 0

    /**
     * Di quanto avanza ogni carta nelle due colonne di fianco, e quanto e' alta una riga di
     * testo girato. Vedi [misura] e [mettiAPostoLaterale].
     */
    private var passoLaterale = 0
    private var altoTesto = 0

    // I tre posti, per indirizzarli con un indice invece che per nome. L'ordine e' quello
    // del giro: sinistra, alto, destra, cioe' i giocatori 1, 2 e 3.
    private val seatBox by lazy { listOf(b.seatLeft, b.seatTop, b.seatRight) }
    private val seatName by lazy { listOf(b.nameLeft, b.nameTop, b.nameRight) }
    private val seatHand by lazy { listOf(b.handLeft, b.handTop, b.handRight) }
    private val seatInfo by lazy { listOf(b.infoLeft, b.infoTop, b.infoRight) }

    /** I due dorsi del mazzo al centro, creati una volta sola: CardView non nasce da XML. */
    private var mazzo: List<CardView> = emptyList()

    // ------------------------------------------------------------- ciclo di vita

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPokerBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

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
        game = partitaNuova()
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
        game = partitaNuova()
        nuovaMano()
    }

    /**
     * Distribuisce e comincia la mano.
     *
     * L'attesa della distribuzione non e' un abbellimento gratuito: mentre le carte volano
     * [busy] resta vero, quindi in quel momento non si tocca niente e nessuno ridisegna il
     * tavolo. Senza, il Banco potrebbe puntare a carte ancora in volo, e un render() in
     * mezzo all'animazione rimetterebbe le carte al loro posto di scatto.
     */
    private fun nuovaMano() {
        daCambiare.clear()
        game.nuovaMano()
        render()
        val attesa = distribuisci()
        if (attesa <= 0) { avanti(); return }
        busy = true
        nascondiPulsanti()
        post(attesa) { avanti() }
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
        if (azioni.isEmpty()) {
            post(t.trickPause) { avanti() }   // all-in: non c'e' niente da fare
            return
        }
        finestraOdds()
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
        finestraOdds()
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

    /**
     * Fine mano. La vincita la scrive [render] al posto del piatto, in oro; qui resta solo
     * chi e' rimasto senza fiches, che va nella riga degli avvisi come tutto il resto.
     */
    private fun mostraEsito() {
        busy = true
        nascondiPulsanti()
        render()
        b.txtStatus.text = (0 until giocatori)
            .filter { game.eliminato[it] && game.fiches[it] == 0 }
            .joinToString("   ") { getString(R.string.poker_out, nomeDi(it)) }
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

    /**
     * Le misure del tavolo: dipendono solo dalla larghezza della finestra.
     *
     * LA CARTA. Cinque carte piu' i margini devono entrare nella larghezza utile, e non si
     * va oltre 66dp perche' su un tablet cinque carte enormi sarebbero solo cinque carte
     * enormi. Questa misura vale per TUTTI: tua mano, posto in alto, posti di fianco.
     *
     * LE DUE COLONNE DI FIANCO sono girate di 90 gradi, quindi in larghezza si portano via
     * l'ALTEZZA di una carta piu' due righe di testo girato: `cardH + 2 * altoTesto`, cioe'
     * 122dp su un telefono da 360 e 111 su uno da 320. Due colonne piu' il mazzo fanno 303dp
     * su 360 e 273 su 320: entrano sempre, e lo scivolamento fuori schermo che serviva quando
     * le file erano orizzontali non serve piu'.
     *
     * IL PASSO DELLA COLONNA, e qui alla 4.7 avevo sbagliato di brutto. Avevo tenuto un
     * quarto di carta, come quando le file erano orizzontali, ma girando la carta il passo
     * non corre piu' lungo il lato lungo (cardH, 82dp): corre lungo il lato CORTO, che e'
     * cardW, 59dp. Un quarto di 59 fa 14dp, cioe' cinque carte accatastate di cui si vedeva
     * una striscia: sovrapposte e illeggibili, ed e' esattamente cosi' che si vedevano.
     *
     * Adesso il passo lo decide lo SPAZIO CHE C'E': la colonna comincia al bordo alto del
     * mazzo e deve stare sopra la tua mano, quindi si divide quello che resta per le quattro
     * carte che seguono la prima. Il conto qui sotto rifa' a mano quello che il layout fa da
     * se' - e' l'unico modo di saperlo PRIMA di disegnare, e disegnare due volte litigherebbe
     * con l'animazione della distribuzione - ma il risultato non e' lasciato al caso: viene
     * stretto fra un quarto e due terzi di carta. Se un giorno il layout cambiasse e il conto
     * qui sbagliasse, il passo resterebbe comunque in quella forchetta.
     *
     * Quanto viene: 39dp su un telefono di oggi (360x915), 34 su uno da 360x640, e 14 su uno
     * da 320x480 - dove non c'e' spazio e meglio di cosi' non si puo' fare.
     */
    private fun misura() {
        val schermo = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt()
        val altezza = (resources.configuration.screenHeightDp * resources.displayMetrics.density).toInt()
        cardW = minOf((schermo - dp(32)) / 5 - dp(6), dp(66))
        cardH = (cardW * 1.4f).toInt()
        altoTesto = dp(20)

        // in ordine, dall'alto: il posto in alto, poi la colonna del centro (piatto, due
        // righe di avvisi, mazzo, fiches) tenuta al 40% dello spazio libero, e in fondo la
        // tua mano con i pulsanti
        val basso = cardH + dp(64)
        val tavolo = altezza - basso
        val postoAlto = dp(48) + cardH + 2 * altoTesto
        val sopraIlMazzo = dp(26) + 2 * altoTesto + dp(4)
        val centro = sopraIlMazzo + cardH + dp(4) + altoTesto
        val cima = postoAlto + (tavolo - postoAlto - centro).coerceAtLeast(0) * 4 / 10
        val sottoIlMazzo = tavolo - cima - sopraIlMazzo
        passoLaterale = ((sottoIlMazzo - cardW) / 4).coerceIn(cardW / 4, cardW * 2 / 3)
    }

    private fun render() {
        aggiornaPiatto()
        aggiornaMazzo()
        // le tue fiches stanno sotto il mazzo, senza il "Tu" davanti: di chi siano lo dice
        // il posto in cui sono, che e' il tuo lato del tavolo
        b.txtChips.text = getString(R.string.poker_chips, game.fiches[0])

        for (slot in 0..2) {
            val p = giocatoreNelPosto(slot)
            if (p < 0) { seatBox[slot].visibility = View.GONE; continue }
            seatBox[slot].visibility = View.VISIBLE
            seatName[slot].text = nomeDi(p) + "  " + getString(R.string.poker_chips, game.fiches[p])
            seatInfo[slot].text = statoDi(p)
            val giro = rotazioneDelPosto(slot)
            if (giro != 0f) mettiAPostoLaterale(slot, giro)
            disegnaMano(seatHand[slot], p, giro)
        }
        disegnaMano(b.youHand, 0, 0f)
    }

    /** Di quanto e' girato il posto [slot]: meno 90 a sinistra, piu' 90 a destra, zero in alto. */
    private fun rotazioneDelPosto(slot: Int): Float = when (slot) {
        POSTO_SINISTRA -> -90f
        POSTO_DESTRA -> 90f
        else -> 0f
    }

    /**
     * Mette a posto un posto di fianco: misura il riquadro e sposta i tre pezzi.
     *
     * COME FUNZIONA UNA VISTA GIRATA. La rotazione non entra nella misura: una vista larga A
     * e alta B, girata di 90 gradi, resta A x B per il layout e diventa B x A per l'occhio,
     * col CENTRO nello stesso punto. Quindi tutti e tre i pezzi - la colonna delle carte e le
     * due righe di testo - stanno al centro del riquadro (layout_gravity="center" nel layout)
     * e da la' si spostano con la translation, che invece si applica nello spazio NON girato
     * del riquadro. Sono tre numeri, simmetrici fra destra e sinistra: basta cambiare segno.
     *
     * Il riquadro e' largo quanto una carta girata piu' due righe di testo, e alto quanto la
     * colonna: le carte stanno sul bordo dello schermo, i due testi verso il centro del
     * tavolo, dove non c'e' un bordo che li tagli.
     *
     * La colonna misura cardH per carta ma se ne vede cardW, e la differenza e' simmetrica
     * rispetto al centro: percio' un riquadro alto `cardW + 4 * passo` contiene esattamente
     * quello che si vede. Il conto sta in [misura].
     */
    private fun mettiAPostoLaterale(slot: Int, giro: Float) {
        val g = if (giro < 0) -1 else 1                  // -1 a sinistra, +1 a destra
        val lungo = cardW + 4 * passoLaterale            // quanto e' lunga la colonna
        val box = seatBox[slot]
        val lpBox = box.layoutParams as ViewGroup.LayoutParams
        lpBox.width = cardH + 2 * altoTesto
        lpBox.height = lungo
        box.layoutParams = lpBox
        seatHand[slot].translationX = (g * altoTesto).toFloat()
        for (tv in listOf(seatName[slot], seatInfo[slot])) {
            val lp = tv.layoutParams as FrameLayout.LayoutParams
            lp.width = lungo; lp.height = altoTesto
            tv.layoutParams = lp
            tv.rotation = giro
        }
        seatName[slot].translationX = (-g * (cardH - altoTesto) / 2).toFloat()
        seatInfo[slot].translationX = (-g * (cardH + altoTesto) / 2).toFloat()
    }

    /**
     * Il piatto, e a mano finita l'avviso di vincita AL SUO POSTO.
     *
     * Sono la stessa riga perche' dicono la stessa cosa in due momenti: quanto c'e' in
     * mezzo, e dove e' andato. Ed e' l'unico posto del tavolo dove compaiono dei colori: se
     * fossero anche il colore dei nomi e dei pulsanti - come era prima - la vincita non si
     * distinguerebbe da tutto il resto.
     *
     * DUE COLORI E NON UNO: oro quando vinci tu, celeste quando vince un avversario. Il
     * colore lo si vede prima di aver letto la riga, quindi dice "e' andata bene" o "e'
     * andata male" nell'istante in cui la mano si chiude; il nome e la cifra si leggono
     * dopo. Il celeste e' lo stesso dell'app, non un colore nuovo.
     *
     * "Piatto laterale: xx" non c'e' piu'. Quel numero compariva solo quando qualcuno era
     * all-in, non diceva a chi andasse, e la somma che conta - quella che vinci davvero -
     * la dice comunque questa riga a mano finita.
     */
    private fun aggiornaPiatto() {
        val vincite = (0 until giocatori).filter { game.incasso[it] > 0 }
        if (game.manoFinita && vincite.isNotEmpty()) {
            b.txtPot.text = vincite.joinToString("   ") {
                if (it == 0) getString(R.string.poker_you_win, game.incasso[it])
                else getString(R.string.poker_wins, nomeDi(it), game.incasso[it])
            }
            b.txtPot.setTextColor(getColor(if (0 in vincite) R.color.gold else R.color.celeste))
        } else {
            b.txtPot.text = getString(R.string.poker_pot, game.piatto)
            b.txtPot.setTextColor(getColor(R.color.silver))
        }
    }

    /**
     * Il mazzo al centro, sotto gli avvisi: due dorsi sfalsati di tre punti, che bastano a
     * far leggere un mazzo invece di una carta.
     *
     * Le viste si creano una volta sola e poi si rimisurano: [misura] cambia le carte
     * quando gira lo schermo, e un mazzo della misura di prima accanto a carte della misura
     * nuova si vedrebbe. Per TalkBack il riquadro parla al singolare - "Mazzo: n carte" - e
     * i due dorsi dentro sono muti, altrimenti annuncerebbe due carte coperte che non sono
     * due carte.
     */
    private fun aggiornaMazzo() {
        if (mazzo.isEmpty()) {
            mazzo = (0..1).map {
                CardView(this).apply {
                    french = true
                    faceUp = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }
            for ((i, cv) in mazzo.withIndex()) {
                val lp = FrameLayout.LayoutParams(cardW, cardH)
                lp.leftMargin = dp(3) * (1 - i)
                lp.topMargin = dp(3) * (1 - i)
                b.deckBox.addView(cv, lp)
            }
        }
        for (cv in mazzo) {
            val lp = cv.layoutParams as FrameLayout.LayoutParams
            if (lp.width != cardW || lp.height != cardH) {
                lp.width = cardW; lp.height = cardH
                cv.layoutParams = lp
            }
        }
        b.deckBox.contentDescription =
            if (game.deck.isEmpty()) getString(R.string.cd_deck_empty)
            else getString(R.string.cd_deck, game.deck.size)
    }

    /**
     * La distribuzione: le carte partono dal mazzo e raggiungono i posti, una a testa per
     * giro come si distribuisce a un tavolo vero. Ritorna quanto dura, in millisecondi.
     *
     * SI SPOSTANO LE VISTE VERE con translation, senza copie in un overlay: se un render()
     * arrivasse a meta' animazione non resterebbe niente di sospeso da ripulire. E' la
     * stessa scelta di BotGameActivity.dealFrom negli altri tre giochi, ed e' l'unica cosa
     * che questa schermata gli somiglia: il resto di quel metodo - le coordinate
     * nell'overlay, resetCard - qui non serve, perche' il poker non fa volare le carte
     * giocate. Sono venti righe, ed estrarle in un posto comune vorrebbe dire toccare tre
     * giochi collaudati per risparmiarne venti.
     *
     * Il passo si dimezza in quattro: venti carte al passo di dieci farebbero un secondo di
     * attesa prima di poter giocare, e un secondo per mano si sente.
     *
     * SI RAGIONA SUI CENTRI, non sugli angoli, e per le carte di fianco e' obbligatorio: una
     * vista girata ha l'angolo in alto a sinistra da un'altra parte, ma il CENTRO dove era
     * prima, perche' la rotazione gira attorno al centro. Il centro di una carta si prende
     * dalla posizione della sua FILA piu' left/top della carta dentro la fila: la fila non e'
     * girata - a girare sono le carte, una per una - quindi quel conto e' esatto in tutti e
     * quattro i posti.
     */
    private fun distribuisci(): Long {
        if (t.fast) return 0L
        val carte = 5 * (0 until giocatori).count { game.inMano(it) }
        if (carte == 0) return 0L
        val passo = if (giocatori > 2) t.dealStep / 2 else t.dealStep
        b.root.doOnLayout {
            if (destroyed || b.deckBox.width == 0) return@doOnLayout
            val (dx, dy) = centroDelMazzo()
            var ritardo = 0L
            for (i in 0 until 5) for (riga in fileInGioco()) {
                val v = riga.getChildAt(i) ?: continue
                if (v.width == 0) continue
                val (tx, ty) = centroDellaCarta(riga, v)
                val restare = v.alpha
                v.alpha = 0f
                v.translationX = (dx - tx).toFloat()
                v.translationY = (dy - ty).toFloat()
                v.animate().translationX(0f).translationY(0f).alpha(restare)
                    .setStartDelay(ritardo).setDuration(t.dealDur).start()
                ritardo += passo
            }
        }
        return (carte - 1) * passo + t.dealDur
    }

    /** Le file che in questa mano ricevono carte: la tua e quelle dei posti in gioco. */
    private fun fileInGioco(): List<LinearLayout> {
        val file = ArrayList<LinearLayout>()
        file.add(b.youHand)
        for (slot in 0..2) if (giocatoreNelPosto(slot) >= 0) file.add(seatHand[slot])
        return file
    }

    private fun centroDelMazzo(): Pair<Int, Int> {
        val loc = IntArray(2)
        b.deckBox.getLocationInWindow(loc)
        return Pair(loc[0] + b.deckBox.width / 2, loc[1] + b.deckBox.height / 2)
    }

    /** Centro della carta [v] dentro la fila [riga], nella finestra. Vedi [distribuisci]. */
    private fun centroDellaCarta(riga: View, v: View): Pair<Int, Int> {
        val loc = IntArray(2)
        riga.getLocationInWindow(loc)
        return Pair(loc[0] + v.left + v.width / 2, loc[1] + v.top + v.height / 2)
    }

    /**
     * L'indice del giocatore che siede nel posto [slot], o -1 se il posto non serve.
     *
     * I tre posti in ordine sono sinistra, alto, destra, e i giocatori 1, 2 e 3 li prendono
     * in quest'ordine: il giro del tavolo - tu in basso, sinistra, alto, destra - e' allora
     * lo stesso dell'ordine dei turni, e chi guarda vede la mano passare in tondo invece
     * che saltare da un posto all'altro.
     *
     * In due l'unico avversario sta in ALTO, di fronte a te, e i due posti di fianco vanno
     * a GONE.
     */
    private fun giocatoreNelPosto(slot: Int): Int = when (giocatori) {
        2 -> if (slot == POSTO_ALTO) 1 else -1
        else -> if (slot < giocatori - 1) slot + 1 else -1
    }

    /**
     * Il nome di un posto: N, E, S, W come al bridge, e S sei tu perche' S sta in basso.
     *
     * In due ci sono solo S e N, uno di fronte all'altro. In quattro il giro e' S, W, N, E:
     * sono i giocatori 0, 1, 2 e 3 nell'ordine dei turni, che e' anche l'ordine in cui
     * girano i posti a schermo. Una lettera sta in qualunque avviso; "Avversario 2" no, e
     * poi non diceva niente che non si vedesse gia'.
     */
    private fun nomeDi(p: Int): String = getString(when {
        p == 0 -> R.string.poker_seat_s
        giocatori == 2 -> R.string.poker_seat_n
        p == 1 -> R.string.poker_seat_w
        p == 2 -> R.string.poker_seat_n
        else -> R.string.poker_seat_e
    })

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
     * Una mano qualunque del tavolo: la tua o quella di un avversario.
     *
     * ERANO DUE FUNZIONI, una per le carte tue e una per quelle coperte degli avversari, e
     * le due misure diverse erano la ragione per cui restavano separate. Con una misura
     * sola restano tre differenze vere, tutte e tre decise dal giocatore a cui la mano
     * appartiene: le tue sono scoperte, le loro si accavallano di fianco, e solo le tue si
     * toccano per scegliere il cambio.
     *
     * Allo showdown le carte degli avversari si scoprono, ma solo se la mano e' arrivata
     * davvero allo showdown: se hanno passato tutti tranne uno restano coperte, ed e' mezzo
     * poker. Lo dice [PokerGame.carteMostrate], non questa schermata.
     */
    private fun disegnaMano(riga: LinearLayout, p: Int, giro: Float) {
        val carte = game.mani[p]
        val tua = p == 0
        val scoperte = tua || (game.carteMostrate && game.inMano(p))
        val scegliendo = tua && game.fase == PokerGame.SCARTO && game.turno == 0 && !busy

        while (riga.childCount > carte.size) riga.removeViewAt(riga.childCount - 1)
        while (riga.childCount < carte.size)
            riga.addView(CardView(this).apply { french = true },
                         LinearLayout.LayoutParams(cardW, cardH))

        for (i in carte.indices) {
            val cv = riga.getChildAt(i) as CardView
            cv.french = true
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.width = cardW; lp.height = cardH
            if (giro != 0f) {
                // colonna: un quarto di carta a vista. La carta resta larga cardW e alta
                // cardH - e' girata, non deformata - quindi il margine che porta al passo
                // voluto e' negativo di tutta l'altezza meno il passo.
                lp.marginStart = 0; lp.marginEnd = 0
                lp.topMargin = if (i == 0) 0 else passoLaterale - cardH
            } else {
                lp.marginStart = dp(3); lp.marginEnd = dp(3); lp.topMargin = 0
            }
            cv.layoutParams = lp
            cv.rotation = giro
            cv.card = if (scoperte) carte[i] else null
            cv.faceUp = scoperte
            cv.alpha = if (tua || game.inMano(p)) 1f else 0.35f
            // la carta scelta per il cambio si alza: si vede a colpo d'occhio quante sono
            cv.translationY = if (tua && i in daCambiare) -dp(14).toFloat() else 0f
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
     * Il pannello didattico, che dalla 4.6 e' UNA FINESTRA con il pulsante Continua.
     *
     * Prima era un riquadro fisso sopra la tua mano, e costava 86dp di schermo a tutti,
     * sempre, anche a chi non lo guardava - su un telefono corto era lo spazio che mancava
     * al tavolo. E soprattutto era un pannello da leggere mentre si guarda altro: quei
     * quattro numeri servono a fermarsi un attimo prima di decidere, quindi tanto vale che
     * fermino davvero, e che si chiudano con un gesto quando si e' letto.
     *
     * Si calcola UNA VOLTA per decisione, qui, e non dentro [render]: render viene chiamata
     * a ogni tocco su una carta, e sedici millisecondi per tocco si sentirebbero.
     */
    private fun finestraOdds() {
        if (!mostraOdds || game.mani[0].size < 5) return
        val righe = ArrayList<String>()

        val punteggio = PokerHand.valuta(game.mani[0])
        righe.add(getString(R.string.odds_hand, nomeCombinazione(punteggio)))

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
                else PokerOdds.equita(game.mani[0], letti, CAMPIONI_SCHERMO,
                                      modello = modello, valori = game.valori)
        righe.add(getString(R.string.odds_equity, Math.round(e * 100).toInt()))

        val daPareggiare = game.daPareggiare(0)
        if (daPareggiare > 0) {
            val soglia = PokerOdds.quoteDelPiatto(game.piatto, daPareggiare)
            val ce = Math.round(e * 100).toInt()
            val cs = Math.round(soglia * 100).toInt()
            righe.add(getString(
                if (e > soglia) R.string.odds_call_good else R.string.odds_call_bad, ce, cs))
        } else {
            righe.add(getString(R.string.odds_bluff_rate,
                Math.round(PokerOdds.frequenzaBluff(game.piatto, game.puntataCorrente()) * 100).toInt()))
        }

        // la lettura dell'avversario: e' l'informazione che il 5-Card Draw regala
        val servito = (1 until giocatori).firstOrNull { game.inMano(it) && game.cambiate[it] == 0 }
        val cambiato = (1 until giocatori).firstOrNull { game.inMano(it) && game.cambiate[it] > 0 }
        when {
            servito != null -> righe.add(getString(R.string.odds_read_pat))
            cambiato != null -> righe.add(getString(R.string.odds_read_changed, game.cambiate[cambiato]))
            daPareggiare > 0 -> righe.add(getString(R.string.odds_defend_rate,
                Math.round(PokerOdds.frequenzaDifesa(game.piatto, daPareggiare) * 100).toInt()))
        }

        track(AlertDialog.Builder(this)
            .setTitle(R.string.show_odds)
            .setMessage(righe.joinToString("\n\n"))
            .setPositiveButton(R.string.poker_continue) { _, _ -> }
            .show())
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

    /**
     * Una partita con i parametri scelti nelle impostazioni.
     *
     * Sta in un posto solo perche' serve in tre - la partita nuova, quella dopo un reset e
     * la prova per il salvataggio - e se in uno dei tre mancasse un parametro la partita
     * ripresa non combacerebbe con quella salvata, quindi si butterebbe senza motivo. I
     * valori si leggono qui e non si tengono in un campo: le impostazioni possono cambiare
     * mentre questa schermata sta in fondo alla pila.
     */
    private fun partitaNuova() = PokerGame(
        giocatori,
        Prefs.pokerFiches(this),
        Prefs.pokerApertura(this),
        Prefs.pokerRilancio(this),
        Prefs.pokerMazzoCorto(this))

    private fun saveState() {
        if (game.partitaFinita) return
        val w = SavedGame.Writer()
        // Il numero dei giocatori va PRIMA di tutto il resto, e non in fondo: e' il dato che
        // decide la MISURA di tutte le sezioni che seguono - nove elenchi lunghi quanti sono
        // i giocatori, piu' una mano per ciascuno - quindi va letto prima di leggere qualunque
        // altra cosa. Scritto in fondo non si poteva leggere se non dopo aver gia' letto tutto
        // il resto con la misura sbagliata, e leggere con la misura sbagliata non da' errore:
        // consuma un numero diverso di sezioni e va avanti. Vedi [restoreState].
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
            // i parametri della partita stanno in testa al salvataggio e li controlla
            // PokerGame.load: se non combaciano lancia, e il catch qui sotto butta la mano
            val prova = partitaNuova()
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
        // I tre posti, nell'ordine del giro: sinistra, alto, destra, cioe' W, N, E.
        const val POSTO_SINISTRA = 0
        /** Il posto di fronte a te: l'unico che si usa quando si gioca in due. */
        const val POSTO_ALTO = 1
        const val POSTO_DESTRA = 2

        /**
         * Campioni per le probabilita' mostrate a schermo. Misurato: nel caso peggiore -
         * tre avversari serviti e che hanno puntato - a 1500 costa 16 ms qui e forse 40 su
         * un telefono, a 6000 ne costerebbe 48 e non basterebbe un fotogramma. L'errore a
         * 1500 sta sotto i due punti percentuali su un numero che si mostra arrotondato.
         */
        const val CAMPIONI_SCHERMO = 1500
    }
}
