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

    fun newGame() {
        val d = shuffledFrenchDeck()
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
