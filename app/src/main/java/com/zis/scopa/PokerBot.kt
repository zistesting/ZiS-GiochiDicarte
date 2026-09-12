package com.zis.scopa

import kotlin.random.Random

/**
 * Il Banco del poker: decide cosa scartare e cosa puntare.
 *
 * KOTLIN PURO e SENZA STATO, come gli altri motori, e fuori da [PokerGame] di proposito:
 * cosi' si puo' far giocare una strategia contro un'altra e misurare quale vince, che e' il
 * modo in cui in questo progetto si decidono le cose. Vedi [bluffX].
 *
 * COME DECIDE. Tutto passa da due numeri, e sono gli stessi che il giocatore vede quando
 * accende le Probabilita' a schermo:
 *
 *   e = la probabilita' che la sua mano sia la migliore, da [PokerOdds.equita], calcolata
 *       tenendo conto di quante carte ha cambiato ciascun avversario;
 *   s = le quote del piatto, da [PokerOdds.quoteDelPiatto]: l'equita' minima che rende
 *       conveniente chiamare.
 *
 * Chiamare quando e > s e passare quando e < s e' la regola giusta, e non e' un'euristica:
 * e' la definizione di s. Tutto il resto - quando puntare per farsi pagare, quando rilanciare,
 * quando bluffare - sono soglie, e le soglie si tarano misurando.
 *
 * PERCHE' IL BLUFF DEVE STARE QUI E NON ALTROVE. Un Banco che non bluffa mai e' sfruttabile
 * in modo elementare: passi ogni volta che punta, e hai finito. Ma c'e' una seconda ragione,
 * meno ovvia, che si e' scoperta scrivendo [PokerOdds]: il calcolo delle probabilita' deve
 * sapere quanto spesso il Banco bluffa, altrimenti dice al giocatore che contro un servito
 * la sua mano vale zero, e gli insegna a passare sempre - cioe' gli insegna a essere
 * sfruttabile. La frequenza di bluff percio' sta in un posto solo, qui, e la leggono in due:
 * il Banco quando decide, e PokerOdds quando interpreta le mosse degli avversari. Tenerne
 * due copie vorrebbe dire un Banco che bluffa e un calcolo che giura che non bluffi.
 */
object PokerBot {

    /**
     * La manopola. Moltiplica la frequenza di bluff calcolata dal piatto: 0 spegne il bluff,
     * 1 la lascia come la teoria la vuole, 2 la raddoppia.
     *
     * MISURATA, a fiches per mano su dodicimila mani per riga, Banco contro Banco. La storia
     * in due tempi, perche' il primo tempo era una misura di niente:
     *
     * PRIMA che il Banco leggesse le puntate - cioe' quando il calcolo dell'equita' ignorava
     * che una puntata vuol dire forza - bluffare perdeva sempre, e in modo monotono: la
     * frequenza della teoria perdeva 1,22 fiches per mano contro chi non bluffava, il
     * quadruplo ne perdeva 2,60. Non era una scoperta sul bluff: era che l'avversario chiamava
     * tutto, perche' non aveva modo di sapere che puntare significa qualcosa.
     *
     * DOPO, con la gamma di puntata modellata in [PokerOdds]:
     *
     *     non bluffa contro teoria .......... -0,50 fiches/mano (-1,5 sigma: niente)
     *     teoria contro doppio .............. -0,75           (-1,8 sigma: niente)
     *     teoria contro QUADRUPLO ........... -3,08           (-6,6 sigma: netto)
     *     controllo, due Banchi identici .... +0,39           (+1,1 sigma: taratura ok)
     *
     * La forma e' cambiata, ed e' la forma che ci si aspettava: c'e' un TETTO. Bluffare
     * troppo si paga, e si paga in modo inequivocabile. Fra zero e la frequenza della teoria,
     * invece, la differenza sta dentro il rumore: questa misura non stabilisce il numero
     * giusto, stabilisce che il quadruplo e' sbagliato. Resta 1,0 perche' e' la frequenza che
     * la teoria calcola, e perche' un Banco che non bluffa mai lo legge in dieci mani una
     * persona - che e' l'avversario che conta e che qui non si puo' simulare.
     */
    var bluffX = 1.0

