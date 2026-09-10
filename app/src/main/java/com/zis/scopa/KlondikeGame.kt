package com.zis.scopa

/**
 * Il solitario Klondike: solo le regole, niente schermo.
 *
 * E' il primo gioco dell'app senza avversario, e questo cambia piu' cose di quante sembri.
 * Non c'e' nessuna euristica, nessuna ricerca, nessun budget di nodi: meta' del lavoro fatto
 * per Scopa, Briscola e Tresette qui non si applica. In compenso servono due cose che gli
 * altri tre non hanno mai avuto: l'ANNULLA, perche' un solitario senza annulla e' una
 * punizione, e il MOVIMENTO DI GRUPPO, perche' si spostano sequenze di carte e non una
 * carta per volta.
 *
 * REGOLE
 *  - Sette colonne, la prima con una carta, la settima con sette. Di ogni colonna e' scoperta
 *    solo l'ultima carta. Le 24 che restano formano il tallone.
 *  - Le colonne si costruiscono a scendere e a COLORI ALTERNATI: sul 9 rosso va l'8 nero.
 *  - Una colonna vuota accetta SOLO il Re. E' la regola piu' severa del gioco ed e' la
 *    ragione principale per cui certe smazzate non si possono vincere: la risorsa piu'
 *    preziosa, lo spazio libero, e' utilizzabile da quattro carte su cinquantadue.
 *  - Le fondazioni si costruiscono a salire per seme, dall'Asso al Re.
 *  - Dal tallone si voltano una o tre carte per volta, a scelta. La differenza non e'
 *    estetica: pescando a tre, due terzi del tallone non passano mai in cima nel giro in
 *    corso, e le smazzate impossibili passano da circa una su undici a circa una su cinque.
 *
 * Il numero di carte scoperte non e' un parametro qualsiasi, e' quello che decide quanto e'
 * duro il gioco. Sta nel costruttore proprio per questo.
 */
/**
 * Toglie e restituisce l'ultima carta.
 *
 * Perche' non `removeLast()`. Kotlin ha da sempre un'estensione `MutableList.removeLast()`,
 * ma Java 21 ha aggiunto un METODO con lo stesso nome a java.util.List, arrivato su Android
 * solo con l'API 35. Compilando contro l'SDK 36, il compilatore risolve la chiamata sul
 * metodo Java invece che sull'estensione Kotlin: il codice compila senza un fiato e poi si
 * pianta con NoSuchMethodError su qualunque telefono sotto Android 15, che con minSdk 24 vuol
 * dire quasi tutti. E' una trappola silenziosa, e si evita scrivendo la stessa cosa in un
 * modo che non ha omonimi.
 *
 * Gli altri tre giochi non ne soffrono: usano ArrayDeque, che ha metodi propri con quel nome.
 */
private fun <T> MutableList<T>.togliUltima(): T = removeAt(size - 1)

class KlondikeGame(val drawCount: Int = 1) {

    /**
     * Una colonna del tavolo, divisa in coperte e scoperte.
     *
     * Tenere due liste separate invece di una lista piu' un contatore di quante sono girate
     * evita l'errore piu' facile di tutto il gioco: sbagliare l'indice che divide le due
     * parti e ritrovarsi a spostare una carta che il giocatore non ha ancora visto.
     *
     * Le carte in [shown] formano SEMPRE una sequenza valida a scendere e a colori alternati.
     * Non e' una speranza, e' una conseguenza: una carta ci arriva solo se ci sta, e quando
     * se ne scopre una nuova quella diventa l'unica scoperta. Per questo un gruppo da
     * spostare e' semplicemente una parte finale di [shown], senza doverla ricontrollare.
     */
    class Column {
        val hidden = mutableListOf<Card>()
        val shown = mutableListOf<Card>()

        val isEmpty: Boolean get() = hidden.isEmpty() && shown.isEmpty()
        val top: Card? get() = shown.lastOrNull()
        val size: Int get() = hidden.size + shown.size
    }

