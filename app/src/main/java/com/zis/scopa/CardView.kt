package com.zis.scopa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import android.view.View
import kotlin.math.min

/** A single playing card: draws the Neapolitan card image (face) or the card back, with a rounded
 *  clip and a thin border. */
class CardView(context: Context) : View(context) {

    /**
     * Carta disegnata, oppure null per il dorso.
     *
     * Il setter richiama invalidate(): finora funzionava solo perche' le schermate ricreano
     * tutte le CardView a ogni render(). Riusando una vista gia' a schermo, senza questo, la
     * carta vecchia sarebbe rimasta disegnata.
     */
    var card: Card? = null
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    /** Carta scoperta o coperta. Stesso motivo del setter qui sopra. */
    var faceUp: Boolean = true
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()

    companion object {
        /**
         * Le immagini stanno in res/drawable-nodpi/, quindi Android non le tocca in base alla
         * densita' dello schermo. La riduzione la facciamo noi qui, alla larghezza a cui la
         * carta viene davvero disegnata: un file sorgente da 448 o 560 px di larghezza puo'
         * cosi' servire sia i telefoni sia i tablet senza sprecare memoria.
         *
         * Esempio su un telefono xxhdpi: la carta a schermo e' larga circa 230 px, la bitmap
         * viene decodificata a 256x468 (circa 470 KB) invece che a 560x1024 (2,3 MB).
         *
         * La cache e' una LruCache limitata a 1/8 della heap: quando lo spazio finisce le
         * bitmap meno usate vengono buttate da sole.
         */
        private val cache: LruCache<String, Bitmap> by lazy {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            object : LruCache<String, Bitmap>(maxKb / 8) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
            }
        }

        /**
         * Mazzo in uso: e' il prefisso dei nomi dei file. Lo impostano le schermate di gioco
         * in onResume leggendo le impostazioni. Cambiandolo la cache si svuota, altrimenti
         * resterebbero a schermo le carte del mazzo precedente.
         */
        private var deckPrefix = Prefs.DECK_ZIS

        fun setDeck(prefix: String) {
            if (prefix != deckPrefix) {
                deckPrefix = prefix
                cache.evictAll()
            }
        }

        /** Nome del file per una carta, o per il dorso se [card] e' null. */
        fun nameFor(card: Card?): String =
            if (card == null) "${deckPrefix}_back" else "${deckPrefix}_${card.suit}_${card.value}"

        /** Le larghezze si arrotondano a multipli di 32 px: piccole differenze fra una schermata
         *  e l'altra non devono far ridecodificare tutto il mazzo. */
        private fun bucket(px: Int): Int = ((px.coerceAtLeast(64) + 31) / 32) * 32

        /**
         * La larghezza fa parte della chiave, non e' piu' uno stato a parte.
         *
         * Prima la cache teneva una sola larghezza e si svuotava tutta appena ne arrivava
         * un'altra. Andava bene finche' a schermo c'era una misura sola, ma il Tresette tiene
         * dieci carte piccole in mano e le carte grandi in tavola: con la vecchia logica le
         * due misure si sarebbero buttate a vicenda a ogni disegno, ridecodificando l'intero
         * mazzo a ogni fotogramma. Con la larghezza nella chiave convivono, e la LruCache
         * butta da sola quello che non serve piu'.
         */
        fun bitmapFor(ctx: Context, name: String, targetW: Int): Bitmap? {
            val w = bucket(targetW)
            val key = "$name@$w"
            cache.get(key)?.let { return it }
            var id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0 && !name.startsWith(Prefs.DECK_ZIS)) {
                // immagine del mazzo alternativo non ancora installata: ripiego su quella ZiS,
                // cosi' un mazzo incompleto non lascia buchi bianchi sul tavolo
                val ripiego = Prefs.DECK_ZIS + name.substring(name.indexOf('_'))
                id = ctx.resources.getIdentifier(ripiego, "drawable", ctx.packageName)
            }
            if (id == 0) return null
            var bm = decode(ctx, id, w)
            if (bm == null) {                // memoria finita: libero e riprovo una volta sola
                cache.evictAll()
                bm = decode(ctx, id, w)
            }
            if (bm != null) cache.put(key, bm)
            return bm
        }

        private fun decode(ctx: Context, id: Int, targetW: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeResource(ctx.resources, id, bounds)
            val srcW = bounds.outWidth
            if (srcW <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (targetW < srcW) {
                    // BitmapFactory riduce di inTargetDensity/inDensity: la carta viene
                    // decodificata gia' alla misura giusta, senza creare la bitmap intera.
                    inScaled = true
                    inDensity = srcW
                    inTargetDensity = targetW
                } else {
                    inScaled = false   // mai ingrandire in memoria: ci pensa il canvas
                }
            }
            return try {
                BitmapFactory.decodeResource(ctx.resources, id, opts)
            } catch (e: OutOfMemoryError) {
                null
            }
        }

        /** Svuota la cache: l'app e' finita in background (vedi ZisApp.onTrimMemory). */
        fun clearCache() {
            cache.evictAll()
        }

        /**
         * Dimezza la cache invece di svuotarla: l'app e' ancora in primo piano e la memoria
         * stringe, quindi conviene tenere le carte usate di recente ed evitare di ridecodificare
         * l'intero tavolo al fotogramma successivo.
         */
        fun trimCache() {
            cache.trimToSize(cache.maxSize() / 2)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) * 0.10f
        val rect = RectF(1f, 1f, w - 1f, h - 1f)

        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        val bm = bitmapFor(context, nameFor(if (faceUp) card else null), width)
        if (bm != null) {
            canvas.drawBitmap(bm, null, RectF(0f, 0f, w, h), paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRect(rect, paint)
        }
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(0x60, 0x60, 0x60)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.FILL
    }
}
