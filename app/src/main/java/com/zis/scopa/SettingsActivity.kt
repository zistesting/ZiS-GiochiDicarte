package com.zis.scopa

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Prefs.scoreTarget(this) == 21) b.radio21.isChecked = true else b.radio11.isChecked = true

        b.groupTarget.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setScoreTarget(this, if (checkedId == R.id.radio21) 21 else 11)
        }

        if (Prefs.briscolaTarget(this) == 11) b.radioB11.isChecked = true else b.radioB5.isChecked = true

        b.groupBriscola.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setBriscolaTarget(this, if (checkedId == R.id.radioB11) 11 else 5)
        }

        setupPauseSwitch()

        b.btnBack.setOnClickListener { finish() }
    }

    /** Attivare la pausa e' libero; disattivarla richiede la password. */
    private fun setupPauseSwitch() {
        b.switchPause.isChecked = Prefs.pauseEnabled(this)
        b.switchPause.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                Prefs.setPauseEnabled(this, true)
            } else {
                askPassword(
                    onOk = { Prefs.setPauseEnabled(this, false) },
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
        setupPauseSwitch()
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

        AlertDialog.Builder(this)
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

    private companion object {
        const val PASSWORD = "zis"
    }
}
