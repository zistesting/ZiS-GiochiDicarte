package com.zis.scopa

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    /** Dialogo della password: va chiuso in onDestroy, altrimenti resta appeso all'activity. */
    private var openDialog: AlertDialog? = null

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

    /** Attivare la pausa e' libero; disattivarla richiede la password. */
    private fun setupPauseSwitch() {
        b.switchPause.isChecked = Prefs.pauseEnabled(this)
        setupPauseListener()
        refreshPauseUi()
    }

    private fun setupPauseListener() {
        b.switchPause.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                Prefs.setPauseEnabled(this, true)
                refreshPauseUi()
            } else {
                askPassword(
                    onOk = { Prefs.setPauseEnabled(this, false); refreshPauseUi() },
                    onFail = {
                        restoreSwitch()
                        Toast.makeText(this, R.string.pause_pwd_wrong, Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { restoreSwitch() }
                )
            }
        }
    }

    /** Rimette l'interruttore su "acceso" senza far riscattare il listener. */
    private fun restoreSwitch() {
        b.switchPause.setOnCheckedChangeListener(null)
        b.switchPause.isChecked = true
        refreshPauseUi()
    }

    private fun askPassword(onOk: () -> Unit, onFail: () -> Unit, onCancel: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(R.string.pause_pwd_hint)
            setTextColor(getColor(R.color.silver))
            setHintTextColor(getColor(R.color.silver_dark))
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        openDialog = AlertDialog.Builder(this)
            .setTitle(R.string.pause_pwd_title)
            .setMessage(R.string.pause_pwd_msg)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString().trim().equals(PASSWORD, ignoreCase = true)) onOk() else onFail()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    override fun onDestroy() {
        openDialog?.let { if (it.isShowing) it.dismiss() }
        openDialog = null
        super.onDestroy()
    }

    private companion object {
        const val PASSWORD = "zis"
    }
}
