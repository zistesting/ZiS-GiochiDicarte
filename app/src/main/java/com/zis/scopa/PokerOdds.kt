package com.zis.scopa

import kotlin.random.Random

/**
 * I numeri del poker: quanto vale una mano, quando conviene chiamare, quanto spesso bluffare.
 *
 * Serve a due cose insieme, e non e' un caso che siano la stessa cosa: al Banco per decidere,
 * e al giocatore per imparare, quando accende le Probabilita' a schermo. Un aiuto che mostra
 * un numero diverso da quello su cui ragiona l'avversario non insegnerebbe a giocare:
 * insegnerebbe a giocare contro quel Banco.
 *
 * KOTLIN PURO, e senza stato: sono funzioni che prendono carte e restituiscono numeri.
 *
 * PERCHE' A CAMPIONE E NON ESATTO. Il conto esatto contro un solo avversario vuole tutte le
 * C(47,5) mani che potrebbe avere, cioe' 1.533.939 valutazioni: mezzo secondo su un computer,
 * di piu' su un telefono, e in quest'app tutto gira su un solo Handler - bloccherebbe la
 * schermata a ogni decisione. Contro tre avversari non e' nemmeno calcolabile. Quindi si
 * estrae un campione, e l'errore si sa quanto vale: su N estrazioni lo scarto tipico e'
 * radice di p(1-p)/N, che con seimila estrazioni e una mano da testa o croce fa lo 0,6%. Per
 * un numero che serve a decidere se chiamare, e per uno che si mostra arrotondato, e' piu'
 * che abbastanza. [equitaEsatta] resta qui, e serve a una cosa sola: controllare che il
 * campione dica la verita'.
 */
object PokerOdds {

    const val CAMPIONI = 6000

    /**
     * Il tetto di estrazioni quando si condiziona su quante carte ha cambiato l'avversario.
     * Serve perche' alcune situazioni sono rare: essersi serviti capita allo 0,76% delle
     * mani, quindi per raccoglierne duemila ne vanno estratte oltre duecentomila. E' il costo
     * piu' alto di tutto il calcolo, e si paga solo quando l'avversario si e' servito.
     */
    const val TENTATIVI_MAX = 250_000

    // ------------------------------------------------------------- lo scarto

    /**
     * Quali carte cambiare: gli indici nella mano.
     *
     * E' insieme la mossa del Banco e il MODELLO DELL'AVVERSARIO usato da [equita] per
     * interpretare quante carte ha cambiato lui. Che sia la stessa funzione e' voluto: il
     * Banco assume che l'avversario giochi come giocherebbe lui, che e' l'assunzione piu'
     * onesta quando non si sa niente di chi si ha davanti.
     *
     * Le regole, nell'ordine in cui si applicano:
     *
     *  - mano gia' fatta (scala o meglio): non si cambia niente, si sta serviti;
     *  - tris: si cambiano le due spaiate;
     *  - doppia coppia: si cambia la quinta;
     *  - quattro a colore o quattro a scala bilaterale: si cambia una carta;
     *  - una coppia: si cambiano le tre spaiate;
     *  - niente: si tiene la carta piu' alta e si cambiano quattro.
     *
     * Alla scala interna - quattro carte con un buco in mezzo - NON si tira: sono quattro
     * carte utili su quarantasette, l'8,5%, contro il 19,1% del colore e il 17% della scala
     * bilaterale. Tirarci perde piu' di quanto rende, ed e' l'errore che fa quasi tutti.
     */
    fun scartoNaturale(mano: List<Card>): List<Int> {
        val punteggio = PokerHand.valuta(mano)
        val categoria = PokerHand.categoria(punteggio)

        // mano fatta: servito
        if (categoria >= PokerHand.SCALA) return emptyList()

        val ranghi = mano.map { PokerHand.rango(it.value) }
        val quanti = HashMap<Int, MutableList<Int>>()
        for (i in mano.indices) quanti.getOrPut(ranghi[i]) { ArrayList() }.add(i)

        when (categoria) {
            PokerHand.TRIS -> return quanti.values.filter { it.size == 1 }.flatten()
            PokerHand.DOPPIA_COPPIA -> return quanti.values.first { it.size == 1 }
            PokerHand.COPPIA -> {
                // quattro a colore o a scala valgono piu' di una coppia bassa? No: con la
                // coppia si ha gia' qualcosa in mano, e rompere una coppia per tirare a un
                // progetto e' l'altro errore classico. Si cambiano le tre spaiate.
                return quanti.values.filter { it.size == 1 }.flatten()
            }
        }

        // da qui: carta alta. Ci sono due progetti che vale la pena tirare.
        val perSeme = mano.indices.groupBy { mano[it].suit }
        val quattroColore = perSeme.values.firstOrNull { it.size == 4 }
        if (quattroColore != null) return mano.indices.filter { it !in quattroColore }

        val quattroScala = quattroABilaterale(ranghi)
        if (quattroScala != null) return mano.indices.filter { it !in quattroScala }

        // niente: si tiene la piu' alta e si cambiano quattro
        val piuAlta = mano.indices.maxBy { ranghi[it] }
        return mano.indices.filter { it != piuAlta }
    }

