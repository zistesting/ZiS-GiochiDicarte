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

    var card: Card? = null
    var faceUp: Boolean = true

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

        /** Larghezza a cui sono decodificate le bitmap attualmente in cache. */
        private var cachedWidth = 0

        /** Le larghezze si arrotondano a multipli di 32 px: piccole differenze fra una schermata
         *  e l'altra non devono far ridecodificare tutto il mazzo. */
        private fun bucket(px: Int): Int = ((px.coerceAtLeast(64) + 31) / 32) * 32

        fun bitmapFor(ctx: Context, name: String, targetW: Int): Bitmap? {
            val w = bucket(targetW)
            if (w != cachedWidth) {          // schermo ruotato o finestra ridimensionata
                cache.evictAll()
                cachedWidth = w
            }
            cache.get(name)?.let { return it }
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) return null
            var bm = decode(ctx, id, w)
            if (bm == null) {                // memoria finita: libero e riprovo una volta sola
                cache.evictAll()
                bm = decode(ctx, id, w)
            }
            if (bm != null) cache.put(name, bm)
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

        /** Svuota la cache (chiamata da onTrimMemory dell'Application). */
        fun clearCache() {
            cache.evictAll()
            cachedWidth = 0
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

        val name = if (!faceUp || card == null) "card_back" else "card_${card!!.suit}_${card!!.value}"
        val bm = bitmapFor(context, name, width)
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
