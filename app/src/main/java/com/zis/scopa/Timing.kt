package com.zis.scopa

/**
 * Tutti i tempi di gioco in un posto solo, cosi' Scopa e Briscola si comportano allo stesso modo.
 *
 * Con [fast] a true (gioco automatico) valgono tutti zero: niente animazioni, niente attese,
 * la partita scorre alla massima velocita' possibile. Le pause "vere" per il giocatore umano
 * si regolano qui, senza andare a cercare i numeri sparsi nelle activity.
 */
class Timing {

    /** Acceso quando gioca il programma: azzera ogni attesa. */
    var fast: Boolean = false

    private fun ms(v: Long): Long = if (fast) 0L else v

    // ---- distribuzione iniziale ----
    /** Durata complessiva della distribuzione, prima che la partita cominci. */
    val deal get() = ms(780)
    /** Ritardo fra una carta e l'altra mentre si distribuisce. */
    val dealStep get() = ms(50)
    /** Durata del volo di ogni singola carta distribuita. */
    val dealDur get() = ms(240)

    // ---- giocata ----
    /** Durata del volo della carta calata verso il centro. */
    val playDur get() = ms(240)
    /** Quanto resta ferma la carta calata prima che il tavolo si aggiorni. */
    val hold get() = ms(500)
    /** Pausa prima che il Banco (o il gioco automatico) cali la sua carta. */
    val think get() = ms(250)

    // ---- Scopa: raccolta delle carte prese ----
    /** Durata dell'animazione delle carte prese che scivolano sotto quella calata. */
    val gatherDur get() = ms(260)
    /** Attesa complessiva dall'inizio del raggruppamento. */
    val gather get() = ms(500)
    /** Quanto resta a schermo l'avviso SCOPA / SETTEBELLO. */
    val banner get() = ms(1000)

    // ---- Tresette: pescata ----
    /**
     * Quanto restano ferme e scoperte le due carte pescate prima di entrare nelle mani.
     * Non e' un abbellimento: la regola vuole che la carta pescata la veda anche
     * l'avversario, quindi deve esserci il tempo materiale di leggerla.
     */
    val drawShow get() = ms(600)

    // ---- Briscola: presa ----
    /** Pausa a carte scoperte, per far vedere chi ha vinto la presa. */
    val trickPause get() = ms(500)
    /** Durata della sparizione delle due carte verso il mazzetto del vincitore. */
    val sweepDur get() = ms(320)
    /** Sfalsamento fra la prima e la seconda carta che spariscono. */
    val sweepStep get() = ms(70)
}
