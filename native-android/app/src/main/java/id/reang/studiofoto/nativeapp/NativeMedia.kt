package id.reang.studiofoto.nativeapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object NativeFiles {
    suspend fun importUri(context: Context, uri: Uri, folder: String = "photos"): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, folder).apply { mkdirs() }
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.let {
                when (it.lowercase()) { "jpeg" -> "jpg"; "png", "webp", "heic" -> it; else -> "jpg" }
            } ?: "jpg"
            val target = File(dir, "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("File sumber tidak dapat dibuka.")
            check(target.length() > 0) { "File gambar kosong." }
            target
        }

    suspend fun writeBytes(context: Context, bytes: ByteArray, folder: String, extension: String): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, folder).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.$extension")
            file.writeBytes(bytes)
            file.absolutePath
        }

    suspend fun imageDataUri(path: String, maxSide: Int = 2400): String = withContext(Dispatchers.IO) {
        val bitmap = NativeImageEngine.decode(path, maxSide) ?: error("Foto tidak dapat dibaca.")
        val output = ByteArrayOutputStream()
        val keepAlpha = bitmap.hasAlpha()
        val mime = if (keepAlpha) "image/png" else "image/jpeg"
        if (keepAlpha) bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        else bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        bitmap.recycle()
        "data:$mime;base64," + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
    }
}

object FalAiClient {
    data class Result(val localPath: String, val raw: JSONObject)