    /**
     * Le mosse possibili. Sono un tipo chiuso e non stringhe o numeri perche' servono a tre
     * cose diverse - eseguirle, elencarle per il suggerimento, ripercorrerle per l'annulla -
     * e in tutte e tre il compilatore deve poter dire se ne manca una.
     */
    sealed class Move {
        /** Volta [drawCount] carte dal tallone agli scarti. */
        object Draw : Move()
        /** Rimette gli scarti nel tallone, nello stesso ordine di prima. */
        object Recycle : Move()
        data class WasteToColumn(val col: Int) : Move()
        data class WasteToFoundation(val suit: Int) : Move()
        data class ColumnToFoundation(val col: Int, val suit: Int) : Move()
        /** Sposta le ultime [count] carte scoperte di [from] su [to]. */
        data class ColumnToColumn(val from: Int, val count: Int, val to: Int) : Move()
        /** Riporta in gioco una carta gia' salita in fondazione. */
        data class FoundationToColumn(val suit: Int, val col: Int) : Move()
    }

    val stock = mutableListOf<Card>()          // coperte; l'ULTIMA e' la prossima da voltare
    val waste = mutableListOf<Card>()          // scoperte; l'ultima e' quella in cima
    val foundations = Array(4) { mutableListOf<Card>() }
    val tableau = Array(7) { Column() }

    var moves = 0; private set
    var passes = 0; private set                // quante volte il tallone e' stato rigirato

    /** Vinta quando tutte e quattro le fondazioni sono complete dall'Asso al Re. */
    val finished: Boolean get() = foundations.all { it.size == 13 }

    // ---------------------------------------------------------------- distribuzione

    /**
     * Il mazzo come e' stato distribuito, nell'ordine in cui e' uscito dal mescolamento.
     *
     * Serve a [ricomincia], e va salvato con la partita: senza, riprendendo una partita da
     * un'altra sessione il pulsante "Rigioca" non avrebbe piu' niente da rimettere a posto.
     *
     * NON sta dentro save(): quella la usa anche snapshot() per la pila dell'annulla, e
     * cinquantadue carte in piu' per ognuna delle cento fotografie sarebbero quindici
     * chilobyte di storia per un dato che non cambia mai. Lo scrive l'activity, accanto al
     * numero di carte da pescare.
     */
    val mazzoIniziale: List<Card> get() = _mazzoIniziale
    private val _mazzoIniziale = mutableListOf<Card>()

    fun caricaMazzoIniziale(d: List<Card>) {
        // la copia prima di svuotare non e' pignoleria: distribuisci() chiama questa con la
        // lista che sta per leggere, e se un giorno qualcuno le passasse _mazzoIniziale
        // stesso, clear() la svuoterebbe e addAll() non aggiungerebbe niente
        val copia = d.toList()
        _mazzoIniziale.clear()
        _mazzoIniziale.addAll(copia)
    }

    fun newGame() = distribuisci(shuffledFrenchDeck())

    /**
     * Rimette le carte come all'inizio della smazzata in corso.
     *
     * Non e' una comodita': e' il complemento del fatto che una smazzata su undici e'
     * impossibile e che il giocatore non vede le ventuno carte coperte. Anche in una
     * smazzata risolvibile si puo' fare presto una scelta irreversibile, e l'annulla si
     * ferma a cento mosse. Con questo, una smazzata persa per una mossa sbagliata si puo'
     * riprovare invece di essere buttata via.
     */
    fun ricomincia(): Boolean {
        if (_mazzoIniziale.size != 52) return false
        distribuisci(_mazzoIniziale.toMutableList())
        return true
    }

