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
 *  clip and an optional gold highlight border. */
class CardView(context: Context) : View(context) {

    var card: Card? = null
    var faceUp: Boolean = true
    var highlight: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()

    companion object {
        /**
         * Le immagini delle carte stanno in res/drawable-nodpi/, quindi Android NON le riscala in
         * base alla densita' dello schermo: restano 220x~410 px (~360 KB in RAM l'una) invece di
         * essere ingrandite 3x su un telefono xxhdpi (~3,3 MB l'una).
         *
         * In piu' la cache e' una LruCache limitata a 1/8 della heap disponibile: quando lo spazio
         * finisce le bitmap meno usate vengono buttate via da sole, invece di accumularsi
         * all'infinito in una HashMap statica.
         */
        private val cache: LruCache<String, Bitmap> by lazy {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            object : LruCache<String, Bitmap>(maxKb / 8) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
            }
        }

        private val decodeOptions = BitmapFactory.Options().apply {
            inScaled = false                 // niente upscaling automatico alla densita' del device
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        fun bitmapFor(ctx: Context, name: String): Bitmap? {
            cache.get(name)?.let { return it }
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) return null
            val bm = try {
                BitmapFactory.decodeResource(ctx.resources, id, decodeOptions)
            } catch (e: OutOfMemoryError) {
                cache.evictAll()
                try { BitmapFactory.decodeResource(ctx.resources, id, decodeOptions) } catch (e2: Throwable) { null }
            } ?: return null
            cache.put(name, bm)
            return bm
        }

        /** Svuota la cache (chiamata da onTrimMemory dell'Application). */
        fun clearCache() = cache.evictAll()
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
        val bm = bitmapFor(context, name)
        if (bm != null) {
            canvas.drawBitmap(bm, null, RectF(0f, 0f, w, h), paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRect(rect, paint)
        }
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.color = if (highlight) Color.rgb(0xE2, 0xAA, 0x28) else Color.rgb(0x60, 0x60, 0x60)
        paint.strokeWidth = if (highlight) 8f else 2f
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.FILL
    }
}
