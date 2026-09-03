package com.zis.scopa

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.DialogStatsBinding

/**
 * Tabella delle statistiche: per ciascun gioco le partite concluse, quelle vinte dal Banco e
 * quelle vinte dall'utente, piu' la riga dei totali.
 *
 * Le colonne le tiene una TableLayout con gli stessi stili del riepilogo di fine mano della
 * Scopa, cosi' le due tabelle dell'app si somigliano invece di essere ognuna a modo suo.
 */
object StatsDialog {

    fun show(activity: AppCompatActivity, onReset: () -> Unit): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null

        val v = DialogStatsBinding.inflate(activity.layoutInflater)
        var totYou = 0
        var totBot = 0

        // (celle del gioco) -> chiave con cui e' salvato
        val righe = listOf(
            Triple(v.scopaPlayed, v.scopaBot, v.scopaYou) to Prefs.GAME_SCOPA,
            Triple(v.briscPlayed, v.briscBot, v.briscYou) to Prefs.GAME_BRISCOLA,
            Triple(v.trePlayed, v.treBot, v.treYou) to Prefs.GAME_TRESETTE
        )
        for ((celle, gioco) in righe) {
            val you = Prefs.wonBy(activity, gioco, true)
            val bot = Prefs.wonBy(activity, gioco, false)
            val (played, cellBot, cellYou) = celle
            played.text = (you + bot).toString()
            cellBot.text = bot.toString()
            cellYou.text = you.toString()
            totYou += you
            totBot += bot
        }
        v.totPlayed.text = (totYou + totBot).toString()
        v.totBot.text = totBot.toString()
        v.totYou.text = totYou.toString()

        // A tabella tutta a zero la nota sui colori non serve: meglio spiegare perche' e' vuota.
        v.statsNote.setText(
            if (totYou + totBot == 0) R.string.stats_empty else R.string.stats_note
        )

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.stats_title)
            .setView(v.root)
            .setPositiveButton(R.string.close, null)
        if (totYou + totBot > 0) {
            // L'azzeramento chiede conferma: e' l'unica azione dell'app che distrugge dati,
            // e sta accanto al pulsante che si preme per uscire dalla finestra.
            builder.setNegativeButton(R.string.stats_reset) { _, _ ->
                AlertDialog.Builder(activity)
                    .setTitle(R.string.stats_reset)
                    .setMessage(R.string.stats_reset_ask)
                    .setPositiveButton(R.string.stats_reset) { _, _ ->
                        Prefs.clearStats(activity)
                        onReset()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        val dialog = builder.create()
        dialog.show()
        return dialog
    }
}