    private fun distribuisci(d: MutableList<Card>) {
        caricaMazzoIniziale(d)
        stock.clear(); waste.clear()
        for (f in foundations) f.clear()
        for (c in tableau) { c.hidden.clear(); c.shown.clear() }
        undoStack.clear()
        moves = 0; passes = 0

        var k = 0
        for (col in 0..6) {
            for (r in 0..col) {
                val card = d[k++]
                if (r == col) tableau[col].shown.add(card) else tableau[col].hidden.add(card)
            }
        }
        // Il resto forma il tallone. Va rovesciato perche' qui la cima e' l'ultimo elemento:
        // cosi' la prima carta voltata e' la prima delle rimanenti, come distribuendo a mano.
        while (k < d.size) stock.add(d[k++])
        stock.reverse()
    }

    // ---------------------------------------------------------------- regole di appoggio

    /** Sulla colonna: a scendere e a colori alternati, e su una colonna vuota solo il Re. */
    fun canPlaceOnColumn(card: Card, col: Int): Boolean {
        val t = tableau[col].top ?: return card.value == 13
        return card.value == t.value - 1 && card.isRed != t.isRed
    }

    /** In fondazione: a salire per seme. La prima e' l'Asso, cioe' quando la pila e' vuota. */
    fun canPlaceOnFoundation(card: Card): Boolean =
        card.value == foundations[card.suit].size + 1

    // ---------------------------------------------------------------- mosse

    /** Tutte le mosse legali nella posizione attuale, comprese quelle inutili. */
    fun legalMoves(): List<Move> {
        val out = mutableListOf<Move>()

        if (stock.isNotEmpty()) out.add(Move.Draw)
        else if (waste.isNotEmpty()) out.add(Move.Recycle)

        waste.lastOrNull()?.let { w ->
            if (canPlaceOnFoundation(w)) out.add(Move.WasteToFoundation(w.suit))
            for (c in 0..6) if (canPlaceOnColumn(w, c)) out.add(Move.WasteToColumn(c))
        }

        for (from in 0..6) {
            val col = tableau[from]
            col.top?.let { t ->
                if (canPlaceOnFoundation(t)) out.add(Move.ColumnToFoundation(from, t.suit))
            }
            // Ogni parte finale delle scoperte e' un gruppo spostabile: basta che la sua
            // prima carta stia sulla colonna di destinazione.
            for (count in 1..col.shown.size) {
                val testa = col.shown[col.shown.size - count]
                for (to in 0..6) {
                    if (to == from) continue
                    // spostare un gruppo intero su una colonna vuota, quando la colonna di
                    // partenza non ha carte coperte sotto, e' un giro a vuoto: si sposta
                    // tutta la colonna da una casella all'altra senza cambiare niente
                    if (tableau[to].isEmpty && count == col.shown.size && col.hidden.isEmpty()) continue
                    if (canPlaceOnColumn(testa, to)) out.add(Move.ColumnToColumn(from, count, to))
                }
            }
        }

        // Riportare giu' una carta dalla fondazione serve piu' spesso di quanto si creda:
        // a volte quel 5 rosso e' l'unico appoggio possibile per un 4 nero.
        for (s in 0..3) {
            val f = foundations[s].lastOrNull() ?: continue
            for (c in 0..6) if (canPlaceOnColumn(f, c)) out.add(Move.FoundationToColumn(s, c))
        }
        return out
    }

    /** Esegue la mossa. Restituisce false, senza toccare niente, se non e' legale. */
    fun play(m: Move): Boolean {
        if (!isLegal(m)) return false
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()

        when (m) {
            is Move.Draw -> repeat(drawCount) { if (stock.isNotEmpty()) waste.add(stock.togliUltima()) }
            is Move.Recycle -> { while (waste.isNotEmpty()) stock.add(waste.togliUltima()); passes++ }
            is Move.WasteToFoundation -> foundations[m.suit].add(waste.togliUltima())
            is Move.WasteToColumn -> tableau[m.col].shown.add(waste.togliUltima())
            is Move.ColumnToFoundation -> {
                foundations[m.suit].add(tableau[m.col].shown.togliUltima())
                scopri(m.col)
            }
            is Move.ColumnToColumn -> {
                val src = tableau[m.from].shown
                // .toList() e' una copia, non una vista: subito dopo si tolgono le carte da
                // src, e una vista su una lista che cambia sotto e' un guaio che si vede solo
                // ogni tanto
                val gruppo = src.subList(src.size - m.count, src.size).toList()
                tableau[m.to].shown.addAll(gruppo)
                repeat(m.count) { src.removeAt(src.size - 1) }
                scopri(m.from)
            }
            is Move.FoundationToColumn -> tableau[m.col].shown.add(foundations[m.suit].togliUltima())
        }
        moves++
        return true
    }