    /**
     * Se vero, il Banco bluffa SOLO nell'ultimo giro di puntate.
     *
     * E' una manopola che esiste perche' la misura l'ha resa necessaria, e la storia vale
     * raccontarla. Con il bluff acceso in tutti due i giri la curva veniva monotona
     * decrescente: piu' bluffava, piu' perdeva, e a bluffo quadruplo prendeva il 34% delle
     * fiches contro il 66%. Un risultato del genere non va letto come "bluffare non conviene":
     * va letto come "sto bluffando nel posto sbagliato".
     *
     * Il posto sbagliato e' il PRIMO giro. Bluffare li' vuol dire puntare su una strada che
     * non e' l'ultima: se l'avversario chiama, dopo lo scarto lui punta e il bluff va
     * abbandonato, avendo pagato due volte. Nell'ultimo giro invece l'avversario deve
     * decidere subito e non c'e' nessuna carta che venga a smentirti, ed e' per questo che
     * nel poker vero i bluff stanno quasi tutti sull'ultima strada.
     *
     * SECONDA MISURA, dopo che il Banco ha imparato a leggere le puntate: il vantaggio si e'
     * sciolto. Limitare all'ultimo giro dava -0,50 fiches per mano al bluffatore, non
     * limitarlo gliene dava +0,46, e sono tutti due dentro il rumore - un sigma e mezzo. La
     * misura non sa scegliere. Resta `true` perche' quando i numeri non decidono, decide
     * l'argomento che non dipende da questo avversario: puntare su una strada che non e'
     * l'ultima ti costringe a difendere il bluff dopo, e quello lo sfrutta anche una persona.
     */
    var bluffSoloUltimoGiro = true

    /**
     * La quota di "serviti" che sono bluff, cioe' quanto spesso uno che non cambia carte in
     * realta' non ha niente. Lo legge anche [PokerOdds.equita].
     *
     * VENTI PER CENTO, e il numero viene da un conto che ha cambiato il progetto. Nel 5-Card
     * Draw servirsi per davvero vuol dire avere scala o meglio, che capita allo 0,76% delle
     * mani: e' un evento rarissimo. Quindi basta un bluff pochissimo frequente per rendere
     * la maggioranza dei serviti dei bluff:
     *
     *     si serve a bluff nell'1% delle mani debolli  ->  il 42% dei serviti sono bluff
     *     nel 2%                                       ->  il 59%
     *     nel 5%                                       ->  il 78%
     *
     * Al contrario, per restare a una quota credibile del 20% il Banco deve servirsi a bluff
     * una volta ogni 289 mani debolli, cioe' quasi mai. E' l'opposto di quello che dice
     * l'intuizione, e cambia dove sta il bluff in questo gioco: il veicolo non e' servirsi
     * con niente - troppo raro per essere credibile a qualunque frequenza utile - ma
     * PUNTARE dopo aver cambiato, raccontando di aver preso. Per questo il bluff del Banco e'
     * quasi tutto nelle puntate, e il servito a vuoto e' un'eccezione rara e voluta.
     */
    const val BLUFF_SERVITO = 0.20

    /** Probabilita' di servirsi con niente in mano, ricavata da [BLUFF_SERVITO]. */
    private const val P_SERVITO_VERO = 0.0076
    private const val P_MANO_DEBOLE = 0.55
    private val patBluff =
        BLUFF_SERVITO * P_SERVITO_VERO / ((1 - BLUFF_SERVITO) * P_MANO_DEBOLE)

    /**
     * Campioni per il calcolo dell'equita' del Banco: meno di quelli mostrati a schermo,
     * perche' qui un errore dell'1-2% non cambia quasi mai la decisione - le soglie sono a
     * distanza di decine di punti percentuali, non di due.
     *
     * E' un `var` per la stessa ragione per cui il gruzzolo iniziale di [PokerGame] e' un
     * parametro: i tornei fra Banchi giocano centinaia di migliaia di mani, e a seicento
     * campioni per decisione non finirebbero. Nell'app non si tocca.
     */
    var campioniBanco = 600

