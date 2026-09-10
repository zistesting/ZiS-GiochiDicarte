package com.zis.scopa

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge to edge esplicito, uguale su tutte le versioni di Android.
 *
 * Da targetSdk 35 il sistema disegna comunque sotto le barre e non si puo' piu' rinunciare;
 * sotto la 35, invece, la decor view applica da sola gli inset al contenuto e li consuma,
 * quindi il listener qui sotto riceveva zero. Il risultato era corretto per caso, ma
 * l'aspetto cambiava fra un telefono vecchio e uno nuovo e dipendeva da un comportamento
 * implicito della piattaforma, che e' gia' cambiato una volta.
 *
 * Con setDecorFitsSystemWindows(false) chiediamo esplicitamente noi lo schermo intero, su
 * qualunque versione: lo sfondo arriva sempre fino ai bordi e il contenuto riceve sempre il
 * margine giusto da questo listener.
 */
fun AppCompatActivity.applySystemBars(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // sfondo scuro: le icone delle barre di sistema devono restare chiare.
    // Il controller si chiede a WindowCompat e non si costruisce a mano: il costruttore
    // WindowInsetsControllerCompat(Window, View) e' deprecato da androidx.core 1.13.
    WindowCompat.getInsetsController(window, root).apply {
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
    // Se la vista e' gia' agganciata (ricreazione dell'activity) il sistema potrebbe non
    // rimandare gli inset da solo: li chiediamo noi.
    ViewCompat.requestApplyInsets(root)
}
