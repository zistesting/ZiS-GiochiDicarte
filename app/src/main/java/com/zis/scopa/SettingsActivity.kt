package com.zis.scopa

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

        setupPauseSwitch()

        b.switchAuto.isChecked = Prefs.autoPlay(this)
        b.switchAuto.setOnCheckedChangeListener { _, checked -> Prefs.setAutoPlay(this, checked) }

        b.switchBotCards.isChecked = Prefs.showBotCards(this)
        b.switchBotCards.setOnCheckedChangeListener { _, checked -> Prefs.setShowBotCards(this, checked) }

        b.btnBack.setOnClickListener { finish() }
    }

    /**
     * Scelta del mazzo. Se le immagini tradizionali non sono ancora nel progetto la voce
     * resta selezionabile ma compare l'avviso: senza, l'utente la sceglierebbe e non
     * vedrebbe cambiare niente, credendo a un difetto.
     */
    private fun setupDeck() {
        val trad = Prefs.deck(this) == Prefs.DECK_TRAD
        if (trad) b.radioDeckTrad.isChecked = true else b.radioDeckZis.isChecked = true
        b.groupDeck.setOnCheckedChangeListener { _, id ->
            Prefs.setDeck(this, if (id == R.id.radioDeckTrad) Prefs.DECK_TRAD else Prefs.DECK_ZIS)
            refreshDeckInfo()
        }
        refreshDeckInfo()
    }

    private fun refreshDeckInfo() {
        val installato = resources.getIdentifier("${Prefs.DECK_TRAD}_0_1", "drawable", packageName) != 0
        b.txtDeckInfo.visibility =
            if (!installato && Prefs.deck(this) == Prefs.DECK_TRAD) View.VISIBLE else View.GONE
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
}