    // -------------------------------------------------------------- lo scarto

    /**
     * Quali carte cambia il Banco. Di regola lo scarto naturale; di rado, con una mano che
     * non vale niente, si serve per raccontare una mano fatta.
     */
    fun scarto(g: PokerGame, p: Int, rnd: Random = Random.Default): List<Int> {
        val naturale = PokerOdds.scartoNaturale(g.mani[p])
        if (naturale.isEmpty()) return naturale                  // servito per davvero
        // il servito a bluff ha senso solo se dopo si potra' puntare, e solo se qualcuno
        // puo' passare: contro avversari tutti all-in non c'e' niente da far passare
        if (naturale.size >= 3 && qualcunoPuoPassare(g, p) &&
            rnd.nextDouble() < patBluff * bluffX) return emptyList()
        return naturale
    }

    // -------------------------------------------------------------- le puntate

    /**
     * La mossa del Banco nel giro di puntate.
     *
     * Le soglie del valore ([valoreMinimo]) crescono col numero di avversari, e devono: una
     * mano che batte un avversario su due ne batte tutti e tre una volta su otto. Puntare con
     * la stessa forza in due e in quattro e' l'errore piu' comune dei programmi di poker.
     */
    fun azione(g: PokerGame, p: Int, rnd: Random = Random.Default): PokerGame.Azione {
        val azioni = g.azioniLegali(p)
        if (azioni.isEmpty()) return PokerGame.Azione.PARIFICA
        val avversari = (0 until g.giocatori).count { it != p && g.inMano(it) }
        val e = equita(g, p, avversari, rnd)
        val daPareggiare = g.daPareggiare(p)

        if (daPareggiare == 0) {
            // Nessuno ha puntato: si punta o si passa la parola. Passare non e' fra le
            // mosse, e giustamente: vedere le carte gratis non si rifiuta.
            val puoBluffare = !bluffSoloUltimoGiro || g.fase == PokerGame.PUNTATE_2
            val punta = e >= valoreMinimo(avversari) ||
                (puoBluffare && e < 0.35 && rnd.nextDouble() < frequenzaBluff(g, avversari))
            return if (punta && PokerGame.Azione.RILANCIA in azioni) PokerGame.Azione.RILANCIA
                   else PokerGame.Azione.PARIFICA
        }

        // C'e' da pareggiare. La soglia non e' un'opinione: e' le quote del piatto.
        val soglia = PokerOdds.quoteDelPiatto(g.piatto, daPareggiare)
        if (e >= rilancioMinimo(avversari) && PokerGame.Azione.RILANCIA in azioni)
            return PokerGame.Azione.RILANCIA
        if (e > soglia) return PokerGame.Azione.PARIFICA
        // Sotto le quote si passa. Chiamare comunque per non essere leggibili non serve:
        // a essere imprevedibile ci pensa il bluff quando punta lui, e chiamare in perdita
        // e' un modo costoso di nascondersi.
        return if (PokerGame.Azione.PASSA in azioni) PokerGame.Azione.PASSA
               else PokerGame.Azione.PARIFICA
    }

    // ------------------------------------------------------------- i numeri

    /**
     * L'equita' della mano di [p], tenendo conto di quante carte hanno cambiato gli altri.
     *
     * Le carte cambiate si passano solo dopo lo scarto: prima non c'e' niente da sapere, e
     * chiedere il condizionamento a vuoto costerebbe senza dare niente.
     */
    fun equita(g: PokerGame, p: Int, avversari: Int, rnd: Random = Random.Default): Double {
        if (avversari <= 0 || g.mani[p].size < 5) return 1.0
        val sapendoLoScarto = g.fase >= PokerGame.PUNTATE_2
        val letti = (0 until g.giocatori).filter { it != p && g.inMano(it) }.map {
            PokerOdds.Avversario(
                cambiate = if (sapendoLoScarto) g.cambiate[it].coerceAtLeast(0) else -1,
                haPuntato = g.haPuntato[it]
            )
        }
        // il modello dell'avversario e' la strategia del Banco: le stesse soglie, lo stesso
        // bluff. E' il punto fisso giusto, e l'unico che non si contraddice.
        val modello = PokerOdds.Modello(
            bluffServito = BLUFF_SERVITO,
            sogliaPuntata = PokerOdds.forza(sogliaPuntataComeForza(avversari)),
            bluffPuntata = frequenzaBluff(g, avversari)
        )
        return PokerOdds.equita(g.mani[p], letti, campioniBanco, rnd, modello)
    }

