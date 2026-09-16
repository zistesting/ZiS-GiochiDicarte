package com.zis.scopa

/**
 * Il Texas Hold'em a limite fisso.
 *
 * DUE CARTE IN MANO, CINQUE IN TAVOLA, QUATTRO GIRI DI PUNTATE. E' un gioco diverso dal
 * 5-Card Draw, non una variante: cambiano le carte, i giri, il modo di leggere l'avversario.
 * Quello che NON cambia, e che percio' e' riusato intatto, e' la macchina delle puntate -
 * chi parla, chi deve pareggiare, quanti rilanci sono ammessi, gli all-in - e due oggetti
 * che di poker sanno tutto e di variante niente: [PokerHand] per il punteggio di cinque
 * carte e [PokerPot] per la divisione del piatto e dei piatti laterali.
 *
 * COS'E' STATO SCRITTO DA CAPO, e perche':
 *
 *  - LE POSTE CIECHE. Nel Draw la posta la mette chiunque partecipi; qui la mettono solo i
 *    due alla sinistra del mazziere, piccolo e grande buio, e il mazziere gira a ogni mano
 *    quindi i bui girano con lui. Non e' una regola in piu' per fare i pignoli: e' la regola
 *    che rende il gioco quello che e' - c'e' sempre qualcosa da vincere, e chi paga il buio
 *    ha un motivo per difendersi che non dipende dalle sue carte.
 *    IN DUE il mazziere paga il buio PICCOLO, che sembra un dettaglio arbitrario e non lo
 *    e': in due il giocatore alla sinistra del mazziere e' anche quello alla sua destra,
 *    quindi la regola "i due alla sinistra" non decide nulla, e la convenzione del poker vero
 *    e' questa. Ne viene che in due il mazziere parla PRIMA nel giro delle carte coperte e
 *    DOPO in tutti gli altri, ed e' il motivo per cui [primoDiTurno] guarda la fase.
 *
 *  - IL GRANDE BUIO HA LA PAROLA. Chi ha pagato il grande buio ha gia' messo l'importo
 *    giusto, quindi se tutti si limitano a pareggiare non gli resta niente da fare - ma deve
 *    poter rilanciare comunque. Qui viene gratis: un giro si chiude quando tutti hanno AGITO
 *    e sono pari, e il buio l'ha pagato senza agire.
 *
 *  - IL PUNTEGGIO SU SETTE CARTE. La mano migliore sono cinque carte scelte fra le due in
 *    mano e le cinque in tavola: ventuno combinazioni, e si prende la piu' alta. Vedi
 *    [PokerHand.migliore].
 *
 * LIMITE FISSO: si punta [rilancio] nei primi due giri e il doppio negli ultimi due, non piu'
 * di [RILANCI_MAX] rilanci per giro. E' il Hold'em con cui si impara, ed e' anche l'unico in
 * cui il Banco puo' decidere SE stare senza dover decidere anche QUANTO puntare.
 */
