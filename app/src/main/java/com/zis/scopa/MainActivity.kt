package com.zis.scopa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.version.text = "v." + appVersion()
        b.btnScopa.setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        b.btnBriscola.setOnClickListener { startActivity(Intent(this, BriscolaActivity::class.java)) }
        b.btnQuit.setOnClickListener { finishAffinity() }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnZis.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zis.it")))
            } catch (_: Exception) { }
        }
    }

    private fun appVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1" } catch (e: Exception) { "1.1" }
}