    fun isLegal(m: Move): Boolean = when (m) {
        is Move.Draw -> stock.isNotEmpty()
        is Move.Recycle -> stock.isEmpty() && waste.isNotEmpty()
        is Move.WasteToFoundation ->
            waste.lastOrNull()?.let { it.suit == m.suit && canPlaceOnFoundation(it) } ?: false
        is Move.WasteToColumn ->
            waste.lastOrNull()?.let { canPlaceOnColumn(it, m.col) } ?: false
        is Move.ColumnToFoundation ->
            tableau[m.col].top?.let { it.suit == m.suit && canPlaceOnFoundation(it) } ?: false
        is Move.ColumnToColumn -> {
            val src = tableau[m.from].shown
            m.from != m.to && m.count in 1..src.size &&
                canPlaceOnColumn(src[src.size - m.count], m.to)
        }
        is Move.FoundationToColumn ->
            foundations[m.suit].lastOrNull()?.let { canPlaceOnColumn(it, m.col) } ?: false
    }

    /** Dopo aver tolto l'ultima scoperta, se sotto c'e' una coperta la si volta. */
    private fun scopri(col: Int) {
        val c = tableau[col]
        if (c.shown.isEmpty() && c.hidden.isNotEmpty()) c.shown.add(c.hidden.togliUltima())
    }

    // ---------------------------------------------------------------- aiuti per l'interfaccia

    /**
     * Dove finisce la carta in cima agli scarti, se la tocchi.
     *
     * Serve al gioco a tocchi: tocchi la carta e va dove ha senso, senza trascinamenti. E'
     * la scelta che tiene il Klondike coerente col resto dell'app, dove tutto si fa toccando.
     */
    fun autoTargetFromWaste(): Move? {
        val c = waste.lastOrNull() ?: return null
        if (canPlaceOnFoundation(c)) return Move.WasteToFoundation(c.suit)
        return miglioreColonna(c, -1)?.let { Move.WasteToColumn(it) }
    }

    /**
     * Dove finisce il gruppo che parte dalla [index]esima carta scoperta di [col].
     *
     * La fondazione si propone solo per una carta singola in cima: un gruppo non ci puo'
     * salire, e proporre una destinazione impossibile confonde e basta.
     */
    fun autoTargetFromColumn(col: Int, index: Int): Move? {
        val shown = tableau[col].shown
        if (index !in shown.indices) return null
        val count = shown.size - index
        val testa = shown[index]
        if (count == 1 && canPlaceOnFoundation(testa)) return Move.ColumnToFoundation(col, testa.suit)
        return miglioreColonna(testa, col)?.let { Move.ColumnToColumn(col, count, it) }
    }

    /**
     * Dove torna la carta in cima alla fondazione [suit], se la tocchi.
     *
     * Riportarla giu' e' legale e ogni tanto e' l'unica mossa che salva la partita: a volte
     * quel 5 rosso e' il solo appoggio possibile per un 4 nero. E' anche l'unica mossa che
     * fa tornare indietro il punteggio, quindi si propone solo se una colonna la accetta
     * davvero.
     */
    fun autoTargetFromFoundation(suit: Int): Move? {
        val c = foundations[suit].lastOrNull() ?: return null
        return miglioreColonna(c, -1)?.let { Move.FoundationToColumn(suit, it) }
    }

