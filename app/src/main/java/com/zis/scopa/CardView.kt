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
     * Il setter richiama invalidate() e aggiorna la descrizione per TalkBack. Serve davvero:
     * le schermate adesso RIUSANO le viste invece di ricrearle a ogni render(), quindi senza
     * questo la carta vecchia resterebbe disegnata.
     */
    var card: Card? = null
        set(value) {
            if (field != value) { field = value; describe(); invalidate() }
        }

    /** Carta scoperta o coperta. Stesso motivo del setter qui sopra. */
    var faceUp: Boolean = true
        set(value) {
            if (field != value) { field = value; describe(); invalidate() }
        }

    /**
     * Falso quando la carta e' in mano ma non si puo' calare (obbligo di seme nel Tresette).
     * Non cambia il disegno, che resta compito dell'alpha impostata dalla schermata: cambia
     * solo quello che TalkBack legge, perche' chi non vede l'alpha non ha altro modo di
     * sapere che quella carta e' fuori gioco.
     */
    var playable: Boolean = true
        set(value) {
            if (field != value) { field = value; describe() }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()

    init {
        describe()
    }

    /** Descrizione letta da TalkBack: "Sette di denari", "Carta coperta", "... non giocabile". */
    private fun describe() {
        val c = card
        contentDescription = when {
            !faceUp || c == null -> context.getString(R.string.cd_card_back)
            !playable -> context.getString(R.string.cd_card_disabled, c.italianName)
            else -> c.italianName
        }
    }

    companion object {
        /**
         * Le immagini stanno in res/drawable-nodpi/, quindi Android non le tocca in base alla
         * densita' dello schermo. La riduzione la facciamo noi qui, alla larghezza a cui la
         * carta viene davvero disegnata: un file sorgente da 448 px di larghezza puo'
         * cosi' servire sia i telefoni sia i tablet senza sprecare memoria.
         *
         * Esempio su un telefono xxhdpi: la carta a schermo e' larga circa 230 px, la bitmap
         * viene decodificata a 256x468 (circa 470 KB) invece che a piena misura.
         *
         * La cache e' una LruCache limitata a 1/8 della heap: quando lo spazio finisce le
         * bitmap meno usate vengono buttate da sole.
         */
        private val cache: LruCache<Long, Bitmap> by lazy {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            object : LruCache<Long, Bitmap>(maxKb / 8) {
                override fun sizeOf(key: Long, value: Bitmap): Int =
                    maxOf(1, value.byteCount / 1024)
            }
        }

        /**
         * Mazzo in uso. Lo impostano le schermate di gioco in onResume leggendo le
         * impostazioni. Cambiandolo la cache si svuota, altrimenti resterebbero a schermo
         * le carte del mazzo precedente.
         */
        private var deckPrefix = Prefs.DECK_ZIS

        fun setDeck(prefix: String) {
            if (prefix != deckPrefix) {
                deckPrefix = prefix
                cache.evictAll()
            }
        }

        /** Id dell'immagine da disegnare: la carta se scoperta, altrimenti il dorso. */
        fun resIdFor(card: Card?, faceUp: Boolean): Int =
            if (card == null || !faceUp) Decks.backId(deckPrefix) else Decks.faceId(deckPrefix, card)

        /** Le larghezze si arrotondano a multipli di 32 px: piccole differenze fra una schermata
         *  e l'altra non devono far ridecodificare tutto il mazzo. */
        private fun bucket(px: Int): Int = ((px.coerceAtLeast(64) + 31) / 32) * 32

        /**
         * La larghezza fa parte della chiave, non e' uno stato a parte.
         *
         * Il Tresette tiene dieci carte piccole in mano e le carte grandi in tavola: con una
         * cache a larghezza unica le due misure si butterebbero a vicenda a ogni disegno,
         * ridecodificando l'intero mazzo a ogni fotogramma. La chiave e' un Long che impacchetta
         * id della risorsa e larghezza, cosi' non si costruisce una stringa a ogni disegno.
         */
        fun bitmapFor(ctx: Context, resId: Int, targetW: Int): Bitmap? {
            if (resId == 0) return null
            val w = bucket(targetW)
            val key = (resId.toLong() shl 20) or w.toLong()
            cache.get(key)?.let { return it }
            var bm = decode(ctx, resId, w)
            if (bm == null) {                // memoria finita: libero e riprovo una volta sola
                cache.evictAll()
                bm = decode(ctx, resId, w)
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

        /** Svuota la cache: l'app non e' piu' visibile (vedi ZisApp.onTrimMemory). */
        fun clearCache() {
            cache.evictAll()
        }

        /**
         * Dimezza la cache invece di svuotarla: l'app e' ancora in primo piano e la memoria
         * stringe, quindi conviene tenere le carte usate di recente ed evitare di ridecodificare
         * l'intero tavolo al fotogramma successivo.
         */
        fun trimCache() {
            cache.trimToSize(maxOf(1, cache.maxSize() / 2))
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

        val bm = bitmapFor(context, resIdFor(card, faceUp), width)
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
