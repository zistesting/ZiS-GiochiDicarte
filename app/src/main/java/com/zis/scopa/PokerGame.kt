package com.zis.scopa

/**
 * Il motore del poker: 5-Card Draw a limite fisso, in due o in quattro.
 *
 * KOTLIN PURO come gli altri motori. Non sa niente di Android, non decide niente di grafico,
 * e non contiene il Banco: la scelta delle mosse sta fuori, perche' cosi' si puo' far giocare
 * una strategia contro un'altra e misurare quale vince, come si e' fatto col Tresette.
 *
 * IL PIATTO E LE MANI stanno in [PokerPot] e [PokerHand], provati a parte. Qui sta soltanto
 * la macchina a stati: chi tocca, cosa puo' fare, quando un giro di puntate e' chiuso.
 *
 * LE QUATTRO FASI di una mano:
 *
 *     PUNTATE_1  ogni giocatore mette la posta, si distribuiscono cinque carte, si punta
 *     SCARTO     ognuno cambia da zero a quattro carte
 *     PUNTATE_2  si punta un'altra volta, al doppio
 *     SHOWDOWN   si mostrano le carte e il piatto va alla mano migliore
 *
 * Se a un certo punto resta un solo giocatore, la mano si chiude subito e senza mostrare le
 * carte: e' il caso piu' frequente di tutti.
 *
 * DUE CONTATORI PER I SOLDI, e serve tenerli distinti. [puntato] e' quanto un giocatore ha
 * messo in TUTTA la mano, posta compresa, e serve a costruire i piatti. [impegno] e' quanto
 * ha messo NEL GIRO in corso, e serve a sapere quanto gli resta da pareggiare. Dopo la posta
 * l'impegno torna a zero, perche' la posta e' denaro morto: l'hanno messa tutti, nessuno la
 * deve pareggiare, e il primo giro comincia con la possibilita' di passare la parola senza
 * mettere niente. Confonderli vuol dire o chiedere due volte la posta o regalarla.
 *
 * IL LIMITE FISSO e' una REGOLA, non una struttura: [rilancia] versa un importo, e quale
 * importo sia ammesso lo dice [puntataCorrente]. Il giorno che si volesse il no-limit si
 * cambia quel metodo e si aggiunge un parametro, non si riscrive niente.
 */
class PokerGame(val giocatori: Int, val fichesIniziali: Int = FICHES_INIZIALI) {

    companion object {
        const val FICHES_INIZIALI = 1000
        const val POSTA = 10
        const val PUNTATA_PRIMO = 20
        const val PUNTATA_SECONDO = 40
        const val RILANCI_MAX = 3
        const val SCARTO_MAX = 4

        const val PUNTATE_1 = 0
        const val SCARTO = 1
        const val PUNTATE_2 = 2
        const val SHOWDOWN = 3
    }

    enum class Azione { PASSA, PARIFICA, RILANCIA }

    // ---- il tavolo ----
    /**
     * Il gruzzolo di ciascuno. Il valore di partenza e' un parametro e non la costante, per
     * una ragione di prova: con mille fiches a testa e puntate da venti gli all-in sono
     * rari, e la parte del motore che li gestisce resterebbe quasi mai esercitata. Dando
     * sessanta fiche a testa si finisce all-in a ogni mano, ed e' cosi' che quel codice si
     * mette alla prova. Nell'app il parametro non si passa e vale [FICHES_INIZIALI].
     */
    val fiches = IntArray(giocatori) { fichesIniziali }
    val mani = Array(giocatori) { ArrayList<Card>() }
    val eliminato = BooleanArray(giocatori)

    // ---- la mano in corso ----
    val puntato = IntArray(giocatori)      // in tutta la mano, posta compresa
    val impegno = IntArray(giocatori)      // solo nel giro in corso
    val fuori = BooleanArray(giocatori)    // ha passato, oppure e' eliminato
    val cambiate = IntArray(giocatori)     // quante carte ha cambiato: -1 = non ancora
    val incasso = IntArray(giocatori)      // quanto ha incassato all'ultima chiusura
    private val haAgito = BooleanArray(giocatori)

    /**
     * Chi ha puntato o rilanciato in QUESTO giro. Non e' un dettaglio di comodo: e'
     * l'informazione con cui il Banco interpreta la forza dell'avversario, e senza di essa
     * chiamava le puntate come se nessuno avesse puntato. Si azzera a ogni giro nuovo.
     */
    val haPuntato = BooleanArray(giocatori)
    val deck = ArrayList<Card>()
    val scarti = ArrayList<Card>()