class HoldemGame(
    val giocatori: Int,
    val fichesIniziali: Int = PokerGame.FICHES_INIZIALI,
    /** Il grande buio. Il piccolo e' la meta'. */
    val buio: Int = PokerGame.APERTURA,
    /** Quanto vale una puntata nei primi due giri; negli ultimi due vale il doppio. */
    val rilancio: Int = PokerGame.RILANCIO,
    /** Il mazzo corto dal 6 all'asso: vedi [PokerGame.mazzoCorto]. */
    val mazzoCorto: Boolean = false
) {

    companion object {
        const val PREFLOP = 0
        const val FLOP = 1
        const val TURN = 2
        const val RIVER = 3
        const val SHOWDOWN = 4

        const val RILANCI_MAX = 3

        /**
         * La firma in testa al salvataggio, e serve per una ragione trovata dal simulatore:
         * i parametri del Hold'em e quelli del 5-Card Draw sono gli STESSI CINQUE NUMERI -
         * giocatori, fiches, posta, rilancio, mazzo corto - quindi un salvataggio di Draw
         * passava il controllo del Hold'em e veniva riletto come se fosse suo. Le carte
         * sarebbero finite dove non devono (cinque in mano, nessuna in tavola) e il gioco si
         * sarebbe rotto a mano aperta, non al caricamento, che e' il modo peggiore.
         *
         * Basta che UNO dei due si firmi perche' nessuno dei due possa leggere l'altro: gli
         * elenchi diventano di lunghezza diversa. Si firma il nuovo, cosi' i salvataggi di
         * Draw in corso non si buttano.
         */
        const val FIRMA = 2

        /** Quante carte comuni sono in tavola in ciascuna fase. */
        fun comuniNellaFase(fase: Int): Int = when (fase) {
            PREFLOP -> 0
            FLOP -> 3
            TURN -> 4
            else -> 5
        }
    }

    val valori: List<Int> = if (mazzoCorto) PokerGame.VALORI_CORTI else PokerGame.VALORI_PIENI

    val fiches = IntArray(giocatori) { fichesIniziali }
    val puntato = IntArray(giocatori)      // quanto ha messo nel piatto in tutta la mano
    val impegno = IntArray(giocatori)      // quanto ha messo in QUESTO giro
    val incasso = IntArray(giocatori)
    val fuori = BooleanArray(giocatori)    // ha passato
    val eliminato = BooleanArray(giocatori)
    val haAgito = BooleanArray(giocatori)
    val haPuntato = BooleanArray(giocatori) // ha puntato o rilanciato in questa mano

    val mani = Array(giocatori) { ArrayList<Card>() }
    val comuni = ArrayList<Card>()
    val deck = ArrayList<Card>()

    var mazziere = 0
    var turno = 0
    var fase = PREFLOP
    var livello = 0                        // quanto bisogna avere nel giro per stare
    var rilanci = 0
    var manoFinita = false
    var partitaFinita = false
    var carteMostrate = false
    var piatti: List<PokerPot.Piatto> = emptyList()

    val piatto: Int get() = puntato.sum()

    fun inMano(p: Int) = !fuori[p] && !eliminato[p]
    fun puoAgire(p: Int) = inMano(p) && fiches[p] > 0
    fun quantiInMano() = (0 until giocatori).count { inMano(it) }
    fun daPareggiare(p: Int) = minOf(livello - impegno[p], fiches[p]).coerceAtLeast(0)

    /** Quanto vale una puntata in questa fase: limite fisso, doppio negli ultimi due giri. */
    fun puntataCorrente(): Int = if (fase >= TURN) 2 * rilancio else rilancio

    /** I cinque numeri che devono combaciare perche' un salvataggio sia di questa partita. */
    fun parametri(): List<Int> =
        listOf(FIRMA, giocatori, fichesIniziali, buio, rilancio, if (mazzoCorto) 1 else 0)

    // ------------------------------------------------------------------ la mano

    fun nuovaMano() {
        for (p in 0 until giocatori) {
            puntato[p] = 0; impegno[p] = 0; incasso[p] = 0
            fuori[p] = eliminato[p]; haAgito[p] = false; haPuntato[p] = false
            mani[p].clear()
        }
        comuni.clear()
        piatti = emptyList()
        manoFinita = false
        carteMostrate = false
        fase = PREFLOP
        rilanci = 0

        deck.clear()
        for (s in 0..3) for (v in valori) deck.add(Card(s, v))
        deck.shuffle()

        // i due bui, e solo loro
        val piccolo = postoDelBuioPiccolo()
        val grande = prossimoInMano(piccolo)
        versa(piccolo, buio / 2)
        versa(grande, buio)
        livello = impegno.max()
        // chi ha pagato il buio NON ha agito: se tutti pareggiano, il grande buio deve
        // poter rilanciare

        // due carte a testa, una per giro come si distribuisce davvero
        repeat(2) {
            for (p in ordineDa(prossimoInMano(mazziere))) if (inMano(p)) mani[p].add(pesca())
        }

        turno = primoDiTurno()
        if (!chiudiSeSoloUno() && giroChiuso()) chiudiGiro()
    }

    /**
     * Chi paga il buio piccolo: il primo in mano alla sinistra del mazziere, e IN DUE il
     * mazziere stesso. Vedi il commento della classe.
     */
    private fun postoDelBuioPiccolo(): Int =
        if (quantiVivi() <= 2) primoInMano(mazziere) else prossimoInMano(mazziere)

    /**
     * Chi parla per primo. Nel giro delle carte coperte si parla dopo i bui, quindi il primo
     * e' quello dopo il grande buio; negli altri tre giri si riparte dalla sinistra del
     * mazziere. In due i due casi si scambiano, ed e' la conseguenza del buio piccolo al
     * mazziere.
     */
    private fun primoDiTurno(): Int {
        val da = if (fase == PREFLOP) prossimoInMano(prossimoInMano(postoDelBuioPiccolo()))
                 else prossimoInMano(mazziere)
        for (p in ordineDa(da)) if (puoAgire(p)) return p
        return primoInMano(da)
    }

    private fun pesca(): Card {
        if (deck.isEmpty()) throw IllegalStateException("mazzo finito")
        return deck.removeAt(deck.size - 1)
    }

    private fun versa(p: Int, quanto: Int) {
        val q = minOf(quanto, fiches[p])
        fiches[p] -= q
        puntato[p] += q
        impegno[p] += q
    }

    // ------------------------------------------------------------------ le mosse

    fun azioniLegali(p: Int): List<PokerGame.Azione> {
        if (manoFinita || partitaFinita || fase == SHOWDOWN) return emptyList()
        if (p != turno || !puoAgire(p)) return emptyList()
        val a = ArrayList<PokerGame.Azione>(3)
        if (daPareggiare(p) > 0) a.add(PokerGame.Azione.PASSA)
        a.add(PokerGame.Azione.PARIFICA)
        if (rilanci < RILANCI_MAX && fiches[p] > daPareggiare(p)) a.add(PokerGame.Azione.RILANCIA)
        return a
    }

    fun agisci(p: Int, azione: PokerGame.Azione) {
        require(azione in azioniLegali(p)) { "mossa non ammessa: $azione per $p" }
        when (azione) {
            PokerGame.Azione.PASSA -> fuori[p] = true
            PokerGame.Azione.PARIFICA -> versa(p, daPareggiare(p))
            PokerGame.Azione.RILANCIA -> {
                versa(p, daPareggiare(p) + puntataCorrente())
                livello = maxOf(livello, impegno[p])
                rilanci++
                haPuntato[p] = true
                // un rilancio riapre il giro a tutti gli altri
                for (q in 0 until giocatori) if (q != p) haAgito[q] = false
            }
        }
        haAgito[p] = true
        avanza()
    }

    private fun avanza() {
        if (chiudiSeSoloUno()) return
        if (giroChiuso()) { chiudiGiro(); return }
        var q = turno
        do { q = (q + 1) % giocatori } while (!puoAgire(q))
        turno = q
    }

    private fun giroChiuso(): Boolean {
        val agenti = (0 until giocatori).filter { puoAgire(it) }
        if (agenti.isEmpty()) return true
        return agenti.all { haAgito[it] && impegno[it] == livello }
    }

    /** Se resta uno solo in mano, la mano e' sua e le carte non si mostrano. */
    private fun chiudiSeSoloUno(): Boolean {
        if (quantiInMano() > 1) return false
        showdown(mostra = false)
        return true
    }

    /** Chiude il giro: scopre le carte che tocca scoprire, o va allo showdown. */
    private fun chiudiGiro() {
        if (fase >= RIVER) { showdown(mostra = true); return }
        fase++
        while (comuni.size < comuniNellaFase(fase)) comuni.add(pesca())
        for (p in 0 until giocatori) { impegno[p] = 0; haAgito[p] = false }
        livello = 0
        rilanci = 0
        turno = primoDiTurno()
        if (giroChiuso()) chiudiGiro()      // tutti all-in: si scoprono le comuni di seguito
    }

    // ------------------------------------------------------------------ la fine

    private fun showdown(mostra: Boolean) {
        fase = SHOWDOWN
        // con tutti all-in o con la mano vinta per abbandono le comuni possono mancare: il
        // punteggio si fa su quello che c'e', e se manca si scoprono adesso
        if (mostra) while (comuni.size < 5) comuni.add(pesca())

        val punteggio = IntArray(giocatori)
        for (p in 0 until giocatori) {
            punteggio[p] = if (inMano(p) && comuni.size == 5)
                PokerHand.migliore(mani[p] + comuni) else 0
        }
        piatti = PokerPot.costruisci(puntato, fuori)
        val inc = PokerPot.assegna(piatti, punteggio, prossimoInMano(mazziere), fuori)
        for (p in 0 until giocatori) {
            incasso[p] = inc[p]
            fiches[p] += inc[p]
        }
        carteMostrate = mostra && quantiInMano() > 1
        manoFinita = true
        for (p in 0 until giocatori) if (fiches[p] <= 0) eliminato[p] = true
        partitaFinita = quantiVivi() <= 1
        if (!partitaFinita) {
            var m = mazziere
            do { m = (m + 1) % giocatori } while (eliminato[m])
            mazziere = m
        }
    }

    fun vincitore(): Int = (0 until giocatori).firstOrNull { !eliminato[it] } ?: -1

    // ------------------------------------------------------------------ giri del tavolo

    private fun quantiVivi() = (0 until giocatori).count { !eliminato[it] }
    private fun ordineDa(da: Int) = (0 until giocatori).map { (da + it) % giocatori }
    private fun primoInMano(da: Int): Int = ordineDa(da).firstOrNull { inMano(it) } ?: da
    private fun prossimoInMano(da: Int): Int =
        ordineDa(da + 1).firstOrNull { inMano(it) } ?: primoInMano(da)

    // ------------------------------------------------------------------ salvataggio

    fun save(w: SavedGame.Writer) {
        w.ints(parametri())
        w.ints(fiches.toList())
        w.ints(puntato.toList())
        w.ints(impegno.toList())
        w.ints(incasso.toList())
        w.ints(fuori.map { if (it) 1 else 0 })
        w.ints(eliminato.map { if (it) 1 else 0 })
        w.ints(haAgito.map { if (it) 1 else 0 })
        w.ints(haPuntato.map { if (it) 1 else 0 })
        w.cards(deck)
        w.cards(comuni)
        for (p in 0 until giocatori) w.cards(mani[p])
        w.ints(listOf(mazziere, turno, fase, livello, rilanci))
        w.ints(listOf(if (manoFinita) 1 else 0, if (partitaFinita) 1 else 0,
                      if (carteMostrate) 1 else 0))
    }

    fun load(r: SavedGame.Reader) {
        require(r.ints() == parametri()) { "il salvataggio e' di un'altra partita" }
        fun dentro(v: List<Int>, a: IntArray) { for (i in a.indices) a[i] = v[i] }
        fun dentroB(v: List<Int>, a: BooleanArray) { for (i in a.indices) a[i] = v[i] == 1 }
        dentro(r.ints(), fiches)
        dentro(r.ints(), puntato)
        dentro(r.ints(), impegno)
        dentro(r.ints(), incasso)
        dentroB(r.ints(), fuori)
        dentroB(r.ints(), eliminato)
        dentroB(r.ints(), haAgito)
        dentroB(r.ints(), haPuntato)
        deck.clear(); deck.addAll(r.cards())
        comuni.clear(); comuni.addAll(r.cards())
        for (p in 0 until giocatori) { mani[p].clear(); mani[p].addAll(r.cards()) }
        val m = r.ints()
        mazziere = m[0]; turno = m[1]; fase = m[2]; livello = m[3]; rilanci = m[4]
        val f = r.ints()
        manoFinita = f[0] == 1; partitaFinita = f[1] == 1; carteMostrate = f[2] == 1
        piatti = PokerPot.costruisci(puntato, fuori)
    }
}
