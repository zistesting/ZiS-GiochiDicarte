package com.zis.scopa

/**
 * Il piatto: come si divide e a chi va.
 *
 * KOTLIN PURO come [PokerHand] e come gli altri quattro motori, e separato dal resto del
 * gioco di proposito: e' il punto in cui il software di poker sbaglia piu' spesso, e tenerlo
 * in un oggetto che prende numeri e restituisce numeri vuol dire poterlo provare da solo,
 * su decine di migliaia di configurazioni, senza giocare una mano.
 *
 * PERCHE' ESISTONO I PIATTI LATERALI. Se tutti hanno abbastanza fiches, il piatto e' uno e
 * lo prende chi ha la mano migliore. Ma chi ha meno fiches della puntata va all'in per
 * quello che ha, e allora non e' giusto che possa vincere anche la parte che gli altri hanno
 * messo in piu' di lui: ha rischiato meno, vince meno. La regola e' che **da ogni avversario
 * si vince al massimo quanto si e' messo**. Da qui i piatti a strati: il primo contiene, per
 * ciascuno, la parte fino alla puntata del piu' corto, e ci puo' vincere chiunque sia ancora
 * in gioco; il secondo contiene la parte fra il primo e il secondo piu' corto, e ci possono
 * vincere solo quelli che erano arrivati fino a la'; e cosi' via.
 *
 * CHI HA PASSATO paga comunque. Le fiches che aveva messo prima di passare restano nei
 * piatti - sono di chi vince - ma lui non e' fra gli aventi diritto. E' l'errore piu' facile
 * di tutti: escludere il passato anche dai CONTRIBUTI, e allora le fiches spariscono.
 *
 * L'INVARIANTE che regge tutto: la somma di quello che i piatti pagano e' esattamente la
 * somma di quello che i giocatori hanno puntato. Non una fiche in piu', non una in meno,
 * nemmeno quando un piatto si divide in tre e non e' divisibile per tre. E' verificabile a
 * macchina, e in questo progetto e' verificato.
 */
object PokerPot {

    /**
     * Un piatto, principale o laterale.
     *
     * [contribuenti] sono quelli che hanno messo fiches in questo strato, [aventiDiritto]
     * quelli fra loro che possono vincerlo, cioe' che non hanno passato. Servono entrambi:
     * il secondo per assegnare, il primo per restituire nel caso di uno strato orfano (vedi
     * [assegna]) e per scrivere a schermo chi si sta giocando cosa.
     */
    class Piatto(val importo: Int, val contribuenti: List<Int>, val aventiDiritto: List<Int>) {
        override fun toString() = "$importo -> ${aventiDiritto.joinToString(",")}"
    }

    /**
     * Costruisce i piatti a strati.
     *
     * [puntato] e' quanto ciascuno ha messo in QUESTA mano, [fuori] dice chi ha passato.
     * I giocatori che non hanno messo niente non entrano da nessuna parte.
     *
     * Gli strati si ricavano dai livelli distinti di puntata, in ordine crescente: ogni
     * livello chiude uno strato alto quanto la differenza dal livello precedente e largo
     * quanti sono quelli che ci sono arrivati. Uno strato che risulti vuoto - succede se due
     * giocatori hanno puntato la stessa cifra - non viene creato.
     */
    fun costruisci(puntato: IntArray, fuori: BooleanArray): List<Piatto> {
        val livelli = puntato.filter { it > 0 }.distinct().sorted()
        val piatti = ArrayList<Piatto>(livelli.size)
        var precedente = 0
        for (livello in livelli) {
            val spessore = livello - precedente
            val contribuenti = puntato.indices.filter { puntato[it] >= livello }
            val importo = spessore * contribuenti.size
            if (importo > 0) {
                // chi ha passato ha contribuito ma non puo' vincere
                val aventi = contribuenti.filter { !fuori[it] }
                piatti.add(Piatto(importo, contribuenti, aventi))
            }
            precedente = livello
        }
        return piatti
    }

    /**
     * Assegna i piatti e restituisce quanto incassa ciascuno.
     *
     * [punteggio] e' il valore della mano di ciascuno secondo [PokerHand]: si guarda solo
     * per chi e' ancora in gioco. [primoDopoMazziere] e' l'indice da cui comincia il giro,
     * e serve ai RESTI: quando un piatto si divide fra due o tre e non e' divisibile, le
     * fiches che avanzano vanno una per volta ai vincitori partendo dal primo dopo il
     * mazziere. E' la regola dei casino', ed e' l'unico modo di non perdere per strada una
     * fiche o di inventarne una - che e' esattamente quello che l'invariante misura.
     *
     * STRATO ORFANO: se di uno strato hanno passato TUTTI i contribuenti, quelle fiches non
     * si vincono, si RESTITUISCONO a chi le aveva messe. E' la regola della puntata non
     * vista - se punti e nessuno ti chiama, la parte non chiamata torna a te - e non e' una
     * finezza: il primo tentativo qui dava quello strato "a chi era rimasto in gioco", e su
     * centomila configurazioni casuali il controllo del tetto e' saltato quattordicimila
     * volte, perche' cosi' qualcuno incassava fiches di uno strato a cui non aveva
     * contribuito. Le fiches non sparivano - l'invariante della somma non se ne accorgeva -
     * ma andavano nella tasca sbagliata.
     *
     * In una mano vera questo caso non si presenta: chi ha puntato di piu' e' l'ultimo che
     * ha rilanciato, e chi ha rilanciato per ultimo non passa. Resta gestito comunque,
     * perche' una funzione che prende numeri deve dare la risposta giusta per tutti i numeri,
     * non solo per quelli che il gioco le passera'.
     */
    fun assegna(piatti: List<Piatto>, punteggio: IntArray, primoDopoMazziere: Int,
                fuori: BooleanArray): IntArray {
        val n = punteggio.size
        val incasso = IntArray(n)

        for (piatto in piatti) {
            if (piatto.aventiDiritto.isEmpty()) {
                // strato orfano: torna ai contribuenti, in parti uguali. Lo spessore e' lo
                // stesso per tutti, quindi la divisione e' esatta e non lascia resti.
                val quota = piatto.importo / piatto.contribuenti.size
                for (c in piatto.contribuenti) incasso[c] += quota
                continue
            }
            val aventi = piatto.aventiDiritto
            val migliore = aventi.maxOf { punteggio[it] }
            val vincitori = aventi.filter { punteggio[it] == migliore }

            val quota = piatto.importo / vincitori.size
            for (v in vincitori) incasso[v] += quota

            // i resti, uno per volta, partendo dal primo dopo il mazziere
            var resto = piatto.importo - quota * vincitori.size
            var i = primoDopoMazziere
            var giri = 0
            while (resto > 0 && giri < n * 2) {
                if (i in vincitori) { incasso[i]++; resto-- }
                i = (i + 1) % n
                giri++
            }
        }
        return incasso
    }

    /**
     * Quanto al massimo puo' incassare [p] da questa mano: quello che ha messo lui, piu' da
     * ciascun avversario non piu' di quanto ha messo lui.
     *
     * Non serve al gioco: serve a dirlo a schermo ("in gioco per te: 240$") e serve come
     * controllo, perche' e' la formulazione diretta della regola dei piatti laterali e deve
     * coincidere con quello che [assegna] produce nel caso in cui p vince tutto.
     */
    fun massimoVincibile(puntato: IntArray, p: Int): Int {
        var somma = puntato[p]
        for (q in puntato.indices) if (q != p) somma += minOf(puntato[q], puntato[p])
        return somma
    }
}