    suspend fun run(
        context: Context,
        model: String,
        input: JSONObject,
        apiKey: String,
        onProgress: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        require(apiKey.trim().length >= 12) { "Masukkan API key fal.ai yang valid." }
        val base = "https://queue.fal.run/$model"
        val submitted = request("POST", base, apiKey, input.toString())
        val requestId = submitted.optString("request_id").ifBlank { submitted.optString("requestId") }
        var result = submitted
        if (requestId.isNotBlank()) {
            val statusUrl = submitted.optString("status_url").ifBlank { "$base/requests/$requestId/status" }
            val resultUrl = submitted.optString("response_url").ifBlank { "$base/requests/$requestId" }
            var completed = false
            for (attempt in 0 until 180) {
                delay(2000)
                val status = request("GET", statusUrl, apiKey)
                val state = status.optString("status", status.optString("state")).uppercase()
                onProgress("Status $state · ${attempt * 2 + 2} detik")
                when (state) {
                    "COMPLETED" -> { result = request("GET", resultUrl, apiKey); completed = true; break }
                    "FAILED", "CANCELLED" -> error(status.optString("error").ifBlank { status.optString("detail", "Proses AI gagal.") })
                }
            }
            check(completed) { "Waktu tunggu fal.ai habis. Periksa koneksi dan saldo, lalu coba lagi." }
        }
        val imageUrl = findImageUrl(result) ?: error("Model tidak mengembalikan gambar. Periksa endpoint dan izin model.")
        val bytes = if (imageUrl.startsWith("data:image/")) {
            android.util.Base64.decode(imageUrl.substringAfter(",", ""), android.util.Base64.DEFAULT)
        } else {
            URL(imageUrl).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 60000
            }.getInputStream().use { it.readBytes() }
        }
        check(bytes.isNotEmpty()) { "File hasil AI kosong." }
        val extension = if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) "png" else "jpg"
        val path = NativeFiles.writeBytes(context, bytes, "ai-results", extension)
        Result(path, result)
    }

    private fun findImageUrl(json: JSONObject): String? {
        fun value(obj: JSONObject?): String? {
            if (obj == null) return null
            val direct = obj.optString("url")
            if (direct.startsWith("http") || direct.startsWith("data:image")) return direct
            val image = obj.optJSONObject("image")?.optString("url")
                ?: obj.optString("image").takeIf { it.startsWith("http") || it.startsWith("data:image") }
            return image
        }
        value(json)?.let { return it }
        value(json.optJSONObject("data"))?.let { return it }
        val images = json.optJSONArray("images") ?: json.optJSONObject("data")?.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val first = images.optJSONObject(0)
            value(first)?.let { return it }
            val raw = images.optString(0)
            if (raw.startsWith("http") || raw.startsWith("data:image")) return raw
        }
        return json.optString("image_url").takeIf { it.startsWith("http") || it.startsWith("data:image") }
    }

    private fun request(method: String, url: String, apiKey: String, body: String? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("Authorization", "Key ${apiKey.trim()}")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val detail = json.optString("detail").ifBlank { json.optString("message") }.ifBlank { "HTTP $code" }
                error("fal.ai: $detail")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

data class CutoutBrushStroke(
    val points: List<Pair<Float, Float>>,
    val erase: Boolean,
    val widthFraction: Float
)

object NativeImageEngine {
    fun imageAspect(path: String?): Float {
        if (path.isNullOrBlank()) return 1f
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight.toFloat() else 1f
    }

    suspend fun applyCutoutBrush(
        context: Context,
        originalPath: String,
        cutoutPath: String,
        strokes: List<CutoutBrushStroke>
    ): String = withContext(Dispatchers.IO) {
        val original = decode(originalPath, 3000) ?: error("Foto asli tidak dapat dibaca.")
        val cutout = decode(cutoutPath, 3000) ?: error("Hasil cutout tidak dapat dibaca.")
        val width = cutout.width
        val height = cutout.height
        val source = if (original.width == width && original.height == height) original
            else Bitmap.createScaledBitmap(original, width, height, true)
        val output = cutout.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = point.first.coerceIn(0f, 1f) * width
                val y = point.second.coerceIn(0f, 1f) * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (stroke.widthFraction.coerceIn(0.003f, 0.15f) * min(width, height)).coerceAtLeast(4f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            if (stroke.erase) {
                paint.color = Color.BLACK
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
            } else {
                paint.color = Color.WHITE
                paint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                canvas.drawCircle(point.first * width, point.second * height, paint.strokeWidth / 2f, paint)
            } else canvas.drawPath(path, paint)
        }
        val bytes = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        if (source !== original) source.recycle()
        original.recycle()
        cutout.recycle()
        output.recycle()
        NativeFiles.writeBytes(context, bytes.toByteArray(), "cutouts", "png")
    }

    fun decode(path: String?, maxSide: Int = 3200): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    fun render(photo: PhotoEntity, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(245, 242, 238))
        val generated = decode(photo.aiCompositePath, max(width, height))
        if (generated != null) {
            drawCover(canvas, generated, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            generated.recycle()
        } else {
            val background = decode(photo.backgroundPath, max(width, height))
            if (background != null) {
                val scale = photo.backgroundScale.coerceIn(0.5f, 3f)
                val offsetX = photo.backgroundX.coerceIn(-100f, 100f) * width / 100f
                val offsetY = photo.backgroundY.coerceIn(-100f, 100f) * height / 100f
                val rect = RectF(
                    -width * (scale - 1f) / 2f + offsetX,
                    -height * (scale - 1f) / 2f + offsetY,
                    width * (1f + scale) / 2f + offsetX,
                    height * (1f + scale) / 2f + offsetY
                )
                drawCover(canvas, background, rect)
                background.recycle()
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.rgb(232, 227, 250), Color.rgb(248, 241, 231), Color.rgb(222, 211, 194)),
                    null, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
        }

        val sourcePath = photo.enhancedPath ?: photo.cutoutPath ?: photo.localPath
        val subject = if (photo.aiCompositePath == null) decode(sourcePath, max(width, height)) else null
        if (subject != null) {
            val baseW = width * 0.82f * photo.objectScale.coerceIn(0.2f, 2.5f)
            val baseH = height * 0.80f * photo.objectScale.coerceIn(0.2f, 2.5f)
            val left = (width - baseW) / 2f + photo.objectX * width / 100f
            val top = (height - baseH) / 2f + photo.objectY * height / 100f
            val rect = RectF(left, top, left + baseW, top + baseH)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val sat = (photo.saturation + photo.vibrance * 0.004f).coerceIn(0f, 2f)
            val inv = 1f - sat
            val r = 0.213f * inv; val g = 0.715f * inv; val b = 0.072f * inv
            val contrast = (photo.contrast * (1f + photo.dehaze.coerceIn(-100f, 100f) * 0.002f + photo.clarity.coerceIn(-100f, 100f) * 0.0015f)).coerceIn(0.35f, 2.2f)
            val exposureOffset = photo.brightness.coerceIn(-0.6f, 0.6f) * 255f
            val shadowOffset = photo.shadows.coerceIn(-100f, 100f) * 0.28f
            val blackOffset = photo.blacks.coerceIn(-100f, 100f) * 0.22f
            val highlightOffset = photo.highlights.coerceIn(-100f, 100f) * 0.16f
            val offset = exposureOffset + shadowOffset + blackOffset + highlightOffset
            val warm = photo.warmth.coerceIn(-100f, 100f) * 0.55f
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                contrast * (r + sat), contrast * g, contrast * b, 0f, offset + warm,
                contrast * r, contrast * (g + sat), contrast * b, 0f, offset,
                contrast * r, contrast * g, contrast * (b + sat), 0f, offset - warm,
                0f, 0f, 0f, 1f, 0f
            )))
            canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(subject, null, fitRect(subject.width, subject.height, rect), paint)
            canvas.restore()
            subject.recycle()
        }

        if (photo.vignette > 0f) {
            val amount = photo.vignette.coerceIn(0f, 100f) / 100f
            val radius = max(width, height) * 0.78f
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    width / 2f, height / 2f, radius,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((amount * 165).toInt(), 0, 0, 0)),
                    floatArrayOf(0f, 0.62f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }

        val overlay = decode(photo.overlayPath, max(width, height))
        if (overlay != null) {
            canvas.drawBitmap(overlay, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            overlay.recycle()
        }
        val logo = decode(photo.logoPath, width / 3)
        if (logo != null) {
            val w = width * 0.20f
            val h = w * logo.height / logo.width
            val rect = RectF(width * .73f, height * .04f, width * .73f + w, height * .04f + h)
            canvas.drawBitmap(logo, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            logo.recycle()
        }
        if (photo.eventText.isNotBlank() || photo.eventDate.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                textSize = width * .035f
                setShadowLayer(width * .004f, 0f, width * .002f, Color.BLACK)
            }
            if (photo.eventText.isNotBlank()) canvas.drawText(photo.eventText.take(60), width / 2f, height * .91f, paint)
            if (photo.eventDate.isNotBlank()) {
                paint.textSize = width * .022f
                canvas.drawText(photo.eventDate.take(40), width / 2f, height * .955f, paint)
            }
        }
        return out
    }

    private fun fitRect(bw: Int, bh: Int, target: RectF): RectF {
        val scale = min(target.width() / bw, target.height() / bh)
        val w = bw * scale; val h = bh * scale
        return RectF(target.centerX() - w / 2f, target.centerY() - h / 2f, target.centerX() + w / 2f, target.centerY() + h / 2f)
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = max(target.width() / bitmap.width, target.height() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val rect = RectF(target.centerX() - w / 2f, target.centerY() - h / 2f, target.centerX() + w / 2f, target.centerY() + h / 2f)
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    suspend fun makeBokeh(context: Context, originalPath: String, cutoutPath: String, blurStrength: Int): String =
        withContext(Dispatchers.IO) {
            val original = decode(originalPath, 2400) ?: error("Foto asli tidak dapat dibaca.")
            val cutout = decode(cutoutPath, 2400) ?: error("Hasil cutout tidak dapat dibaca.")
            val w = original.width; val h = original.height
            val small = Bitmap.createScaledBitmap(original, max(1, w / (8 + blurStrength / 2)), max(1, h / (8 + blurStrength / 2)), true)
            val blurred = Bitmap.createScaledBitmap(small, w, h, true)
            val out = blurred.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(out).drawBitmap(cutout, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val bytes = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            original.recycle(); cutout.recycle(); small.recycle(); blurred.recycle(); out.recycle()
            NativeFiles.writeBytes(context, bytes.toByteArray(), "edits", "png")
        }

    suspend fun mergeEnhancement(context: Context, originalPath: String, enhancedPath: String, strength: Int): String =
        withContext(Dispatchers.IO) {
            val original = decode(originalPath, 2600) ?: error("Layer asli tidak dapat dibaca.")
            val enhanced = decode(enhancedPath, 2600) ?: error("Hasil AI tidak dapat dibaca.")
            val w = enhanced.width; val h = enhanced.height
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val amount = strength.coerceIn(0, 100) / 100f
            val originalScaled = Bitmap.createScaledBitmap(original, w, h, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.alpha = (amount * 255).toInt()
            canvas.drawBitmap(enhanced, 0f, 0f, paint)
            paint.alpha = ((1f - amount) * 255).toInt()
            canvas.drawBitmap(originalScaled, 0f, 0f, paint)
            paint.alpha = 255
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(originalScaled, 0f, 0f, paint)
            paint.xfermode = null
            val bytes = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            original.recycle(); enhanced.recycle(); originalScaled.recycle(); out.recycle()
            NativeFiles.writeBytes(context, bytes.toByteArray(), "edits", "png")
        }

    suspend fun export(context: Context, photo: PhotoEntity, format: String, dpi: Int): Uri =
        withContext(Dispatchers.IO) {
            val cmW = photo.templateWidthCm.coerceIn(5f, 200f)
            val cmH = photo.templateHeightCm.coerceIn(5f, 250f)
            var width = (cmW / 2.54f * dpi.coerceIn(72, 600)).toInt()
            var height = (cmH / 2.54f * dpi.coerceIn(72, 600)).toInt()
            val factor = min(5000f / width, 6500f / height).coerceAtMost(1f)
            width = (width * factor).toInt().coerceAtLeast(1)
            height = (height * factor).toInt().coerceAtLeast(1)
            val bitmap = render(photo, width, height)
            val mime = if (format == "jpeg") "image/jpeg" else "image/png"
            val ext = if (format == "jpeg") "jpg" else "png"
            val name = "Reang-${System.currentTimeMillis()}.$ext"
            val temp = File(context.cacheDir, name)
            try {
                temp.outputStream().use { stream ->
                    val ok = bitmap.compress(if (format == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, stream)
                    check(ok) { "Gagal merender gambar." }
                }
                bitmap.recycle()
                if (format == "jpeg") {
                    ExifInterface(temp.absolutePath).apply {
                        setAttribute(ExifInterface.TAG_X_RESOLUTION, dpi.toString())
                        setAttribute(ExifInterface.TAG_Y_RESOLUTION, dpi.toString())
                        setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2")
                        saveAttributes()
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ReangStudioFoto")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Tidak dapat membuat file ekspor di galeri.")
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    temp.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Tidak dapat membuka file tujuan.")
                uri
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                temp.delete()
            }
        }
}