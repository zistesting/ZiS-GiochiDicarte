package com.zis.scopa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    /** Finestra delle informazioni: va chiusa in onDestroy, come tutte le altre. */
    private var infoDialog: AlertDialog? = null

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
            infoDialog = InfoDialog.show(this, R.string.info_app_title, R.string.info_app)
        }
        b.btnZis.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zis.it")))
            } catch (_: Exception) { }
        }
    }

    /** Dopo un azzeramento la tabella si riapre, cosi' si vede subito che e' vuota. */
    private fun showStats() {
        infoDialog?.let { if (it.isShowing) it.dismiss() }
        infoDialog = StatsDialog.show(this, onReset = { showStats() })
    }

    override fun onDestroy() {
        infoDialog?.let { if (it.isShowing) it.dismiss() }
        infoDialog = null
        super.onDestroy()
    }

    private fun appVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
}
