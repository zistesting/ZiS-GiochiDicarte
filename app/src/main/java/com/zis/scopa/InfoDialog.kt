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
 *
 * Le parole in grassetto sono tag <b> veri dentro le risorse, non testo da interpretare.
 * setText(idRisorsa) legge la stringa con getText(), che restituisce un CharSequence gia'
 * formattato: il grassetto lo applica Android, senza passare da HtmlCompat.
 *
 * Un tentativo precedente metteva quei testi dentro CDATA per poterci scrivere gli apostrofi
 * senza proteggerli. NON funziona: il CDATA mette al riparo dal parser XML, ma non dalle
 * regole di escape di Android, che vengono applicate dopo, sul testo gia' estratto. La
 * compilazione falliva con "Invalid unicode escape sequence in string", che e' il modo in cui
 * aapt2 segnala un apostrofo non protetto.
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
