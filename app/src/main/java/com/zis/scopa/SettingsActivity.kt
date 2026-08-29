package com.zis.scopa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zis.scopa.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Prefs.scoreTarget(this) == 21) b.radio21.isChecked = true else b.radio11.isChecked = true

        b.groupTarget.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setScoreTarget(this, if (checkedId == R.id.radio21) 21 else 11)
        }

        if (Prefs.briscolaTarget(this) == 11) b.radioB11.isChecked = true else b.radioB5.isChecked = true

        b.groupBriscola.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setBriscolaTarget(this, if (checkedId == R.id.radioB11) 11 else 5)
        }

        b.btnBack.setOnClickListener { finish() }
    }
}
