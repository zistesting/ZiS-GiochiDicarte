package com.zis.scopa

import android.content.Context

object Prefs {
    private const val FILE = "zis_giochi_prefs"

    /** Durata della pausa obbligatoria fra una partita e l'altra. */
    const val PAUSE_MS = 60_000L

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

    // ---------------- pausa responsabile ----------------

    /** Attiva di serie: si disattiva solo con la password. */
    fun pauseEnabled(ctx: Context): Boolean = p(ctx).getBoolean("pause_enabled", true)

    fun setPauseEnabled(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean("pause_enabled", value).apply()
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