    /**
     * Quanto spesso bluffare, adesso, con questo piatto.
     *
     * Il numero di base e' quello della teoria ([PokerOdds.frequenzaBluff]): la frequenza che
     * rende indifferenti le due scelte dell'avversario. Poi si divide per il numero di
     * avversari, perche' un bluff deve farli passare TUTTI: contro tre, la probabilita' che
     * funzioni e' molto piu' bassa, e la frequenza deve scendere con lei. La divisione e'
     * grossolana - non e' la correzione esatta, che dipenderebbe da come giocano gli altri -
     * e per questo c'e' [bluffX], che e' la manopola da misurare.
     */
    fun frequenzaBluff(g: PokerGame, avversari: Int): Double {
        if (!qualcunoPuoPassare(g, indiceDiTurno(g))) return 0.0
        val base = PokerOdds.frequenzaBluff(g.piatto, g.puntataCorrente())
        return (base / maxOf(1, avversari)) * bluffX
    }

    private fun indiceDiTurno(g: PokerGame) = g.turno

    /**
     * Vero se c'e' almeno un avversario che potrebbe passare.
     *
     * Bluffare contro chi e' all-in non ha nessun modo di funzionare: non puo' passare, e
     * allo showdown con niente in mano non si vince. E' il primo dei tre casi in cui il
     * bluff e' un errore e non una scelta.
     */
    private fun qualcunoPuoPassare(g: PokerGame, p: Int): Boolean =
        (0 until g.giocatori).any { it != p && g.inMano(it) && g.fiches[it] > 0 }

    /**
     * La soglia di puntata espressa in FORZA e non in equita'.
     *
     * Serve al modello dell'avversario: [PokerOdds.forza] e' un percentile fra tutte le mani
     * possibili, l'equita' e' la probabilita' di battere N avversari, e non sono la stessa
     * cosa - una mano che batte l'80% delle mani non batte tre avversari nell'80% dei casi,
     * li batte nel 51%. La conversione approssimata e' la radice n-esima: se batte una mano
     * a caso con probabilita' f, ne batte n con f elevato a n, quindi per chiedere equita' e
     * si vuole forza pari a e elevato a 1/n.
     */
    private fun sogliaPuntataComeForza(avversari: Int): Int {
        val e = valoreMinimo(avversari)
        val f = Math.pow(e, 1.0 / maxOf(1, avversari))
        // si restituisce un punteggio finto che abbia quella forza: basta al modello, che
        // di questo numero usa solo la forza
        return punteggioConForza(f)
    }

    /** Un punteggio qualsiasi la cui [PokerOdds.forza] valga circa [f]. */
    private fun punteggioConForza(f: Double): Int {
        var basso = 0
        var alto = 9 shl 20
        repeat(40) {
            val mezzo = (basso + alto) / 2
            if (PokerOdds.forza(mezzo) < f) basso = mezzo else alto = mezzo
        }
        return alto
    }

    /** L'equita' da cui in su conviene puntare per farsi pagare. */
    private fun valoreMinimo(avversari: Int): Double = when (avversari) {
        1 -> 0.58
        2 -> 0.70
        else -> 0.78
    }

    /** L'equita' da cui in su conviene rilanciare. */
    private fun rilancioMinimo(avversari: Int): Double = when (avversari) {
        1 -> 0.72
        2 -> 0.82
        else -> 0.88
    }
}