    /**
     * Gli indici di quattro carte consecutive aperte da tutte due le parti, o null.
     *
     * Bilaterale vuol dire che la scala si chiude da entrambi i lati, e sono otto carte
     * utili. 2-3-4-5 e J-Q-K-A no: si chiudono da un lato solo, sono quattro carte utili come
     * la scala interna, e per lo stesso motivo non ci si tira.
     */
    private fun quattroABilaterale(ranghi: List<Int>): List<Int>? {
        val distinti = ranghi.distinct().sorted()
        if (distinti.size < 4) return null
        for (i in 0..distinti.size - 4) {
            val q = distinti.subList(i, i + 4)
            if (q[3] - q[0] != 3) continue
            if (q[0] <= 2 || q[3] >= 14) continue      // si chiude da un lato solo
            return ranghi.indices.filter { ranghi[it] in q }.take(4)
        }
        return null
    }

    // -------------------------------------------------------------- la forza

    /**
     * Il percentile esatto per categoria e rango primario: quante mani, in frazione, stanno
     * sotto una mano di quella categoria con quel primo criterio di parita'.
     *
     * COME E' NATA, che e' la parte che conta. La prima versione aveva nove numeri - le
     * frequenze cumulate delle categorie - e interpolava linearmente dentro la categoria sul
     * rango primario. Sembrava ragionevole e non lo era: confrontata col percentile esatto,
     * ricavato enumerando tutte le 2.598.960 mani, sbagliava fino a 32,8 punti percentuali
     * sulla CARTA ALTA, che e' la meta' esatta di tutte le mani, per un errore medio pesato
     * di 10,3 punti. Il motivo e' che dentro la carta alta le mani non sono distribuite
     * uniformemente per carta piu' alta: quelle con l'asso sono il 19% del mazzo da sole.
     *
     * Questa tabella e' quella vera, generata dall'enumerazione completa (lo strumento sta
     * nel banco di prova, GeneraTabella.kt). Centotrentacinque numeri, e dentro ogni cella
     * resta da interpolare un residuo che al massimo vale il 19% delle mani, contro i 50
     * della versione prima.
     */
    private val PERCENTILE = arrayOf(
        doubleArrayOf(0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.001570, 0.007064, 0.020408, 0.047488, 0.096546, 0.178571, 0.307692),
        doubleArrayOf(0.501177, 0.501177, 0.501177, 0.533683, 0.566188, 0.598693, 0.631199, 0.663704, 0.696209, 0.728715, 0.761220, 0.793725, 0.826230, 0.858736, 0.891241),
        doubleArrayOf(0.923746, 0.923746, 0.923746, 0.923746, 0.924356, 0.925575, 0.927403, 0.929841, 0.932889, 0.936545, 0.940812, 0.945688, 0.951173, 0.957268, 0.963972),
        doubleArrayOf(0.971285, 0.971285, 0.971285, 0.972911, 0.974536, 0.976161, 0.977786, 0.979412, 0.981037, 0.982662, 0.984288, 0.985913, 0.987538, 0.989163, 0.990789),
        doubleArrayOf(0.992414, 0.992414, 0.992414, 0.992414, 0.992414, 0.992414, 0.992806, 0.993199, 0.993591, 0.993984, 0.994376, 0.994769, 0.995161, 0.995554, 0.995946),
        doubleArrayOf(0.996339, 0.996339, 0.996339, 0.996339, 0.996339, 0.996339, 0.996339, 0.996339, 0.996345, 0.996366, 0.996419, 0.996525, 0.996717, 0.997039, 0.997545),
        doubleArrayOf(0.998304, 0.998304, 0.998304, 0.998415, 0.998526, 0.998636, 0.998747, 0.998858, 0.998969, 0.999080, 0.999190, 0.999301, 0.999412, 0.999523, 0.999634),
        doubleArrayOf(0.999745, 0.999745, 0.999745, 0.999763, 0.999781, 0.999800, 0.999818, 0.999837, 0.999855, 0.999874, 0.999892, 0.999911, 0.999929, 0.999948, 0.999966),
        doubleArrayOf(0.999985, 0.999985, 0.999985, 0.999985, 0.999985, 0.999985, 0.999986, 0.999988, 0.999989, 0.999991, 0.999992, 0.999994, 0.999995, 0.999997, 0.999998)
    )

