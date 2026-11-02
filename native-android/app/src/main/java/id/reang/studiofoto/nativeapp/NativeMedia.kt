package id.reang.studiofoto.nativeapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
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

    fun deletePrivatePhotoFiles(photo: PhotoEntity) {
        listOf(photo.localPath, photo.cutoutPath, photo.enhancedPath, photo.aiCompositePath)
            .plus(photo.localAdjustmentPath)
            .filterNotNull()
            .distinct()
            .forEach { path -> runCatching { File(path).delete() } }
    }

    suspend fun imageDataUri(path: String, maxSide: Int = 2400, forcePng: Boolean = false): String = withContext(Dispatchers.IO) {
        val bitmap = NativeImageEngine.decode(path, maxSide) ?: error("Foto tidak dapat dibaca.")
        val output = ByteArrayOutputStream()
        val keepAlpha = bitmap.hasAlpha() || forcePng
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
                paint.style = Paint.Style.FILL
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

    suspend fun applyLocalBrush(
        context: Context,
        basePath: String,
        strokes: List<CutoutBrushStroke>,
        exposure: Float,
        warmth: Float,
        saturation: Float,
        texture: Float
    ): String = withContext(Dispatchers.IO) {
        val source = decode(basePath, 2800) ?: error("Foto untuk kuas tidak dapat dibaca.")
        require(strokes.isNotEmpty()) { "Sapukan kuas pada area yang ingin diubah." }
        val width = source.width
        val height = source.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = point.first.coerceIn(0f, 1f) * width
                val y = point.second.coerceIn(0f, 1f) * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val strokeWidth = (stroke.widthFraction.coerceIn(0.003f, 0.15f) * min(width, height)).coerceAtLeast(4f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                maskFilter = BlurMaskFilter((strokeWidth * 0.12f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                if (stroke.erase) xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
            }
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                paint.style = Paint.Style.FILL
                maskCanvas.drawCircle(point.first * width, point.second * height, strokeWidth / 2f, paint)
            } else maskCanvas.drawPath(path, paint)
        }
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val srcRow = IntArray(width)
        val maskRow = IntArray(width)
        val exp = Math.pow(2.0, (exposure.coerceIn(-100f, 100f) / 100f * 0.8f).toDouble()).toFloat()
        val warm = warmth.coerceIn(-100f, 100f) / 100f * 0.16f
        val sat = 1f + saturation.coerceIn(-100f, 100f) / 100f
        val detail = texture.coerceIn(-100f, 100f) / 100f * 0.28f
        for (y in 0 until height) {
            source.getPixels(srcRow, 0, width, 0, y, width, 1)
            mask.getPixels(maskRow, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val amount = Color.alpha(maskRow[x]) / 255f
                if (amount <= 0f) continue
                val px = srcRow[x]
                if (Color.alpha(px) == 0) continue
                val gray = (0.2126f * Color.red(px) + 0.7152f * Color.green(px) + 0.0722f * Color.blue(px)) / 255f
                var red = Color.red(px) / 255f
                var green = Color.green(px) / 255f
                var blue = Color.blue(px) / 255f
                val originalR = red; val originalG = green; val originalB = blue
                red = red * exp + warm
                green *= exp
                blue = blue * exp - warm
                val luma = (red * 0.2126f + green * 0.7152f + blue * 0.0722f).coerceIn(0f, 1f)
                red = luma + (red - luma) * sat
                green = luma + (green - luma) * sat
                blue = luma + (blue - luma) * sat
                red += (originalR - gray) * detail
                green += (originalG - gray) * detail
                blue += (originalB - gray) * detail
                red = originalR + (red - originalR) * amount
                green = originalG + (green - originalG) * amount
                blue = originalB + (blue - originalB) * amount
                srcRow[x] = Color.argb(Color.alpha(px), (red.coerceIn(0f, 1f) * 255f).toInt(), (green.coerceIn(0f, 1f) * 255f).toInt(), (blue.coerceIn(0f, 1f) * 255f).toInt())
            }
            output.setPixels(srcRow, 0, width, 0, y, width, 1)
        }
        val bytes = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        source.recycle(); mask.recycle(); output.recycle()
        NativeFiles.writeBytes(context, bytes.toByteArray(), "local-adjustments", "png")
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

    fun renderPhotoPreview(photo: PhotoEntity, maxSide: Int = 1600): Bitmap {
        val sourcePath = photo.localAdjustmentPath ?: photo.enhancedPath ?: photo.localPath
        val bitmap = decode(sourcePath, maxSide) ?: error("Foto asli tidak dapat dibaca.")
        applyAdvancedLightAdjustments(bitmap, photo)
        return bitmap
    }

    fun render(photo: PhotoEntity, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(245, 242, 238))
        val generated = decode(photo.aiCompositePath, max(width, height))
        if (generated != null) {
            applyAdvancedLightAdjustments(generated, photo)
            val generatedTarget = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(generated, null, fitRect(generated.width, generated.height, generatedTarget), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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

        val sourcePath = photo.localAdjustmentPath ?: photo.enhancedPath ?: photo.cutoutPath ?: photo.localPath
        val subject = if (photo.aiCompositePath == null) decode(sourcePath, max(width, height)) else null
        if (subject != null) {
            val baseW = width * 0.82f * photo.objectScale.coerceIn(0.2f, 2.5f)
            val baseH = height * 0.80f * photo.objectScale.coerceIn(0.2f, 2.5f)
            val left = (width - baseW) / 2f + photo.objectX * width / 100f
            val top = (height - baseH) / 2f + photo.objectY * height / 100f
            val rect = RectF(left, top, left + baseW, top + baseH)
            applyAdvancedLightAdjustments(subject, photo)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(subject, null, fitRect(subject.width, subject.height, rect), paint)
            canvas.restore()
            subject.recycle()
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

    private fun applyAdvancedLightAdjustments(bitmap: Bitmap, photo: PhotoEntity) {
        val width = bitmap.width
        val height = bitmap.height
        val row = IntArray(width)
        val exposure = Math.pow(2.0, (photo.brightness.coerceIn(-0.6f, 0.6f) * 1.4f).toDouble()).toFloat()
        val contrast = (photo.contrast * (1f + photo.dehaze.coerceIn(-100f, 100f) * 0.0035f)).coerceIn(0.25f, 2.5f)
        val saturation = photo.saturation.coerceIn(0f, 2f)
        val vibrance = photo.vibrance.coerceIn(-100f, 100f) / 100f
        val warmth = photo.warmth.coerceIn(-100f, 100f) / 100f * 0.12f
        val shadow = photo.shadows.coerceIn(-100f, 100f) / 100f * 0.42f
        val highlight = photo.highlights.coerceIn(-100f, 100f) / 100f * 0.38f
        val black = photo.blacks.coerceIn(-100f, 100f) / 100f * 0.28f
        val vignette = photo.vignette.coerceIn(0f, 100f) / 100f * 0.72f
        val whiteLift = photo.whites.coerceIn(-100f, 100f) / 100f * 0.25f
        val detailFactor = photo.clarity.coerceIn(-100f, 100f) / 100f * 0.65f +
            photo.texture.coerceIn(-100f, 100f) / 100f * 0.38f +
            photo.sharpen.coerceIn(0f, 100f) / 100f * 0.9f
        val mixer = runCatching { JSONObject(photo.colorMixJson) }.getOrDefault(JSONObject())
        val mixerNames = arrayOf("red", "orange", "yellow", "green", "aqua", "blue", "purple", "magenta")
        val mixerCenters = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 275f, 330f)
        val mixerHue = FloatArray(8) { mixer.optDouble(mixerNames[it] + "Hue", 0.0).toFloat().coerceIn(-180f, 180f) }
        val mixerSat = FloatArray(8) { mixer.optDouble(mixerNames[it] + "Saturation", 0.0).toFloat().coerceIn(-100f, 100f) }
        val mixerLum = FloatArray(8) { mixer.optDouble(mixerNames[it] + "Luminance", 0.0).toFloat().coerceIn(-100f, 100f) }
        val hasMixer = mixerHue.any { it != 0f } || mixerSat.any { it != 0f } || mixerLum.any { it != 0f }
        val detailBitmap = if (detailFactor != 0f) {
            val small = Bitmap.createScaledBitmap(bitmap, max(1, width / 7), max(1, height / 7), true)
            val enlarged = Bitmap.createScaledBitmap(small, width, height, true)
            small.recycle()
            enlarged
        } else null
        val detailRow = if (detailBitmap != null) IntArray(width) else null
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            if (detailBitmap != null && detailRow != null) detailBitmap.getPixels(detailRow, 0, width, 0, y, width, 1)
            val ny = (y - centerY) / maxDistance
            for (x in 0 until width) {
                val pixel = row[x]
                val alpha = Color.alpha(pixel)
                if (alpha == 0) continue
                var red = Color.red(pixel) / 255f
                var green = Color.green(pixel) / 255f
                var blue = Color.blue(pixel) / 255f
                val luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 1f)
                val shadowLift = shadow * (1f - luma) * (1f - luma)
                val highlightLift = highlight * luma * luma
                val blackLift = black * (1f - luma) * 0.65f
                val whites = whiteLift * luma * luma * luma
                val tone = exposure
                red = ((red * tone - 0.5f) * contrast + 0.5f) + shadowLift + highlightLift + blackLift + whites + warmth
                green = ((green * tone - 0.5f) * contrast + 0.5f) + shadowLift + highlightLift + blackLift + whites
                blue = ((blue * tone - 0.5f) * contrast + 0.5f) + shadowLift + highlightLift + blackLift + whites - warmth
                val mid = (red + green + blue) / 3f
                val chroma = (max(red, max(green, blue)) - min(red, min(green, blue))).coerceIn(0f, 1f)
                val vibranceFactor = 1f + vibrance * (1f - chroma)
                val satFactor = saturation * vibranceFactor
                red = mid + (red - mid) * satFactor
                green = mid + (green - mid) * satFactor
                blue = mid + (blue - mid) * satFactor
                if (detailBitmap != null && detailRow != null && detailFactor != 0f) {
                    val low = detailRow[x]
                    red += (red - Color.red(low) / 255f) * detailFactor
                    green += (green - Color.green(low) / 255f) * detailFactor
                    blue += (blue - Color.blue(low) / 255f) * detailFactor
                }
                if (hasMixer) {
                    val hsl = rgbToHsl(red, green, blue)
                    var hueShift = 0f
                    var saturationShift = 0f
                    var luminanceShift = 0f
                    for (i in mixerCenters.indices) {
                        if (mixerHue[i] == 0f && mixerSat[i] == 0f && mixerLum[i] == 0f) continue
                        var distance = kotlin.math.abs(hsl[0] - mixerCenters[i])
                        distance = min(distance, 360f - distance)
                        val weight = (1f - distance / 48f).coerceIn(0f, 1f)
                        hueShift += mixerHue[i] * weight
                        saturationShift += mixerSat[i] * weight
                        luminanceShift += mixerLum[i] * weight
                    }
                    val adjusted = hslToRgb(
                        hsl[0] + hueShift,
                        (hsl[1] * (1f + saturationShift / 100f)).coerceIn(0f, 1f),
                        (hsl[2] + luminanceShift / 100f * 0.30f).coerceIn(0f, 1f)
                    )
                    red = adjusted[0]; green = adjusted[1]; blue = adjusted[2]
                }
                val nx = (x - centerX) / maxDistance
                val distance = kotlin.math.sqrt(nx * nx + ny * ny)
                val edge = ((distance - 0.30f) / 0.70f).coerceIn(0f, 1f)
                val vignetteFactor = 1f - vignette * edge * edge
                red = (red * vignetteFactor).coerceIn(0f, 1f)
                green = (green * vignetteFactor).coerceIn(0f, 1f)
                blue = (blue * vignetteFactor).coerceIn(0f, 1f)
                row[x] = Color.argb(alpha, (red * 255f).toInt(), (green * 255f).toInt(), (blue * 255f).toInt())
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        detailBitmap?.recycle()
    }

    private fun rgbToHsl(red: Float, green: Float, blue: Float): FloatArray {
        val r = red.coerceIn(0f, 1f); val g = green.coerceIn(0f, 1f); val b = blue.coerceIn(0f, 1f)
        val maxValue = max(r, max(g, b)); val minValue = min(r, min(g, b))
        val delta = maxValue - minValue
        var hue = 0f
        val light = (maxValue + minValue) / 2f
        var saturation = 0f
        if (delta > 0.0001f) {
            saturation = delta / (1f - kotlin.math.abs(2f * light - 1f)).coerceAtLeast(0.0001f)
            hue = when (maxValue) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            if (hue < 0f) hue += 360f
        }
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), light.coerceIn(0f, 1f))
    }

    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): FloatArray {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f); val l = lightness.coerceIn(0f, 1f)
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val values = when {
            h < 60f -> floatArrayOf(c, x, 0f)
            h < 120f -> floatArrayOf(x, c, 0f)
            h < 180f -> floatArrayOf(0f, c, x)
            h < 240f -> floatArrayOf(0f, x, c)
            h < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return floatArrayOf((values[0] + m).coerceIn(0f, 1f), (values[1] + m).coerceIn(0f, 1f), (values[2] + m).coerceIn(0f, 1f))
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

    private fun injectPngDpi(file: File, dpi: Int) {
        val temp = File(file.parentFile, file.nameWithoutExtension + "-dpi.png")
        val ihdr = "IHDR".toByteArray(Charsets.US_ASCII)
        val iend = "IEND".toByteArray(Charsets.US_ASCII)
        val pixelsPerMeter = (dpi.coerceIn(72, 600) * (1000.0 / 25.4)).toInt()
        val physData = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
            .putInt(pixelsPerMeter).putInt(pixelsPerMeter).put(1.toByte()).array()
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                DataOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->
                    val signature = ByteArray(8)
                    input.readFully(signature)
                    output.write(signature)
                    var finished = false
                    while (!finished) {
                        val length = try { input.readInt() } catch (_: EOFException) { break }
                        require(length >= 0) { "PNG memiliki chunk dengan ukuran tidak valid." }
                        val type = ByteArray(4)
                        input.readFully(type)
                        output.writeInt(length)
                        output.write(type)
                        val crc = CRC32().apply { update(type) }
                        var remaining = length
                        val buffer = ByteArray(64 * 1024)
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) error("PNG terpotong saat menulis metadata DPI.")
                            output.write(buffer, 0, count)
                            crc.update(buffer, 0, count)
                            remaining -= count
                        }
                        output.writeInt(input.readInt())
                        if (type.contentEquals(ihdr)) {
                            val chunkType = "pHYs".toByteArray(Charsets.US_ASCII)
                            val chunkCrc = CRC32().apply { update(chunkType); update(physData) }
                            output.writeInt(physData.size)
                            output.write(chunkType)
                            output.write(physData)
                            output.writeInt(chunkCrc.value.toInt())
                        }
                        if (type.contentEquals(iend)) finished = true
                    }
                }
            }
            if (temp.length() > 0L) {
                if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
            } else error("Gagal menambahkan metadata DPI PNG.")
        } finally {
            if (temp.exists()) temp.delete()
        }
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
                } else {
                    injectPngDpi(temp, dpi)
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