    /**
     * Fra le colonne dove la carta ci sta, la migliore.
     *
     * L'ordine di preferenza non e' arbitrario. Prima una colonna con carte sopra, perche'
     * appoggiare li' non consuma niente. Poi una colonna vuota, che si spende malvolentieri:
     * uno spazio libero e' la risorsa piu' preziosa del Klondike e riempirlo con un Re e'
     * una decisione, non un ripiego automatico.
     */
    private fun miglioreColonna(c: Card, escludi: Int): Int? {
        var vuota: Int? = null
        for (to in 0..6) {
            if (to == escludi || !canPlaceOnColumn(c, to)) continue
            if (tableau[to].isEmpty) { if (vuota == null) vuota = to } else return to
        }
        return vuota
    }

    /**
     * Le carte si possono ancora muovere?
     *
     * E' una risposta PRUDENTE per costruzione: dice di si' anche in posizioni che un
     * solutore dichiarerebbe perse. Sapere davvero se una smazzata di Klondike e' ancora
     * vincibile costa una ricerca esaustiva, e sbagliare dicendo "hai perso" a chi invece
     * poteva vincere e' l'errore da non fare mai. Qui si dice di no solo quando non resta
     * proprio niente da toccare.
     */
    fun hasAnyMove(): Boolean = legalMoves().isNotEmpty()

    /**
     * Vero se mandare [c] in fondazione non puo' piu' servire a nessuno in colonna.
     *
     * Una carta in fondazione non torna piu' giu' da sola, e a volte serve come appoggio:
     * quel 5 rosso puo' essere l'unico posto dove mettere un 4 nero.
     *
     * La regola classica, la stessa che usa verifica/solutore_klondike.py: assi e due sempre;
     * per il resto un valore v e' al sicuro se le due fondazioni del colore OPPOSTO sono
     * arrivate almeno a v-1 e l'altra dello STESSO colore almeno a v-2. La prima condizione
     * dice che nessuna carta del colore opposto avra' bisogno di appoggiarsi su questa; la
     * seconda copre il passo dopo, la carta di valore v-2 dello stesso colore che avrebbe
     * bisogno di una v-1 del colore opposto che a sua volta avrebbe bisogno di questa.
     */
    private fun sicuraInFondazione(c: Card): Boolean {
        if (c.value <= 2) return true
        // semi: 0 cuori, 1 quadri (rossi), 2 fiori, 3 picche (neri)
        val opposti = if (c.isRed) intArrayOf(2, 3) else intArrayOf(0, 1)
        val stesso = if (c.isRed) 1 - c.suit else 5 - c.suit
        return opposti.all { foundations[it].size >= c.value - 1 } &&
            foundations[stesso].size >= c.value - 2
    }

    /**
     * Vero se qualche carta del tallone o degli scarti puo' finire da qualche parte.
     *
     * Se nessuna delle ventiquattro carte che girano ha un posto ne' in fondazione ne' su
     * una colonna, continuare a voltare non cambiera' niente per definizione, e il gioco
     * automatico puo' fermarsi subito invece di sfogliare il tallone due volte per scoprirlo.
     * Non e' il contrario: che una carta abbia un posto non vuol dire che ci arrivera',
     * perche' pescando a tre non tutte passano in cima. Per quel caso serve ancora il conto
     * dei giri a vuoto, che sta nell'activity.
     */
    private fun girareServe(): Boolean {
        for (c in stock) {
            if (canPlaceOnFoundation(c)) return true
            for (col in 0..6) if (canPlaceOnColumn(c, col)) return true
        }
        for (c in waste) {
            if (canPlaceOnFoundation(c)) return true
            for (col in 0..6) if (canPlaceOnColumn(c, col)) return true
        }
        return false
    }

