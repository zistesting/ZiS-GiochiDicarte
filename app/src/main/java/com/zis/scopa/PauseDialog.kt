package com.zis.scopa

import android.os.CountDownTimer
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Avviso sul gioco compulsivo con conto alla rovescia. Il pulsante per continuare
 * resta disabilitato finche' il tempo non e' scaduto; si puo' sempre tornare al menu.
 */
object PauseDialog {

    fun show(activity: AppCompatActivity, remainingMs: Long, onReady: () -> Unit, onLeave: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.pause_title)
            .setMessage(R.string.pause_message)
            .setCancelable(false)
            .setPositiveButton(R.string.pause_wait, null)   // listener impostato dopo, vedi sotto
            .setNegativeButton(R.string.back_home) { _, _ -> onLeave() }
            .create()

        var timer: CountDownTimer? = null
        dialog.setOnShowListener {
            val ok: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = false
            ok.setOnClickListener { dialog.dismiss(); onReady() }

            timer = object : CountDownTimer(remainingMs, 250) {
                override fun onTick(msLeft: Long) {
                    val s = ((msLeft + 999) / 1000).toInt()
                    ok.text = activity.getString(R.string.pause_countdown, s)
                }
                override fun onFinish() {
                    ok.isEnabled = true
                    ok.setText(R.string.continue_match)
                }
            }.start()
        }
        // il timer non deve sopravvivere alla finestra, altrimenti tocca una view morta
        dialog.setOnDismissListener { timer?.cancel() }
        dialog.show()
    }
}
