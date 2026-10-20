package com.jucha.acometidasapp.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageProcessor {

    /**
     * Replica el filtro original de OpenCV en espacio HSV:
     *  1. Whitening selectivo: solo modifica el canal V (brillo), preserva H y S (colores intactos)
     *  2. Saturación x1.5 → letras más vibrantes
     *  3. Unsharp mask 1.8/0.8 → texto más nítido
     *  4. Contraste final x1.2 + 10
     */
    suspend fun applyScanFilter(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val original = loadBitmapFromUri(context, uri) ?: return@withContext null
            val width  = original.width
            val height = original.height

            val pixels = IntArray(width * height)
            original.getPixels(pixels, 0, width, 0, 0, width, height)

            // ── PASO 1: Whitening en HSV (igual que OpenCV) ───────────────────
            // Modifica SOLO el canal V (brillo) → fondo blanco, colores intactos
            // Boost de saturación x1.5 → letras más vibrantes
            val step1 = IntArray(width * height)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r  = (px shr 16) and 0xFF
                val g  = (px shr 8)  and 0xFF
                val b  = px and 0xFF

                val hsv = rgbToHsv(r, g, b)
                val h   = hsv[0]
                var s   = hsv[1]
                var v   = hsv[2]

                val vInt = (v * 255).toInt()

                // Whitening selectivo (mismo umbral que OpenCV original)
                v = when {
                    vInt > 180 -> 1.0f                     // blanco puro
                    vInt > 150 -> v + (1f - v) * 0.5f     // blanqueado parcial 50%
                    else       -> v                        // texto oscuro: sin cambio
                }

                // Boost de saturación igual que OpenCV (s * 1.5)
                s = (s * 1.5f).coerceIn(0f, 1f)

                val rgb = hsvToRgb(h, s, v)
                step1[i] = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
            }

            // ── PASO 2: Unsharp mask (nitidez de texto) ───────────────────────
            // Igual que OpenCV: sharpened = 1.8 * original - 0.8 * blurred
            val blurred = boxBlur(step1, width, height, radius = 2)
            val step2   = IntArray(width * height)
            for (i in step2.indices) {
                val p  = step1[i]
                val bl = blurred[i]
                val r = (1.8 * ((p shr 16) and 0xFF) - 0.8 * ((bl shr 16) and 0xFF)).toInt().coerceIn(0, 255)
                val g = (1.8 * ((p shr 8)  and 0xFF) - 0.8 * ((bl shr 8)  and 0xFF)).toInt().coerceIn(0, 255)
                val b = (1.8 * (p and 0xFF)           - 0.8 * (bl and 0xFF)          ).toInt().coerceIn(0, 255)
                step2[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            // ── PASO 3: Contraste final (alpha=1.2, beta=10) ──────────────────
            val result = IntArray(width * height)
            for (i in result.indices) {
                val p = step2[i]
                val r = ((((p shr 16) and 0xFF) * 1.2) + 10).toInt().coerceIn(0, 255)
                val g = ((((p shr 8)  and 0xFF) * 1.2) + 10).toInt().coerceIn(0, 255)
                val b = (((p and 0xFF)           * 1.2) + 10).toInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output.setPixels(result, 0, width, 0, 0, width, height)

            Log.d("ImageProcessor", "Filtro scanner HSV aplicado: colores preservados + fondo blanco")
            output
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error aplicando filtro: ${e.message}", e)
            null
        }
    }

    // ── Conversión RGB → HSV ──────────────────────────────────────────────────
    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max   = maxOf(rf, gf, bf)
        val min   = minOf(rf, gf, bf)
        val delta = max - min

        val h = when {
            delta == 0f -> 0f
            max == rf   -> { var h = 60f * ((gf - bf) / delta); if (h < 0f) h += 360f; h }
            max == gf   -> 60f * ((bf - rf) / delta + 2f)
            else        -> 60f * ((rf - gf) / delta + 4f)
        }
        val s = if (max == 0f) 0f else delta / max
        return floatArrayOf(h, s, max)
    }

    // ── Conversión HSV → RGB ──────────────────────────────────────────────────
    private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
        if (s == 0f) {
            val vi = (v * 255).toInt().coerceIn(0, 255)
            return intArrayOf(vi, vi, vi)
        }
        val sector = ((h / 60f).toInt()) % 6
        val f = h / 60f - (h / 60f).toInt()
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)
        val (rf, gf, bf) = when (sector) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return intArrayOf(
            (rf * 255).toInt().coerceIn(0, 255),
            (gf * 255).toInt().coerceIn(0, 255),
            (bf * 255).toInt().coerceIn(0, 255)
        )
    }

    // ── Box blur (ventana radius*2+1) ─────────────────────────────────────────
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var rS = 0; var gS = 0; var bS = 0; var n = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val p  = pixels[ny * width + nx]
                        rS += (p shr 16) and 0xFF
                        gS += (p shr 8)  and 0xFF
                        bS += p and 0xFF
                        n++
                    }
                }
                result[y * width + x] = (0xFF shl 24) or ((rS/n) shl 16) or ((gS/n) shl 8) or (bS/n)
            }
        }
        return result
    }

    // ── Carga bitmap escalado a máx 1920px (evita OOM) ───────────────────────
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

            val maxDim = 1920
            val sample = if (opts.outWidth > maxDim || opts.outHeight > maxDim)
                maxOf(opts.outWidth / maxDim, opts.outHeight / maxDim).coerceAtLeast(1)
            else 1

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error cargando bitmap: ${e.message}")
            null
        }
    }
}