    /**
     * Una mossa scelta dal programma, per il gioco automatico delle impostazioni.
     *
     * NON e' un risolutore, e non prova a esserlo: e' quello che serve alla voce "gioco
     * automatico", cioe' provare in fretta che animazioni, annulla, vittoria e statistiche
     * funzionino. Vince meno spesso di una persona attenta, perche' non riordina il tavolo
     * per costruire sequenze: sposta una carta da colonna a colonna solo quando quel
     * movimento SCOPRE una carta coperta.
     *
     * Quella regola non e' pigrizia, e' l'antidoto ai giri a vuoto. Uno spostamento fra
     * colonne che non scopre niente e' reversibile, e due mosse reversibili bastano a far
     * girare in tondo per sempre un giocatore automatico. Per lo stesso motivo non riporta
     * mai giu' una carta dalla fondazione: legale, a volte utile, e la strada piu' breve
     * per un rimpallo infinito fra fondazione e colonna.
     *
     * L'ordine delle priorita' e' quello classico, e ogni gradino sotto al primo esiste
     * perche' il precedente non ha trovato niente.
     */
    fun mossaAutomatica(): Move? {
        if (finished) return null
        val mosse = legalMoves()

        // 1. in fondazione, ma solo quando e' al sicuro
        for (m in mosse) {
            val c = when (m) {
                is Move.WasteToFoundation -> waste.lastOrNull()
                is Move.ColumnToFoundation -> tableau[m.col].top
                else -> null
            } ?: continue
            if (sicuraInFondazione(c)) return m
        }

        // 2. qualunque mossa che scopra una carta coperta: e' il vero progresso del gioco
        mosse.firstOrNull { m ->
            m is Move.ColumnToColumn &&
                m.count == tableau[m.from].shown.size && tableau[m.from].hidden.isNotEmpty()
        }?.let { return it }
        mosse.firstOrNull { m ->
            m is Move.ColumnToFoundation &&
                tableau[m.col].shown.size == 1 && tableau[m.col].hidden.isNotEmpty()
        }?.let { return it }

        // 3. un Re su una colonna vuota: la apre, e su una colonna vuota ci va solo un Re
        mosse.firstOrNull { m ->
            (m is Move.WasteToColumn && tableau[m.col].isEmpty) ||
                (m is Move.ColumnToColumn && tableau[m.to].isEmpty)
        }?.let { return it }

        // 4. svuotare una colonna, spostando TUTTE le sue scoperte su un'altra.
        //
        // Vale il quadruplo di tutto il resto: da sola porta le vittorie dal 9% al 32%
        // pescando una carta alla volta (misurato su 1500 smazzate col motore in
        // verifica/). Il motivo e' che una colonna vuota e' l'unica cosa che accoglie un Re,
        // e i Re bloccati sono la ragione per cui le smazzate si piantano.
        //
        // E' anche l'UNICA mossa fra colonne che non scopre niente e che qui si accetta,
        // perche' e' l'unica che non si puo' disfare: la colonna resta vuota e su una
        // colonna vuota puo' salire solo un Re, mentre la testa del gruppo appena spostato
        // un Re non e' - se lo fosse, non sarebbe stata su nessuna carta. E ogni volta che
        // scatta il numero di colonne occupate scende di uno, quindi non puo' scattare piu'
        // di sette volte.
        mosse.firstOrNull { m ->
            m is Move.ColumnToColumn && m.count == tableau[m.from].shown.size &&
                tableau[m.from].hidden.isEmpty() && !tableau[m.to].isEmpty
        }?.let { return it }

        // 5. dagli scarti al tavolo: fa scorrere il tallone
        mosse.firstOrNull { it is Move.WasteToColumn }?.let { return it }

        // 6. voltare, ma solo se girare puo' servire a qualcosa
        if (mosse.contains(Move.Draw) && girareServe()) return Move.Draw

        // 7. in fondazione anche se non e' al sicuro: a questo punto e' l'unica cosa che
        //    cambia qualcosa
        mosse.firstOrNull {
            it is Move.WasteToFoundation || it is Move.ColumnToFoundation
        }?.let { return it }

        // 8. rigirare il tallone, alle stesse condizioni del punto 6
        if (mosse.contains(Move.Recycle) && girareServe()) return Move.Recycle

        return null
    }

