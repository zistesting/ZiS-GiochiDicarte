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
import com.zis.scopa.databinding.ActivityHoldemBinding

/**
 * Il Texas Hold'em: la schermata.
 *
 * E' LA SORELLA DI [PokerActivity], non la sua sottoclasse, e la scelta e' la stessa che
 * aveva portato a BotGameActivity al contrario. Le due schermate si somigliano nel disegno -
 * tre posti attorno al tavolo, le carte di fianco girate, il riquadro che si accende sotto
 * chi deve giocare - ma il gioco sotto e' un altro: due carte invece di cinque, cinque carte
 * comuni in tavola, quattro giri di puntate e nessuno scarto. Una classe base che serva a
 * tutte due dovrebbe astrarre proprio le cose che cambiano, e diventerebbe un albero di
 * `if (holdem)`: peggio di due file che si assomigliano.
 *
 * Quello che invece e' in comune STA in comune, e non e' poco: [PokerGame.Azione] per le
 * mosse, [PokerHand] per il punteggio, [PokerPot] per i piatti laterali, [CardView] per le
 * carte, [PokerGame] per i valori di partenza degli importi, e [PokerBotHoldem] che e'
 * l'unico pezzo scritto per questo gioco.
 *
 * Il perche' di ogni scelta di GEOMETRIA - la misura delle carte, le colonne girate, il
 * passo, la luce del turno, i centri invece degli angoli nel volo - sta nei commenti di
 * PokerActivity e di activity_poker.xml. Qui ci sono solo le cose che cambiano.
 */
class HoldemActivity : AppCompatActivity() {

    private lateinit var b: ActivityHoldemBinding
    private lateinit var game: HoldemGame
    private var giocatori = 2

    private val handler = Handler(Looper.getMainLooper())
    private var openDialog: AlertDialog? = null
    private var busy = false
    private var destroyed = false
    private var stopped = false
    private val t = Timing()

    private var autoPlay = false
    private var mostraCarte = false

    /** La probabilita' di vincere a schermo, e la situazione su cui e' stata calcolata. */
    private var mostraOdds = false
    private var chiaveEquita = ""

    /**
     * L'ULTIMA MOSSA DI OGNI GIOCATORE, scritta sotto il suo nome. Il tavolo diceva solo gli
     * stati definitivi - chi ha passato, chi e' all-in - e la mossa appena fatta compariva
     * per un attimo nella riga del centro, una sola per tutti: chi guardava un secondo dopo
     * non sapeva piu' se N avesse visto o rilanciato. Adesso ogni posto se la tiene.
     */
    private val ultimaMossa = Array(4) { "" }

    private var cardW = 0
    private var cardH = 0
    private var cardLatW = 0
    private var cardLatH = 0
    private var passoLaterale = 0
    private var altoTesto = 0
    private var strisciaLaterale = 0

    private val seatBox by lazy { listOf(b.seatLeft, b.seatTop, b.seatRight) }
    private val seatName by lazy { listOf(b.nameLeft, b.nameTop, b.nameRight) }
    private val seatHand by lazy { listOf(b.handLeft, b.handTop, b.handRight) }

    private var mazzo: List<CardView> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHoldemBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

        giocatori = Prefs.pokerGiocatori(this)

        b.btnFold.setOnClickListener { gioca(PokerGame.Azione.PASSA) }
        b.btnCheck.setOnClickListener { gioca(PokerGame.Azione.PARIFICA) }
        b.btnRaise.setOnClickListener { gioca(PokerGame.Azione.RILANCIA) }
        b.btnNext.setOnClickListener { avantiDopoLaMano() }
        b.btnInfo.setOnClickListener {
            track(InfoDialog.show(this, R.string.info_title, R.string.rules_poker_holdem))
        }
        b.btnSettings.setOnClickListener {
            startActivity(SettingsActivity.intent(this, Prefs.GAME_POKER))
        }

