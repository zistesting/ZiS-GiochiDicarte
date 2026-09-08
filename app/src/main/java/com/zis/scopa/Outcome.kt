package com.zis.scopa

import android.widget.TextView

/**
 * Colore delle righe di esito nei riepiloghi: **oro quando sei in vantaggio tu**, celeste
 * negli altri casi.
 *
 * Sta qui e non dentro le tre activity perche' la regola deve restare identica in tutti e
 * tre i giochi: e' quella che fa capire come sta andando a colpo d'occhio, prima ancora di
 * leggere i numeri. Basta che due schermate la applichino in modo diverso e l'indizio smette
 * di funzionare.
 */
fun TextView.tintByOutcome(youAhead: Boolean) {
    setTextColor(context.getColor(if (youAhead) R.color.gold else R.color.celeste))
}

/**
 * Come sopra, ma con il pareggio distinto dallo svantaggio.
 *
 * Serve alla riga del punteggio mostrata durante l'incontro. Con la versione a due colori un
 * incontro in parita' si tingeva di celeste, cioe' esattamente come stare sotto: chi guardava
 * di sfuggita leggeva un vantaggio del Banco che non c'era. In parita' il testo resta neutro
 * e non dice niente, che e' la cosa giusta da dire.
 */
fun TextView.tintByScore(you: Int, bot: Int) {
    setTextColor(context.getColor(when {
        you > bot -> R.color.gold
        bot > you -> R.color.celeste
        else -> R.color.silver
    }))
}
