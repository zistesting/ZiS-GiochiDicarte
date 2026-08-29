package com.zis.scopa

import android.content.Context

object Prefs {
    private const val FILE = "zis_giochi_prefs"

    fun scoreTarget(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("score_target", 11)

    fun setScoreTarget(ctx: Context, value: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("score_target", value).apply()
    }

    /** Briscola match: number of games (hands) a player must win to take the match (5 or 11). */
    fun briscolaTarget(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("briscola_target", 5)

    fun setBriscolaTarget(ctx: Context, value: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("briscola_target", value).apply()
    }
}