        misura()
        b.root.doOnLayout { adattaColonne() }
        game = partitaNuova()
        if (!restoreState()) iniziaPartita()
    }

    override fun onResume() {
        super.onResume()
        autoPlay = Prefs.autoPlay(this)
        mostraCarte = Prefs.showBotCards(this)
        mostraOdds = Prefs.pokerOdds(this)
        t.fast = autoPlay
        CardView.setDeck(Prefs.DECK_FR)
        misura()
        render()
        b.root.doOnLayout { adattaColonne() }
        if (stopped) { stopped = false; if (!busy) avanti() }
    }

    override fun onStop() {
        super.onStop()
        stopped = true
        saveState()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        closeDialog()
        if (isFinishing) SavedGame.clear(this, SavedGame.HOLDEM)
    }

    /** Una partita con i parametri scelti nelle impostazioni, che sono quelli del poker. */
    private fun partitaNuova() = HoldemGame(
        giocatori,
        Prefs.pokerFiches(this),
        Prefs.pokerApertura(this),
        Prefs.pokerRilancio(this),
        Prefs.pokerMazzoCorto(this))

    /** La pausa responsabile all'inizio, come negli altri cinque giochi. */
    private fun iniziaPartita() {
        val attesa = Prefs.pauseRemaining(this)
        if (attesa > 0) {
            busy = true
            track(PauseDialog.show(this, attesa, onReady = { nuovaMano() }, onLeave = { finish() }))
            return
        }
        nuovaMano()
    }

    private fun nuovaMano() {
        for (i in ultimaMossa.indices) ultimaMossa[i] = ""
        game.nuovaMano()
        render()
        val attesa = distribuisci()
        if (attesa <= 0) { avanti(); return }
        busy = true
        nascondiPulsanti()
        post(attesa) { avanti() }
    }

    // ------------------------------------------------------------------ il giro

    private fun avanti() {
        if (destroyed || isFinishing) return
        if (game.manoFinita) { mostraEsito(); return }
        val p = game.turno
        if (p == 0 && !autoPlay) {
            busy = false
            chiediAzione()
        } else {
            busy = true
            render()
            b.txtStatus.text = getString(R.string.poker_turn_of, nomeDi(p))
            post(t.think) { giocaIlBanco(p) }
        }
    }

    private fun chiediAzione() {
        val azioni = game.azioniLegali(0)
        b.btnFold.visibility = if (PokerGame.Azione.PASSA in azioni) View.VISIBLE else View.GONE
        b.btnCheck.visibility = if (PokerGame.Azione.PARIFICA in azioni) View.VISIBLE else View.GONE
        b.btnRaise.visibility = if (PokerGame.Azione.RILANCIA in azioni) View.VISIBLE else View.GONE
        b.buttonsRow.visibility = if (azioni.isEmpty()) View.GONE else View.VISIBLE
        b.btnNext.visibility = View.GONE
        val da = game.daPareggiare(0)
        b.btnCheck.text = if (da == 0) getString(R.string.poker_check)
                          else getString(R.string.poker_call, da)
        b.btnRaise.text = getString(R.string.poker_raise, game.puntataCorrente())
        b.txtStatus.text = ""
        render()
        if (azioni.isEmpty()) post(t.trickPause) { avanti() }   // all-in: niente da fare
    }

    private fun gioca(azione: PokerGame.Azione) {
        if (busy || game.manoFinita || game.turno != 0) return
        if (azione !in game.azioniLegali(0)) return
        busy = true
        nascondiPulsanti()
        val da = game.daPareggiare(0)
        game.agisci(0, azione)
        ultimaMossa[0] = nomeMossa(azione, da)
        render()
        post(t.trickPause) { avanti() }
    }

    private fun giocaIlBanco(p: Int) {
        if (destroyed || game.manoFinita || game.turno != p) { avanti(); return }
        val azione = PokerBotHoldem.azione(game, p)
        val daPareggiare = game.daPareggiare(p)
        game.agisci(p, azione)
        ultimaMossa[p] = nomeMossa(azione, daPareggiare)
        b.txtStatus.text = when (azione) {
            PokerGame.Azione.PASSA -> getString(R.string.poker_folded, nomeDi(p))
            PokerGame.Azione.PARIFICA ->
                if (daPareggiare == 0) getString(R.string.poker_check)
                else getString(R.string.poker_call, daPareggiare)
            PokerGame.Azione.RILANCIA -> getString(R.string.poker_raise, game.puntataCorrente())
        }
        if (game.fiches[p] == 0 && game.inMano(p))
            b.txtStatus.text = getString(R.string.poker_all_in, nomeDi(p))
        render()
        post(t.trickPause) { avanti() }
    }

    private fun mostraEsito() {
        busy = true
        nascondiPulsanti()
        render()
        b.txtStatus.text = ""
        b.btnNext.visibility = View.VISIBLE
        b.btnNext.text = if (game.partitaFinita) getString(R.string.back_home)
                         else getString(R.string.poker_next_hand)
        if (autoPlay && !game.partitaFinita) post(t.trickPause) { nuovaMano() }
    }

    private fun avantiDopoLaMano() {
        if (!game.partitaFinita) { nuovaMano(); return }
        // le statistiche del Hold'em sono le sue: il Draw e' un altro gioco
        Prefs.recordMatch(this, Prefs.GAME_HOLDEM, game.vincitore() == 0)
        Prefs.markMatchEnded(this)
        finish()
    }

    private fun nascondiPulsanti() {
        b.buttonsRow.visibility = View.GONE
        b.btnNext.visibility = View.GONE
    }

    // ------------------------------------------------------------------ disegno

    private fun misura() {
        val schermo = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt()
        val altezza = (resources.configuration.screenHeightDp * resources.displayMetrics.density).toInt()
        // cinque carte comuni affiancate devono entrare nella larghezza: e' la misura piu'
        // stretta del tavolo, e decide anche quanto sono grandi le carte in mano
        cardW = minOf((schermo - dp(32)) / 5 - dp(6), dp(66))
        cardH = (cardW * 1.4f).toInt()
        altoTesto = dp(20)

        // la fascia dei posti di fianco: sta fra il posto di N e le carte comuni. La stima e'
        // prudente di 72 punti (le barre di sistema) e la misura esatta la fa adattaColonne.
        val inAlto = dp(48) + cardH + altoTesto + dp(12)
        // le carte comuni, il piatto, la riga del Banco, e sotto il tuo blocco con i
        // pulsanti. Il mazzo non c'e' piu' in mezzo: sta nell'angolo in alto a destra.
        // le comuni, il piatto, la riga del Banco, e sotto il tuo blocco: le fiches adesso
        // stanno DI FIANCO alle carte, non sopra, quindi quella riga non si conta piu'
        val inBasso = cardH + dp(6) + altoTesto + dp(20) + cardH + dp(64) + dp(12)
        val fascia = altezza - inAlto - inBasso - dp(72)
        misuraLaterali(fascia)
    }

    /**
     * Le carte dei due posti di fianco: uguali alle tue finche' ci stanno, piu' piccole
     * quando la fascia e' corta, mai sotto la meta'. Due carte girate in colonna sono alte
     * due volte il lato corto della carta, quindi qui lo spazio avanza quasi sempre: e' il
     * vantaggio del Hold'em sul Draw, dove le carte in mano sono cinque.
     */
    private fun misuraLaterali(fascia: Int) {
        // il pavimento e' un terzo di carta e non la meta': con due sole carte in colonna la
        // differenza fra le due e' di pochi punti, e in cambio le colonne ci stanno SEMPRE
        // nella fascia - accavallarsi sulle carte comuni e' peggio che essere un po' piccole
        val stanno = (fascia - dp(12) - dp(2)) / 2
        cardLatW = stanno.coerceIn(cardW / 3, cardW)
        cardLatH = (cardLatW * 1.4f).toInt()
        passoLaterale = cardLatW + dp(2)
        strisciaLaterale = cardLatH + altoTesto
    }

    /** La fascia vera, misurata a layout fatto: vedi PokerActivity.adattaColonne. */
    private fun adattaColonne() {
        if (giocatori <= 2 || b.communityRow.top == 0) return
        val prima = cardLatW
        misuraLaterali(b.communityRow.top - b.seatTop.bottom - dp(4))
        if (cardLatW != prima) render()
    }

    private fun render() {
        aggiornaPiatto()
        aggiornaMazzo()
        b.txtChips.text = getString(R.string.poker_chips, game.fiches[0])

        val attivo = if (game.manoFinita) -1 else game.turno
        for (slot in 0..2) {
            val p = giocatoreNelPosto(slot)
            if (p < 0) { seatBox[slot].visibility = View.GONE; continue }
            seatBox[slot].visibility = View.VISIBLE
            illumina(seatBox[slot], p == attivo)
            seatName[slot].text =
                (nomeDi(p) + "  " + getString(R.string.poker_chips, game.fiches[p]) +
                 "  " + statoDi(p)).trim()
            val giro = rotazioneDelPosto(slot)
            if (giro != 0f) mettiAPostoLaterale(slot, giro)
            if (giro == 0f) disegnaMano(seatHand[slot], p, giro, cardW, cardH)
            else disegnaMano(seatHand[slot], p, giro, cardLatW, cardLatH)
        }
        val margine = if (giocatori > 2) strisciaLaterale + dp(6) else dp(48)
        for (tv in listOf(b.txtPot, b.txtStatus)) {
            val lp = tv.layoutParams as ViewGroup.MarginLayoutParams
            if (lp.marginStart != margine) {
                lp.marginStart = margine; lp.marginEnd = margine
                tv.layoutParams = lp
            }
        }
        illumina(b.youBox, attivo == 0)
        disegnaMano(b.youHand, 0, 0f, cardW, cardH)
        disegnaComuni()
        aggiornaEquita()
    }

    /** Le carte comuni: scoperte per tutti, e non ce n'e' nessuna quando il giro e' il primo. */
    private fun disegnaComuni() {
        val riga = b.communityRow
        val carte = game.comuni
        // LA FILA SI TIENE LA SUA ALTEZZA ANCHE QUANDO E' VUOTA, e non e' un vezzo: i due
        // posti di fianco sono ancorati al suo bordo alto, e la loro misura si calcola una
        // volta, al primo layout. Se la fila crescesse di una carta al flop, quel bordo
        // salirebbe di ottanta punti e le colonne - misurate quando la fila era vuota - si
        // troverebbero addosso alle carte comuni. Era il difetto che si vedeva: W ed E
        // coperti dalla prima carta comune.
        riga.minimumHeight = cardH
        while (riga.childCount > carte.size) riga.removeViewAt(riga.childCount - 1)
        while (riga.childCount < carte.size)
            riga.addView(CardView(this).apply { french = true },
                         LinearLayout.LayoutParams(cardW, cardH))
        for (i in carte.indices) {
            val cv = riga.getChildAt(i) as CardView
            cv.french = true
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.width = cardW; lp.height = cardH
            lp.marginStart = dp(3); lp.marginEnd = dp(3)
            cv.layoutParams = lp
            cv.card = carte[i]
            cv.faceUp = true
            // IL GESTO DEL MAZZIERE, e sono due righe con due difetti corretti dentro.
            //
            // SI MUOVONO SOLO LE CARTE CHE NON SERVONO, e solo a carte scoperte. Le cinque
            // buone restano dove sono sempre state: alzarle le porterebbe addosso alle carte
            // di W e di E, che stanno appena sopra. Scendono di venti punti le altre, quelle
            // che il mazziere vero lascia indietro quando spinge avanti le cinque che
            // contano - ed e' la sola cosa a schermo che dica PERCHE' quella mano ha vinto.
            //
            // E DURANTE IL GIOCO SONO PIENE. Prima il gesto era scritto senza guardare se la
            // mano fosse finita: [comuniDellaVincente] fuori dallo showdown ritorna un
            // insieme vuoto, quindi NESSUNA carta "serviva" e tutte e cinque restavano
            // spente a mezz'aria per tutta la mano. Era il velo che si vedeva sul tavolo.
            val gesto = if (game.carteMostrate) comuniDellaVincente() else null
            val serve = gesto == null || i in gesto
            cv.alpha = if (serve) 1f else 0.4f
            cv.translationY = if (serve) 0f else dp(20).toFloat()
        }
    }

    /**
     * Quali carte comuni entrano nella mano migliore di chi ha vinto, per indice.
     *
     * Si cerca fra le ventuno combinazioni di cinque carte su sette quella col punteggio piu'
     * alto, e si guarda quali delle cinque vengono dal tavolo. Vuoto se la mano non e' andata
     * a carte scoperte: se hanno passato tutti tranne uno non c'e' niente da mostrare.
     *
     * Il vincitore e' chi ha incassato di piu': con i piatti laterali possono essere in due,
     * e in quel caso si prende quello col punteggio migliore - e' la mano che ha deciso il
     * piatto principale.
     */
    private fun comuniDellaVincente(): Set<Int> {
        if (!game.carteMostrate || game.comuni.size != 5) return emptySet()
        // TUTTI i vincitori, non uno: col piatto diviso le mani vincenti sono due, e possono
        // usare carte diverse della tavola. Lo si e' visto in una mano vera: tavola
        // 7 8 9 10 J, S con il J in mano e E col J della tavola - la stessa scala, il piatto
        // diviso, e il mio gesto abbassava il J perche' guardava solo il primo dei due. Una
        // carta che ha fatto vincere qualcuno non puo' essere mostrata come inutile.
        val insieme = HashSet<Int>()
        for (p in 0 until giocatori) {
            if (game.incasso[p] <= 0 || !game.inMano(p)) continue
            val sette = game.mani[p] + game.comuni
            var meglio = 0
            var scelte = emptySet<Int>()
            for (a in 0..2) for (b in a + 1..3) for (c in b + 1..4) for (d in c + 1..5) for (e in d + 1..6) {
                val idx = listOf(a, b, c, d, e)
                val v = PokerHand.valuta(idx.map { sette[it] })
                if (v > meglio) {
                    meglio = v
                    // le prime due carte dei sette sono in mano: in tavola sono le altre cinque
                    scelte = idx.filter { it >= 2 }.map { it - 2 }.toSet()
                }
            }
            insieme.addAll(scelte)
        }
        return insieme
    }

    /** "S e E", oppure "S, E e W": la congiunzione solo davanti all'ultimo. */
    private fun elenco(chi: List<Int>): String {
        val nomi = chi.map { nomeDi(it) }
        if (nomi.size == 1) return nomi[0]
        return nomi.dropLast(1).joinToString(", ") + " " + getString(R.string.poker_and) +
               " " + nomi.last()
    }

    private fun aggiornaPiatto() {
        val vincite = (0 until giocatori).filter { game.incasso[it] > 0 }
        if (game.manoFinita && vincite.isNotEmpty()) {
            // UNA FRASE SOLA QUANDO IL PIATTO SI DIVIDE. Due mani pari non vincono
            // ognuna per conto suo: dividono, e "S e E vincono 90$" lo dice in una riga
            // dove "S vince 90$   E vince 90$" ne prendeva due. Se le somme sono diverse -
            // succede con i piatti laterali - si torna a scriverle una per una, perche'
            // allora dire un numero solo sarebbe falso.
            val pari = vincite.map { game.incasso[it] }.distinct().size == 1
            b.txtPot.text = if (vincite.size > 1 && pari)
                getString(R.string.poker_wins_split, elenco(vincite), game.incasso[vincite[0]])
            else vincite.joinToString("   ") {
                getString(R.string.poker_wins, nomeDi(it), game.incasso[it])
            }
            b.txtPot.setTextColor(getColor(if (0 in vincite) R.color.gold else R.color.celeste))
        } else {
            b.txtPot.text = getString(R.string.poker_pot, game.piatto)
            b.txtPot.setTextColor(getColor(R.color.silver))
        }
    }

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

    /** Due carte a testa che arrivano dal mazzo, una per giro. Ritorna quanto dura. */
    private fun distribuisci(): Long {
        if (t.fast) return 0L
        val file = ArrayList<LinearLayout>()
        file.add(b.youHand)
        for (slot in 0..2) if (giocatoreNelPosto(slot) >= 0) file.add(seatHand[slot])
        val quante = 2 * (0 until giocatori).count { game.inMano(it) }
        if (quante == 0) return 0L
        val ordine = ArrayList<Pair<LinearLayout, Int>>()
        for (i in 0 until 2) for (riga in file) ordine.add(riga to i)
        volaDalMazzo(ordine, t.dealStep)
        return (quante - 1) * t.dealStep + t.dealDur
    }

    private fun volaDalMazzo(carte: List<Pair<LinearLayout, Int>>, passo: Long) {
        b.root.doOnLayout {
            if (destroyed || b.deckBox.width == 0) return@doOnLayout
            val loc = IntArray(2)
            b.deckBox.getLocationInWindow(loc)
            val dx = loc[0] + b.deckBox.width / 2
            val dy = loc[1] + b.deckBox.height / 2
            var ritardo = 0L
            for ((riga, i) in carte) {
                val v = riga.getChildAt(i) ?: continue
                if (v.width == 0) continue
                val l = IntArray(2)
                riga.getLocationInWindow(l)
                val tx = l[0] + v.left + v.width / 2
                val ty = l[1] + v.top + v.height / 2
                val restare = v.alpha
                v.alpha = 0f
                v.translationX = (dx - tx).toFloat()
                v.translationY = (dy - ty).toFloat()
                v.animate().translationX(0f).translationY(0f).alpha(restare)
                    .setStartDelay(ritardo).setDuration(t.dealDur).start()
                ritardo += passo
            }
        }
    }

    private fun disegnaMano(riga: LinearLayout, p: Int, giro: Float, cw: Int, ch: Int) {
        val carte = game.mani[p]
        val tua = p == 0
        val scoperte = tua || mostraCarte || (game.carteMostrate && game.inMano(p))
        while (riga.childCount > carte.size) riga.removeViewAt(riga.childCount - 1)
        while (riga.childCount < carte.size)
            riga.addView(CardView(this).apply { french = true },
                         LinearLayout.LayoutParams(cw, ch))
        for (i in carte.indices) {
            val cv = riga.getChildAt(i) as CardView
            cv.french = true
            val lp = cv.layoutParams as LinearLayout.LayoutParams
            lp.width = cw; lp.height = ch
            if (giro != 0f) {
                val sporgenza = (ch - cw) / 2
                lp.marginStart = 0; lp.marginEnd = 0
                lp.topMargin = if (i == 0) -sporgenza else passoLaterale - ch
                lp.bottomMargin = if (i == carte.size - 1) -sporgenza else 0
            } else {
                lp.marginStart = dp(3); lp.marginEnd = dp(3)
                lp.topMargin = 0; lp.bottomMargin = 0
            }
            cv.layoutParams = lp
            cv.rotation = giro
            cv.card = if (scoperte) carte[i] else null
            cv.faceUp = scoperte
            cv.alpha = if (tua || game.inMano(p)) 1f else 0.35f
            cv.translationY = 0f
        }
    }

    /**
     * LA PROBABILITA' DI VINCERE, di fianco alle tue carte, se l'hai accesa nelle
     * impostazioni ("Probabilita' a schermo").
     *
     * Si calcola SIMULANDO: agli avversari si danno due carte a caso fra quelle che non
     * vedi, le comuni che mancano si completano a caso, e si conta quante volte la tua mano
     * risulta la migliore. Seicento simulazioni per volta, ed e' una scelta misurata: con
     * seicento il numero balla di tre o quattro punti fra un calcolo e l'altro sulla stessa
     * mano, con millecinquecento di due o tre ma il conto costa fino a settanta millisecondi
     * - che in mano a un telefono diventano due o tre decimi e si sentirebbero. Quindi il
     * numero e' giusto a un paio di punti, non al decimale, e chi lo guarda lo deve sapere:
     * sta scritto nelle regole.
     *
     * SI CALCOLA UNA VOLTA PER SITUAZIONE, non a ogni disegno. L'equita' cambia quando
     * cambiano le carte comuni o quando qualcuno esce dalla mano; NON cambia quando qualcuno
     * punta. Percio' la chiave qui sotto e' fatta di quelle cose sole, e fra il giro delle
     * carte coperte e il quinto il conto si fa quattro volte, non quaranta.
     *
     * E si calcola FUORI DAL FOTOGRAMMA (post): al flop compaiono tre carte, e un decimo di
     * secondo di conto proprio in quell'istante si vedrebbe come uno scatto. Cosi' invece il
     * tavolo si disegna, e il numero arriva un momento dopo.
     */
    private fun aggiornaEquita() {
        val visibile = mostraOdds && !game.manoFinita && game.mani[0].size == 2 && game.inMano(0)
        if (!visibile) {
            b.txtEquita.visibility = View.GONE
            chiaveEquita = ""
            return
        }
        b.txtEquita.visibility = View.VISIBLE
        val avversari = (1 until giocatori).count { game.inMano(it) }
        val chiave = "${game.comuni.size}|$avversari|${game.mani[0]}"
        if (chiave == chiaveEquita) return
        chiaveEquita = chiave
        post(0) {
            if (chiave != chiaveEquita) return@post          // la situazione e' gia' cambiata
            val e = Math.round(PokerBotHoldem.equita(game, 0, avversari, quanti = CAMPIONI_SCHERMO) * 100).toInt()
            b.txtEquita.text = getString(R.string.holdem_equity, e)
            b.txtEquita.contentDescription = getString(R.string.cd_equity, e)
        }
    }

    private fun illumina(v: View, acceso: Boolean) {
        v.setBackgroundResource(if (acceso) R.drawable.turno_acceso else 0)
    }

    private fun rotazioneDelPosto(slot: Int): Float = when (slot) {
        0 -> -90f
        2 -> 90f
        else -> 0f
    }

    private fun mettiAPostoLaterale(slot: Int, giro: Float) {
        val g = if (giro < 0) -1 else 1
        val lungo = cardLatW + passoLaterale
        val box = seatBox[slot]
        val lpBox = box.layoutParams as ViewGroup.LayoutParams
        lpBox.width = strisciaLaterale + 2 * dp(6)
        lpBox.height = lungo + 2 * dp(6)
        box.layoutParams = lpBox
        seatHand[slot].translationX = (g * altoTesto / 2).toFloat()
        val tv = seatName[slot]
        val lp = tv.layoutParams as FrameLayout.LayoutParams
        lp.width = lungo; lp.height = altoTesto
        tv.layoutParams = lp
        tv.rotation = giro
        tv.translationX = (-g * cardLatH / 2).toFloat()
    }

    /** W a sinistra, N in alto, E a destra; in due c'e' solo N. */
    private fun giocatoreNelPosto(slot: Int): Int = when (giocatori) {
        2 -> if (slot == 1) 1 else -1
        else -> if (slot < giocatori - 1) slot + 1 else -1
    }

    /** Il nome di una mossa come lo legge un giocatore: parola, vedo 10$, rilancio 20$, passo. */
    private fun nomeMossa(azione: PokerGame.Azione, daPareggiare: Int): String = when (azione) {
        PokerGame.Azione.PASSA -> getString(R.string.poker_folded, "").trim()
        PokerGame.Azione.PARIFICA ->
            if (daPareggiare == 0) getString(R.string.poker_check)
            else getString(R.string.poker_call, daPareggiare)
        PokerGame.Azione.RILANCIA -> getString(R.string.poker_raise, game.puntataCorrente())
    }

    /**
     * Cosa si scrive sotto il nome: lo STATO quando e' definitivo - fuori, all-in, ha
     * passato - altrimenti l'ultima mossa. Uno stato definitivo vale piu' di una mossa:
     * "all-in" dice anche che non giochera' piu', "vedo 10$" no.
     */
    private fun statoDi(p: Int): String = when {
        game.eliminato[p] -> getString(R.string.poker_out, "").trim()
        game.fuori[p] -> getString(R.string.poker_folded, "").trim()
        game.fiches[p] == 0 -> getString(R.string.poker_all_in, "").trim()
        else -> ultimaMossa[p]
    }

    private fun nomeDi(p: Int): String = getString(when {
        p == 0 -> R.string.poker_seat_s
        giocatori == 2 -> R.string.poker_seat_n
        p == 1 -> R.string.poker_seat_w
        p == 2 -> R.string.poker_seat_n
        else -> R.string.poker_seat_e
    })

    // ------------------------------------------------------------------ salvataggio

    private fun saveState() {
        if (game.partitaFinita) return
        val w = SavedGame.Writer()
        game.save(w)
        SavedGame.write(this, SavedGame.HOLDEM, w)
    }

    private fun restoreState(): Boolean {
        val r = SavedGame.read(this, SavedGame.HOLDEM) ?: return false
        try {
            val prova = partitaNuova()
            prova.load(r)
            game = prova
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.HOLDEM)
            return false
        }
        render()
        avanti()
        return true
    }

    // ------------------------------------------------------------------ attrezzi

    private fun post(delay: Long, action: () -> Unit) {
        handler.postDelayed({ if (!destroyed && !isFinishing) action() }, delay)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Tiene da parte l'ultimo dialogo aperto, per poterlo chiudere in onDestroy. */
    private fun track(d: AlertDialog?) { closeDialog(); openDialog = d }

    private fun closeDialog() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
    }
    private companion object {
        /**
         * Quante simulazioni per il numero a schermo. Vedi [aggiornaEquita]: seicento sono
         * il punto in cui il conto e' abbastanza preciso (un paio di punti) e abbastanza
         * rapido da non farsi sentire.
         */
        const val CAMPIONI_SCHERMO = 600
    }

}