    /**
     * Quanto e' forte una mano, da 0 a 1: la frazione di mani da cinque carte che batte.
     *
     * A COSTO ZERO, e serve a questo: il Banco, per interpretare la puntata di un avversario,
     * deve chiedersi "con quali mani avrebbe puntato?", e per farlo deve valutare la forza di
     * migliaia di mani campionate. Chiamare [equita] su ognuna vorrebbe dire un calcolo Monte
     * Carlo dentro un altro: centomila volte piu' lento, e inutile. Qui invece si prende la
     * categoria, si guarda dove sta nella distribuzione vera, e si interpola dentro la
     * categoria sul primo criterio di parita' - il rango della coppia, del tris, della carta
     * alta della scala.
     *
     * E' un'APPROSSIMAZIONE, e l'interpolazione dentro la categoria e' lineare mentre la
     * distribuzione vera non lo e'. Quanto sbaglia non e' una questione di opinione: si
     * confronta col percentile esatto, ricavato enumerando tutte le mani del mazzo, e il
     * numero sta scritto nella prova. Per il mestiere che deve fare - stare sopra o sotto una
     * soglia larga - quello che conta e' l'ordine, e l'ordine e' esatto per costruzione
     * dentro ogni categoria.
     */
    fun forza(punteggio: Int): Double {
        val cat = PokerHand.categoria(punteggio).coerceIn(0, 8)
        val primo = ((punteggio shr 16) and 0xF).coerceIn(0, 14)
        val basso = PERCENTILE[cat][primo]
        // il limite alto della cella: il primo valore piu' grande nella tabella
        var alto = 1.0
        var c = cat
        var r = primo + 1
        while (c <= 8) {
            while (r <= 14) {
                if (PERCENTILE[c][r] > basso) { alto = PERCENTILE[c][r]; r = 15; c = 9; break }
                r++
            }
            if (c > 8) break
            c++; r = 0
        }
        // dentro la cella si interpola sui quattro criteri di parita' che restano, letti
        // come un numero solo: non e' la distribuzione vera, ma il residuo e' piccolo
        val resto = punteggio and 0xFFFF
        return basso + (alto - basso) * (resto / 65536.0)
    }

    // ------------------------------------------------------------- l'equita'

    /** Quello che si sa di un avversario a un certo punto della mano. */
    class Avversario(
        /** Quante carte ha cambiato, o -1 se non lo si sa ancora. */
        val cambiate: Int = -1,
        /** Vero se in questo giro ha puntato o rilanciato lui. */
        val haPuntato: Boolean = false
    )

