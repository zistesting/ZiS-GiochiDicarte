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
