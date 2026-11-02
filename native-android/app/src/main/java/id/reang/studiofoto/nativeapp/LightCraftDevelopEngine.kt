package id.reang.studiofoto.nativeapp

import ai.storyteller.lightcraft.core.MobileSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Reang adapter for LightCraft's native Rust develop engine.
 *
 * Reang remains the owner of its projects, Room rows, cutout, template and export workflow.
 * LightCraft owns a private develop catalog and persists each imported photo's id in Room.
 */
object LightCraftDevelopEngine {
    private const val LIBRARY_FOLDER = "lightcraft-develop-library"
    private val mutex = Mutex()
    @Volatile private var session: MobileSession? = null
    @Volatile private var sessionPath: String? = null

    data class Preview(val photoId: Long, val bitmap: Bitmap)
    data class Developed(val photoId: Long, val path: String)

    private fun getSession(context: Context): MobileSession {
        val directory = File(context.filesDir, LIBRARY_FOLDER).apply { mkdirs() }
        val absolute = directory.absolutePath
        val current = session
        if (current != null && sessionPath == absolute) return current
        return MobileSession.open(absolute, false).also {
            session = it
            sessionPath = absolute
        }
    }

    private fun ensurePhoto(engine: MobileSession, photo: PhotoEntity): Long {
        photo.lightcraftPhotoId?.let { known ->
            if (engine.photo(known.toULong()) != null) return known
        }
        val source = photo.localPath ?: error("Foto asli belum tersedia untuk LightCraft.")
        check(File(source).isFile) { "File foto asli tidak ditemukan: $source" }
        val report = engine.`import`(listOf(source), false, false)
        val id = report.imported.firstOrNull() ?: report.existing.firstOrNull()
            ?: error(report.failed.firstOrNull() ?: "LightCraft gagal mengimpor foto.")
        return id.toLong()
    }

    private fun controlValues(photo: PhotoEntity): JSONObject {
        val values = JSONObject()
        fun put(key: String, value: Float) { values.put(key, value.toDouble()) }

        // Translate Reang's current sliders to the corresponding LightCraft develop controls.
        put("light.exposure", photo.brightness * 1.4f)
        val contrast = photo.contrast.coerceIn(0.5f, 1.8f)
        val contrastValue = if (contrast < 1f) (contrast - 1f) * 200f else (contrast - 1f) * 125f
        put("light.contrast", contrastValue.coerceIn(-100f, 100f))
        put("light.highlights", photo.highlights)
        put("light.shadows", photo.shadows)
        put("light.blacks", photo.blacks)
        put("light.whites", photo.whites)
        val relativeWarmth = photo.warmth.toDouble().coerceIn(-100.0, 100.0)
        val kelvin = (1_000_000.0 / (1_000_000.0 / 6500.0 - relativeWarmth * 0.8)).toFloat()
        put("wb.temp", kelvin)
        put("color.saturation", (photo.saturation - 1f) * 100f)
        put("color.vibrance", photo.vibrance)
        put("effects.dehaze", photo.dehaze)
        put("effects.clarity", photo.clarity)
        put("effects.texture", photo.texture)
        put("detail.sharpenAmount", photo.sharpen * 1.5f)
        put("vignette.amount", -photo.vignette)

        val mixer = runCatching { JSONObject(photo.colorMixJson) }.getOrDefault(JSONObject())
        val colors = arrayOf("red", "orange", "yellow", "green", "aqua", "blue", "purple", "magenta")
        colors.forEach { color ->
            put("mixer.$color.hue", mixer.optDouble(color + "Hue", 0.0).toFloat())
            put("mixer.$color.sat", mixer.optDouble(color + "Saturation", 0.0).toFloat())
            put("mixer.$color.lum", mixer.optDouble(color + "Luminance", 0.0).toFloat())
        }
        return values
    }

    private fun applySettings(engine: MobileSession, photoId: Long, photo: PhotoEntity) {
        engine.openPhoto(photoId.toULong())
        val params = JSONObject().put("values", controlValues(photo)).toString()
        engine.execute("develop.set", params)
    }

    suspend fun renderPreview(context: Context, photo: PhotoEntity, maxSide: Int = 1600): Preview =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val engine = getSession(context.applicationContext)
                val id = ensurePhoto(engine, photo)
                applySettings(engine, id, photo)
                val aspect = NativeImageEngine.imageAspect(photo.localPath)
                val width = maxSide.coerceAtLeast(1)
                val height = (width / aspect.coerceAtLeast(0.05f)).roundToInt().coerceAtLeast(1)
                val rendered = engine.renderView(
                    id.toULong(), width.toUInt(), height.toUInt(), false, false, null
                )
                val bitmap = Bitmap.createBitmap(
                    rendered.width.toInt(), rendered.height.toInt(), Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rendered.rgba))
                Preview(id, bitmap)
            }
        }

    /** Render a full-resolution developed image and store it in Reang's private app directory. */
    suspend fun exportDeveloped(context: Context, photo: PhotoEntity): Developed =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val engine = getSession(context.applicationContext)
                val id = ensurePhoto(engine, photo)
                applySettings(engine, id, photo)
                val options = JSONObject()
                    .put("format", "png")
                    .put("longEdge", 0)
                    .put("colorSpace", "srgb")
                    .toString()
                val exported = engine.export(id.toULong(), options)
                val directory = File(context.filesDir, "lightcraft-developed").apply { mkdirs() }
                val output = File(directory, "photo-${photo.id}-${System.currentTimeMillis()}.png")
                output.writeBytes(exported.bytes)
                check(output.length() > 0L) { "LightCraft menghasilkan file kosong." }
                Developed(id, output.absolutePath)
            }
        }

    fun neutralPhoto(photo: PhotoEntity): PhotoEntity = photo.copy(
        brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f,
        highlights = 0f, shadows = 0f, blacks = 0f, whites = 0f,
        dehaze = 0f, clarity = 0f, texture = 0f, sharpen = 0f,
        vibrance = 0f, vignette = 0f, colorMixJson = "{}"
    )

    /** Save a screen-sized LightCraft render so the existing local brush can blend over it. */
    suspend fun writePreview(context: Context, bitmap: Bitmap, photoId: Long): String =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "lightcraft-preview").apply { mkdirs() }
            val output = File(directory, "photo-$photoId.png")
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Gagal menyimpan pratinjau LightCraft."
                }
            }
            output.absolutePath
        }
}