    /**
     * Il modello di come gioca l'avversario. Non e' una tabella di numeri sparsi: sono le
     * tre cose che servono a interpretare le sue mosse, e le decide [PokerBot], che e' anche
     * quello che le usa per giocare. Un modello diverso dalla strategia vorrebbe dire un
     * Banco che bluffa e un calcolo che giura che non bluffi.
     */
    class Modello(
        /** Quota di "serviti" che sono bluff. */
        val bluffServito: Double = 0.0,
        /** La forza da cui in su si punta per valore. */
        val sogliaPuntata: Double = 1.0,
        /** Con che probabilita' si punta anche da deboli, cioe' si bluffa. */
        val bluffPuntata: Double = 0.0
    )


    /**
     * La probabilita' che [mano] sia la migliore, contro [avversari] avversari.
     *
     * La parita' conta mezza vittoria, perche' il piatto si divide: e' la definizione che
     * rende il numero direttamente confrontabile con le quote del piatto.
     *
     * [cambiate] e' quante carte ha cambiato ciascun avversario, se lo si sa - cioe' nel
     * secondo giro di puntate - oppure null. Quando c'e', il campione non estrae cinque carte
     * a caso: estrae una mano che PRIMA dello scarto avrebbe cambiato proprio quel numero di
     * carte secondo [scartoNaturale], e poi le fa cambiare davvero. E' quello che rende il
     * numero utile: essersi serviti e aver cambiato tre carte sono due informazioni enormi, e
     * un conto che le ignora dice che due coppie valgono sempre lo stesso.
     */
    /** Comodita': avversari di cui si sa solo quante carte hanno cambiato. */
    fun equita(mano: List<Card>, avversari: Int, cambiate: IntArray? = null,
               campioni: Int = CAMPIONI, rnd: Random = Random.Default,
               bluffServito: Double = 0.0): Double =
        equita(mano, (0 until avversari).map { Avversario(cambiate?.getOrNull(it) ?: -1) },
               campioni, rnd, Modello(bluffServito))

    /**
     * L'equita' della mano contro avversari di cui si sa quello che sta in [letti].
     *
     * QUELLO CHE CAMBIA RISPETTO A PRIMA, e che la misura ha reso necessario: se un
     * avversario ha PUNTATO, le sue mani possibili non sono piu' tutte quelle compatibili col
     * suo scarto - sono quelle con cui avrebbe puntato. Cioe' le forti, piu' i bluff nella
     * proporzione giusta. Senza questa riga il Banco chiamava le puntate con la soglia di una
     * situazione in cui nessuno ha puntato, e i bluff finivano tutti pagati: misurato,
     * bluffare perdeva sempre, a qualunque frequenza. Il bluff e la lettura della puntata
     * sono un solo meccanismo.
     */
    fun equita(mano: List<Card>, letti: List<Avversario>,
               campioni: Int = CAMPIONI, rnd: Random = Random.Default,
               modello: Modello = Modello()): Double {
        val avversari = letti.size
        if (avversari <= 0) return 1.0
        val ignote = ArrayList<Card>(52)
        for (s in 0..3) for (v in 1..13) {
            val c = Card(s, v)
            if (c !in mano) ignote.add(c)
        }
        val mio = PokerHand.valuta(mano)

        // Una riserva di punteggi plausibili per ciascun avversario. Costruirle prima e poi
        // pescarci dentro non e' un'ottimizzazione: e' l'unico modo di condizionare senza
        // barare. Vedi [riservaPunteggi].
        val riserve = letti.map { riservaPunteggi(ignote, it, campioni, rnd, modello) }
        val effettivi = riserve.minOf { it.size }
        if (effettivi == 0) return equita(mano, List(avversari) { Avversario() }, campioni, rnd, modello)

        var punti = 0.0
        for (i in 0 until effettivi) {
            var migliore = 0
            for (r in riserve) if (r[i] > migliore) migliore = r[i]
            punti += when {
                mio > migliore -> 1.0
                mio < migliore -> 0.0
                else -> 0.5                    // parita': mezzo piatto
            }
        }
        return punti / effettivi
    }