    var mazziere = 0
    var turno = 0
    var fase = PUNTATE_1
    var livello = 0                        // l'impegno da pareggiare in questo giro
    var rilanci = 0
    var manoFinita = false
    var partitaFinita = false
    var carteMostrate = false              // allo showdown si, con un solo superstite no
    var piatti: List<PokerPot.Piatto> = emptyList()

    /** Tutto quello che c'e' in mezzo al tavolo. */
    val piatto: Int get() = puntato.sum()

    /** Sta ancora giocando questa mano: non e' eliminato e non ha passato. */
    fun inMano(p: Int) = !fuori[p]

    /** Puo' ancora fare una mossa: e' in mano e non e' all-in. */
    fun puoAgire(p: Int) = inMano(p) && fiches[p] > 0

    /** Quanti sono ancora in corsa per il piatto. */
    fun quantiInMano(): Int = (0 until giocatori).count { inMano(it) }

    /** La puntata di questo giro: e' qui che vive la regola del limite fisso. */
    fun puntataCorrente(): Int = if (fase == PUNTATE_2) PUNTATA_SECONDO else PUNTATA_PRIMO

    /** Quanto deve mettere [p] per restare in gioco. Meno delle sue fiches se va all-in. */
    fun daPareggiare(p: Int): Int = minOf(livello - impegno[p], fiches[p])

    // ------------------------------------------------------------ la partita

    /**
     * Distribuisce una mano nuova. Chi ha finito le fiches e' eliminato e non partecipa: lo
     * si segna come [fuori], cosi' tutta la macchina lo salta senza casi particolari.
     */
    fun nuovaMano() {
        manoFinita = false
        carteMostrate = false
        piatti = emptyList()
        for (p in 0 until giocatori) {
            mani[p].clear()
            puntato[p] = 0; impegno[p] = 0; incasso[p] = 0
            haAgito[p] = false
            haPuntato[p] = false
            cambiate[p] = -1
            fuori[p] = eliminato[p]
        }
        deck.clear(); scarti.clear()
        for (s in 0..3) for (v in 1..13) deck.add(Card(s, v))
        deck.shuffle()

        // la posta: la mette chiunque partecipi, e chi ha meno di dieci fiches la mette
        // per quello che ha e resta all-in prima ancora di vedere le carte
        for (p in 0 until giocatori) if (inMano(p)) versa(p, POSTA)
        // denaro morto: nessuno la deve pareggiare
        for (p in 0 until giocatori) impegno[p] = 0
        livello = 0
        rilanci = 0
        fase = PUNTATE_1

        // cinque carte a testa, una per volta come si distribuisce davvero
        repeat(5) {
            for (p in ordineDa(primoDopoMazziere())) if (inMano(p)) mani[p].add(deck.removeAt(deck.size - 1))
        }

        turno = primoCheAgisce(primoDopoMazziere())
        // caso limite: tutti all-in sulla posta, nessuno puo' puntare. Si passa allo scarto.
        if (!chiudiSeSoloUno() && giroChiuso()) chiudiGiro()
    }

    // ------------------------------------------------------------- le mosse

    /**
     * Le mosse ammesse a [p] in questo momento. Serve anche alla schermata, per accendere e
     * spegnere i pulsanti.
     *
     * PASSARE QUANDO NON C'E' NIENTE DA PAREGGIARE non e' fra le mosse, e non e' una
     * dimenticanza: e' una mossa che non puo' mai convenire - butti via una mano che potresti
     * vedere gratis - e offrirla vorrebbe dire solo far perdere partite a chi sbaglia a
     * toccare. Chi non vuole mettere niente passa la parola, che qui e' PARIFICA con zero.
     */
    fun azioniLegali(p: Int): List<Azione> {
        if (manoFinita || partitaFinita || fase == SCARTO || fase == SHOWDOWN) return emptyList()
        if (p != turno || !puoAgire(p)) return emptyList()
        val a = ArrayList<Azione>(3)
        if (livello > impegno[p]) a.add(Azione.PASSA)
        a.add(Azione.PARIFICA)
        if (rilanci < RILANCI_MAX && fiches[p] > daPareggiare(p)) a.add(Azione.RILANCIA)
        return a
    }

