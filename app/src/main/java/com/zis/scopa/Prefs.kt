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

    // ---------------- mazzo ----------------

    /**
     * Prefisso dei file delle carte. Le immagini si chiamano <prefisso>_seme_valore, quindi
     * cambiare mazzo vuol dire cambiare prefisso: card_0_1 oppure trad_0_1.
     */
    /** Punti a cui si vince l'incontro di Tresette: 21 (breve) o 31 (classico). */
    fun tresetteTarget(ctx: Context): Int = p(ctx).getInt("tresette_target", 21)

    fun setTresetteTarget(ctx: Context, value: Int) {
        p(ctx).edit().putInt("tresette_target", value).apply()
    }

    const val DECK_ZIS = "card"
    const val DECK_TRAD = "trad"

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
     * Attiva di serie. Si disattiva solo con la password e, comunque, la disattivazione
     * scade dopo un'ora: passato quel tempo la pausa torna accesa da sola.
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
}
