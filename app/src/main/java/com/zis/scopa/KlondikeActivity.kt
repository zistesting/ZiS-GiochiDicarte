package com.zis.scopa

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityKlondikeBinding
import kotlin.math.max
import kotlin.math.min

/**
 * Il tavolo del Klondike.
 *
 * SI GIOCA A TOCCHI, non trascinando. Tocchi una carta e va dove ha senso che vada: prima la
 * fondazione, poi una colonna. E' la stessa interazione degli altri tre giochi, e su un
 * telefono e' anche piu' precisa del trascinamento, perche' una carta larga quaranta punti
 * si prende male con un dito. Il prezzo e' che quando una carta potrebbe andare in due
 * colonne diverse, la scelta la fa il gioco; l'annulla e' li' apposta.
 *
 * LE CARTE LE POSIZIONA IL CODICE. Negli altri giochi le mani sono LinearLayout e le carte
 * figli in fila; qui c'e' un contenitore solo e ogni carta viene messa alla sua coordinata.
 * Serve perche' le colonne sono ventagli con scarti diversi fra coperte e scoperte, e quello
 * scarto va compresso quando la colonna si allunga: un LinearLayout non sa farlo.
 */
class KlondikeActivity : AppCompatActivity() {

    private lateinit var b: ActivityKlondikeBinding
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var game: KlondikeGame

    private var destroyed = false
    private var started = false

    // ---- gioco automatico ----
    //
    // La voce delle impostazioni c'era da sempre ma qui non la leggeva nessuno: la
    // guardavano solo i tre giochi contro il Banco, e nel Klondike accenderla non faceva
    // niente. Adesso il programma gioca il solitario da solo, con la stessa idea di la':
    // serve a provare in fretta che animazioni, annulla, vittoria e statistiche funzionino.
    private var autoPlay = false
    private var autoFermo = false
    private var autoMosse = 0
    private var autoGiri = 0
    private var openDialog: AlertDialog? = null

    // geometria, calcolata una volta che il tavolo conosce la propria misura
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private var boardW = 0
    private var topY = 0
    private var tableauY = 0
    private var stockX = 0
    private var wasteX = 0
    private var passoScarti = 0

    /** Rientro orizzontale del blocco delle sette colonne: vale piu' di gap solo quando
     *  a decidere la misura della carta e' stata l'altezza e in larghezza avanza spazio. */
    private var marginX = 0

    /**
     * Le viste in gioco, riusate fra un disegno e l'altro.
     *
     * Stessa lezione degli altri tre giochi: ricostruire tutto a ogni disegno significa
     * buttare e riallocare fino a cinquantadue viste per ogni tocco. Qui pero' e' peggio che
     * altrove, perche' le carte sono piu' del doppio.
     */
    private val cardPool = mutableListOf<CardView>()
    private val slotPool = mutableListOf<View>()

    /**
     * Dove si trovava ogni carta all'ultimo disegno, e quale vista la sta mostrando.
     *
     * E' quello che rende possibile l'animazione senza dover sapere quale mossa e' stata
     * fatta. Prima di muovere si mette da parte questa mappa; dopo aver ridisegnato si
     * confrontano le posizioni e si anima tutto quello che si e' spostato. Cosi' funziona da
     * sola anche per le mosse che spostano UN GRUPPO di carte, o per il rigiro del tallone
     * che ne sposta ventiquattro insieme, senza un caso per ogni tipo di mossa.
     */
    private val posizioni = HashMap<Card, Pair<Float, Float>>()
    private val vistaDi = HashMap<Card, CardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKlondikeBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

        game = KlondikeGame(Prefs.klondikeDraw(this))
        b.btnUndo.setOnClickListener {
            val prima = HashMap(posizioni)
            if (game.undo()) render(prima)
        }
        // LE ETICHETTE NON SONO I NOMI DEL CODICE, ed e' voluto: i nomi dicono cosa fanno i
        // pulsanti, le etichette come si chiamano per chi gioca. Cercando "Rigioca" nel
        // codice non si trova niente, quindi la corrispondenza sta qui:
        //   pulsante "Rigioca" -> btnRestart -> chiediRicomincia() -> la STESSA smazzata
        //   pulsante "Nuovo"   -> btnDeal    -> chiediNuovaPartita() -> una smazzata nuova
        b.btnRestart.setOnClickListener { chiediRicomincia() }
        b.btnDeal.setOnClickListener { chiediNuovaPartita() }
        b.btnFinish.setOnClickListener { completaDaSolo() }
        b.btnInfo.setOnClickListener {
            openDialog?.dismiss()
            openDialog = InfoDialog.show(this, R.string.info_title, R.string.rules_klondike)
        }