    /**
     * Vero quando la partita e' ormai vinta e resta solo da salire.
     *
     * Se non ci sono piu' carte coperte e il tallone e' finito, ogni carta e' visibile e
     * raggiungibile: da qui in poi non ci sono piu' scelte da fare, solo mosse da eseguire.
     * E' il momento di offrire il completamento automatico invece di far cliccare
     * cinquantadue volte.
     */
    fun canAutoFinish(): Boolean =
        !finished && stock.isEmpty() && waste.isEmpty() && tableau.all { it.hidden.isEmpty() }

    /** Una mossa verso la fondazione, se ce n'e' una. Serve al completamento automatico. */
    fun nextAutoMove(): Move? {
        waste.lastOrNull()?.let { if (canPlaceOnFoundation(it)) return Move.WasteToFoundation(it.suit) }
        for (c in 0..6) tableau[c].top?.let {
            if (canPlaceOnFoundation(it)) return Move.ColumnToFoundation(c, it.suit)
        }
        return null
    }

    // ---------------------------------------------------------------- annulla

    /**
     * L'annulla tiene una fotografia completa per mossa, non la mossa da rifare al contrario.
     *
     * Rifare al contrario sembra piu' elegante e costa meno memoria, ma ha una trappola: una
     * mossa che scopre una carta non e' reversibile da sola, perche' rimettendo giu' la carta
     * bisogna anche ricordarsi di ricoprire quella sotto. Con le fotografie il problema non
     * esiste. Una fotografia sono cinquantadue numeri: cento mosse di storia stanno in pochi
     * chilobyte, e in cambio l'annulla e' giusto per costruzione.
     */
    private val undoStack = ArrayDeque<String>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        restore(SavedGame.Reader(prev))
        return true
    }

    // ---------------------------------------------------------------- salvataggio

    private fun snapshot(): String {
        val w = SavedGame.Writer()
        save(w)
        return w.toString()
    }

    fun save(w: SavedGame.Writer) {
        w.cards(stock); w.cards(waste)
        for (f in foundations) w.cards(f)
        for (c in tableau) { w.cards(c.hidden); w.cards(c.shown) }
        w.ints(listOf(moves, passes))
    }

    fun load(r: SavedGame.Reader) {
        restore(r)
        undoStack.clear()      // la storia dell'annulla non si salva: vedi il commento sotto
    }

    private fun restore(r: SavedGame.Reader) {
        stock.clear(); stock.addAll(r.cards())
        waste.clear(); waste.addAll(r.cards())
        for (f in foundations) { f.clear(); f.addAll(r.cards()) }
        for (c in tableau) {
            c.hidden.clear(); c.hidden.addAll(r.cards())
            c.shown.clear();  c.shown.addAll(r.cards())
        }
        val m = r.ints()
        moves = m[0]; passes = m[1]
    }

    companion object {
        /**
         * Quante mosse si possono annullare.
         *
         * Non e' un limite di memoria - cento fotografie sono pochi chilobyte - ma una scelta:
         * oltre questa soglia l'annulla non serve piu' a correggere un errore, serve a rigiocare
         * la partita da capo, e per quello c'e' il pulsante che ridistribuisce.
         *
         * La storia dell'annulla NON viene salvata quando si chiude l'app: si riprende la
         * partita dov'era, ma non si puo' tornare indietro a prima della chiusura. Salvare
         * anche cento fotografie farebbe crescere il salvataggio di cento volte per una cosa
         * che, riaprendo l'app il giorno dopo, nessuno si aspetta di poter fare.
         */
        const val MAX_UNDO = 100
    }
}
