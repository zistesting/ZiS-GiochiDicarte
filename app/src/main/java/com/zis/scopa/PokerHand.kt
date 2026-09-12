package com.zis.scopa

/**
 * Valutazione di una mano di poker: dice quanto vale, e quindi chi vince.
 *
 * KOTLIN PURO, come gli altri quattro motori: non sa niente di Android, non tocca risorse e
 * non conosce le stringhe. Il nome della combinazione da mostrare a schermo lo mette la
 * schermata, partendo da [categoria]. Serve a due cose: girare in un test JVM senza
 * emulatore, e poter essere provato qui contro la forza bruta.
 *
 * IL PUNTEGGIO E' UN INT SOLO, e questa e' la scelta che regge tutto il resto. Confrontare
 * due mani diventa confrontare due interi con `>`, senza casi particolari e senza scrivere
 * un comparatore che qualcuno prima o poi sbaglia. Dentro l'intero stanno sei campi da
 * quattro bit:
 *
 *     categoria | primo | secondo | terzo | quarto | quinto
 *
 * La categoria e' una delle costanti qui sotto, dalla carta alta alla scala reale. I cinque
 * campi che seguono sono i ranghi che risolvono la parita', in ordine di importanza, e
 * l'ordine giusto dipende dalla categoria: in un full prima il tris e poi la coppia, in due
 * coppie prima la coppia alta, poi la bassa, poi la quinta carta. Il trucco che li mette a
 * posto tutti in una riga sola e' ordinare i ranghi distinti per QUANTI sono e, a pari
 * numero, per quanto valgono: un full da re e sette da' [13, 7], due coppie d'assi e otto
 * col re da' [14, 8, 13], una mano senza niente da' i cinque ranghi in ordine decrescente.
 * Ogni categoria viene fuori giusta senza un ramo per ciascuna.
 *
 * L'ASSO VALE 14, non 1, perche' nel poker e' la carta piu' alta: [Card] lo tiene a 1 come
 * nel mazzo francese, e [rango] fa la conversione. L'unica eccezione e' la scala minima,
 * A-2-3-4-5, dove l'asso conta come 1 e la scala vale cinque: e' il caso che quasi tutti i
 * valutatori sbagliano la prima volta, e qui e' scritto a parte.
 */
object PokerHand {

    // ---- le nove categorie, dalla piu' debole alla piu' forte ----
    const val CARTA_ALTA = 0
    const val COPPIA = 1
    const val DOPPIA_COPPIA = 2
    const val TRIS = 3
    const val SCALA = 4
    const val COLORE = 5
    const val FULL = 6
    const val POKER = 7
    const val SCALA_REALE = 8

    /** Il rango di poker di una carta: l'asso in alto, il resto come sta nel mazzo. */
    fun rango(valore: Int): Int = if (valore == 1) 14 else valore

    /**
     * Il punteggio di una mano di ESATTAMENTE cinque carte.
     *
     * Non controlla che siano cinque ne' che siano diverse: chiamarla con altro e' un errore
     * del chiamante, e un controllo qui costerebbe a ogni mano di ogni simulazione.
     */
    fun valuta(carte: List<Card>): Int {
        val quanti = IntArray(15)          // quante carte per rango, indici 2..14
        var semeUnico = true
        val primoSeme = carte[0].suit
        for (c in carte) {
            quanti[rango(c.value)]++
            if (c.suit != primoSeme) semeUnico = false
        }

        // I ranghi distinti, ordinati per quanti sono e poi per valore: e' questo che fa
        // venire giusti full, doppie coppie e kicker senza un caso per categoria.
        val ordinati = ArrayList<Int>(5)
        for (r in 14 downTo 2) if (quanti[r] > 0) ordinati.add(r)
        ordinati.sortWith(compareByDescending<Int> { quanti[it] }.thenByDescending { it })

        val scalaAlta = scalaAlta(quanti)   // 0 se non e' una scala

        val categoria = when {
            scalaAlta > 0 && semeUnico -> SCALA_REALE
            ordinati.any { quanti[it] == 4 } -> POKER
            ordinati.size == 2 && quanti[ordinati[0]] == 3 -> FULL
            semeUnico -> COLORE
            scalaAlta > 0 -> SCALA
            quanti[ordinati[0]] == 3 -> TRIS
            ordinati.size == 3 && quanti[ordinati[0]] == 2 && quanti[ordinati[1]] == 2 -> DOPPIA_COPPIA
            quanti[ordinati[0]] == 2 -> COPPIA
            else -> CARTA_ALTA
        }

        // Una scala si riconosce dalla sua carta piu' alta e da nient'altro: due scale al
        // re sono uguali, anche se le altre quattro carte sono diverse. Quindi i campi di
        // parita' vengono sostituiti dalla sola carta alta.
        val chiavi = if (categoria == SCALA || categoria == SCALA_REALE) listOf(scalaAlta) else ordinati

        var punteggio = categoria
        for (i in 0 until 5) {
            punteggio = (punteggio shl 4) or (chiavi.getOrElse(i) { 0 })
        }
        return punteggio
    }

