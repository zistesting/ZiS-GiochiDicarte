package com.zis.scopa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityMainBinding

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
        b.btnQuit.setOnClickListener { finishAffinity() }
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
