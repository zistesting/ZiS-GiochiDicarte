package com.zis.scopa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityMainBinding
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    /**
     * Tutte le finestre aperte da questa schermata, da chiudere in onDestroy.
     *
     * Prima era un campo solo, riusato sia per la finestra delle informazioni sia per quella
     * delle statistiche: chi apriva la seconda sovrascriveva il riferimento alla prima senza
     * chiuderla. In pratica non era raggiungibile, perche' i dialoghi sono modali, ma la
     * conferma dell'azzeramento e' annidata dentro la tabella, quindi due finestre aperte
     * insieme esistono davvero. Con una lista il caso e' coperto per costruzione e la regola
     * diventa una sola: tutto quello che si apre finisce qui dentro.
     */
    private val openDialogs = mutableListOf<AlertDialog>()

    private fun track(d: AlertDialog?) {
        if (d != null) openDialogs.add(d)
    }

    /** Chiude e dimentica tutto quello che e' rimasto aperto. */
    private fun closeDialogs() {
        for (d in openDialogs) if (d.isShowing) d.dismiss()
        openDialogs.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySystemBars(b.root)

        b.version.text = "v." + appVersion()
        b.btnScopa.setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        b.btnBriscola.setOnClickListener { startActivity(Intent(this, BriscolaActivity::class.java)) }
        b.btnTresette.setOnClickListener { startActivity(Intent(this, TresetteActivity::class.java)) }
        b.btnQuit.setOnClickListener { quitApp() }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnStats.setOnClickListener { showStats() }
        b.btnInfo.setOnClickListener {
            closeDialogs()
            track(InfoDialog.show(this, R.string.info_app_title, R.string.info_app))
        }
        b.btnZis.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zis.it")))
            } catch (_: Exception) { }
        }
    }

    /**
     * Pulsante Esci: chiude l'app e la toglie di mezzo davvero.
     *
     * Tre passi, in quest'ordine, e l'ordine conta.
     *
     * 1. Le preferenze si scrivono su disco in modo asincrono. Nel funzionamento normale
     *    Android completa quelle scritture quando l'ultima activity si ferma, ma chiudendo
     *    il processo a mano non le aspetta piu' nessuno: senza il flush si perderebbero
     *    l'ultima impostazione toccata, l'ultima statistica e la partita salvata.
     *
     * 2. finishAndRemoveTask chiude tutte le activity dell'app **e** toglie la scheda dalle
     *    app recenti. Il vecchio finishAffinity chiudeva solo le activity: la scheda restava
     *    li', e riaprendola l'app ripartiva.
     *
     * 3. exitProcess termina il processo.
     *
     * Sul terzo passo va detta una cosa. Android non ne ha bisogno: dopo il passo 2 il
     * processo resta in giro "vuoto", non consuma nulla di utile ed e' il primo che il
     * sistema butta via quando serve memoria. Ucciderlo a mano e' sconsigliato in generale,
     * perche' un'app con servizi o lavori in sospeso puo' restarci male. Questa non ne ha
     * nessuno: nessun servizio, nessun permesso, nessuna rete, e dopo il passo 1 non ci sono
     * scritture in sospeso. Quindi qui e' sicuro, e da' quello che serve a te: l'app sparisce
     * dall'elenco dei processi invece di restare in cache.
     *
     * Se un domani l'app dovesse acquisire un servizio o un WorkManager, questa riga va tolta
     * per prima.
     */
    private fun quitApp() {
        closeDialogs()
        Prefs.flush(this)
        SavedGame.flush(this)
        finishAndRemoveTask()
        exitProcess(0)
    }

    /** Dopo un azzeramento la tabella si riapre, cosi' si vede subito che e' vuota. */
    private fun showStats() {
        closeDialogs()
        StatsDialog.show(this, onReset = { showStats() }, onOpened = { track(it) })
    }

    override fun onDestroy() {
        closeDialogs()
        super.onDestroy()
    }

    private fun appVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
}
