package com.zis.scopa

import kotlin.random.Random

/**
 * Il Banco del Texas Hold'em.
 *
 * NON E' IL BANCO DEL DRAW ADATTATO, ed e' il pezzo che piu' di tutti doveva essere riscritto.
 * Nel 5-Card Draw il Banco legge l'avversario da QUANTE CARTE HA CAMBIATO: chi non cambia
 * niente e' servito, chi ne cambia tre ha una coppia. Nel Hold'em quel segnale non esiste -
 * nessuno cambia niente - e al suo posto ci sono le puntate: chi rilancia sul giro delle
 * carte coperte dice una cosa, chi rilancia sul quinto e' un'altra.
 *
 * QUESTA VERSIONE NON LEGGE ANCORA NIENTE, e lo dico invece di farlo credere. Decide su due
 * numeri e li calcola bene:
 *
 *   L'EQUITA': la probabilita' di avere la mano migliore alla fine. Si stima simulando -
 *   agli avversari si danno due carte a caso fra quelle che il Banco non vede, le comuni che
 *   mancano si completano a caso, e si conta quante volte vince. E' la stessa idea di
 *   [PokerOdds.equita] ma senza modello dell'avversario: qui gli avversari sono "due carte
 *   qualunque", che e' ottimista quando in mano restano in tre e pessimista quando l'unico
 *   rimasto ha rilanciato due volte.
 *
 *   LE QUOTE DEL PIATTO: quanto costa stare diviso quanto c'e' da vincere. Se l'equita' e'
 *   sopra la quota, stare conviene; sotto, no. E' l'aritmetica che fa la differenza fra
 *   giocare e tirare a indovinare, ed e' anche l'unica cosa che un giocatore alle prime armi
 *   dovrebbe imparare prima di tutto il resto.
 *
 * COSA MANCA, per chi legge dopo: la lettura delle puntate (chi ha rilanciato e dove), la
 * posizione (parlare per ultimi vale piu' che parlare per primi), e il bluff. Il Banco di
 * adesso e' onesto e prevedibile: non bluffa mai e non si fa mai bluffare, quindi contro di
 * lui conviene puntare solo con le mani buone - che e' il difetto di un avversario da
 * esercizio, non di un avversario da battere.
 */
object PokerBotHoldem {

    /** Quante simulazioni per decisione. Alto = piu' preciso e piu' lento. */
    var campioni = 300

    fun azione(g: HoldemGame, p: Int, rnd: Random = Random.Default): PokerGame.Azione {
        val azioni = g.azioniLegali(p)
        if (azioni.isEmpty()) return PokerGame.Azione.PARIFICA

        val avversari = (0 until g.giocatori).count { it != p && g.inMano(it) }
        val e = equita(g, p, avversari, rnd)
        val costo = g.daPareggiare(p)
        val piatto = g.piatto

        // la quota: quanto peserebbe la mia puntata nel piatto che ne viene. Se l'equita' la
        // supera, stare e' in guadagno; e' la stessa soglia che il Draw usa per il pareggio.
        val quota = if (costo <= 0) 0.0 else costo.toDouble() / (piatto + costo)

        val rilanciabile = PokerGame.Azione.RILANCIA in azioni
        // Il rilancio vuole un margine, non il pareggio: una mano che sta appena sopra la
        // quota conviene vederla, non farsi rilanciare sopra. La soglia scende col numero
        // degli avversari perche' con tre avversari il 40% e' molto piu' di quanto serva.
        val sogliaRilancio = 0.62 / maxOf(1, avversari) + 0.30

        return when {
            rilanciabile && e >= sogliaRilancio -> PokerGame.Azione.RILANCIA
            costo == 0 -> PokerGame.Azione.PARIFICA          // gratis: si sta sempre
            e >= quota -> PokerGame.Azione.PARIFICA
            PokerGame.Azione.PASSA in azioni -> PokerGame.Azione.PASSA
            else -> PokerGame.Azione.PARIFICA
        }
    }

    /**
     * L'equita' di [p]: quante volte, su [campioni] mani finte, ha il punteggio piu' alto.
     *
     * I pareggi contano per la loro parte - meta' con un avversario, un terzo con due -
     * perche' un piatto diviso non e' un piatto perso: contarlo zero renderebbe il Banco
     * timido esattamente sulle mani che non si possono perdere.
     */
    fun equita(g: HoldemGame, p: Int, avversari: Int, rnd: Random = Random.Default,
               quanti: Int = campioni): Double {
        if (avversari <= 0) return 1.0
        val mia = g.mani[p]
        if (mia.size < 2) return 0.0

        val viste = HashSet<Int>()
        for (c in mia) viste.add(c.suit * 13 + c.value)
        for (c in g.comuni) viste.add(c.suit * 13 + c.value)
        val ignote = ArrayList<Card>(52)
        for (s in 0..3) for (v in g.valori)
            if (s * 13 + v !in viste) ignote.add(Card(s, v))

        val servono = 2 * avversari + (5 - g.comuni.size)
        if (ignote.size < servono) return 1.0

        var punti = 0.0
        val tavolo = ArrayList<Card>(5)
        val mano = ArrayList<Card>(7)
        repeat(quanti) {
            // mescolo solo le carte che servono: uno scambio per carta, non tutto il mazzo
            for (i in 0 until servono) {
                val j = i + rnd.nextInt(ignote.size - i)
                val t = ignote[i]; ignote[i] = ignote[j]; ignote[j] = t
            }
            var k = 0
            tavolo.clear(); tavolo.addAll(g.comuni)
            while (tavolo.size < 5) tavolo.add(ignote[k++])

            mano.clear(); mano.addAll(mia); mano.addAll(tavolo)
            val mio = PokerHand.migliore(mano)
            var meglioAltrui = 0
            repeat(avversari) {
                mano.clear()
                mano.add(ignote[k++]); mano.add(ignote[k++]); mano.addAll(tavolo)
                val suo = PokerHand.migliore(mano)
                if (suo > meglioAltrui) meglioAltrui = suo
            }
            punti += when {
                mio > meglioAltrui -> 1.0
                mio == meglioAltrui -> 1.0 / (1 + avversari)
                else -> 0.0
            }
        }
        return punti / quanti
    }
}