    fun agisci(p: Int, azione: Azione) {
        require(azione in azioniLegali(p)) { "mossa non ammessa: $azione per il giocatore $p" }
        when (azione) {
            Azione.PASSA -> fuori[p] = true
            Azione.PARIFICA -> versa(p, livello - impegno[p])
            Azione.RILANCIA -> {
                versa(p, livello + puntataCorrente() - impegno[p])
                // Se non gli bastavano le fiches per rilanciare davvero, quello che ha messo
                // non alza il livello: e' un all-in che vale come pareggio, e il conto dei
                // rilanci non si muove. Contarlo come rilancio chiuderebbe il giro in
                // anticipo agli altri.
                if (impegno[p] > livello) {
                    livello = impegno[p]
                    rilanci++
                    haPuntato[p] = true
                    // gli altri devono rispondere di nuovo
                    for (q in 0 until giocatori) if (q != p && puoAgire(q)) haAgito[q] = false
                }
            }
        }
        haAgito[p] = true
        avanza()
    }

    /**
     * Cambia le carte di [p]: [indici] sono le posizioni nella sua mano, da zero a quattro.
     *
     * Le scartate finiscono in [scarti] e non tornano nel mazzo. Non serve rimescolarle: in
     * quattro con quattro cambi a testa si arriva a trentasei carte su cinquantadue, e il
     * mazzo non si esaurisce mai.
     */
    fun scarta(p: Int, indici: List<Int>) {
        require(fase == SCARTO && p == turno && inMano(p)) { "non e' il momento di scartare" }
        require(indici.size <= SCARTO_MAX) { "si cambiano al massimo $SCARTO_MAX carte" }
        require(indici.distinct().size == indici.size) { "indici ripetuti" }
        for (i in indici.sortedDescending()) {
            scarti.add(mani[p][i])
            mani[p].removeAt(i)
        }
        repeat(indici.size) { mani[p].add(deck.removeAt(deck.size - 1)) }
        cambiate[p] = indici.size
        avanzaScarto()
    }

    // ---------------------------------------------------------- la macchina

    private fun versa(p: Int, quanto: Int) {
        val q = minOf(maxOf(quanto, 0), fiches[p])
        fiches[p] -= q
        impegno[p] += q
        puntato[p] += q
    }

    private fun primoDopoMazziere() = (mazziere + 1) % giocatori

    private fun ordineDa(primo: Int) = (0 until giocatori).map { (primo + it) % giocatori }

    private fun primoCheAgisce(da: Int): Int {
        for (p in ordineDa(da)) if (puoAgire(p)) return p
        return da
    }

    /**
     * Il giro e' chiuso quando tutti quelli che potevano muoversi hanno mosso e hanno
     * pareggiato il livello. Chi e' all-in non conta: ha finito le fiches, non ha piu' mosse.
     */
    private fun giroChiuso(): Boolean {
        val agenti = (0 until giocatori).filter { puoAgire(it) }
        if (agenti.isEmpty()) return true
        // con un solo giocatore che puo' muoversi il giro e' chiuso appena ha pareggiato:
        // non c'e' nessuno a cui passare la parola
        return agenti.all { haAgito[it] && impegno[it] == livello }
    }

    private fun avanza() {
        if (chiudiSeSoloUno()) return
        if (giroChiuso()) { chiudiGiro(); return }
        var q = turno
        do { q = (q + 1) % giocatori } while (!puoAgire(q))
        turno = q
    }

    private fun chiudiGiro() {
        when (fase) {
            PUNTATE_1 -> {
                fase = SCARTO
                for (p in 0 until giocatori) { impegno[p] = 0; haAgito[p] = false; haPuntato[p] = false }
                livello = 0; rilanci = 0
                turno = primoInMano(primoDopoMazziere())
            }
            PUNTATE_2 -> showdown()
        }
    }

    private fun primoInMano(da: Int): Int {
        for (p in ordineDa(da)) if (inMano(p)) return p
        return da
    }

    private fun avanzaScarto() {
        // tocca al prossimo che deve ancora cambiare
        for (p in ordineDa(primoDopoMazziere())) if (inMano(p) && cambiate[p] < 0) { turno = p; return }
        // hanno cambiato tutti: secondo giro di puntate
        fase = PUNTATE_2
        for (p in 0 until giocatori) { impegno[p] = 0; haAgito[p] = false; haPuntato[p] = false }
        livello = 0; rilanci = 0
        turno = primoCheAgisce(primoDopoMazziere())
        if (giroChiuso()) chiudiGiro()
    }