    /**
     * La carta piu' alta della scala, o 0 se non c'e' scala.
     *
     * A-2-3-4-5 e' una scala e vale CINQUE, non quattordici: l'asso li' fa da uno. Non e' un
     * capriccio delle regole, e' quello che rende il poker un gioco chiuso ad anello solo a
     * un'estremita': A-K-Q-J-10 e' la scala massima, A-2-3-4-5 la minima, e K-A-2-3-4 non e'
     * una scala per niente.
     */
    private fun scalaAlta(quanti: IntArray): Int {
        var distinti = 0
        for (r in 2..14) if (quanti[r] > 1) return 0 else if (quanti[r] == 1) distinti++
        if (distinti != 5) return 0
        for (alta in 14 downTo 6) {
            if (quanti[alta] == 1 && quanti[alta - 1] == 1 && quanti[alta - 2] == 1 &&
                quanti[alta - 3] == 1 && quanti[alta - 4] == 1) return alta
        }
        // la scala minima, con l'asso in fondo
        if (quanti[14] == 1 && quanti[2] == 1 && quanti[3] == 1 && quanti[4] == 1 && quanti[5] == 1) return 5
        return 0
    }

    /**
     * Il meglio di cinque fra CINQUE O PIU' carte: cinque per il 5-Card Draw, sette per il
     * Texas Hold'em (le due in mano piu' le cinque comuni).
     *
     * Prova tutte le combinazioni e tiene la migliore. Con sette carte sono ventuno prove,
     * e a ventuno prove non conviene essere furbi: un valutatore diretto a sette carte e'
     * piu' rapido ma molto piu' facile da sbagliare, e questo qui gira comunque milioni di
     * volte al secondo. Se un giorno il calcolo delle probabilita' diventasse lento, e'
     * questo il posto da guardare - non prima di averlo misurato.
     */
    fun meglio(carte: List<Card>): Int {
        if (carte.size == 5) return valuta(carte)
        var massimo = 0
        val cinque = ArrayList<Card>(5)
        val n = carte.size
        for (a in 0 until n - 4) for (b in a + 1 until n - 3) for (c in b + 1 until n - 2)
            for (d in c + 1 until n - 1) for (e in d + 1 until n) {
                cinque.clear()
                cinque.add(carte[a]); cinque.add(carte[b]); cinque.add(carte[c])
                cinque.add(carte[d]); cinque.add(carte[e])
                val p = valuta(cinque)
                if (p > massimo) massimo = p
            }
        return massimo
    }

    /** La categoria di un punteggio, per scrivere a schermo come si chiama la combinazione. */
    fun categoria(punteggio: Int): Int = punteggio shr 20

    /**
     * I ranghi che decidono la parita', dal piu' importante: serve alle statistiche per
     * dire "coppia di re" e non solo "coppia". Sono al massimo cinque, gli zeri in coda
     * vanno ignorati.
     */
    fun chiavi(punteggio: Int): List<Int> =
        (4 downTo 0).map { (punteggio shr (it * 4)) and 0xF }
}