        // LA GEOMETRIA SEGUE IL CONTENITORE, non il ciclo di vita dell'activity.
        //
        // Il tavolo non conosce la propria misura finche' non e' stato disposto, quindi il
        // primo calcolo deve aspettare. Ma non basta calcolarlo una volta: le carte non
        // stanno nel layout, le posiziona il codice sulle misure trovate qui, e se il
        // contenitore cambia misura senza che nessuno le ricalcoli le carte restano dove
        // erano. Succede a ogni rotazione su un dispositivo grande e a ogni schermo diviso
        // su qualunque telefono - l'activity non viene ricreata, per i configChanges nel
        // manifest - e anche aprendo un pieghevole o cambiando le barre di sistema.
        //
        // Il listener sul layout copre tutti questi casi insieme, perche' guarda l'unica
        // cosa che conta davvero: che la misura del tavolo sia cambiata. Il ricalcolo passa
        // da un post per non ridisegnare dentro il layout stesso.
        b.board.addOnLayoutChangeListener { _, l, t, r, bo, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || bo - t != oldB - oldT) chiediRimisura()
        }
        chiediRimisura()
    }

    /**
     * Il ricalcolo passa sempre dall'Handler e mai direttamente.
     *
     * Due motivi. Il listener del layout viene chiamato DENTRO il passaggio di layout, e
     * render() aggiunge viste e cambia layoutParams: farlo li' significa chiedere un layout
     * mentre uno e' in corso. E il removeCallbacks fa in modo che piu' segnalazioni
     * ravvicinate - un ridimensionamento a trascinamento ne produce parecchie al secondo -
     * si riducano a un solo ricalcolo.
     */
    private fun chiediRimisura() {
        ui.removeCallbacks(rimisura)
        ui.post(rimisura)
    }

    /** Ricalcola la geometria e ridisegna; alla prima volta avvia anche la partita. */
    private val rimisura = Runnable {
        if (!destroyed && b.board.width > 0 && b.board.height > 0) {
            misura()
            if (started) render() else if (!restoreState()) nuovaPartita()
            avviaAutomatico()
        }
    }

    /**
     * Rotazione o ridimensionamento della finestra: come negli altri tre giochi l'activity
     * non viene ricreata, quindi la partita resta viva e basta ricalcolare le misure. Qui
     * pero' non e' un ritocco: senza il ricalcolo le carte, che sono posizionate a mano,
     * restano alle coordinate della finestra precedente.
     *
     * Il grosso del lavoro lo fa comunque il listener sul layout in onCreate: questo metodo
     * c'e' perche' il cambio di configurazione puo' non produrre un layout di misura diversa
     * (per esempio cambiando solo la densita' o la dimensione del carattere) e anche in quel
     * caso cardH, e con lui gli sfalsamenti delle colonne, va rifatto.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        chiediRimisura()
    }

    override fun onResume() {
        super.onResume()
        // il Klondike usa sempre e solo il mazzo francese: il mazzo scelto nelle impostazioni
        // vale per i tre giochi italiani e qui non c'entra
        CardView.setDeck(Prefs.DECK_FR)
        // Riletto a ogni ritorno: la voce si cambia nelle impostazioni, che sono un'altra
        // schermata, e tornando qui deve valere subito.
        autoPlay = Prefs.autoPlay(this)
        // Un solo punto di ingresso per tutto, invece del render() diretto di prima. Fa due
        // cose in piu' che servono entrambe: rimisura, perche' la finestra puo' essere
        // cambiata mentre la schermata era ferma (schermo diviso aperto da un'altra app), e
        // avvia la partita se non e' ancora partita, perche' onStop svuota l'Handler e
        // fermarsi nell'istante fra la creazione e il primo layout lasciava il ricalcolo
        // per strada e il tavolo vuoto per sempre.
        chiediRimisura()
    }

    override fun onStop() {
        saveState()
        ui.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        if (isFinishing) SavedGame.clear(this, SavedGame.KLONDIKE)
        ui.removeCallbacksAndMessages(null)
        openDialog?.dismiss()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- geometria

    /**
     * Misure del tavolo: il minimo fra un vincolo di larghezza e uno di altezza, come fa
     * CardSize per gli altri tre giochi.
     *
     * In larghezza sette colonne devono stare affiancate, e questo di solito e' il vincolo
     * che comanda. In verticale servono la fila alta piu' una colonna sotto: se si guarda
     * solo la larghezza, in orizzontale su un dispositivo grande le carte vengono tanto
     * alte che alle colonne non resta spazio.
     *
     * La proporzione e' 1,4 e non 1,829: le carte francesi sono piu' tozze. E' la ragione
     * per cui la misura non passa da CardSize, che quella proporzione ce l'ha cablata.
     */
    private fun misura() {
        gap = dp(4)
        boardW = b.board.width
        val boardH = b.board.height

        // In larghezza sette colonne piu' otto spazi: e' il vincolo di sempre.
        val perLarghezza = (boardW - 8 * gap) / 7
        // In altezza servono la fila alta piu' una colonna di riferimento: in tutto
        // ALTEZZE_UTILI volte l'altezza di una carta, un numero che viene dagli
        // sfalsamenti veri e non da una stima.
        //
        // Senza questo secondo vincolo, in orizzontale su un dispositivo grande la
        // larghezza disponibile e' quasi il doppio, le carte venivano alte in proporzione
        // e al tavolo non restava spazio: le colonne si schiacciavano fino a mostrare una
        // carta sola. E' lo stesso difetto che CardSize evita agli altri tre giochi
        // prendendo il minimo fra i due vincoli, e qui mancava.
        val perAltezza = ((boardH - gap * 7) / (RAPPORTO_FRANCESE * ALTEZZE_UTILI)).toInt()
        // Sotto una certa misura la carta non si legge comunque e il gioco non e' giocabile:
        // meglio lasciare che l'ultima carta di una colonna profonda resti tagliata in
        // basso che rimpicciolire tutto il tavolo per farcela stare.
        cardW = minOf(perLarghezza, perAltezza).coerceAtLeast(dp(28))
        cardH = (cardW * RAPPORTO_FRANCESE).toInt()

        // Quando comanda l'altezza le sette colonne non riempiono piu' la larghezza:
        // si centrano, invece di restare accostate a sinistra con un vuoto a destra.
        marginX = ((boardW - (7 * cardW + 6 * gap)) / 2).coerceAtLeast(gap)
        topY = gap
        tableauY = topY + cardH + gap * 5
        passoScarti = cardW / 4
        // Il MAZZO sta all'estrema destra, gli SCARTI subito alla sua sinistra.
        //
        // wasteX e' la posizione della carta IN CIMA agli scarti, non della prima del
        // ventaglio: le piu' vecchie si dispongono verso sinistra, a scalare. Il motivo e'
        // che la carta in cima e' l'unica giocabile, e con l'ancoraggio sulla prima si
        // spostava ogni volta che il numero di carte visibili cambiava da tre a due a una.
        // Ancorando quella in cima, sta sempre nello stesso punto: la si prende senza
        // guardare, ed e' esattamente quello che serve in un gioco fatto di raffiche di
        // tocchi.
        // Ancorati alla settima e alla sesta colonna e non al bordo della finestra: quando
        // comanda l'altezza il blocco delle colonne e' piu' stretto della finestra, e col
        // vecchio calcolo il mazzo restava staccato, a destra, fuori squadra con le colonne.
        stockX = slotX(6)
        wasteX = slotX(5)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun slotX(i: Int): Int = marginX + i * (cardW + gap)

    // ---------------------------------------------------------------- disegno

    private fun render(daAnimare: Map<Card, Pair<Float, Float>>? = null) {
        if (destroyed || cardW == 0) return
        var carte = 0
        var slot = 0
        posizioni.clear(); vistaDi.clear()

        // --- segnaposti ---
        // Le quattro fondazioni a sinistra, sotto la scritta; il prelievo a destra.
        // Quello del tallone resta toccabile anche da vuoto: e' cosi' che si rigira.
        for (s in 0..3) slot = piazzaSlot(slot, slotX(s), topY, null)
        slot = piazzaSlot(slot, stockX, topY) { pesca() }
        // anche gli scarti hanno il loro posto disegnato: a inizio partita e' vuoto, e senza
        // segnaposto non si capirebbe dove vanno a finire le carte che si voltano
        slot = piazzaSlot(slot, wasteX, topY, null)

        // --- tallone ---
        if (game.stock.isNotEmpty()) {
            carte = piazzaCarta(carte, null, false, stockX, topY) { pesca() }
        }

        // --- scarti: si vedono le ultime tre, sfalsate, cosi' pescando a tre si sa che c'e'
        //     sotto invece di vedere una carta sola comparire dal nulla ---
        val visibili = min(3, game.waste.size)
        for (k in 0 until visibili) {
            val c = game.waste[game.waste.size - visibili + k]
            val ultima = (k == visibili - 1)
            // la carta in cima cade su wasteX, le piu' vecchie a scalare verso sinistra
            val x = wasteX - (visibili - 1 - k) * passoScarti
            carte = piazzaCarta(carte, c, true, x, topY) {
                if (ultima) muovi(game.autoTargetFromWaste())
            }
        }

        // --- fondazioni ---
        for (s in 0..3) {
            val f = game.foundations[s]
            if (f.isEmpty()) continue
            carte = piazzaCarta(carte, f.last(), true, slotX(s), topY) {
                muovi(game.autoTargetFromFoundation(s))
            }
        }

        // --- colonne ---
        for (col in 0..6) {
            val c = game.tableau[col]
            if (c.isEmpty) { slot = piazzaSlot(slot, slotX(col), tableauY, null); continue }
            val (dCoperte, dScoperte) = scarti(c)
            var y = tableauY.toFloat()
            for (h in c.hidden.indices) {
                // una coperta non e' toccabile: passare null invece di una lambda vuota
                // evita che si prenda il tocco destinato alla scoperta che le sta sopra
                carte = piazzaCarta(carte, null, false, slotX(col), y.toInt(), null)
                y += dCoperte
            }
            for (i in c.shown.indices) {
                val carta = c.shown[i]
                carte = piazzaCarta(carte, carta, true, slotX(col), y.toInt()) {
                    muovi(game.autoTargetFromColumn(col, i))
                }
                y += dScoperte
            }
        }

        // Le viste che avanzano NON si tolgono dal contenitore, si nascondono: toglierle per
        // posizione sarebbe sbagliato, perche' l'ordine dei figli e' stato rimescolato da
        // bringToFront per impilare i ventagli, e si finirebbe per rimuovere una carta in uso.
        for (i in carte until cardPool.size) spegni(cardPool[i])
        for (i in slot until slotPool.size) spegni(slotPool[i])
        aggiornaBarra()
        if (daAnimare != null) animaSpostate(daAnimare)
    }

    /**
     * Fa scivolare ogni carta dalla posizione di prima a quella di adesso.
     *
     * La vista e' gia' al posto giusto: si riporta indietro con una traslazione e la si
     * lascia tornare. Cosi' l'animazione non puo' mai finire in un punto sbagliato, perche'
     * non e' l'animazione a decidere dove va la carta: quando finisce, la carta e' dove il
     * disegno l'ha messa comunque. Se l'animazione viene interrotta, al massimo la carta
     * salta in posizione, che e' esattamente il comportamento di prima.
     */
    private fun animaSpostate(prima: Map<Card, Pair<Float, Float>>) {
        for ((carta, ora) in posizioni) {
            val da = prima[carta] ?: continue
            if (da == ora) continue
            val v = vistaDi[carta] ?: continue
            // translationZ e NON bringToFront.
            //
            // bringToFront riordina i figli del contenitore, e l'ordine dei figli e' proprio
            // quello che impila i ventagli. Animando piu' carte insieme - un gruppo, o le
            // ventiquattro del rigiro - l'ordine in cui venivano portate avanti era quello
            // casuale della mappa, e dopo qualche mossa il ventaglio degli scarti si
            // ritrovava impilato al contrario: si vedeva sfalsato, e soprattutto una carta
            // vecchia, che non ha il tocco, finiva sopra a quella in cima, che ce l'ha.
            // Sembrava che le carte non si potessero piu' prendere; in realta' il tocco
            // arrivava alla carta sbagliata.
            //
            // translationZ alza la carta solo per il disegno, senza toccare l'ordine dei
            // figli, e si azzera appena l'animazione finisce.
            v.translationZ = 1f
            v.translationX = da.first
            v.translationY = da.second
            v.animate().translationX(ora.first).translationY(ora.second)
                .setDuration(DURATA_MOSSA)
                .withEndAction { v.translationZ = 0f }
                .start()
        }
    }

    /**
     * Di quanto si sfalsano le carte di una colonna.
     *
     * Le coperte si stringono piu' delle scoperte: di una coperta basta vedere che c'e', di
     * una scoperta bisogna leggere l'angolo. Se cosi' la colonna sfora l'altezza del tavolo,
     * i due scarti si comprimono insieme finche' non ci sta: meglio carte piu' vicine che
     * una colonna che esce dallo schermo, perche' quella che esce e' l'ultima, cioe' proprio
     * quella su cui si gioca.
     */
    private fun scarti(c: KlondikeGame.Column): Pair<Float, Float> {
        var dCop = cardH * SFALSO_COPERTA
        var dSco = cardH * SFALSO_SCOPERTA
        val disponibile = (b.board.height - tableauY - gap).toFloat()
        val servono = c.hidden.size * dCop + max(0, c.shown.size - 1) * dSco + cardH
        if (servono > disponibile && servono > cardH) {
            // Il fattore va tenuto dentro 0..1, e non e' pignoleria.
            //
            // Se lo spazio disponibile e' MINORE di una carta - finestra molto bassa in
            // schermo diviso, o il caso in cui la carta veniva calcolata sulla sola
            // larghezza - il numeratore e' negativo, e con lui i due sfalsamenti: le carte
            // coperte si disegnavano verso l'ALTO, finendo sopra le fondazioni. Il
            // pavimento del 5% e del 10% qui sotto proteggeva dSco e non dCop.
            //
            // Comprimere fino a zero non ha senso comunque: da un certo punto in giu' le
            // carte sono sovrapposte del tutto e tanto vale lasciarle sforare in basso,
            // dove al massimo l'ultima resta un po' tagliata, invece di impilarle tutte
            // nello stesso punto e non far vedere piu' niente.
            val k = ((disponibile - cardH) / (servono - cardH)).coerceIn(0f, 1f)
            dCop *= k; dSco *= k
        }
        return Pair(max(dCop, cardH * 0.05f), max(dSco, cardH * 0.10f))
    }

    private fun spegni(v: View) {
        v.visibility = View.GONE
        v.setOnClickListener(null)
        v.isClickable = false
    }

    /**
     * Mette in posizione una vista del serbatoio, creandola se non c'e' ancora.
     *
     * bringToFront a ogni passaggio non e' uno spreco: e' quello che impila il ventaglio nel
     * verso giusto. Disegnando dall'alto verso il basso, ogni carta finisce sopra a quella
     * che la precede, ed e' cosi' che si vede l'angolo di ognuna e la faccia intera solo
     * dell'ultima. Senza, l'ordine sarebbe quello in cui le viste sono state create la prima
     * volta, che dopo qualche mossa non vuol piu' dire niente.
     */
    private fun sistema(v: View, x: Int, y: Int, onTap: (() -> Unit)?) {
        v.animate().cancel()      // una vista riusata puo' avere un'animazione ancora in corso
        v.translationZ = 0f       // e puo' essere rimasta sollevata da un'animazione interrotta
        if (v.parent == null) b.board.addView(v)
        v.bringToFront()
        val lp = (v.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(cardW, cardH)
        lp.width = cardW; lp.height = cardH
        v.layoutParams = lp
        v.translationX = x.toFloat(); v.translationY = y.toFloat()
        v.visibility = View.VISIBLE
        v.alpha = 1f
        if (onTap == null) { v.setOnClickListener(null); v.isClickable = false }
        else v.setOnClickListener { onTap() }
    }

    private fun piazzaSlot(indice: Int, x: Int, y: Int, onTap: (() -> Unit)? = null): Int {
        val v = if (indice < slotPool.size) slotPool[indice] else {
            val nuovo = View(this)
            nuovo.setBackgroundResource(R.drawable.slot_vuoto)
            slotPool.add(nuovo); nuovo
        }
        sistema(v, x, y, onTap)
        return indice + 1
    }

    private fun piazzaCarta(indice: Int, c: Card?, faceUp: Boolean, x: Int, y: Int,
                            onTap: (() -> Unit)?): Int {
        val v = if (indice < cardPool.size) cardPool[indice] else {
            val nuovo = CardView(this)
            nuovo.french = true          // TalkBack: "Fante di cuori", non "11 di denari"
            cardPool.add(nuovo); nuovo
        }
        v.card = c
        v.faceUp = faceUp
        sistema(v, x, y, onTap)
        // Solo le scoperte: una coperta e' indistinguibile da un'altra, e animarle
        // significherebbe far volare dorsi che a occhio non si sono mossi.
        if (c != null && faceUp) {
            posizioni[c] = Pair(x.toFloat(), y.toFloat())
            vistaDi[c] = v
        }
        return indice + 1
    }

    private fun aggiornaBarra() {
        val rimaste = 52 - game.foundations.sumOf { it.size }
        // hasAnyMove() era scritta e non la chiamava nessuno: il giocatore non veniva MAI
        // avvisato che la smazzata era chiusa e restava a toccare carte senza capire perche'
        // non si muovesse niente. La risposta e' prudente per costruzione - dice di no solo
        // quando non resta proprio niente da toccare - quindi si puo' mostrare senza il
        // rischio di dire "hai perso" a chi invece poteva ancora vincere.
        b.txtStatus.text = when {
            !game.finished && !game.hasAnyMove() -> getString(R.string.kl_no_moves)
            autoFermo && !game.finished -> getString(R.string.kl_auto_stop)
            else -> getString(R.string.kl_status, game.moves, rimaste)
        }
        b.btnUndo.isEnabled = game.canUndo
        b.btnUndo.alpha = if (game.canUndo) 1f else 0.4f
        // Rigioca si spegne a smazzata appena distribuita: non c'e' niente da rimettere
        // a posto, e un pulsante che si puo' premere senza che accada nulla e' peggio di uno
        // spento.
        val puoRicominciare = started && game.moves > 0
        b.btnRestart.isEnabled = puoRicominciare
        b.btnRestart.alpha = if (puoRicominciare) 1f else 0.4f
        b.btnFinish.visibility = if (game.canAutoFinish()) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- mosse

    private fun pesca() {
        val m = if (game.stock.isNotEmpty()) KlondikeGame.Move.Draw else KlondikeGame.Move.Recycle
        muovi(m)
    }

    private fun muovi(m: KlondikeGame.Move?) {
        if (m == null || destroyed) return
        val prima = HashMap(posizioni)
        if (!game.play(m)) return
        render(prima)
        if (game.finished) vinta() else avviaAutomatico()
    }

    /**
     * Il passo del gioco automatico, se e' accesa la voce nelle impostazioni.
     *
     * Passa sempre dall'Handler, e sempre con un removeCallbacks davanti: muovi() la chiama
     * a ogni mossa, comprese quelle dell'utente, e senza il removeCallbacks due tocchi
     * vicini metterebbero in coda due catene che si pestano i piedi.
     */
    private fun avviaAutomatico() {
        if (!autoPlay || autoFermo || destroyed || !started || game.finished) return
        ui.removeCallbacks(passoAutomatico)
        ui.postDelayed(passoAutomatico, DURATA_MOSSA + 60)
    }

    private val passoAutomatico = Runnable {
        // Non gioca dietro a una finestra aperta: la vittoria e le due conferme aspettano
        // una risposta, e vedere le carte muoversi da sole sotto un dialogo
        // sarebbe solo confondente.
        if (autoPlay && started && !game.finished && !destroyed && openDialog?.isShowing != true) {
            val m = game.mossaAutomatica()
            if (m == null || autoMosse >= AUTO_MOSSE_MAX || autoGiri >= AUTO_GIRI_MAX) {
                // Si fermi e lo dica. Fermarsi in silenzio lascerebbe a chi guarda il dubbio
                // se sia finito il gioco o si sia piantata l'app.
                autoFermo = true
                aggiornaBarra()
            } else {
                autoMosse++
                // I giri del tallone si contano, il resto li azzera. Il motore si ferma da
                // solo quando NESSUNA carta che gira ha un posto; questo conto serve per
                // l'altro caso, pescando a tre: una carta buona c'e', ma non passa mai in
                // cima. Due giri interi bastano, perche' dopo un rigiro l'allineamento e'
                // lo stesso e il terzo giro mostrerebbe le stesse carte del secondo.
                if (m is KlondikeGame.Move.Recycle) autoGiri++
                else if (m !is KlondikeGame.Move.Draw) autoGiri = 0
                muovi(m)
            }
        }
    }

    /**
     * Il completamento automatico.
     *
     * Non e' un lusso: quando non ci sono piu' carte coperte e il tallone e' finito, ogni
     * carta e' raggiungibile e non restano scelte da fare. Senza questo pulsante ci sarebbero
     * da fare fino a cinquantadue tocchi che non decidono niente.
     */
    private fun completaDaSolo() {
        // una catena per volta: se no il completamento e il gioco automatico postano
        // entrambi e le carte partono l'una sopra l'altra
        autoFermo = true
        val m = game.nextAutoMove()
        if (m == null || destroyed) { render(); return }
        val prima = HashMap(posizioni)
        game.play(m)
        render(prima)
        // il passo aspetta un filo piu' della durata dell'animazione, se no le carte
        // partirebbero l'una sopra l'altra e si vedrebbe un ingorgo invece di una salita
        if (game.finished) vinta() else ui.postDelayed({ completaDaSolo() }, DURATA_MOSSA + 30)
    }

    // ---------------------------------------------------------------- partita

    private fun nuovaPartita() {
        // Abbandonare una partita gia' cominciata la conta come persa. E' l'unico momento in
        // cui ha senso farlo: uscire dal gioco non conta niente, perche' la partita resta
        // salvata e la riprendi, e una smazzata bloccata non si puo' riconoscere in modo
        // affidabile. Distribuire di nuovo, invece, e' una rinuncia dichiarata.
        // La soglia e' la STESSA della conferma qui sotto, e devono restare la stessa cosa.
        // Con "almeno una mossa" bastava voltare una carta del tallone - che conta come
        // mossa - e toccare Nuovo: la partita veniva ridistribuita senza chiedere niente,
        // perche' sotto le cinque mosse la conferma non compare, e intanto in statistica
        // arrivava una sconfitta che nessuno aveva dichiarato.
        if (started && !game.finished && game.moves >= MOSSE_PER_ABBANDONO) {
            Prefs.recordMatch(this, Prefs.GAME_KLONDIKE, false)
        }
        game = KlondikeGame(Prefs.klondikeDraw(this))
        game.newGame()
        started = true
        vittoriaContata = false
        autoFermo = false
        autoMosse = 0
        autoGiri = 0
        render()
        avviaAutomatico()
    }

    /**
     * Ridistribuire chiede SEMPRE conferma, tranne a partita finita.
     *
     * Prima la chiedeva solo sopra le cinque mosse, e sotto ridistribuiva di colpo: ma un
     * tocco per sbaglio su Nuovo butta via il lavoro comunque, e l'unico modo di
     * accorgersene e' vedere le carte cambiare. A partita finita l'eccezione resta: non c'e'
     * niente da abbandonare, e far confermare dopo aver vinto e' attrito e niente altro.
     *
     * Le due domande sono diverse perche' le due situazioni sono diverse: sopra la soglia la
     * partita conta come persa, sotto no. Chi risponde deve sapere quale delle due sta
     * accettando, se no la conferma non informa, chiede solo di ripetere il tocco.
     */
    private fun chiediNuovaPartita() {
        if (game.finished) { nuovaPartita(); return }
        val conta = started && game.moves >= MOSSE_PER_ABBANDONO
        openDialog?.dismiss()
        openDialog = AlertDialog.Builder(this)
            .setTitle(R.string.kl_deal)
            .setMessage(if (conta) R.string.kl_deal_ask else R.string.kl_deal_ask_free)
            .setPositiveButton(R.string.yes) { _, _ -> nuovaPartita() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /**
     * Rimette la stessa smazzata, e chiede conferma perche' butta via le mosse fatte.
     *
     * E' il pulsante che a schermo dice "Rigioca". Non conta niente in statistica, ed e' la
     * differenza con "Nuovo": quello cambia smazzata, cioe' rinuncia, e conta come partita
     * persa; questo e' un secondo tentativo sulle stesse carte. La domanda lo dice, perche'
     * i due pulsanti sono accanto.
     *
     * Sopra ci sta anche l'unico effetto collaterale: azzerando vittoriaContata, una
     * smazzata gia' vinta e poi ricominciata, se rivinta, conta una seconda vittoria. E'
     * l'interpretazione coerente - una partita giocata, una partita vinta - ma vuol dire
     * che chi vuole gonfiare le statistiche puo' farlo.
     */
    private fun chiediRicomincia() {
        if (!started || game.moves == 0) return
        openDialog?.dismiss()
        openDialog = AlertDialog.Builder(this)
            .setTitle(R.string.kl_restart)
            .setMessage(R.string.kl_restart_ask)
            .setPositiveButton(R.string.yes) { _, _ -> ricominciaOra() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun ricominciaOra() {
        if (!game.ricomincia()) return
        vittoriaContata = false
        autoFermo = false
        autoMosse = 0
        autoGiri = 0
        render()
        avviaAutomatico()
    }

    private var vittoriaContata = false

    private fun vinta() {
        if (destroyed || isFinishing) return
        // il controllo evita di contarla due volte se la finestra viene riaperta
        if (!vittoriaContata) {
            vittoriaContata = true
            Prefs.recordMatch(this, Prefs.GAME_KLONDIKE, true)
        }
        openDialog?.dismiss()
        openDialog = AlertDialog.Builder(this)
            .setTitle(R.string.kl_won_title)
            .setMessage(getString(R.string.kl_won, game.moves))
            .setCancelable(false)
            .setPositiveButton(R.string.kl_deal) { _, _ -> nuovaPartita() }
            .setNegativeButton(R.string.back_home) { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------- salvataggio

    private fun saveState() {
        if (!started || destroyed) return
        val w = SavedGame.Writer()
        // prima il numero di carte da pescare: serve per costruire il gioco, quindi va letto
        // prima dello stato
        w.int(game.drawCount)
        // il mazzo come e' stato distribuito: serve a Rigioca dopo una ripresa
        w.cards(game.mazzoIniziale)
        game.save(w)
        SavedGame.write(this, SavedGame.KLONDIKE, w)
    }

    /**
     * Il numero di carte da pescare fa parte del salvataggio, e non si rilegge dalle
     * impostazioni: cambiandolo a partita in corso la smazzata diventerebbe un'altra cosa,
     * con il tallone gia' consumato secondo la regola vecchia. La nuova impostazione entra in
     * vigore alla smazzata successiva.
     */
    private fun restoreState(): Boolean {
        val r = SavedGame.read(this, SavedGame.KLONDIKE) ?: return false
        try {
            game = KlondikeGame(r.int())
            game.caricaMazzoIniziale(r.cards())
            game.load(r)
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.KLONDIKE)
            return false
        }
        started = true
        vittoriaContata = game.finished
        render()
        // Se la partita ripresa era gia' vinta, la finestra va rimessa: senza, si ritrovava
        // il tavolo completo e nessun messaggio, con Completa nascosto (non c'e' piu' niente
        // da completare) e nessun indizio di essere arrivati in fondo. La vittoria non viene
        // contata due volte, ci pensa vittoriaContata qui sopra.
        if (game.finished) vinta()
        return true
    }

    companion object {
        /** 840/600: le carte francesi sono piu' tozze delle italiane, che stanno a 1,829. */
        const val RAPPORTO_FRANCESE = 1.4f

        /**
         * Quanto dura lo scivolamento di una carta.
         *
         * Centosessanta millesimi: abbastanza da far vedere DA DOVE arriva la carta, che nel
         * Klondike e' l'informazione che serve, perche' i tocchi sono tanti e ravvicinati e
         * senza il movimento si perde il filo di cosa e' appena successo. Piu' lunga
         * rallenterebbe il gioco, che qui e' fatto di raffiche di mosse e non di una giocata
         * ogni tanto come negli altri tre.
         */
        const val DURATA_MOSSA = 160L

        /**
         * Da quante mosse in poi ridistribuire e' un abbandono.
         *
         * Un numero solo per due decisioni che devono coincidere: sopra la soglia
         * ridistribuire chiede conferma E conta come partita persa, sotto non fa ne' l'una
         * ne' l'altra cosa. La soglia e' bassa apposta: chi ha fatto due mosse sta ancora
         * guardando le carte e vuole solo un'altra smazzata; chi ne ha fatte trenta ci sta
         * lavorando, e allora la rinuncia e' dichiarata e va contata.
         */
        const val MOSSE_PER_ABBANDONO = 5

        /**
         * Quanti rigiri del tallone tollerare, senza che si muova nient'altro, prima di
         * fermare il gioco automatico. Due: dopo un rigiro l'allineamento delle carte e' lo
         * stesso, quindi il terzo giro mostrerebbe esattamente le stesse carte del secondo.
         */
        const val AUTO_GIRI_MAX = 2

        /** Tetto di sicurezza sulle mosse automatiche in una smazzata: le mosse utili sono
         *  limitate per costruzione, ma un tetto costa un confronto e chiude il discorso. */
        const val AUTO_MOSSE_MAX = 800

        /**
         * Di quanto si sfalsano le carte in colonna, in frazioni dell'altezza di una carta.
         *
         * Le coperte si stringono piu' delle scoperte: di una coperta basta vedere che c'e',
         * di una scoperta bisogna leggere l'angolo.
         *
         * Stanno qui, e non dentro scarti(), perche' li usa anche misura(): il vincolo di
         * altezza e' esattamente "una colonna con questi sfalsamenti ci deve stare". Con i
         * numeri scritti in due posti, cambiarne uno avrebbe fatto misurare il tavolo su
         * uno sfalsamento e disegnarlo su un altro.
         */
        const val SFALSO_COPERTA = 0.16f

        /**
         * Lo sfalso delle scoperte e' 0,32 e non 0,30, e il numero viene dall'indice.
         *
         * Di una carta coperta da quella sotto si vede una fascia alta quanto lo sfalso, e
         * in quella fascia deve entrare l'indice INTERO. L'inchiostro dell'indice arriva a
         * 0,310 dell'altezza (vedi INK_BOT in art/carte_francesi.py): con 0,30 restava
         * tagliato il gancio del J, che senza il gancio si legge come una I. Il prezzo e'
         * niente: una colonna da sei coperte e sette scoperte passa da 737 a 760 px sui
         * 1130 disponibili di un telefono da 360dp.
         */
        const val SFALSO_SCOPERTA = 0.32f

        /**
         * Quante altezze di carta deve poter contenere il tavolo.
         *
         * Una per la fila in alto (fondazioni, mazzo, scarti) piu' la colonna di
         * riferimento: SEI COPERTE E SETTE SCOPERTE, cioe' la settima colonna dopo che le
         * sue coperte sono state girate. Non il caso peggiore assoluto, che sarebbero
         * diciannove carte (sei coperte piu' una sequenza da Re ad Asso): tararlo su quello
         * vorrebbe dire carte piccole per tutti per una colonna che si vede una volta su
         * cento. Alle colonne piu' profonde di questa pensa la compressione in scarti().
         */
        const val ALTEZZE_UTILI = 1f + 1f + 6 * SFALSO_COPERTA + 6 * SFALSO_SCOPERTA
    }
}
