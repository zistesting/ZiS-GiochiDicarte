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

    /**
     * @param onOpened viene richiamato per OGNI finestra aperta da qui, compresa la conferma
     *   dell'azzeramento, che e' annidata dentro la prima. Prima quella conferma non veniva
     *   consegnata a nessuno: se il sistema distruggeva l'activity mentre era aperta restava
     *   appesa al suo contesto e il log segnava WindowLeaked. Ora chi apre la tabella la
     *   registra come registra tutte le altre.
     */
    fun show(
        activity: AppCompatActivity,
        onReset: () -> Unit,
        onOpened: (AlertDialog) -> Unit = {}
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null

        val v = DialogStatsBinding.inflate(activity.layoutInflater)
        var totYou = 0
        var totBot = 0

        // (celle del gioco) -> chiave con cui e' salvato
        //
        // Le colonne si chiamano VINTE e PERSE, non piu' "Tu" e "Banco". Il motivo e' il
        // Klondike: li' non c'e' nessun Banco che vince quando perdi tu, e una colonna
        // intitolata a un avversario che non esiste non vorrebbe dire niente. Vinte e perse
        // funzionano per tutti e quattro, e nei tre giochi contro il Banco significano
        // esattamente quello che significavano prima.
        //
        // I nomi interni delle celle sono rimasti quelli vecchi (You per le vinte, Bot per
        // le perse): rinominare quattordici id in tre file per una parola sull'etichetta
        // avrebbe fatto piu' danni che bene.
        val righe = listOf(
            Triple(v.scopaPlayed, v.scopaBot, v.scopaYou) to Prefs.GAME_SCOPA,
            Triple(v.briscPlayed, v.briscBot, v.briscYou) to Prefs.GAME_BRISCOLA,
            Triple(v.trePlayed, v.treBot, v.treYou) to Prefs.GAME_TRESETTE,
            Triple(v.klonPlayed, v.klonBot, v.klonYou) to Prefs.GAME_KLONDIKE
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
                confirmReset(activity, onReset, onOpened)
            }
        }
        val dialog = builder.create()
        dialog.show()
        onOpened(dialog)
        return dialog
    }

    /** Conferma dell'azzeramento, anch'essa consegnata a chi la deve chiudere in onDestroy. */
    private fun confirmReset(
        activity: AppCompatActivity,
        onReset: () -> Unit,
        onOpened: (AlertDialog) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val confirm = AlertDialog.Builder(activity)
            .setTitle(R.string.stats_reset)
            .setMessage(R.string.stats_reset_ask)
            // La domanda e' "Vuoi azzerare tutte le statistiche?": la risposta e' si' o no.
            // Prima i pulsanti dicevano "Azzera" e "Annulla", cioe' un'azione e un ripensamento
            // invece delle due risposte alla domanda appena letta.
            .setPositiveButton(R.string.yes) { _, _ ->
                Prefs.clearStats(activity)
                onReset()
            }
            .setNegativeButton(R.string.no, null)
            .create()
        confirm.show()
        onOpened(confirm)
    }
}
