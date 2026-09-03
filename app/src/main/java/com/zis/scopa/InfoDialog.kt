package com.zis.scopa

import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Finestra di sole spiegazioni, usata dal pulsante info delle tre schermate di gioco e da
 * quello della schermata iniziale.
 *
 * Il testo e' lungo, percio' va dentro uno ScrollView costruito qui: il messaggio di un
 * AlertDialog scorre da solo su alcune versioni di Android e su altre no, e si finirebbe con
 * l'ultimo paragrafo tagliato via su meta' dei telefoni.
 */
object InfoDialog {

    fun show(activity: AppCompatActivity, titleRes: Int, bodyRes: Int): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null

        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val text = TextView(activity).apply {
            setText(bodyRes)
            setTextColor(activity.getColor(R.color.silver))
            textSize = 15f
            setLineSpacing(0f, 1.15f)
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val scroll = ScrollView(activity).apply { addView(text) }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .create()
        dialog.show()
        return dialog
    }
}
