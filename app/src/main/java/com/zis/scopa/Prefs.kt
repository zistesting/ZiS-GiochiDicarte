package com.zis.scopa

import android.content.Context

object Prefs {
    private const val FILE = "zis_giochi_prefs"

    /** Durata della pausa obbligatoria fra una partita e l'altra. */
    const val PAUSE_MS = 60_000L

    /** Quanto dura la disattivazione della pausa prima che si riaccenda da sola. */
    const val PAUSE_OFF_MS = 60L * 60L * 1000L

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun scoreTarget(ctx: Context): Int = p(ctx).getInt("score_target", 11)

    fun setScoreTarget(ctx: Context, value: Int) {
        p(ctx).edit().putInt("score_target", value).apply()
    }

    /** Briscola match: number of games (hands) a player must win to take the match (5 or 11). */
    fun briscolaTarget(ctx: Context): Int = p(ctx).getInt("briscola_target", 5)

    fun setBriscolaTarget(ctx: Context, value: Int) {
        p(ctx).edit().putInt("briscola_target", value).apply()
    }

    // ---------------- statistiche ----------------

    const val GAME_SCOPA = "scopa"
    const val GAME_BRISCOLA = "briscola"
    const val GAME_TRESETTE = "tresette"

    /**
     * Il Klondike sta nelle statistiche come gli altri, ma "vinta" e "persa" vogliono dire
     * un'altra cosa: non c'e' un avversario che vince quando perdi tu. Una partita si conta
     * come persa quando la abbandoni distribuendo di nuovo dopo aver fatto almeno una mossa.
     * Uscire dal gioco non conta niente, perche' la partita resta li' e la riprendi.
     */
    const val GAME_KLONDIKE = "klondike"

    /**
     * Registra una partita conclusa. Si contano solo le vittorie, da una parte o dall'altra:
     * le partite giocate sono la loro somma. Tenere un terzo contatore separato vorrebbe dire
     * poterlo veder divergere dagli altri due, e non ci sarebbe modo di sapere quale dei tre
     * ha ragione. Le partite abbandonate a meta' non si contano, perche' non hanno un esito.
     */
    fun recordMatch(ctx: Context, game: String, youWon: Boolean) {
        val key = "stats_${game}_" + if (youWon) "you" else "bot"
        val pr = p(ctx)
        pr.edit().putInt(key, pr.getInt(key, 0) + 1).apply()
    }

    /**
     * Annulla l'ultima registrazione. Serve quando, a partita finita, l'utente rigioca
     * l'ultima giocata: la partita torna aperta e il suo esito non e' piu' quello segnato.
     */
    fun unrecordMatch(ctx: Context, game: String, youWon: Boolean) {
        val key = "stats_${game}_" + if (youWon) "you" else "bot"
        val pr = p(ctx)
        pr.edit().putInt(key, maxOf(0, pr.getInt(key, 0) - 1)).apply()
    }

    fun wonBy(ctx: Context, game: String, you: Boolean): Int =
        p(ctx).getInt("stats_${game}_" + if (you) "you" else "bot", 0)

    fun clearStats(ctx: Context) {
        val e = p(ctx).edit()
        for (g in listOf(GAME_SCOPA, GAME_BRISCOLA, GAME_TRESETTE, GAME_KLONDIKE)) {
            e.remove("stats_${g}_you"); e.remove("stats_${g}_bot")
        }
        e.apply()
    }

    /** Secondi per cui la carta pescata resta scoperta a Tresette: 1, 2 o 3. */
    fun drawShowSeconds(ctx: Context): Int = p(ctx).getInt("tre_draw_seconds", 2)

    fun setDrawShowSeconds(ctx: Context, value: Int) {
        p(ctx).edit().putInt("tre_draw_seconds", value).apply()
    }

    /** Punti a cui si vince l'incontro di Tresette: 21 (breve) o 31 (classico). */
    fun tresetteTarget(ctx: Context): Int = p(ctx).getInt("tresette_target", 21)

    fun setTresetteTarget(ctx: Context, value: Int) {
        p(ctx).edit().putInt("tresette_target", value).apply()
    }

    // ---------------- mazzo ----------------

    /**
     * Mazzo scelto. E' la chiave con cui Decks.kt sceglie quale delle tre tabelle di immagini
     * usare: DECK_ZIS per le illustrazioni ZiS, DECK_TRAD per le piacentine, DECK_BERG per
     * le bergamasche, DECK_NAP per le napoletane.
     */
    const val DECK_ZIS = "card"
    const val DECK_TRAD = "trad"
    const val DECK_BERG = "berg"
    const val DECK_NAP = "nap"

    /**
     * Il mazzo francese non e' una scelta: e' l'unico che il Klondike puo' usare, e i tre
     * giochi italiani non possono usarlo. Per questo non compare fra i pulsanti delle
     * impostazioni, dove starebbe come un'opzione che a seconda del gioco non fa niente o
     * rompe tutto.
     */
    const val DECK_FR = "fr"

    fun deck(ctx: Context): String = p(ctx).getString("deck", DECK_ZIS) ?: DECK_ZIS

    fun setDeck(ctx: Context, value: String) {
        p(ctx).edit().putString("deck", value).apply()
    }

    // ---------------- gioco automatico (test) ----------------