    /**
     * I punteggi di un mucchio di mani plausibili per un avversario che ha cambiato [voluto]
     * carte, o a caso se [voluto] e' negativo.
     *
     * QUI C'ERA IL DIFETTO PIU' GROSSO DI TUTTO IL POKER, e vale raccontarlo perche' non
     * dava errore: dava un numero. La prima versione, per ogni campione, estraeva una mano e
     * la rifiutava se non avrebbe cambiato il numero giusto di carte, con un tetto di
     * ventiquattro tentativi; esaurito il tetto, teneva l'ultima mano rifiutata. Sembra
     * innocuo. Non lo e': essersi serviti vuol dire avere scala o meglio, che capita allo
     * 0,76% delle mani, quindi in ventiquattro tentativi si accetta solo una volta su sei -
     * e le altre cinque si usava una mano che per costruzione NON era servita. Il risultato:
     * contro un avversario servito, due coppie risultavano valere l'80%, quando valgono il
     * 3%. Il numero era perfettamente sbagliato e sembrava ragionevole.
     *
     * La versione giusta non ripiega mai su una mano rifiutata: accumula solo mani accettate,
     * dentro un tetto di tentativi, e se ne raccoglie meno di quante ne servirebbero il conto
     * si fa su quelle - meno preciso, ma non falso. Il chiamante non se ne accorge; la
     * differenza fra le due versioni e' sessanta punti percentuali.
     *
     * Le mani dei diversi avversari si estraggono indipendentemente, quindi puo' capitare che
     * due di loro ricevano la stessa carta. E' un'approssimazione voluta: sono quindici carte
     * su quarantasette, lo scarto che introduce e' molto sotto l'errore del campione, e
     * togliere la sovrapposizione costerebbe piu' di quanto rende.
     */
    private fun riservaPunteggi(ignote: List<Card>, letto: Avversario, voluti: Int,
                                rnd: Random, modello: Modello): IntArray {
        val voluto = letto.cambiate
        val fuori = ArrayList<Card>(ignote)
        val punteggi = IntArray(voluti)
        var trovate = 0
        var tentativi = 0
        val tettoTentativi = if (voluto < 0) voluti + 8 else TENTATIVI_MAX
        val cinque = ArrayList<Card>(5)

        while (trovate < voluti && tentativi < tettoTentativi) {
            tentativi++
            // mescolata parziale: solo le carte che servono a questa mano
            var usate = 0
            fun pesca(): Card {
                val j = usate + rnd.nextInt(fuori.size - usate)
                val t = fuori[usate]; fuori[usate] = fuori[j]; fuori[j] = t
                return fuori[usate++]
            }
            cinque.clear()
            repeat(5) { cinque.add(pesca()) }
            if (voluto == 0 && modello.bluffServito > 0.0 && rnd.nextDouble() < modello.bluffServito) {
                // Un servito su [bluffServito] e' un bluff: uno che si e' servito con
                // niente in mano. Questa riga e' quella che impedisce al calcolo di
                // insegnare al giocatore a passare sempre contro un servito - cioe' a
                // essere sfruttabile. Il numero lo decide PokerBot: vedi il suo commento
                // su perche' deve stare in un posto solo.
                punteggi[trovate++] = PokerHand.valuta(cinque)
                continue
            }
            if (voluto >= 0) {
                val da = scartoNaturale(cinque)
                if (da.size != voluto) continue          // rifiutata, e non si usa
                for (i in da.sortedDescending()) cinque.removeAt(i)
                repeat(da.size) { cinque.add(pesca()) }
            }
            val punteggio = PokerHand.valuta(cinque)
            // se ha puntato, si tengono solo le mani con cui avrebbe puntato: le forti
            // sempre, le deboli nella proporzione in cui si bluffa. Accettare cosi' da'
            // alla riserva esattamente la composizione della sua gamma di puntata.
            if (letto.haPuntato) {
                val f = forza(punteggio)
                if (f < modello.sogliaPuntata && rnd.nextDouble() >= modello.bluffPuntata) continue
            }
            punteggi[trovate++] = punteggio
        }
        return if (trovate == voluti) punteggi else punteggi.copyOf(trovate)
    }

