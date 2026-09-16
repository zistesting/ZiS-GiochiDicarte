package com.zis.scopa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {


    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

        setupKlondike()
        setupDeck()

        if (Prefs.scoreTarget(this) == 21) b.radio21.isChecked = true else b.radio11.isChecked = true

        b.groupTarget.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setScoreTarget(this, if (checkedId == R.id.radio21) 21 else 11)
        }

        if (Prefs.briscolaTarget(this) == 11) b.radioB11.isChecked = true else b.radioB5.isChecked = true

        b.groupBriscola.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setBriscolaTarget(this, if (checkedId == R.id.radioB11) 11 else 5)
        }

        if (Prefs.tresetteTarget(this) == 31) b.radioT31.isChecked = true else b.radioT21.isChecked = true

        when (Prefs.drawShowSeconds(this)) {
            1 -> b.radioDraw1.isChecked = true
            3 -> b.radioDraw3.isChecked = true
            else -> b.radioDraw2.isChecked = true
        }

        b.groupDraw.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setDrawShowSeconds(this, when (checkedId) {
                R.id.radioDraw1 -> 1
                R.id.radioDraw3 -> 3
                else -> 2
            })
        }

        b.groupTresette.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setTresetteTarget(this, if (checkedId == R.id.radioT31) 31 else 21)
        }

        // ---- poker ----
        // La variante Texas e' spenta nel layout finche' il motore non sa giocarla: il
        // gruppo si imposta comunque, cosi' il giorno che si accende non c'e' niente da
        // ricordarsi di collegare qui.
        b.groupVariante.check(
            if (Prefs.pokerVariante(this) == Prefs.POKER_HOLDEM) R.id.radioHoldem else R.id.radioDraw)
        b.groupVariante.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setPokerVariante(this,
                if (checkedId == R.id.radioHoldem) Prefs.POKER_HOLDEM else Prefs.POKER_DRAW)
        }

        b.groupGiocatori.check(if (Prefs.pokerGiocatori(this) == 4) R.id.radioG4 else R.id.radioG2)
        b.groupGiocatori.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setPokerGiocatori(this, if (checkedId == R.id.radioG4) 4 else 2)
        }

        b.switchOdds.isChecked = Prefs.pokerOdds(this)
        b.switchOdds.setOnCheckedChangeListener { _, checked -> Prefs.setPokerOdds(this, checked) }

        importi()

        b.switchMazzoCorto.isChecked = Prefs.pokerMazzoCorto(this)
        b.switchMazzoCorto.setOnCheckedChangeListener { _, checked ->
            Prefs.setPokerMazzoCorto(this, checked)
        }

        setupPauseSwitch()
        mostraSoloIlPannelloGiusto()

        b.switchAuto.isChecked = Prefs.autoPlay(this)
        b.switchAuto.setOnCheckedChangeListener { _, checked -> Prefs.setAutoPlay(this, checked) }

        b.switchBotCards.isChecked = Prefs.showBotCards(this)
        b.switchBotCards.setOnCheckedChangeListener { _, checked -> Prefs.setShowBotCards(this, checked) }

        b.btnBack.setOnClickListener { finish() }
    }

    /**
     * Scelta del mazzo.
     *
     * Non c'e' piu' l'avviso "mazzo tradizionale non installato": serviva quando le carte si
     * cercavano per nome a runtime e potevano mancare. Adesso i due mazzi sono tabelle di
     * riferimenti a R.drawable (vedi Decks.kt), quindi se un'immagine manca il progetto non
     * compila proprio e il caso a runtime non esiste piu'.
     */
    /**
     * Quante carte si voltano dal tallone nel solitario.
     *
     * Cambiarlo NON tocca una partita gia' cominciata: quella continua con la regola con cui
     * e' stata distribuita, perche' a meta' strada il tallone e' gia' stato consumato in un
     * certo modo e cambiargli la regola sotto renderebbe la smazzata un'altra cosa.
     */
    private fun setupKlondike() {
        if (Prefs.klondikeDraw(this) == 3) b.radioKl3.isChecked = true else b.radioKl1.isChecked = true
        b.groupDraw3.setOnCheckedChangeListener { _, id ->
            Prefs.setKlondikeDraw(this, if (id == R.id.radioKl3) 3 else 1)
        }
    }

    private fun setupDeck() {
        when (Prefs.deck(this)) {
            Prefs.DECK_TRAD -> b.radioDeckTrad.isChecked = true
            Prefs.DECK_BERG -> b.radioDeckBerg.isChecked = true
            Prefs.DECK_NAP -> b.radioDeckNap.isChecked = true
            else -> b.radioDeckZis.isChecked = true
        }
        b.groupDeck.setOnCheckedChangeListener { _, id ->
            Prefs.setDeck(this, when (id) {
                R.id.radioDeckTrad -> Prefs.DECK_TRAD
                R.id.radioDeckBerg -> Prefs.DECK_BERG
                R.id.radioDeckNap -> Prefs.DECK_NAP
                else -> Prefs.DECK_ZIS
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // se nel frattempo e' scaduta l'ora di disattivazione, l'interruttore torna acceso
        refreshPauseUi()
    }

    /** Aggiorna interruttore e riga informativa in base allo stato attuale. */
    private fun refreshPauseUi() {
        val on = Prefs.pauseEnabled(this)
        b.switchPause.setOnCheckedChangeListener(null)
        b.switchPause.isChecked = on
        setupPauseListener()
        if (on) {
            b.txtPauseInfo.visibility = View.GONE
        } else {
            val min = ((Prefs.pauseOffRemaining(this) + 59_999L) / 60_000L).toInt()
            b.txtPauseInfo.text = getString(R.string.pause_off_info, min)
            b.txtPauseInfo.visibility = View.VISIBLE
        }
    }

    /**
     * L'interruttore della pausa si muove liberamente nei due sensi. Prima disattivarla
     * chiedeva una password: una barriera che non proteggeva nessuno, perche' e' lo stesso
     * utente a decidere per se stesso, e che si limitava a mettere un ostacolo in mezzo.
     */
    private fun setupPauseSwitch() {
        b.switchPause.isChecked = Prefs.pauseEnabled(this)
        setupPauseListener()
        refreshPauseUi()
    }

    private fun setupPauseListener() {
        b.switchPause.setOnCheckedChangeListener { _, checked ->
            Prefs.setPauseEnabled(this, checked)
            refreshPauseUi()
        }
    }
    /**
     * I TRE IMPORTI DEL POKER: fiches di partenza, apertura, rilancio.
     *
     * Meno e piu' invece di tre caselle da scrivere. Da un campo di testo puo' uscire
     * qualunque cosa - zero, un milione, una lettera, il campo vuoto - e ogni valore assurdo
     * andrebbe intercettato e spiegato; con meno e piu' i valori impossibili NON ESISTONO,
     * non serve la tastiera, e si vede di quanto si sale prima di salire.
     *
     * I LIMITI SONO LEGATI FRA LORO, e non sono tre gabbie indipendenti:
     *   - l'apertura non supera il rilancio, perche' una posta piu' grande di una puntata
     *     non e' poker;
     *   - il rilancio non scende sotto l'apertura, che e' la stessa regola letta al
     *     rovescio, ed e' il motivo per cui i due tasti si aggiustano a vicenda invece di
     *     bloccarsi;
     *   - le fiches di partenza non scendono sotto dieci volte l'apertura, sennonche' la
     *     partita sarebbe finita prima di cominciare - dieci mani di sola posta.
     * Quando un limite morde, l'altro valore si sposta di conseguenza e si riscrive a
     * schermo: non c'e' nessun avviso da leggere, si vede.
     *
     * Ogni tocco scrive subito nelle impostazioni, come tutto il resto di questa schermata.
     * La mano eventualmente in corso nel poker si chiude da se': il suo salvataggio non
     * combacia piu' coi parametri e viene scartato (vedi PokerGame.parametri).
     */
    private fun importi() {
        b.btnFichesMeno.setOnClickListener { cambiaFiches(-PASSO_FICHES) }
        b.btnFichesPiu.setOnClickListener { cambiaFiches(PASSO_FICHES) }
        b.btnAperturaMeno.setOnClickListener { cambiaApertura(-PASSO_PUNTATA) }
        b.btnAperturaPiu.setOnClickListener { cambiaApertura(PASSO_PUNTATA) }
        b.btnRilancioMeno.setOnClickListener { cambiaRilancio(-PASSO_PUNTATA) }
        b.btnRilancioPiu.setOnClickListener { cambiaRilancio(PASSO_PUNTATA) }
        scriviImporti()
    }

    private fun cambiaFiches(d: Int) {
        val minimo = maxOf(PASSO_FICHES, 10 * Prefs.pokerApertura(this))
        Prefs.setPokerFiches(this, (Prefs.pokerFiches(this) + d).coerceIn(minimo, FICHES_MAX))
        scriviImporti()
    }

    private fun cambiaApertura(d: Int) {
        val a = (Prefs.pokerApertura(this) + d).coerceIn(PASSO_PUNTATA, PUNTATA_MAX)
        Prefs.setPokerApertura(this, a)
        if (Prefs.pokerRilancio(this) < a) Prefs.setPokerRilancio(this, a)
        if (Prefs.pokerFiches(this) < 10 * a)
            Prefs.setPokerFiches(this, minOf(FICHES_MAX, 10 * a))
        scriviImporti()
    }

    private fun cambiaRilancio(d: Int) {
        val minimo = maxOf(PASSO_PUNTATA, Prefs.pokerApertura(this))
        Prefs.setPokerRilancio(this, (Prefs.pokerRilancio(this) + d).coerceIn(minimo, PUNTATA_MAX))
        scriviImporti()
    }

    private fun scriviImporti() {
        b.txtFiches.text = getString(R.string.poker_amount, Prefs.pokerFiches(this))
        b.txtApertura.text = getString(R.string.poker_amount, Prefs.pokerApertura(this))
        b.txtRilancio.text = getString(R.string.poker_amount, Prefs.pokerRilancio(this))
    }

    companion object {
        /**
         * Il gioco di cui mostrare le impostazioni, messo nell'Intent da chi apre questa
         * schermata. Senza, si aprono le impostazioni GENERALI.
         *
         * Perche' una schermata sola e non cinque: i comandi sono gli stessi, li legge e li
         * scrive lo stesso codice, e cinque schermate vorrebbero dire cinque copie che
         * possono divergere. Quello che cambia e' quali pannelli si vedono, e quella e' una
         * riga per pannello.
         */
        const val EXTRA_GIOCO = "gioco"

        /** Apre le impostazioni di un gioco solo. */
        fun intent(ctx: Context, gioco: String): Intent =
            Intent(ctx, SettingsActivity::class.java).putExtra(EXTRA_GIOCO, gioco)

        const val PASSO_FICHES = 50
        const val FICHES_MAX = 2000
        const val PASSO_PUNTATA = 5
        const val PUNTATA_MAX = 100
    }

    /**
     * Accende i pannelli che servono e spegne gli altri.
     *
     * DAL MENU si vede quello che non e' di un gioco in particolare: la scelta del mazzo -
     * che vale per i tre giochi italiani insieme, quindi non sta bene in nessuno dei tre -
     * il gioco responsabile e le prove.
     *
     * DA UN GIOCO si vede il pannello di quel gioco e nient'altro. La schermata era diventata
     * lunga sei pannelli e mezzo, e chi la apriva a meta' partita per cambiare una cosa
     * doveva scorrere davanti alle impostazioni di altri quattro giochi.
     *
     * A GONE e non a INVISIBLE: invisibile lascerebbe il buco, e la schermata si aprirebbe
     * su un pannello circondato da niente.
     */
    private fun mostraSoloIlPannelloGiusto() {
        val gioco = intent.getStringExtra(EXTRA_GIOCO)
        val delGioco = mapOf(
            Prefs.GAME_SCOPA to b.panelScopa,
            Prefs.GAME_BRISCOLA to b.panelBriscola,
            Prefs.GAME_TRESETTE to b.panelTresette,
            Prefs.GAME_KLONDIKE to b.panelKlondike,
            Prefs.GAME_POKER to b.panelPoker)
        val generali = listOf(b.panelDeck, b.panelPausa, b.panelTest)

        for ((chiave, pannello) in delGioco)
            pannello.visibility = if (chiave == gioco) View.VISIBLE else View.GONE
        for (pannello in generali)
            pannello.visibility = if (gioco == null) View.VISIBLE else View.GONE
    }

}