    /** Se attivo il programma gioca da solo entrambe le mani: serve a provare in fretta. */
    fun autoPlay(ctx: Context): Boolean = p(ctx).getBoolean("auto_play", false)

    fun setAutoPlay(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean("auto_play", value).apply()
    }

    /** Mostra scoperte le carte del Banco: serve a verificare la logica di gioco. */
    fun showBotCards(ctx: Context): Boolean = p(ctx).getBoolean("show_bot_cards", false)

    fun setShowBotCards(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean("show_bot_cards", value).apply()
    }

    // ---------------- pausa responsabile ----------------

    /**
     * Attiva di serie. Si puo' spegnere dalle impostazioni, ma la disattivazione scade
     * dopo un'ora: passato quel tempo la pausa torna accesa da sola.
     */
    fun pauseEnabled(ctx: Context): Boolean {
        val pr = p(ctx)
        if (pr.getBoolean("pause_enabled", true)) return true
        val offAt = pr.getLong("pause_off_at", 0L)
        val elapsed = System.currentTimeMillis() - offAt
        if (offAt <= 0L || elapsed < 0 || elapsed >= PAUSE_OFF_MS) {
            // scaduta (o orologio spostato indietro): riaccendo e ripulisco
            pr.edit().putBoolean("pause_enabled", true).remove("pause_off_at").apply()
            return true
        }
        return false
    }

    fun setPauseEnabled(ctx: Context, value: Boolean) {
        val e = p(ctx).edit().putBoolean("pause_enabled", value)
        if (value) e.remove("pause_off_at") else e.putLong("pause_off_at", System.currentTimeMillis())
        e.apply()
    }

    /** Millisecondi che mancano alla riaccensione automatica; 0 se la pausa e' gia' attiva. */
    fun pauseOffRemaining(ctx: Context): Long {
        if (pauseEnabled(ctx)) return 0
        val offAt = p(ctx).getLong("pause_off_at", 0L)
        return (PAUSE_OFF_MS - (System.currentTimeMillis() - offAt)).coerceIn(0L, PAUSE_OFF_MS)
    }

    fun markMatchEnded(ctx: Context) {
        p(ctx).edit().putLong("last_match_end", System.currentTimeMillis()).apply()
    }

    /** Istante dell'ultima fine partita (0 se nessuna): si legge prima di [markMatchEnded] per poterlo rimettere. */
    fun lastMatchEnd(ctx: Context): Long = p(ctx).getLong("last_match_end", 0L)

    /** Rimette il valore letto con [lastMatchEnd]: annulla un [markMatchEnded] quando la partita viene riaperta. */
    fun setLastMatchEnd(ctx: Context, value: Long) {
        val e = p(ctx).edit()
        if (value <= 0L) e.remove("last_match_end") else e.putLong("last_match_end", value)
        e.apply()
    }

    /**
     * Millisecondi che mancano prima di poter iniziare una nuova partita.
     * Vale 0 se la pausa e' disattivata, se non e' ancora finita nessuna partita
     * o se il minuto e' gia' trascorso. Il conto si basa sull'orologio di sistema,
     * quindi resta valido anche se l'utente chiude e riapre l'app.
     */
    fun pauseRemaining(ctx: Context): Long {
        if (!pauseEnabled(ctx)) return 0
        val last = p(ctx).getLong("last_match_end", 0L)
        if (last <= 0L) return 0
        val elapsed = System.currentTimeMillis() - last
        if (elapsed < 0) return 0                       // orologio spostato indietro
        return (PAUSE_MS - elapsed).coerceIn(0L, PAUSE_MS)
    }

    /**
     * Forza la scrittura su disco di tutto quello che e' ancora in sospeso.
     *
     * Tutti i metodi qui sopra usano apply(), che aggiorna subito la memoria e rimanda il
     * file a dopo. Va benissimo nel funzionamento normale, perche' Android completa quelle
     * scritture quando l'ultima activity si ferma. Non va bene se il processo viene chiuso a
     * mano: li' nessuno aspetta piu' nessuno, e l'ultima impostazione toccata potrebbe non
     * arrivare mai al file.
     *
     * Il commit() a vuoto non aggiunge niente, ma scrive l'intera mappa in memoria (che
     * contiene gia' tutte le modifiche fatte con apply) e ritorna solo a scrittura finita.
     * Serve solo al pulsante Esci: nel resto dell'app non va chiamato, perche' bloccherebbe
     * il thread principale sull'accesso al disco.
     */
    fun flush(ctx: Context) {
        @Suppress("ApplySharedPref")
        p(ctx).edit().commit()
    }

    // ---------------- Klondike ----------------

    /**
     * Quante carte si voltano dal tallone: una o tre.
     *
     * Non e' un dettaglio, e' il parametro che decide quanto e' duro il gioco. Pescando a
     * tre, due terzi del tallone non passano mai in cima nel giro in corso, e le smazzate
     * impossibili passano da circa una su undici a circa una su cinque. Il valore
     * predefinito e' UNA proprio per questo: e' la regola piu' gentile, e chi vuole quella
     * classica del solitario di Windows la sceglie.
     */
    fun klondikeDraw(ctx: Context): Int = p(ctx).getInt("klondike_draw", 1)

    fun setKlondikeDraw(ctx: Context, n: Int) {
        p(ctx).edit().putInt("klondike_draw", n).apply()
    }
}
