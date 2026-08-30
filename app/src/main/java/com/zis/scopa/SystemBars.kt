package com.zis.scopa

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/**
 * Da Android 15 in poi, e in modo definitivo con targetSdk 36, le schermate disegnano sempre
 * a tutto schermo: passano sotto la barra di stato e sotto quella di navigazione, e non c'e'
 * piu' modo di rinunciarci. Qui lo sfondo continua ad arrivare fino ai bordi, ma il contenuto
 * riceve il margine giusto per non finire sotto le barre di sistema o sotto il notch.
 *
 * Sostituisce android:fitsSystemWindows="true" nei layout, che copriva solo i casi semplici.
 */
fun AppCompatActivity.applySystemBars(root: View) {
    // sfondo scuro: le icone delle barre di sistema devono restare chiare
    WindowInsetsControllerCompat(window, root).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
        insets
    }
}
