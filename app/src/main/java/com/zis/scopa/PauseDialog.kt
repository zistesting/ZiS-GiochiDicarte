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

    /**
     * Restituisce il dialogo creato (null se l'activity stava gia' morendo) cosi' che chi lo
     * apre possa chiuderlo in onDestroy. Senza quel riferimento, se il sistema distruggeva
     * l'activity a dialogo aperto restavano appesi sia la finestra (WindowLeaked) sia il
     * CountDownTimer, che continuava a scrivere su un pulsante ormai morto tenendo in vita
     * l'intera activity.
     */
    fun show(
        activity: AppCompatActivity,
        remainingMs: Long,
        onReady: () -> Unit,
        onLeave: () -> Unit
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null

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
        // il timer non deve sopravvivere alla finestra, altrimenti tocca una view morta.
        // Vale anche quando a chiudere il dialogo e' onDestroy dell'activity.
        dialog.setOnDismissListener { timer?.cancel() }
        dialog.show()
        return dialog
    }
}