    /**
     * Se e' rimasto un solo giocatore la mano finisce senza showdown, e le carte NON si
     * mostrano: e' il caso piu' comune, e nascondere quelle carte e' mezzo poker. Il piatto
     * si assegna con la stessa macchina dello showdown, e li' la regola dello strato orfano
     * di [PokerPot] fa esattamente la cosa giusta - la parte di puntata che nessuno ha
     * chiamato torna a chi l'aveva messa.
     */
    private fun chiudiSeSoloUno(): Boolean {
        if (manoFinita || quantiInMano() > 1) return false
        assegna(mostra = false)
        return true
    }

    private fun showdown() {
        fase = SHOWDOWN
        assegna(mostra = true)
    }

    private fun assegna(mostra: Boolean) {
        val punteggio = IntArray(giocatori) {
            if (inMano(it) && mani[it].size == 5) PokerHand.valuta(mani[it]) else 0
        }
        piatti = PokerPot.costruisci(puntato, fuori)
        val inc = PokerPot.assegna(piatti, punteggio, primoDopoMazziere(), fuori)
        for (p in 0 until giocatori) {
            incasso[p] = inc[p]
            fiches[p] += inc[p]
        }
        carteMostrate = mostra && quantiInMano() > 1
        manoFinita = true
        chiudiMano()
    }

    /** Elimina chi ha finito le fiches, sposta il mazziere, e vede se la partita e' finita. */
    private fun chiudiMano() {
        for (p in 0 until giocatori) if (fiches[p] <= 0) eliminato[p] = true
        val vivi = (0 until giocatori).count { !eliminato[it] }
        partitaFinita = vivi <= 1
        if (!partitaFinita) {
            var m = mazziere
            do { m = (m + 1) % giocatori } while (eliminato[m])
            mazziere = m
        }
    }

    /** Chi ha vinto la partita, o -1 se non e' finita. */
    fun vincitore(): Int =
        if (!partitaFinita) -1 else (0 until giocatori).firstOrNull { !eliminato[it] } ?: -1

    // ------------------------------------------------------------ salvataggio

    fun save(w: SavedGame.Writer) {
        w.ints(fiches.toList())
        w.ints(puntato.toList())
        w.ints(impegno.toList())
        w.ints(cambiate.toList())
        w.ints(incasso.toList())
        w.ints(fuori.map { if (it) 1 else 0 })
        w.ints(eliminato.map { if (it) 1 else 0 })
        w.ints(haAgito.map { if (it) 1 else 0 })
        w.ints(haPuntato.map { if (it) 1 else 0 })
        w.cards(deck)
        w.cards(scarti)
        for (p in 0 until giocatori) w.cards(mani[p])
        w.ints(listOf(mazziere, turno, fase, livello, rilanci))
        w.ints(listOf(if (manoFinita) 1 else 0, if (partitaFinita) 1 else 0,
                      if (carteMostrate) 1 else 0))
    }

    fun load(r: SavedGame.Reader) {
        fun dentro(v: List<Int>, a: IntArray) { for (i in a.indices) a[i] = v[i] }
        fun dentroB(v: List<Int>, a: BooleanArray) { for (i in a.indices) a[i] = v[i] == 1 }
        dentro(r.ints(), fiches)
        dentro(r.ints(), puntato)
        dentro(r.ints(), impegno)
        dentro(r.ints(), cambiate)
        dentro(r.ints(), incasso)
        dentroB(r.ints(), fuori)
        dentroB(r.ints(), eliminato)
        dentroB(r.ints(), haAgito)
        dentroB(r.ints(), haPuntato)
        deck.clear(); deck.addAll(r.cards())
        scarti.clear(); scarti.addAll(r.cards())
        for (p in 0 until giocatori) { mani[p].clear(); mani[p].addAll(r.cards()) }
        val m = r.ints()
        mazziere = m[0]; turno = m[1]; fase = m[2]; livello = m[3]; rilanci = m[4]
        val f = r.ints()
        manoFinita = f[0] == 1; partitaFinita = f[1] == 1; carteMostrate = f[2] == 1
        // i piatti si ricostruiscono, non si salvano: sono una funzione di puntato e fuori
        piatti = if (manoFinita) PokerPot.costruisci(puntato, fuori) else emptyList()
    }
}
