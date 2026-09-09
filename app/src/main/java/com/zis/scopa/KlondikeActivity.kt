package com.zis.scopa

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
    private var openDialog: AlertDialog? = null

    // geometria, calcolata una volta che il tavolo conosce la propria misura
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private var topY = 0
    private var tableauY = 0

    /**
     * Le viste in gioco, riusate fra un disegno e l'altro.
     *
     * Stessa lezione degli altri tre giochi: ricostruire tutto a ogni disegno significa
     * buttare e riallocare fino a cinquantadue viste per ogni tocco. Qui pero' e' peggio che
     * altrove, perche' le carte sono piu' del doppio.
     */
    private val cardPool = mutableListOf<CardView>()
    private val slotPool = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKlondikeBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

        game = KlondikeGame(Prefs.klondikeDraw(this))
        b.btnUndo.setOnClickListener { if (game.undo()) render() }
        b.btnDeal.setOnClickListener { chiediNuovaPartita() }
        b.btnFinish.setOnClickListener { completaDaSolo() }
        b.btnInfo.setOnClickListener {
            openDialog?.dismiss()
            openDialog = InfoDialog.show(this, R.string.info_title, R.string.rules_klondike)
        }

        // Il tavolo non conosce la propria misura finche' non e' stato disposto: la geometria
        // si calcola al primo passaggio, e la partita comincia solo dopo.
        b.board.post {
            misura()
            if (!restoreState()) nuovaPartita()
        }
    }

    override fun onResume() {
        super.onResume()
        // il Klondike usa sempre e solo il mazzo francese: il mazzo scelto nelle impostazioni
        // vale per i tre giochi italiani e qui non c'entra
        CardView.setDeck(Prefs.DECK_FR)
        if (started) render()
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
     * Misure del tavolo.
     *
     * Sette colonne devono stare in larghezza, e questo decide tutto: la carta e' larga
     * quanto resta diviso sette. Non c'e' margine di scelta come negli altri giochi, dove
     * si poteva prendere il minimo fra un vincolo di larghezza e uno di altezza.
     *
     * La proporzione e' 1,4 e non 1,829: le carte francesi sono piu' tozze. E' la ragione
     * per cui la misura non passa da CardSize, che quella proporzione ce l'ha cablata.
     */
    private fun misura() {
        gap = dp(4)
        val w = b.board.width
        cardW = (w - 8 * gap) / 7
        cardH = (cardW * RAPPORTO_FRANCESE).toInt()
        topY = gap
        tableauY = topY + cardH + gap * 4
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun slotX(i: Int): Int = gap + i * (cardW + gap)

    // ---------------------------------------------------------------- disegno

    private fun render() {
        if (destroyed || cardW == 0) return
        var carte = 0
        var slot = 0

        // --- segnaposti: tallone, scarti, quattro fondazioni ---
        // Quello del tallone resta toccabile anche da vuoto: e' cosi' che si rigira.
        slot = piazzaSlot(slot, slotX(0), topY) { pesca() }
        // anche gli scarti hanno il loro posto disegnato: a inizio partita e' vuoto, e senza
        // segnaposto non si capirebbe dove vanno a finire le carte che si voltano
        slot = piazzaSlot(slot, slotX(1), topY, null)
        for (s in 0..3) slot = piazzaSlot(slot, slotX(3 + s), topY, null)

        // --- tallone ---
        if (game.stock.isNotEmpty()) {
            carte = piazzaCarta(carte, null, false, slotX(0), topY) { pesca() }
        }

        // --- scarti: si vedono le ultime tre, sfalsate, cosi' pescando a tre si sa che c'e'
        //     sotto invece di vedere una carta sola comparire dal nulla ---
        val visibili = min(3, game.waste.size)
        val passo = cardW / 4
        for (k in 0 until visibili) {
            val c = game.waste[game.waste.size - visibili + k]
            val ultima = (k == visibili - 1)
            carte = piazzaCarta(carte, c, true, slotX(1) + k * passo, topY) {
                if (ultima) muovi(game.autoTargetFromWaste())
            }
        }

        // --- fondazioni ---
        for (s in 0..3) {
            val f = game.foundations[s]
            if (f.isEmpty()) continue
            carte = piazzaCarta(carte, f.last(), true, slotX(3 + s), topY) {
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
        var dCop = cardH * 0.16f
        var dSco = cardH * 0.30f
        val disponibile = (b.board.height - tableauY - gap).toFloat()
        val servono = c.hidden.size * dCop + max(0, c.shown.size - 1) * dSco + cardH
        if (servono > disponibile && servono > cardH) {
            val k = (disponibile - cardH) / (servono - cardH)
            dCop *= k; dSco *= k
        }
        return Pair(dCop, max(dSco, cardH * 0.10f))
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
            val nuovo = CardView(this); cardPool.add(nuovo); nuovo
        }
        v.card = c
        v.faceUp = faceUp
        sistema(v, x, y, onTap)
        return indice + 1
    }

    private fun aggiornaBarra() {
        val rimaste = 52 - game.foundations.sumOf { it.size }
        b.txtStatus.text = getString(R.string.kl_status, game.moves, rimaste)
        b.btnUndo.isEnabled = game.canUndo
        b.btnUndo.alpha = if (game.canUndo) 1f else 0.4f
        b.btnFinish.visibility = if (game.canAutoFinish()) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- mosse

    private fun pesca() {
        val m = if (game.stock.isNotEmpty()) KlondikeGame.Move.Draw else KlondikeGame.Move.Recycle
        muovi(m)
    }

    private fun muovi(m: KlondikeGame.Move?) {
        if (m == null || destroyed) return
        if (!game.play(m)) return
        render()
        if (game.finished) vinta()
    }

    /**
     * Il completamento automatico.
     *
     * Non e' un lusso: quando non ci sono piu' carte coperte e il tallone e' finito, ogni
     * carta e' raggiungibile e non restano scelte da fare. Senza questo pulsante ci sarebbero
     * da fare fino a cinquantadue tocchi che non decidono niente.
     */
    private fun completaDaSolo() {
        val m = game.nextAutoMove()
        if (m == null || destroyed) { render(); return }
        game.play(m)
        render()
        if (game.finished) vinta() else ui.postDelayed({ completaDaSolo() }, 90)
    }

    // ---------------------------------------------------------------- partita

    private fun nuovaPartita() {
        game = KlondikeGame(Prefs.klondikeDraw(this))
        game.newGame()
        started = true
        render()
    }

    /**
     * Ridistribuire a meta' partita chiede conferma; a partita appena cominciata no.
     *
     * La soglia e' bassa apposta: chi ha fatto due mosse sta ancora guardando le carte e
     * vuole solo un'altra smazzata, chi ne ha fatte trenta ci sta lavorando e un tocco
     * sbagliato gli butterebbe via il lavoro.
     */
    private fun chiediNuovaPartita() {
        if (game.moves < 5 || game.finished) { nuovaPartita(); return }
        openDialog?.dismiss()
        openDialog = AlertDialog.Builder(this)
            .setTitle(R.string.kl_deal)
            .setMessage(R.string.kl_deal_ask)
            .setPositiveButton(R.string.yes) { _, _ -> nuovaPartita() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun vinta() {
        if (destroyed || isFinishing) return
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
            game.load(r)
        } catch (e: Exception) {
            SavedGame.clear(this, SavedGame.KLONDIKE)
            return false
        }
        started = true
        render()
        return true
    }

    companion object {
        /** 840/600: le carte francesi sono piu' tozze delle italiane, che stanno a 1,829. */
        const val RAPPORTO_FRANCESE = 1.4f
    }
}