    /**
     * L'equita' ESATTA contro un solo avversario: tutte le C(47,5) mani che potrebbe avere.
     *
     * Non si usa nell'app, e' troppo lenta. Serve a controllare che [equita] dica il vero.
     */
    fun equitaEsatta(mano: List<Card>): Double {
        val ignote = ArrayList<Card>(47)
        for (s in 0..3) for (v in 1..13) {
            val c = Card(s, v)
            if (c !in mano) ignote.add(c)
        }
        val mio = PokerHand.valuta(mano)
        var vinte = 0L
        var pari = 0L
        var totale = 0L
        val cinque = ArrayList<Card>(5)
        val n = ignote.size
        for (a in 0 until n - 4) for (b in a + 1 until n - 3) for (c in b + 1 until n - 2)
            for (d in c + 1 until n - 1) for (e in d + 1 until n) {
                cinque.clear()
                cinque.add(ignote[a]); cinque.add(ignote[b]); cinque.add(ignote[c])
                cinque.add(ignote[d]); cinque.add(ignote[e])
                val suo = PokerHand.valuta(cinque)
                totale++
                if (mio > suo) vinte++ else if (mio == suo) pari++
            }
        return (vinte + pari / 2.0) / totale
    }

    // ------------------------------------------------- i numeri da insegnare

    /**
     * L'equita' minima che rende conveniente chiamare: le quote del piatto.
     *
     * Rischi [daPareggiare] per vincere [piatto] piu' quello che metti. Il punto di
     * indifferenza e' dove le due scelte valgono lo stesso, cioe' daPareggiare diviso quello
     * che c'e' in gioco. Se la tua mano vale di piu' di questo numero, chiamare rende; se
     * vale di meno, passare. E' il numero piu' utile da mostrare a schermo, perche' non
     * dipende da come gioca l'avversario: e' aritmetica.
     */
    fun quoteDelPiatto(piatto: Int, daPareggiare: Int): Double =
        if (daPareggiare <= 0) 0.0 else daPareggiare.toDouble() / (piatto + daPareggiare)

    /**
     * Quanto spesso conviene che una puntata sia un bluff.
     *
     * Il conto: se bluffi troppo l'avversario chiama sempre, se non bluffi mai passa sempre.
     * In mezzo c'e' la frequenza che rende le sue due scelte indifferenti, e la si trova
     * imponendo che chiamare e passare valgano lo stesso. Viene puntata / (piatto + 2 x
     * puntata). Con una puntata pari al piatto fa un terzo, che e' il famoso "bluffa una
     * volta su tre"; a limite fisso i numeri sono altri, perche' la puntata e' piccola
     * rispetto al piatto: 40 dentro un piatto da 80 da' il 25%, 20 dentro un piatto da 100
     * da' il 14%.
     *
     * NON e' una costante da scegliere: e' un conto da fare sul piatto di quel momento.
     */
    fun frequenzaBluff(piatto: Int, puntata: Int): Double =
        if (puntata <= 0) 0.0 else puntata.toDouble() / (piatto + 2.0 * puntata)

    /**
     * Quanto spesso si deve CHIAMARE per non essere sfruttabili da chi bluffa: piatto /
     * (piatto + puntata).
     *
     * E' la meta' che i programmi dimenticano. Con 20 in un piatto da 100 fa l'83%: passare
     * piu' spesso di cosi' rende il bluff dell'avversario gratis. Un Banco che passa troppo
     * e' molto piu' facile da spennare di uno che bluffa un po' troppo, e "passare troppo"
     * e' esattamente quello che viene fuori da un'euristica scritta a intuito, perche'
     * sembra prudente.
     */
    fun frequenzaDifesa(piatto: Int, puntata: Int): Double =
        if (piatto + puntata <= 0) 0.0 else piatto.toDouble() / (piatto + puntata)
}
