package com.zis.scopa

import android.content.Context

/**
 * Salvataggio della partita in corso, uno per gioco.
 *
 * Perche' non `onSaveInstanceState`. Il Bundle di Android copre il caso "il sistema ha
 * ucciso il processo mentre l'app era in secondo piano", e sparisce appena l'app viene
 * chiusa davvero: tolta dalle recenti, riavviato il telefono, e la partita non c'e' piu'.
 * Qui invece si scrive nelle preferenze, quindi la mano riprende anche il giorno dopo.
 *
 * Quando si salva e quando si cancella:
 *  - si salva in `onStop`, che Android garantisce di chiamare prima di poter uccidere il
 *    processo;
 *  - si cancella in `onDestroy` **solo se** l'activity sta davvero finendo, cioe' se sei
 *    uscito col pulsante Menu o col tasto indietro. Uscire e' una scelta, e chi esce non
 *    si aspetta di ritrovarsi la stessa mano la volta dopo. Chiudere l'app, invece, non
 *    passa da `onDestroy`, e li' la partita resta.
 *
 * Il formato e' volutamente elementare: sezioni separate da `|`, numeri separati da `,`
 * dentro ogni sezione. Ogni carta e' un numero da 0 a 51 (`seme * 13 + valore - 1`), i
 * posti vuoti sono -1.
 *
 * Il moltiplicatore e' 13 e non 10 perche' deve bastare anche al mazzo francese, che ha
 * tredici valori per seme. Le carte italiane arrivano al 10 e lasciano dei buchi nella
 * numerazione: non e' un problema, i numeri non devono essere contigui, devono solo essere
 * univoci e riconvertibili. Niente JSON e niente serializzazione automatica: cosi' non serve
 * aggiungere plugin o dipendenze, e il formato resta leggibile a occhio se qualcosa non
 * torna.
 *
 * Il numero di versione in testa serve a buttare via i salvataggi vecchi dopo un
 * aggiornamento che cambi i campi, invece di leggerli storti. Se il formato cambia, si
 * alza VERSION e i salvataggi precedenti vengono semplicemente ignorati: si perde una
 * partita a metà, non si va in errore.
 */
object SavedGame {

    // Alzato a 2 quando la codifica delle carte e' passata da base 10 a base 13 per fare
    // La 3 aggiunge al salvataggio del Klondike il mazzo come e' stato distribuito, che
    // serve al pulsante Ricomincia: i salvataggi della 2 hanno una sezione in meno e
    // verrebbero riletti sfalsati, quindi si buttano.
    // La 2 fece posto al mazzo francese: i salvataggi scritti con la versione 1 verrebbero riletti
    // storti, quindi vanno scartati. Si perde una partita a meta', non si va in errore.
    private const val VERSION = 3
    private const val FILE = "zis_partite"

    const val SCOPA = "scopa"
    const val BRISCOLA = "briscola"
    const val TRESETTE = "tresette"
    const val KLONDIKE = "klondike"

    private fun code(c: Card) = c.suit * 13 + (c.value - 1)
    private fun card(n: Int) = Card(n / 13, n % 13 + 1)

    class Writer {
        private val sb = StringBuilder().append(VERSION)
        fun ints(v: List<Int>) = apply { sb.append('|').append(v.joinToString(",")) }
        fun int(v: Int) = ints(listOf(v))
        fun long(v: Long) = apply { sb.append('|').append(v) }
        fun bool(v: Boolean) = int(if (v) 1 else 0)
        fun cards(v: Collection<Card>) = ints(v.map { code(it) })
        /** Elenco di carte con dei buchi: i posti vuoti diventano -1. */
        fun cardsOrNull(v: Collection<Card?>) = ints(v.map { if (it == null) -1 else code(it) })
        override fun toString() = sb.toString()
    }

    /**
     * Le sezioni si leggono nello stesso ordine in cui sono state scritte. Se il testo e'
     * troncato o incoerente qualunque metodo solleva un'eccezione: chi legge la cattura e
     * butta via il salvataggio, che e' il comportamento giusto (meglio ricominciare che
     * ripartire da uno stato a metà).
     */
    class Reader(text: String) {
        private val parts = text.split('|')
        private var i = 1                       // la posizione 0 e' la versione
        val version: Int = parts[0].toIntOrNull() ?: -1
        fun ints(): List<Int> {
            val s = parts[i++]
            return if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }
        }
        fun int(): Int = ints().first()
        fun long(): Long = parts[i++].toLong()
        fun bool(): Boolean = int() == 1
        fun cards(): MutableList<Card> = ints().map { card(it) }.toMutableList()
        fun cardsOrNull(): List<Card?> = ints().map { if (it < 0) null else card(it) }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun write(ctx: Context, key: String, w: Writer) {
        prefs(ctx).edit().putString(key, w.toString()).apply()
    }

    /** Null se non c'e' niente da riprendere, o se il salvataggio e' di una versione vecchia. */
    fun read(ctx: Context, key: String): Reader? {
        val text = prefs(ctx).getString(key, null) ?: return null
        val r = try { Reader(text) } catch (e: Exception) { null } ?: return null
        if (r.version != VERSION) { clear(ctx, key); return null }
        return r
    }

    fun clear(ctx: Context, key: String) {
        prefs(ctx).edit().remove(key).apply()
    }

    /** Come Prefs.flush: scrive subito e aspetta. Serve solo prima di chiudere il processo. */
    fun flush(ctx: Context) {
        @Suppress("ApplySharedPref")
        prefs(ctx).edit().commit()
    }
}
