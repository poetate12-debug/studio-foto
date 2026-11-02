package id.reang.studiofoto.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONArray
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.roundToInt

private data class SliderHistoryCallbacks(
    val onStart: () -> Unit,
    val onFinish: () -> Unit
)
private val LocalWorkflowSliderHistory = androidx.compose.runtime.compositionLocalOf {
    SliderHistoryCallbacks({}, {})
}

@Composable
fun PhotoWorkflowScreen(
    photo: PhotoEntity,
    projectName: String,
    projectPhotos: List<PhotoEntity>,
    onSelectPhoto: (PhotoEntity) -> Unit,
    assets: List<StudioAssetEntity>,
    lightPresets: List<LightPresetEntity>,
    onSaveLightPreset: (String, String) -> Unit,
    falApiKey: String,
    onBack: () -> Unit,
    onSave: (PhotoEntity) -> Unit,
    onRegisterAsset: (String, String, String) -> Unit,
    onDeleteAsset: (StudioAssetEntity) -> Unit,
    onExportAll: (PhotoEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable(photo.id) { mutableIntStateOf(0) }
    var edited by remember(photo.id) { mutableStateOf(photo) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Foto berada di penyimpanan internal aplikasi.") }
    var cutoutModel by rememberSaveable(photo.id) { mutableStateOf("bria") }
    var cutoutMode by rememberSaveable(photo.id) { mutableStateOf("cutout") }
    var prompt by rememberSaveable(photo.id) { mutableStateOf("taman bunga elegan, cahaya sore lembut, latar realistis") }
    var blurStrength by rememberSaveable(photo.id) { mutableFloatStateOf(16f) }
    var enhanceModel by rememberSaveable(photo.id) { mutableStateOf("topaz-hifi") }
    var scale by rememberSaveable(photo.id) { mutableIntStateOf(2) }
    var objectPrompt by rememberSaveable(photo.id) { mutableStateOf("") }
    var showAssetManager by rememberSaveable(photo.id) { mutableStateOf(false) }
    var presetMenuExpanded by rememberSaveable(photo.id) { mutableStateOf(false) }
    var showSavePresetDialog by rememberSaveable(photo.id) { mutableStateOf(false) }
    var newPresetName by rememberSaveable(photo.id) { mutableStateOf("") }
    var format by rememberSaveable(photo.id) { mutableStateOf(photo.exportFormat) }
    var dpi by rememberSaveable(photo.id) { mutableIntStateOf(photo.exportDpi) }
    var customWidth by rememberSaveable(photo.id) { mutableStateOf(photo.templateWidthCm.toString()) }
    var customHeight by rememberSaveable(photo.id) { mutableStateOf(photo.templateHeightCm.toString()) }
    var brushEnabled by rememberSaveable(photo.id) { mutableStateOf(false) }
    var brushErase by rememberSaveable(photo.id) { mutableStateOf(false) }
    var brushSize by rememberSaveable(photo.id) { mutableFloatStateOf(0.035f) }
    var brushSoftness by rememberSaveable(photo.id) { mutableFloatStateOf(0.65f) }
    var brushStrokes by remember(photo.id) { mutableStateOf(emptyList<CutoutBrushStroke>()) }
    var savedLocalBrushStrokes by remember(photo.id) { mutableStateOf(brushStrokesFromJson(photo.localBrushHistoryJson)) }
    var savedCutoutBrushStrokes by remember(photo.id) { mutableStateOf(brushStrokesFromJson(photo.cutoutBrushHistoryJson)) }
    var faceMaskProcessing by remember(photo.id) { mutableStateOf(false) }
    var faceMaskFaceCount by remember(photo.id) { mutableIntStateOf(0) }
    val undoHistory = remember(photo.id) { mutableStateListOf<PhotoEntity>() }
    val redoHistory = remember(photo.id) { mutableStateListOf<PhotoEntity>() }
    var sliderEditInProgress by remember(photo.id) { mutableStateOf(false) }
    var lightPanel by rememberSaveable(photo.id) { mutableStateOf("cahaya") }
    var photoZoom by rememberSaveable(photo.id) { mutableFloatStateOf(1f) }
    var photoPanX by rememberSaveable(photo.id) { mutableFloatStateOf(0f) }
    var photoPanY by rememberSaveable(photo.id) { mutableFloatStateOf(0f) }
    var showBefore by remember(photo.id) { mutableStateOf(false) }
    var localExposure by rememberSaveable(photo.id) { mutableFloatStateOf(photo.localBrushExposure) }
    var localWarmth by rememberSaveable(photo.id) { mutableFloatStateOf(photo.localBrushWarmth) }
    var localSaturation by rememberSaveable(photo.id) { mutableFloatStateOf(photo.localBrushSaturation) }
    var localTexture by rememberSaveable(photo.id) { mutableFloatStateOf(photo.localBrushTexture) }
    var localRedReduction by rememberSaveable(photo.id) { mutableFloatStateOf(photo.localBrushRedReduction) }
    var selectedColor by rememberSaveable(photo.id) { mutableStateOf("orange") }
    val colorNames = listOf("red", "orange", "yellow", "green", "aqua", "blue", "purple", "magenta")
    val colorLabels = listOf("Merah", "Oranye", "Kuning", "Hijau", "Aqua", "Biru", "Ungu", "Magenta")
    val colorSwatches = listOf(
        Color(0xFFE53935), Color(0xFFF57C22), Color(0xFFFBC02D), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E5BC7), Color(0xFFD64A9B)
    )
    val lightBrushBase = edited.localBrushBasePath ?: edited.localAdjustmentPath ?: edited.enhancedPath ?: edited.localPath
    val brushSourcePath = if (stage == 1) (edited.cutoutBrushBasePath ?: edited.cutoutPath) else lightBrushBase
    val zoomModifier = Modifier.graphicsLayer {
        scaleX = photoZoom
        scaleY = photoZoom
        translationX = photoPanX
        translationY = photoPanY
    }
    val latestBrushStrokes by rememberUpdatedState(brushStrokes)
    val latestSavedLocalBrushStrokes by rememberUpdatedState(savedLocalBrushStrokes)
    val latestSavedCutoutBrushStrokes by rememberUpdatedState(savedCutoutBrushStrokes)
    val latestLightBrushBase by rememberUpdatedState(lightBrushBase)
    val latestEditedPhoto by rememberUpdatedState(edited)
    val latestStage by rememberUpdatedState(stage)
    val latestLocalExposure by rememberUpdatedState(localExposure)
    val latestLocalWarmth by rememberUpdatedState(localWarmth)
    val latestLocalSaturation by rememberUpdatedState(localSaturation)
    val latestLocalTexture by rememberUpdatedState(localTexture)
    val latestLocalRedReduction by rememberUpdatedState(localRedReduction)
    val brushAspect = remember(stage, lightBrushBase, edited.cutoutPath) {
        NativeImageEngine.imageAspect(brushSourcePath)
    }

    fun historySnapshot(): PhotoEntity = edited.copy(
        localBrushExposure = localExposure,
        localBrushWarmth = localWarmth,
        localBrushSaturation = localSaturation,
        localBrushTexture = localTexture,
        localBrushRedReduction = localRedReduction
    )
    fun pushUndoSnapshot() {
        val snapshot = historySnapshot()
        if (undoHistory.lastOrNull() != snapshot) {
            undoHistory.add(snapshot)
            if (undoHistory.size > 40) undoHistory.removeAt(0)
        }
        redoHistory.clear()
    }
    fun restoreHistorySnapshot(snapshot: PhotoEntity) {
        edited = snapshot
        savedLocalBrushStrokes = brushStrokesFromJson(snapshot.localBrushHistoryJson)
        savedCutoutBrushStrokes = brushStrokesFromJson(snapshot.cutoutBrushHistoryJson)
        localExposure = snapshot.localBrushExposure
        localWarmth = snapshot.localBrushWarmth
        localSaturation = snapshot.localBrushSaturation
        localTexture = snapshot.localBrushTexture
        localRedReduction = snapshot.localBrushRedReduction
        brushStrokes = emptyList()
        brushEnabled = false
        onSave(snapshot)
    }
    fun undoEdit() {
        if (undoHistory.isEmpty()) return
        redoHistory.add(historySnapshot())
        val previous = undoHistory.removeAt(undoHistory.lastIndex)
        restoreHistorySnapshot(previous)
        status = "Perubahan terakhir dibatalkan."
    }
    fun redoEdit() {
        if (redoHistory.isEmpty()) return
        undoHistory.add(historySnapshot())
        val next = redoHistory.removeAt(redoHistory.lastIndex)
        restoreHistorySnapshot(next)
        status = "Perubahan dikembalikan."
    }
    fun beginSliderEdit() {
        if (!sliderEditInProgress) {
            pushUndoSnapshot()
            sliderEditInProgress = true
        }
    }
    fun finishSliderEdit() {
        sliderEditInProgress = false
    }
    fun persist() = onSave(historySnapshot())
    fun change(block: (PhotoEntity) -> PhotoEntity) {
        if (!sliderEditInProgress) pushUndoSnapshot()
        edited = block(edited)
    }
    fun changeLight(block: (PhotoEntity) -> PhotoEntity) {
        if (!sliderEditInProgress) pushUndoSnapshot()
        edited = block(edited).copy(
            lightPreset = "Kustom",
            lightcraftRenderedPath = null,
            cutoutPath = null,
            cutoutBrushBasePath = null,
            cutoutBrushHistoryJson = "[]",
            enhancedPath = null,
            aiCompositePath = null
        )
        savedCutoutBrushStrokes = emptyList()
    }
    fun colorValue(component: String): Float =
        runCatching { JSONObject(edited.colorMixJson).optDouble(selectedColor + component, 0.0).toFloat() }.getOrDefault(0f)
    fun updateColorValue(component: String, value: Float) {
        val json = runCatching { JSONObject(edited.colorMixJson) }.getOrDefault(JSONObject())
        json.put(selectedColor + component, value.toDouble())
        changeLight { it.copy(colorMixJson = json.toString()) }
    }
    fun localBrushColorValue(component: String): Float =
        runCatching { JSONObject(edited.localBrushColorMixJson).optDouble(selectedColor + component, 0.0).toFloat() }
            .getOrDefault(0f)
    fun updateLocalBrushColorValue(component: String, value: Float) {
        val json = runCatching { JSONObject(edited.localBrushColorMixJson) }.getOrDefault(JSONObject())
        json.put(selectedColor + component, value.toDouble())
        change { it.copy(localBrushColorMixJson = json.toString()) }
    }

    var previewBitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        stage, brushStrokes, savedLocalBrushStrokes, localExposure, localWarmth, localSaturation, localTexture, localRedReduction,
        edited.localPath, edited.cutoutPath, edited.enhancedPath, edited.aiCompositePath, edited.localAdjustmentPath,
        edited.lightcraftPhotoId, edited.lightcraftRenderedPath,
        edited.backgroundPath, edited.overlayPath, edited.logoPath, edited.brightness,
        edited.contrast, edited.saturation, edited.warmth, edited.highlights, edited.shadows,
        edited.blacks, edited.dehaze, edited.clarity, edited.vibrance, edited.vignette, edited.texture, edited.sharpen, edited.whites, edited.colorMixJson,
        edited.objectX, edited.objectY, edited.objectScale, edited.backgroundScale,
        edited.backgroundX, edited.backgroundY, edited.eventText, edited.eventDate,
        edited.templateWidthCm, edited.templateHeightCm
    ) {
        delay(180)
        val landscape = edited.templateWidthCm >= edited.templateHeightCm
        val previewWidth = if (landscape) 900 else 640
        val previewHeight = if (landscape) 636 else 900
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                if (stage == 0) {
                    val developed = LightCraftDevelopEngine.renderPreview(context, edited, 1600)
                    // Brush mode controls painting input only. Saved mask corrections stay composited
                    // while switching back to global light/color controls, so the preview never drops them.
                    val history = savedLocalBrushStrokes + brushStrokes
                    val display = if (history.isNotEmpty()) {
                        val basePath = LightCraftDevelopEngine.writePreview(context, developed.bitmap, developed.photoId)
                        try {
                            NativeImageEngine.previewLocalBrush(
                                context, basePath, history,
                                localExposure, localWarmth, localSaturation, localTexture,
                                LightCraftDevelopEngine.neutralPhoto(edited), localRedReduction, edited.localBrushColorMixJson
                            )
                        } finally {
                            developed.bitmap.recycle()
                        }
                    } else developed.bitmap
                    developed.photoId to display
                } else {
                    null to NativeImageEngine.render(edited, previewWidth, previewHeight)
                }
            }
        }
        previewBitmap = rendered.getOrNull()?.second
        rendered.getOrNull()?.first?.let { id ->
            if (edited.lightcraftPhotoId != id) {
                val updated = edited.copy(lightcraftPhotoId = id)
                edited = updated
                onSave(updated)
            }
        }
        rendered.exceptionOrNull()?.let { error ->
            status = "Pratinjau LightCraft gagal: ${error.message ?: error.javaClass.simpleName}"
            if (stage == 0) {
                val fallback = withContext(Dispatchers.IO) { runCatching { NativeImageEngine.renderPhotoPreview(edited, 1600) }.getOrNull() }
                previewBitmap = fallback
            }
        }
    }
    if (showAssetManager) {
        AlertDialog(
            onDismissRequest = { showAssetManager = false },
            title = { Text("Koleksi aset tersimpan") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (assets.isEmpty()) {
                        Text("Belum ada aset. Tambahkan background, PNG template, atau logo pada tahap Template.")
                    } else {
                        assets.forEach { asset ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(asset.name, fontWeight = FontWeight.SemiBold)
                                    Text(asset.kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                IconButton(onClick = { onDeleteAsset(asset) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus aset", tint = Color(0xFFFF8D8D))
                                }
                            }
                            Divider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssetManager = false }) { Text("Selesai") }
            }
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Buat preset cahaya") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it.take(32) },
                    singleLine = true,
                    label = { Text("Nama preset") },
                    placeholder = { Text("Contoh: Karnaval Cerah") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPresetName.trim().isNotEmpty(),
                    onClick = {
                        val cleanName = newPresetName.trim()
                        onSaveLightPreset(cleanName, lightSettingsJson(edited))
                        edited = edited.copy(lightPreset = cleanName)
                        onSave(edited)
                        showSavePresetDialog = false
                        newPresetName = ""
                        status = "Preset $cleanName berhasil disimpan."
                    }
                ) { Text("Simpan preset") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Batal") }
            }
        )
    }

    DisposableEffect(previewBitmap) {
        val bitmapToRelease = previewBitmap
        onDispose { bitmapToRelease?.recycle() }
    }

    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "backgrounds").absolutePath }
                .onSuccess { file -> change { it.copy(backgroundPath = file, aiCompositePath = null) }; onRegisterAsset(documentDisplayName(context, uri, "Background"), file, "background"); persist(); status = "Background tersimpan di koleksi dan proyek." }
                .onFailure { status = it.message ?: "Gagal membaca background." }
        }
    }
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "overlays").absolutePath }
                .onSuccess { file -> change { it.copy(overlayPath = file) }; onRegisterAsset(documentDisplayName(context, uri, "Template PNG"), file, "template"); persist(); status = "Template PNG disimpan ke koleksi." }
                .onFailure { status = it.message ?: "Gagal membaca template PNG." }
        }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "logos").absolutePath }
                .onSuccess { file -> change { it.copy(logoPath = file) }; onRegisterAsset(documentDisplayName(context, uri, "Logo"), file, "logo"); persist(); status = "Logo disimpan ke koleksi." }
                .onFailure { status = it.message ?: "Gagal membaca logo." }
        }
    }

    suspend fun bakeLightCraft(sourcePhoto: PhotoEntity): PhotoEntity {
        val developed = LightCraftDevelopEngine.exportDeveloped(context, sourcePhoto)
        val strokes = brushStrokesFromJson(sourcePhoto.localBrushHistoryJson)
        val finalPath = if (strokes.isNotEmpty()) {
            NativeImageEngine.applyLocalBrush(
                context, developed.path, strokes,
                localExposure, localWarmth, localSaturation, localTexture, localRedReduction,
                sourcePhoto.localBrushColorMixJson
            )
        } else developed.path
        return sourcePhoto.copy(
            lightcraftPhotoId = developed.photoId,
            lightcraftRenderedPath = finalPath,
            localBrushExposure = localExposure,
            localBrushWarmth = localWarmth,
            localBrushSaturation = localSaturation,
            localBrushTexture = localTexture,
            localBrushRedReduction = localRedReduction
        )
    }

    fun detectAndCreateFaceMask() {
        val sourcePath = edited.localPath?.takeIf { it.isNotBlank() && File(it).exists() }
        if (sourcePath == null) {
            status = "Foto asli tidak ditemukan. Impor ulang foto sebelum membuat mask."
            return
        }
        if (faceMaskProcessing || working) return
        val inputImage = try {
            InputImage.fromFilePath(context, Uri.fromFile(File(sourcePath)))
        } catch (error: Exception) {
            status = "Foto tidak dapat dibaca untuk deteksi wajah: ${error.message ?: "kesalahan file"}"
            return
        }
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.035f)
            .build()
        val detector = FaceDetection.getClient(options)
        faceMaskProcessing = true
        faceMaskFaceCount = 0
        status = "Mendeteksi kontur wajah utama…"
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val mainFace = faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height().toLong() }
                val faceContour = mainFace?.getContour(FaceContour.FACE)?.points.orEmpty()
                if (mainFace == null || faceContour.size < 8) {
                    status = "Kontur wajah belum terbaca. Pastikan wajah cukup besar, fokus, dan tidak tertutup."
                } else {
                    val protected = listOf(
                        FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE,
                        FaceContour.UPPER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM
                    ).mapNotNull { type ->
                        mainFace.getContour(type)?.points?.takeIf { it.isNotEmpty() }
                    }
                    val maskStrokes = buildFaceContourMaskStrokes(
                        faceContour, protected, inputImage.width, inputImage.height, mainFace.boundingBox
                    )
                    if (maskStrokes.isEmpty()) {
                        status = "Mask wajah gagal dibuat dari kontur. Coba foto dengan wajah lebih jelas."
                    } else {
                        pushUndoSnapshot()
                        val combined = savedLocalBrushStrokes + maskStrokes
                        savedLocalBrushStrokes = combined
                        val updated = edited.copy(
                            localBrushBasePath = edited.localBrushBasePath ?: edited.localPath,
                            localBrushHistoryJson = brushStrokesToJson(combined),
                            localBrushExposure = localExposure,
                            localBrushWarmth = localWarmth,
                            localBrushSaturation = localSaturation,
                            localBrushTexture = localTexture,
                            localBrushRedReduction = localRedReduction,
                            localBrushColorMixJson = edited.localBrushColorMixJson,
                            localAdjustmentPath = null,
                            lightcraftRenderedPath = null
                        )
                        edited = updated
                        onSave(updated)
                        faceMaskFaceCount = 1
                        lightPanel = "wajah"
                        brushEnabled = false
                        brushStrokes = emptyList()
                        status = "Mask kontur wajah utama dibuat. Mata dan bibir dilindungi dari sapuan otomatis; periksa lalu sempurnakan manual bila perlu."
                    }
                }
            }
            .addOnFailureListener { error ->
                status = "Deteksi wajah gagal: ${error.message ?: "model tidak tersedia"}"
            }
            .addOnCompleteListener {
                detector.close()
                faceMaskProcessing = false
            }
    }

    fun runAi(action: String) {
        val key = falApiKey.trim()
        if (key.length < 12) { status = "Masukkan API key fal.ai terlebih dahulu."; return }
        if (edited.localPath.isNullOrBlank()) { status = "File asli belum tersedia. Impor ulang foto ini."; return }
        pushUndoSnapshot()
        scope.launch {
            working = true
            status = "Menyiapkan hasil cahaya LightCraft…"
            try {
                val readyPhoto = bakeLightCraft(edited)
                edited = readyPhoto
                onSave(readyPhoto)
                val source = readyPhoto.lightcraftRenderedPath ?: error("Hasil cahaya LightCraft belum tersedia.")
                status = "Menyiapkan permintaan AI…"
                when (action) {
                    "cutout", "blur" -> {
                        val model = when (cutoutModel) { "birefnet" -> "fal-ai/birefnet/v2"; "rmbg" -> "fal-ai/imageutils/rembg"; else -> "fal-ai/bria/background/remove" }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                        if (cutoutModel == "birefnet") input.put("model", "Portrait").put("operating_resolution", "2048x2048").put("refine_foreground", true).put("output_format", "png")
                        val result = FalAiClient.run(context, model, input, key) { status = it }
                        if (action == "cutout") {
                            change { it.copy(cutoutPath = result.localPath, cutoutBrushBasePath = null, cutoutBrushHistoryJson = "[]", enhancedPath = null, aiCompositePath = null, localAdjustmentPath = null) }
                            savedCutoutBrushStrokes = emptyList()
                            brushStrokes = emptyList()
                            status = "Cutout berhasil. File transparan tersimpan."
                        } else {
                            val blurred = NativeImageEngine.makeBokeh(context, source, result.localPath, blurStrength.roundToInt())
                            change { it.copy(aiCompositePath = blurred, cutoutPath = result.localPath, enhancedPath = null, localAdjustmentPath = null) }
                            status = "Efek bokeh berhasil dibuat."
                        }
                        persist()
                    }
                    "generate" -> {
                        require(prompt.isNotBlank()) { "Isi deskripsi background terlebih dahulu." }
                        status = "Memisahkan objek agar wajah dan tubuh asli tetap dipakai…"
                        val removeModel = when (cutoutModel) {
                            "birefnet" -> "fal-ai/birefnet"
                            "rmbg" -> "fal-ai/imageutils/rembg"
                            else -> "fal-ai/bria/background/remove"
                        }
                        val removeInput = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                        if (cutoutModel == "birefnet") removeInput.put("model", "Portrait").put("operating_resolution", "2048x2048").put("refine_foreground", true).put("output_format", "png")
                        val cutout = FalAiClient.run(context, removeModel, removeInput, key) { status = it }
                        status = "Membuat background baru…"
                        val ratio = if (edited.templateWidthCm >= edited.templateHeightCm) "3:2" else "2:3"
                        val bgInput = JSONObject().put("prompt", prompt)
                            .put("negative_prompt", "person, people, face, body, human, text, watermark, logo, blurry, low quality")
                            .put("num_images", 1).put("aspect_ratio", ratio)
                        val bg = FalAiClient.run(context, "fal-ai/bria/text-to-image/base", bgInput, key) { status = it }
                        onRegisterAsset("Background AI", bg.localPath, "background")
                        change { it.copy(backgroundPath = bg.localPath, cutoutPath = cutout.localPath, cutoutBrushBasePath = null, cutoutBrushHistoryJson = "[]", enhancedPath = null, aiCompositePath = null, localAdjustmentPath = null) }
                        persist()
                        savedCutoutBrushStrokes = emptyList()
                        brushStrokes = emptyList()
                        status = "Background selesai. Objek asli tetap terpisah agar wajah tidak digambar ulang; atur posisinya di tahap Template."
                    }
                    "enhance" -> {
                        if (edited.enhanceStrength <= 0) {
                            change { it.copy(enhancedPath = null) }
                            persist()
                            status = "Kekuatan 0%: objek asli dipertahankan tanpa menjalankan AI atau memakai saldo."
                            return@launch
                        }
                        val subject = edited.cutoutPath ?: source
                        val data = NativeFiles.imageDataUri(subject, 2200, forcePng = enhanceModel == "recraft")
                        val model: String
                        val input: JSONObject
                        when (enhanceModel) {
                            "esrgan" -> {
                                model = "fal-ai/esrgan"
                                input = JSONObject().put("image_url", data).put("scale", scale)
                                    .put("model", if (scale == 4) "RealESRGAN_x4plus" else "RealESRGAN_x2plus")
                                    .put("face", false).put("output_format", "png")
                            }
                            "recraft" -> {
                                model = "fal-ai/recraft/upscale/crisp"
                                input = JSONObject().put("image_url", data).put("enable_safety_checker", true)
                            }
                            else -> {
                                model = "topaz/upscale/image/precision"
                                input = JSONObject().put("image_url", data)
                                    .put("model", if (enhanceModel == "topaz-standard") "Standard V2" else "High Fidelity V3")
                                    .put("upscale_factor", scale).put("output_format", "png")
                                    .put("subject_detection", "Foreground").put("face_enhancement", false)
                                    .put("sharpen", 0.32).put("denoise", 0.16).put("enable_safety_checker", true)
                            }
                        }
                        val result = FalAiClient.run(context, model, input, key) { status = it }
                        val merged = NativeImageEngine.mergeEnhancement(context, subject, result.localPath, edited.enhanceStrength)
                        change { it.copy(enhancedPath = merged, aiCompositePath = null, localAdjustmentPath = null) }
                        persist()
                        status = "Enhance selesai dengan kekuatan " + edited.enhanceStrength + "%."
                    }
                    "object" -> {
                        require(objectPrompt.isNotBlank()) { "Tuliskan objek yang akan dihapus." }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", objectPrompt).put("model", "high_quality").put("mask_expansion", 15)
                        val result = FalAiClient.run(context, "fal-ai/object-removal", input, key) { status = it }
                        change { it.copy(aiCompositePath = result.localPath, enhancedPath = null, localAdjustmentPath = null) }
                        persist()
                        status = "Penghapusan objek selesai. Periksa hasil."
                    }
                    "gaze" -> {
                        val instruction = "Naturally improve visible face clarity and fine details with realistic eyes and natural skin. Preserve exact identity, facial structure, age, expression, hairstyle, skin tone, clothing, pose, number of people and scene. Do not reshape, beautify or replace faces. For unclear or small faces leave unchanged. Photorealistic, subtle and faithful to the original."
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", instruction).put("num_inference_steps", 24).put("guidance_scale", 2.5)
                            .put("num_images", 1).put("enable_safety_checker", true).put("output_format", "jpeg")
                        val result = FalAiClient.run(context, "fal-ai/flux-kontext/dev", input, key) { status = it }
                        change { it.copy(aiCompositePath = result.localPath, enhancedPath = null) }
                        persist()
                        status = "Pemrosesan wajah selesai. Verifikasi wajah asli secara manual."
                    }
                }
            } catch (error: Exception) {
                status = error.message ?: "Pemrosesan AI gagal. Periksa API key, model, koneksi, dan saldo."
            } finally { working = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stage > 0) stage-- else onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF475569)) }
            Column(Modifier.weight(1f)) {
                Text(projectName, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                Text(photo.name, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(
                enabled = undoHistory.isNotEmpty() && !working,
                onClick = { undoEdit() }
            ) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (undoHistory.isNotEmpty()) Color(0xFF475569) else Color(0xFFCBD5E1)) }
            IconButton(
                enabled = redoHistory.isNotEmpty() && !working,
                onClick = { redoEdit() }
            ) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (redoHistory.isNotEmpty()) Color(0xFF475569) else Color(0xFFCBD5E1)) }
            IconButton(onClick = { persist(); status = "Perubahan disimpan." }) { Icon(Icons.Default.Save, contentDescription = "Simpan", tint = Color(0xFFF07824)) }
        }
        Box(
            Modifier.weight(0.9f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF263449))))
                .border(4.dp, Color(0xFF33435A), RoundedCornerShape(0.dp))
                .pointerInput(photo.id, stage, edited.templateWidthCm, edited.templateHeightCm, brushAspect) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val previous = photoZoom
                        val updated = (previous * zoom).coerceIn(1f, 4.5f)
                        if (updated <= 1.01f) {
                            photoZoom = 1f
                            photoPanX = 0f
                            photoPanY = 0f
                        } else {
                            val factor = updated / previous
                            // graphicsLayer scales around the preview center. Keep the point
                            // under the pinch centroid anchored, then add pan movement.
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val nextPanX = photoPanX * factor + (centroid.x - centerX) * (1f - factor) + pan.x
                            val nextPanY = photoPanY * factor + (centroid.y - centerY) * (1f - factor) + pan.y

                            // Bound movement to the visible image area.
                            val imageAspect = if (stage >= 2) {
                                (edited.templateWidthCm / edited.templateHeightCm).coerceAtLeast(0.05f)
                            } else brushAspect.coerceAtLeast(0.05f)
                            val viewportWidth = size.width.toFloat().coerceAtLeast(1f)
                            val viewportHeight = size.height.toFloat().coerceAtLeast(1f)
                            val viewportAspect = viewportWidth / viewportHeight
                            val fitWidth: Float
                            val fitHeight: Float
                            if (viewportAspect > imageAspect) {
                                fitHeight = viewportHeight
                                fitWidth = fitHeight * imageAspect
                            } else {
                                fitWidth = viewportWidth
                                fitHeight = fitWidth / imageAspect
                            }
                            val maxPanX = ((fitWidth * updated - viewportWidth) / 2f).coerceAtLeast(0f)
                            val maxPanY = ((fitHeight * updated - viewportHeight) / 2f).coerceAtLeast(0f)
                            photoPanX = nextPanX.coerceIn(-maxPanX, maxPanX)
                            photoPanY = nextPanY.coerceIn(-maxPanY, maxPanY)
                            photoZoom = updated
                        }
                    }
                }
                .pointerInput(stage, brushEnabled) {
                    if (stage == 0 && !brushEnabled) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val releasedBeforeDelay = withTimeoutOrNull(1000L) {
                                waitForUpOrCancellation()
                                true
                            }
                            if (releasedBeforeDelay == null) {
                                showBefore = true
                                waitForUpOrCancellation()
                                showBefore = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val brushPreview = !showBefore && ((stage == 1 && brushEnabled && !edited.cutoutPath.isNullOrBlank()) || (stage == 0 && brushEnabled))
            if (showBefore && stage == 0) {
                AsyncImage(
                    model = edited.localPath?.let { File(it) } ?: edited.uri,
                    contentDescription = "Before · foto asli",
                    modifier = Modifier.fillMaxSize().then(zoomModifier),
                    contentScale = ContentScale.Fit
                )
            } else if (brushPreview && stage == 0 && previewBitmap != null) {
                androidx.compose.foundation.Image(
                    previewBitmap!!.asImageBitmap(),
                    contentDescription = "Pratinjau kuas lokal",
                    modifier = Modifier.fillMaxSize().then(zoomModifier),
                    contentScale = ContentScale.Fit
                )
            } else if (brushPreview) {
                AsyncImage(model = (if (stage == 1) edited.cutoutPath else brushSourcePath)?.let { File(it) }, contentDescription = "Area kuas",
                    modifier = Modifier.fillMaxSize().then(zoomModifier), contentScale = ContentScale.Fit)
            } else {
                val composedPreview = previewBitmap
                if (composedPreview != null) {
                    androidx.compose.foundation.Image(
                        composedPreview.asImageBitmap(),
                        contentDescription = edited.name,
                        modifier = Modifier.fillMaxSize().then(zoomModifier),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AsyncImage(model = edited.localPath?.let { File(it) } ?: edited.uri, contentDescription = edited.name,
                        modifier = Modifier.fillMaxSize().then(zoomModifier), contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(editorPreviewMatrix(edited)))
                }
            }
            if (brushPreview) {
                Canvas(
                    modifier = Modifier.fillMaxSize().then(zoomModifier).pointerInput(stage, brushErase, brushSize, brushSoftness, brushAspect, working, edited.cutoutPath) {
                        fun normalized(point: Offset): Pair<Float, Float>? {
                            val viewW = size.width.toFloat()
                            val viewH = size.height.toFloat()
                            if (viewW <= 0f || viewH <= 0f || brushAspect <= 0f) return null
                            val viewAspect = viewW / viewH
                            val contentW: Float
                            val contentH: Float
                            val left: Float
                            val top: Float
                            if (viewAspect > brushAspect) {
                                contentH = viewH; contentW = viewH * brushAspect
                                left = (viewW - contentW) / 2f; top = 0f
                            } else {
                                contentW = viewW; contentH = viewW / brushAspect
                                left = 0f; top = (viewH - contentH) / 2f
                            }
                            if (point.x !in left..(left + contentW) || point.y !in top..(top + contentH)) return null
                            return ((point.x - left) / contentW) to ((point.y - top) / contentH)
                        }
                        if (!working) detectDragGestures(
                            onDragEnd = {
                                val pendingStrokes = latestBrushStrokes
                                val stageToSave = latestStage
                                val photoToSave = latestEditedPhoto
                                if (pendingStrokes.isNotEmpty()) {
                                    scope.launch {
                                        working = true
                                        status = "Menyimpan kuas dan mempertahankan area sebelumnya…"
                                        var generatedLightCraftId = photoToSave.lightcraftPhotoId
                                        val result = runCatching {
                                            when (stageToSave) {
                                                0 -> {
                                                    val developed = LightCraftDevelopEngine.exportDeveloped(context, photoToSave)
                                                    generatedLightCraftId = developed.photoId
                                                    val history = latestSavedLocalBrushStrokes + pendingStrokes
                                                    NativeImageEngine.applyLocalBrush(
                                                        context, developed.path, history,
                                                        latestLocalExposure, latestLocalWarmth,
                                                        latestLocalSaturation, latestLocalTexture,
                                                        latestLocalRedReduction, photoToSave.localBrushColorMixJson
                                                    ) to history
                                                }
                                                1 -> {
                                                    val original = photoToSave.lightcraftRenderedPath ?: photoToSave.localPath ?: error("Foto asli tidak ditemukan.")
                                                    val base = photoToSave.cutoutBrushBasePath ?: photoToSave.cutoutPath
                                                        ?: error("Hasil cutout belum tersedia.")
                                                    val history = latestSavedCutoutBrushStrokes + pendingStrokes
                                                    NativeImageEngine.applyCutoutBrush(context, original, base, history) to history
                                                }
                                                else -> error("Kuas hanya tersedia pada tahap Cahaya dan Cutout.")
                                            }
                                        }
                                        result.onSuccess { (savedPath, history) ->
                                            val updated = if (stageToSave == 0) {
                                                photoToSave.copy(
                                                    localAdjustmentPath = savedPath,
                                                    localBrushBasePath = savedPath,
                                                    localBrushHistoryJson = brushStrokesToJson(history),
                                                    localBrushExposure = latestLocalExposure,
                                                    localBrushWarmth = latestLocalWarmth,
                                                    localBrushSaturation = latestLocalSaturation,
                                                    localBrushTexture = latestLocalTexture,
                                                    localBrushRedReduction = latestLocalRedReduction,
                                                    localBrushColorMixJson = photoToSave.localBrushColorMixJson,
                                                    lightcraftPhotoId = generatedLightCraftId,
                                                    lightcraftRenderedPath = savedPath
                                                )
                                            } else {
                                                val base = photoToSave.cutoutBrushBasePath ?: photoToSave.cutoutPath
                                                photoToSave.copy(
                                                    cutoutPath = savedPath,
                                                    cutoutBrushBasePath = base,
                                                    cutoutBrushHistoryJson = brushStrokesToJson(history),
                                                    enhancedPath = null,
                                                    aiCompositePath = null,
                                                    localAdjustmentPath = null
                                                )
                                            }
                                            pushUndoSnapshot()
                                            edited = updated
                                            onSave(updated)
                                            if (stageToSave == 0) savedLocalBrushStrokes = history
                                            else savedCutoutBrushStrokes = history
                                            brushStrokes = emptyList()
                                            status = if (stageToSave == 0) {
                                                "Area kuas cahaya tersimpan dan tetap bisa dilanjutkan."
                                            } else {
                                                "Koreksi cutout tersimpan dan tetap bisa dilanjutkan."
                                            }
                                        }.onFailure { error ->
                                            status = error.message ?: "Gagal menyimpan hasil kuas."
                                        }
                                        working = false
                                    }
                                }
                            },
                            onDragCancel = {
                                status = "Coretan kuas dibatalkan."
                            },
                            onDragStart = { point ->
                                normalized(point)?.let { uv ->
                                    brushStrokes = brushStrokes + CutoutBrushStroke(listOf(uv), brushErase, brushSize, brushSoftness)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                normalized(change.position)?.let { uv ->
                                    val last = brushStrokes.lastOrNull()
                                    if (last != null && last.erase == brushErase && last.widthFraction == brushSize && last.softness == brushSoftness) {
                                        brushStrokes = brushStrokes.dropLast(1) + last.copy(points = last.points + uv)
                                    } else {
                                        brushStrokes = brushStrokes + CutoutBrushStroke(listOf(uv), brushErase, brushSize)
                                    }
                                }
                            }
                        )
                    }
                ) {
                    val viewW = size.width; val viewH = size.height
                    val viewAspect = viewW / viewH
                    val contentW: Float
                    val contentH: Float
                    val left: Float
                    val top: Float
                    if (viewAspect > brushAspect) {
                        contentH = viewH; contentW = viewH * brushAspect
                        left = (viewW - contentW) / 2f; top = 0f
                    } else {
                        contentW = viewW; contentH = viewW / brushAspect
                        left = 0f; top = (viewH - contentH) / 2f
                    }
                    (if (stage == 1) savedCutoutBrushStrokes else savedLocalBrushStrokes).plus(brushStrokes).forEach { stroke ->
                        if (stroke.points.isNotEmpty()) {
                            val path = Path()
                            stroke.points.forEachIndexed { index, uv ->
                                val x = left + uv.first * contentW
                                val y = top + uv.second * contentH
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            val color = if (stroke.erase) Color(0xAAFF5555) else Color(0xAA55E7B5)
                            val lineWidth = stroke.widthFraction * minOf(contentW, contentH)
                            if (stroke.filled) {
                                path.close()
                                drawPath(path, color)
                            } else if (stroke.points.size == 1) {
                                drawCircle(color, lineWidth / 2f, Offset(left + stroke.points[0].first * contentW, top + stroke.points[0].second * contentH))
                            } else {
                                drawPath(path, color, style = Stroke(width = lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                            }
                        }
                    }
                }
            }
            if (stage == 0 && lightPanel == "wajah" && !brushEnabled && savedLocalBrushStrokes.any { it.filled }) {
                Canvas(Modifier.fillMaxSize().then(zoomModifier)) {
                    val viewAspect = size.width / size.height.coerceAtLeast(1f)
                    val contentW: Float
                    val contentH: Float
                    val left: Float
                    val top: Float
                    if (viewAspect > brushAspect) {
                        contentH = size.height
                        contentW = contentH * brushAspect
                        left = (size.width - contentW) / 2f
                        top = 0f
                    } else {
                        contentW = size.width
                        contentH = contentW / brushAspect.coerceAtLeast(0.01f)
                        left = 0f
                        top = (size.height - contentH) / 2f
                    }
                    savedLocalBrushStrokes.filter { it.filled }.forEach { stroke ->
                        if (stroke.points.size >= 3) {
                            val maskPath = Path()
                            stroke.points.forEachIndexed { index, point ->
                                val x = left + point.first * contentW
                                val y = top + point.second * contentH
                                if (index == 0) maskPath.moveTo(x, y) else maskPath.lineTo(x, y)
                            }
                            maskPath.close()
                            drawPath(maskPath, if (stroke.erase) Color(0x88EF5350) else Color(0x7752D6A5))
                        }
                    }
                }
            }
            if (photoZoom > 1.01f) {
                Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color(0xB3080D17), shape = CircleShape) {
                    TextButton(onClick = { photoZoom = 1f; photoPanX = 0f; photoPanY = 0f }) {
                        Text("1×", color = Color.White)
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).padding(9.dp), color = Color(0xB3080D17), shape = RoundedCornerShape(16.dp)) {
                Text(if (showBefore) "BEFORE · FOTO ASLI" else edited.name, color = Color.White, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), maxLines = 1)
            }
        }
        Button(
            onClick = {
                val completedPhoto = edited.copy(isCompleted = true)
                edited = completedPhoto
                onSave(completedPhoto)
                val currentIndex = projectPhotos.indexOfFirst { it.id == photo.id }
                val nextPhoto = if (currentIndex >= 0) {
                    projectPhotos.drop(currentIndex + 1).firstOrNull { !it.isCompleted }
                        ?: projectPhotos.take(currentIndex).firstOrNull { !it.isCompleted }
                } else {
                    projectPhotos.firstOrNull { !it.isCompleted && it.id != photo.id }
                }
                if (nextPhoto != null) {
                    onSelectPhoto(nextPhoto)
                } else {
                    status = "Foto ini selesai. Semua foto lain sudah ditandai selesai."
                }
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text("Tandai selesai · lanjut foto berikutnya")
        }
        Column(
            Modifier.fillMaxWidth().weight(1.25f).background(Color(0xFFF5F7FA))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(listOf("Cahaya", "Cutout", "Template", "Enhance", "Export")[stage], color = Color(0xFF1E293B), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("CONTROL PANEL  •  REANG PHOTO LAB", color = Color(0xFF94A0B4), fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(color = Color(0xFFF1F0FA), shape = RoundedCornerShape(10.dp)) {
                        Text("${stage + 1}/5", color = Color(0xFF514A9A), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(11.dp))
                CompositionLocalProvider(
                    LocalWorkflowSliderHistory provides SliderHistoryCallbacks(
                        onStart = { beginSliderEdit() },
                        onFinish = { finishSliderEdit() }
                    )
                ) {
                when (stage) {
                    0 -> {
                        val activePanelIcon = when (lightPanel) {
                            "warna" -> Icons.Default.Palette
                            "kuas" -> Icons.Default.Brush
                            "wajah" -> Icons.Default.Face
                            else -> Icons.Default.WbSunny
                        }
                        val activePanelTitle = when (lightPanel) {
                            "warna" -> "Color Lab"
                            "kuas" -> "Selective Brush"
                            "wajah" -> "Face Mask Lab"
                            else -> "LightCraft Develop"
                        }
                        val activePanelSubtitle = when (lightPanel) {
                            "warna" -> "Pilih rentang warna dan atur karakter warnanya."
                            "kuas" -> "Koreksi lokal dengan mask yang tetap tersimpan."
                            "wajah" -> "Mask kontur wajah lokal tanpa menggambar ulang identitas."
                            else -> "Kontrol cahaya presisi dengan pratinjau langsung."
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF5F6FB),
                            shape = RoundedCornerShape(19.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E8F1)),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(listOf(Color(0xFF514A9A), Color(0xFF26859A)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(activePanelIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(activePanelTitle, color = Color(0xFF202743), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    Text(activePanelSubtitle, color = Color(0xFF758098), style = MaterialTheme.typography.labelSmall)
                                }
                                Surface(color = Color.White, shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E4EF))) {
                                    Text("LIVE", color = Color(0xFF299B78), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAEDF5)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PremiumEditorTab("Cahaya", Icons.Default.WbSunny, lightPanel == "cahaya", Modifier.weight(1f)) { lightPanel = "cahaya"; brushEnabled = false }
                            PremiumEditorTab("Warna", Icons.Default.Palette, lightPanel == "warna", Modifier.weight(1f)) { lightPanel = "warna"; brushEnabled = false }
                            PremiumEditorTab("Kuas", Icons.Default.Brush, lightPanel == "kuas", Modifier.weight(1f)) { lightPanel = "kuas" }
                            PremiumEditorTab("Wajah", Icons.Default.Face, lightPanel == "wajah", Modifier.weight(1f)) { lightPanel = "wajah"; brushEnabled = false }
                        }
                        when (lightPanel) {
                            "cahaya" -> {
Text("PRESET CAHAYA", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { presetMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Preset: ${edited.lightPreset}", modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih preset")
                            }
                            DropdownMenu(
                                expanded = presetMenuExpanded,
                                onDismissRequest = { presetMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 420.dp)
                            ) {
                                if (lightPresets.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Belum ada preset. Buat preset sendiri.") },
                                        enabled = false,
                                        onClick = {}
                                    )
                                } else {
                                    lightPresets.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text("★ ${preset.name}") },
                                            onClick = {
                                                changeLight { applyCustomLightPreset(it, preset) }
                                                edited = edited.copy(lightPreset = preset.name)
                                                persist()
                                                presetMenuExpanded = false
                                                status = "Preset pribadi ${preset.name} diterapkan."
                                            },
                                            trailingIcon = if (edited.lightPreset == preset.name) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF249B62)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (edited.lightPreset == "Kustom") "Pengaturan manual" else "Preset aktif: ${edited.lightPreset}",
                                color = Color(0xFF64748B),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { newPresetName = ""; showSavePresetDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Buat preset")
                            }
                        }
                        WorkflowSlider("Brightness", edited.brightness, -0.5f..0.5f) { v -> changeLight { it.copy(brightness = v) } }
                        WorkflowSlider("Contrast", edited.contrast, 0.5f..1.8f, resetValue = 1f) { v -> changeLight { it.copy(contrast = v) } }
                        WorkflowSlider("Saturation", edited.saturation, 0f..2f, resetValue = 1f) { v -> changeLight { it.copy(saturation = v) } }
                        WorkflowSlider("Warmth", edited.warmth, -100f..100f) { v -> changeLight { it.copy(warmth = v) } }
                        Divider()
                        Text("TONE & DETAIL", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Highlights", edited.highlights, -100f..100f) { v -> changeLight { it.copy(highlights = v) } }
                        WorkflowSlider("Shadows", edited.shadows, -100f..100f) { v -> changeLight { it.copy(shadows = v) } }
                        WorkflowSlider("Blacks", edited.blacks, -100f..100f) { v -> changeLight { it.copy(blacks = v) } }
                        WorkflowSlider("Whites", edited.whites, -100f..100f) { v -> changeLight { it.copy(whites = v) } }
                        WorkflowSlider("Dehaze", edited.dehaze, -100f..100f) { v -> changeLight { it.copy(dehaze = v) } }
                        Divider()
                        Text("DETAIL", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Clarity", edited.clarity, -100f..100f) { v -> changeLight { it.copy(clarity = v) } }
                        WorkflowSlider("Texture", edited.texture, -100f..100f) { v -> changeLight { it.copy(texture = v) } }
                        WorkflowSlider("Sharpen", edited.sharpen, 0f..100f) { v -> changeLight { it.copy(sharpen = v) } }
                        WorkflowSlider("Vibrance", edited.vibrance, -100f..100f) { v -> changeLight { it.copy(vibrance = v) } }
                        WorkflowSlider("Vignette", edited.vignette, 0f..100f) { v -> changeLight { it.copy(vignette = v) } }
                        Divider()
                        TextButton(onClick = {
                            changeLight { it.copy(brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f, highlights = 0f, shadows = 0f, blacks = 0f, whites = 0f, dehaze = 0f, clarity = 0f, texture = 0f, sharpen = 0f, vibrance = 0f, vignette = 0f, colorMixJson = "{}", localAdjustmentPath = null, localBrushBasePath = null, localBrushHistoryJson = "[]", lightcraftRenderedPath = null) }
                            savedLocalBrushStrokes = emptyList()
                            brushStrokes = emptyList()
                            persist()
                        }) { Text("Reset semua cahaya") }
                            }
                            "warna" -> {
Text("COLOR MIXER · PENGATURAN PER WARNA", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(colorNames.size) { i ->
                                val selected = selectedColor == colorNames[i]
                                Surface(
                                    onClick = { selectedColor = colorNames[i] },
                                    modifier = Modifier.size(42.dp).semantics { contentDescription = colorLabels[i] },
                                    shape = CircleShape,
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (selected) 2.5.dp else 1.dp,
                                        if (selected) Color(0xFF5C55B8) else Color(0xFFD9DFEA)
                                    ),
                                    shadowElevation = if (selected) 3.dp else 0.dp
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().padding(5.dp).clip(CircleShape).background(colorSwatches[i]),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                        WorkflowSlider("Hue / Rona", colorValue("Hue"), -100f..100f) { updateColorValue("Hue", it) }
                        WorkflowSlider("Saturation / Kepekatan", colorValue("Saturation"), -100f..100f) { updateColorValue("Saturation", it) }
                        WorkflowSlider("Luminance / Kecerahan", colorValue("Luminance"), -100f..100f) { updateColorValue("Luminance", it) }
                        TextButton(onClick = {
                            val json = runCatching { JSONObject(edited.colorMixJson) }.getOrDefault(JSONObject())
                            listOf("Hue", "Saturation", "Luminance").forEach { json.put(selectedColor + it, 0) }
                            changeLight { it.copy(colorMixJson = json.toString()) }
                        }) { Text("Reset warna terpilih") }
                        Divider()
                                                    }
                            "kuas" -> {
                        Text("KUAS LOKAL · ATUR AREA TERTENTU", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("Area kuas tetap ditandai setelah tersimpan. Saat kuas diaktifkan kembali, coretan sebelumnya dilanjutkan, bukan dihapus.", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                        Text("Area tersimpan: ${savedLocalBrushStrokes.size} coretan", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(brushEnabled, { brushEnabled = !brushEnabled }, label = { Text(if (brushEnabled) "Kuas aktif" else "Aktifkan kuas") })
                            FilterChip(!brushErase, { brushErase = false }, label = { Text("Tambah area") })
                            FilterChip(brushErase, { brushErase = true }, label = { Text("Kurangi area") })
                        }
                        WorkflowSlider("Ukuran kuas", brushSize * 100f, 1f..12f, resetValue = 3.5f) { brushSize = it / 100f }
                        WorkflowSlider("Kelembutan tepi", brushSoftness * 100f, 0f..100f, resetValue = 65f) { brushSoftness = it / 100f }
                        WorkflowSlider("Exposure lokal", localExposure, -100f..100f) { localExposure = it }
                        WorkflowSlider("Warmth lokal", localWarmth, -100f..100f) { localWarmth = it }
                        WorkflowSlider("Saturation lokal", localSaturation, -100f..100f) { localSaturation = it }
                        WorkflowSlider("Reduksi merah wajah", localRedReduction, 0f..100f, resetValue = 0f) { localRedReduction = it }
                        Text("Mengurangi kejenuhan merah dan oranye hanya pada area yang disapu. Gunakan perlahan agar warna kulit tetap alami.", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Texture lokal", localTexture, -100f..100f) { localTexture = it }
                        Divider()
                        Text("MIX WARNA DI AREA KUAS", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                            items(colorNames.size) { i ->
                                val selected = selectedColor == colorNames[i]
                                Surface(
                                    onClick = { selectedColor = colorNames[i] },
                                    modifier = Modifier.size(34.dp).semantics { contentDescription = colorLabels[i] },
                                    shape = CircleShape,
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color(0xFF5C55B8) else Color(0xFFD9DFEA)
                                    )
                                ) {
                                    Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(colorSwatches[i]), contentAlignment = Alignment.Center) {
                                        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        WorkflowSlider("Hue warna lokal", localBrushColorValue(edited.localBrushColorMixJson, "Hue"), -100f..100f) { updateLocalBrushColorValue("Hue", it) }
                        WorkflowSlider("Saturasi warna lokal", localBrushColorValue(edited.localBrushColorMixJson, "Saturation"), -100f..100f) { updateLocalBrushColorValue("Saturation", it) }
                        WorkflowSlider("Luminansi warna lokal", localBrushColorValue(edited.localBrushColorMixJson, "Luminance"), -100f..100f) { updateLocalBrushColorValue("Luminance", it) }
                        TextButton(onClick = {
                            val json = runCatching { JSONObject(edited.localBrushColorMixJson) }.getOrDefault(JSONObject())
                            listOf("Hue", "Saturation", "Luminance").forEach { json.put(selectedColor + it, 0) }
                            change { it.copy(localBrushColorMixJson = json.toString()) }
                        }) { Text("Reset warna kuas terpilih") }
                        if (savedLocalBrushStrokes.isNotEmpty()) {
                            Button(
                                onClick = {
                                    if (edited.localPath.isNullOrBlank()) {
                                        status = "Foto asli untuk kuas tidak ditemukan."
                                    } else scope.launch {
                                        working = true
                                        status = "Merender ulang area kuas melalui LightCraft…"
                                        runCatching {
                                            val developed = LightCraftDevelopEngine.exportDeveloped(context, edited)
                                            val path = NativeImageEngine.applyLocalBrush(
                                                context, developed.path, savedLocalBrushStrokes,
                                                localExposure, localWarmth, localSaturation, localTexture,
                                                localRedReduction, edited.localBrushColorMixJson
                                            )
                                            developed to path
                                        }.onSuccess { (developed, path) ->
                                            val updated = edited.copy(
                                                lightcraftPhotoId = developed.photoId,
                                                lightcraftRenderedPath = path,
                                                localAdjustmentPath = path,
                                                localBrushBasePath = path,
                                                localBrushExposure = localExposure,
                                                localBrushWarmth = localWarmth,
                                                localBrushSaturation = localSaturation,
                                                localBrushTexture = localTexture,
                                                localBrushRedReduction = localRedReduction,
                                                localBrushColorMixJson = edited.localBrushColorMixJson
                                            )
                                            edited = updated
                                            onSave(updated)
                                            status = "Pengaturan diterapkan ke seluruh area kuas."
                                        }.onFailure { status = it.message ?: "Gagal menerapkan ulang kuas." }
                                        working = false
                                    }
                                },
                                enabled = !working
                            ) { Text("Terapkan pengaturan ke area tersimpan") }
                        }
                        if (edited.localAdjustmentPath != null) {
                            TextButton(onClick = {
                                change { it.copy(localAdjustmentPath = null, localBrushBasePath = null, localBrushHistoryJson = "[]", localBrushColorMixJson = "{}", lightcraftRenderedPath = null) }
                                savedLocalBrushStrokes = emptyList()
                                brushStrokes = emptyList()
                                persist()
                                status = "Penyesuaian dan area kuas direset."
                            }) { Text("Reset area kuas tersimpan") }
                        }
                            }
                            "wajah" -> {
                                Text("MASKING WAJAH OTOMATIS", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "ML Kit membaca kontur wajah utama lalu membentuk area koreksi halus. Ini bukan segmentasi kulit sempurna: periksa hasil dan gunakan Kuas untuk merapikan tepi.",
                                    color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall
                                )
                                Button(
                                    onClick = { detectAndCreateFaceMask() },
                                    enabled = !faceMaskProcessing && !working,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (faceMaskProcessing) {
                                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Default.Face, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (faceMaskProcessing) "Mendeteksi wajah…" else "Deteksi & buat mask wajah")
                                }
                                if (faceMaskFaceCount > 0 || savedLocalBrushStrokes.isNotEmpty()) {
                                    Text(
                                        if (faceMaskFaceCount > 0) "Mask wajah utama tersedia · area mask tersimpan bersama kuas lokal"
                                        else "Area mask lokal tersimpan: ${savedLocalBrushStrokes.size} stroke",
                                        color = Color(0xFF249B78), style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Divider()
                                Text("PENYESUAIAN KULIT", color = Color(0xFFF07824), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                WorkflowSlider("Exposure kulit", localExposure, -100f..100f) { localExposure = it }
                                WorkflowSlider("Warmth kulit", localWarmth, -100f..100f) { localWarmth = it }
                                WorkflowSlider("Saturasi kulit", localSaturation, -100f..100f) { localSaturation = it }
                                WorkflowSlider("Reduksi merah", localRedReduction, 0f..100f, resetValue = 0f) { localRedReduction = it }
                                WorkflowSlider("Tekstur / kelembutan", localTexture, -100f..100f) { localTexture = it }
                                Text("Untuk kulit lebih halus, turunkan Tekstur sedikit. Reduksi merah bekerja lokal agar warna kulit tidak berubah berlebihan.", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                                Button(
                                    onClick = {
                                        if (savedLocalBrushStrokes.isEmpty()) {
                                            status = "Buat mask wajah terlebih dahulu."
                                        } else {
                                            scope.launch {
                                                pushUndoSnapshot()
                                                working = true
                                                status = "Menerapkan koreksi ke area mask wajah…"
                                                runCatching {
                                                    val developed = LightCraftDevelopEngine.exportDeveloped(context, edited)
                                                    val path = NativeImageEngine.applyLocalBrush(
                                                        context, developed.path, savedLocalBrushStrokes,
                                                        localExposure, localWarmth, localSaturation, localTexture,
                                                        localRedReduction, edited.localBrushColorMixJson
                                                    )
                                                    developed to path
                                                }.onSuccess { (developed, path) ->
                                                    val updated = edited.copy(
                                                        lightcraftPhotoId = developed.photoId,
                                                        lightcraftRenderedPath = path,
                                                        localAdjustmentPath = path,
                                                        localBrushBasePath = path,
                                                        localBrushHistoryJson = brushStrokesToJson(savedLocalBrushStrokes),
                                                        localBrushExposure = localExposure,
                                                        localBrushWarmth = localWarmth,
                                                        localBrushSaturation = localSaturation,
                                                        localBrushTexture = localTexture,
                                                        localBrushRedReduction = localRedReduction,
                                                        localBrushColorMixJson = edited.localBrushColorMixJson
                                                    )
                                                    edited = updated
                                                    onSave(updated)
                                                    status = "Koreksi mask wajah diterapkan tanpa mengubah bentuk atau menggambar ulang wajah."
                                                }.onFailure { error ->
                                                    status = error.message ?: "Gagal menerapkan mask wajah."
                                                }
                                                working = false
                                            }
                                        }
                                    },
                                    enabled = !working && !faceMaskProcessing,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Terapkan koreksi wajah") }
                                OutlinedButton(
                                    onClick = {
                                        change {
                                            it.copy(
                                                localAdjustmentPath = null,
                                                localBrushBasePath = null,
                                                localBrushHistoryJson = "[]",
                                                localBrushExposure = 0f,
                                                localBrushWarmth = 0f,
                                                localBrushSaturation = 0f,
                                                localBrushTexture = 0f,
                                                localBrushRedReduction = 0f,
                                                localBrushColorMixJson = "{}",
                                                lightcraftRenderedPath = null
                                            )
                                        }
                                        savedLocalBrushStrokes = emptyList()
                                        brushStrokes = emptyList()
                                        localExposure = 0f
                                        localWarmth = 0f
                                        localSaturation = 0f
                                        localTexture = 0f
                                        localRedReduction = 0f
                                        faceMaskFaceCount = 0
                                        persist()
                                        status = "Mask wajah dan koreksi lokal direset."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Reset mask wajah") }
                                TextButton(onClick = { lightPanel = "kuas"; brushEnabled = true }) {
                                    Text("Sempurnakan mask dengan Kuas")
                                }
                            }
                        }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(cutoutMode == "cutout", { cutoutMode = "cutout" }, label = { Text("Transparan") })
                            FilterChip(cutoutMode == "blur", { cutoutMode = "blur" }, label = { Text("Bokeh") })
                            FilterChip(cutoutMode == "generate", { cutoutMode = "generate" }, label = { Text("Latar AI") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("rmbg" to "RMBG", "birefnet" to "BiRefNet", "bria" to "Bria").forEach { (k, label) -> FilterChip(cutoutModel == k, { cutoutModel = k }, label = { Text(label) }) }
                        }
                        if (cutoutMode == "blur") WorkflowSlider("Kekuatan bokeh", blurStrength, 1f..30f, resetValue = 16f) { blurStrength = it }
                        if (cutoutMode == "generate") OutlinedTextField(prompt, { prompt = it }, label = { Text("Deskripsi background") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(onClick = { runAi(cutoutMode) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "Memproses…" else "Jalankan " + cutoutMode) }
                        if (!edited.cutoutPath.isNullOrBlank()) {
                            Divider()
                            Text("Koreksi tepi cutout", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
                            Text("Aktifkan kuas, lalu sapukan pada foto transparan. Pulihkan mengambil piksel dari foto asli; Hapus membuat area transparan.", color = Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(!brushErase, { brushErase = false; brushEnabled = true }, label = { Text("Pulihkan") })
                                FilterChip(brushErase, { brushErase = true; brushEnabled = true }, label = { Text("Hapus") })
                                FilterChip(brushEnabled, { brushEnabled = !brushEnabled }, label = { Text(if (brushEnabled) "Kuas aktif" else "Kuas mati") })
                            }
                            Text("Area tersimpan: ${savedCutoutBrushStrokes.size} coretan", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                            WorkflowSlider("Ukuran kuas", brushSize * 100f, 1f..12f) { brushSize = it / 100f }
                            WorkflowSlider("Kelembutan tepi", brushSoftness * 100f, 0f..100f, resetValue = 65f) { brushSoftness = it / 100f }
                            OutlinedButton(onClick = { brushStrokes = emptyList() }, enabled = brushStrokes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Batalkan coretan yang belum tersimpan") }

                        }
                    }
                    2 -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            item { FilterChip(edited.templateName == "A3 Landscape", { change { it.copy(templateName = "A3 Landscape", templateWidthCm = 42f, templateHeightCm = 29.7f) }; customWidth = "42"; customHeight = "29.7" }, label = { Text("A3 Landscape") }) }
                            item { FilterChip(edited.templateName == "A3 Portrait", { change { it.copy(templateName = "A3 Portrait", templateWidthCm = 29.7f, templateHeightCm = 42f) }; customWidth = "29.7"; customHeight = "42" }, label = { Text("A3 Portrait") }) }
                            item { FilterChip(edited.templateName == "A4 Portrait", { change { it.copy(templateName = "A4 Portrait", templateWidthCm = 21f, templateHeightCm = 29.7f) }; customWidth = "21"; customHeight = "29.7" }, label = { Text("A4 Portrait") }) }
                            item { FilterChip(edited.templateName == "A4 Landscape", { change { it.copy(templateName = "A4 Landscape", templateWidthCm = 29.7f, templateHeightCm = 21f) }; customWidth = "29.7"; customHeight = "21" }, label = { Text("A4 Landscape") }) }
                            item { FilterChip(edited.templateName == "Bebas", { change { it.copy(templateName = "Bebas") } }, label = { Text("Bebas") }) }
                        }
                        Text("Ukuran khusus (cm)", color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = customWidth, onValueChange = { customWidth = it }, label = { Text("Lebar") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = customHeight, onValueChange = { customHeight = it }, label = { Text("Tinggi") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedButton(onClick = {
                            val w = customWidth.replace(",", ".").toFloatOrNull()
                            val h = customHeight.replace(",", ".").toFloatOrNull()
                            if (w == null || h == null || w !in 5f..200f || h !in 5f..250f) status = "Ukuran harus 5–200 cm untuk lebar dan 5–250 cm untuk tinggi."
                            else { change { it.copy(templateName = "Custom", templateWidthCm = w, templateHeightCm = h) }; status = "Ukuran khusus diterapkan." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Terapkan ukuran khusus") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { pickBackground.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Pilih background") }
                            OutlinedButton(onClick = { pickOverlay.launch(arrayOf("image/png")) }, modifier = Modifier.weight(1f)) { Text("Template PNG") }
                        }
                        OutlinedButton(onClick = { pickLogo.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Tambah logo / ornamen") }
                        TextButton(onClick = { showAssetManager = true }) { Text("Kelola koleksi background, template, dan logo") }
                        if (assets.any { it.kind == "background" }) {
                            Text("BACKGROUND TERSIMPAN", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(assets.filter { it.kind == "background" }) { asset ->
                                    FilterChip(edited.backgroundPath == asset.localPath, { change { it.copy(backgroundPath = asset.localPath, aiCompositePath = null) } }, label = { Text(asset.name, maxLines = 1) })
                                }
                            }
                        }
                        if (assets.any { it.kind == "template" }) {
                            Text("TEMPLATE PNG TERSIMPAN", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(assets.filter { it.kind == "template" }) { asset ->
                                    FilterChip(edited.overlayPath == asset.localPath, { change { it.copy(overlayPath = asset.localPath) } }, label = { Text(asset.name, maxLines = 1) })
                                }
                            }
                        }
                        if (assets.any { it.kind == "logo" }) {
                            Text("LOGO TERSIMPAN", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(assets.filter { it.kind == "logo" }) { asset ->
                                    FilterChip(edited.logoPath == asset.localPath, { change { it.copy(logoPath = asset.localPath) } }, label = { Text(asset.name, maxLines = 1) })
                                }
                            }
                        }
                        Text("ATUR BACKGROUND", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Zoom background", edited.backgroundScale, 0.5f..3f, resetValue = 1f) { v -> change { it.copy(backgroundScale = v) } }
                        WorkflowSlider("Geser background X", edited.backgroundX, -100f..100f) { v -> change { it.copy(backgroundX = v) } }
                        WorkflowSlider("Geser background Y", edited.backgroundY, -100f..100f) { v -> change { it.copy(backgroundY = v) } }
                        Divider()
                        Text("ATUR OBJEK / SUBJEK", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Ukuran objek", edited.objectScale, 0.3f..1.7f, resetValue = 1f) { v -> change { it.copy(objectScale = v) } }
                        WorkflowSlider("Posisi objek X", edited.objectX, -40f..40f) { v -> change { it.copy(objectX = v) } }
                        WorkflowSlider("Posisi objek Y", edited.objectY, -40f..40f) { v -> change { it.copy(objectY = v) } }
                        OutlinedTextField(edited.eventText, { v -> change { it.copy(eventText = v) } }, label = { Text("Nama / tulisan acara") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(edited.eventDate, { v -> change { it.copy(eventDate = v) } }, label = { Text("Tanggal acara") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    3 -> {
                        listOf("topaz-hifi" to "Topaz High Fidelity", "topaz-standard" to "Topaz Standard", "esrgan" to "Real-ESRGAN Natural", "recraft" to "Recraft Crisp").forEach { (k, label) ->
                            FilterChip(enhanceModel == k, { enhanceModel = k }, label = { Text(label) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Upscale", color = Color(0xFF334155), modifier = Modifier.weight(1f))
                            FilterChip(scale == 2, { scale = 2 }, label = { Text("2×") })
                            FilterChip(scale == 4, { scale = 4 }, label = { Text("4×") })
                        }
                        WorkflowSlider("Kekuatan enhance", edited.enhanceStrength.toFloat(), 0f..100f) { v -> change { it.copy(enhanceStrength = v.roundToInt()) } }
                        Button(onClick = { runAi("enhance") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Tingkatkan objek") }
                        Divider()
                        OutlinedTextField(objectPrompt, { objectPrompt = it }, label = { Text("Objek yang ingin dihapus") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedButton(onClick = { runAi("object") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Hapus objek dengan AI") }
                        OutlinedButton(onClick = { runAi("gaze") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Perbaiki detail wajah / arah mata") }
                        Text("Model generatif dapat mengubah detail wajah. Periksa identitas secara manual sebelum cetak.", color = Color(0xFFFFC76A), style = MaterialTheme.typography.labelSmall)
                    }
                    4 -> {
                        Text("Ukuran: " + edited.templateWidthCm + " × " + edited.templateHeightCm + " cm", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(150, 200, 300).forEach { d -> FilterChip(dpi == d, { dpi = d; change { it.copy(exportDpi = d) } }, label = { Text("$d DPI") }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(format == "png", { format = "png"; change { it.copy(exportFormat = "png") } }, label = { Text("PNG") })
                            FilterChip(format == "jpeg", { format = "jpeg"; change { it.copy(exportFormat = "jpeg") } }, label = { Text("JPEG") })
                        }
                        Button(onClick = {
                            scope.launch {
                                working = true
                                persist()
                                status = "Membuat file cetak…"
                                runCatching { NativeImageEngine.export(context, edited.copy(exportDpi = dpi, exportFormat = format), format, dpi) }
                                    .onSuccess { status = "Tersimpan di Pictures/ReangStudioFoto." }
                                    .onFailure { status = it.message ?: "Ekspor gagal." }
                                working = false
                            }
                        }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "Mengekspor…" else "Ekspor foto ini") }
                        OutlinedButton(onClick = { persist(); onExportAll(edited) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Ekspor semua foto proyek") }
                    }
                }
                }
                Spacer(Modifier.height(9.dp))
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, color = if (status.contains("gagal", true)) Color(0xFFFF9C9C) else Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
        }
        if (projectPhotos.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(68.dp).background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val photoPosition = (projectPhotos.indexOfFirst { it.id == photo.id } + 1).coerceAtLeast(1)
                Text("$photoPosition/${projectPhotos.size}", color = Color(0xFF64748B), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 5.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    items(projectPhotos, key = { it.id }) { item ->
                        val thumbAspect = remember(item.id, item.localPath) {
                            NativeImageEngine.imageAspect(item.localPath).coerceIn(0.35f, 3.2f)
                        }
                        val thumbWidth = (58f * thumbAspect).coerceIn(30f, 72f).dp
                        Surface(
                            onClick = { persist(); onSelectPhoto(item) },
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE2E8F0),
                            border = if (item.id == photo.id) androidx.compose.foundation.BorderStroke(3.dp, Color(0xFFF07824)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3DCE8)),
                            modifier = Modifier.width(thumbWidth).height(56.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.localPath?.let { File(it) } ?: item.uri,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = if (item.isCompleted) Color(0xFF16A34A) else Color(0xFFF59E0B),
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(9.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stageNames = listOf("Cahaya", "Cutout", "Template", "Wajah", "Ekspor")
            val stageIcons = listOf(Icons.Default.WbSunny, Icons.Default.ContentCut, Icons.Default.Layers, Icons.Default.AutoAwesome, Icons.Default.FileDownload)
            stageNames.forEachIndexed { index, label ->
                Surface(
                    onClick = {
                        persist()
                        if (working) {
                            // Do not navigate while a render or AI action is still running.
                        } else if (index > 0 && edited.localPath != null) {
                            scope.launch {
                                working = true
                                status = "Menyiapkan foto hasil LightCraft untuk tahap berikutnya…"
                                runCatching { bakeLightCraft(edited) }
                                    .onSuccess { updated ->
                                        edited = updated
                                        onSave(updated)
                                        stage = index
                                        brushEnabled = false
                                        status = "Hasil cahaya LightCraft siap dipakai di tahap berikutnya."
                                    }
                                    .onFailure { status = "Gagal menyiapkan hasil LightCraft: ${it.message ?: "kesalahan tidak diketahui"}" }
                                working = false
                            }
                        } else if (index == 0) {
                            stage = 0
                        } else {
                            stage = index
                        }
                    },
                    color = Color.Transparent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            Modifier.size(29.dp).clip(RoundedCornerShape(50.dp))
                                .background(if (stage == index) Color(0xFFF07824) else Color(0xFFDDF8E8)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stage == index) Text((index + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold)
                            else Icon(stageIcons[index], contentDescription = null, tint = Color(0xFF249B62), modifier = Modifier.size(17.dp))
                        }
                        Text(label, color = if (stage == index) Color(0xFFF07824) else Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun lightSettingsJson(photo: PhotoEntity): String = JSONObject()
    .put("brightness", photo.brightness)
    .put("contrast", photo.contrast)
    .put("saturation", photo.saturation)
    .put("warmth", photo.warmth)
    .put("highlights", photo.highlights)
    .put("shadows", photo.shadows)
    .put("blacks", photo.blacks)
    .put("whites", photo.whites)
    .put("dehaze", photo.dehaze)
    .put("clarity", photo.clarity)
    .put("texture", photo.texture)
    .put("sharpen", photo.sharpen)
    .put("vibrance", photo.vibrance)
    .put("vignette", photo.vignette)
    .put("colorMixJson", photo.colorMixJson)
    .toString()

private fun applyCustomLightPreset(photo: PhotoEntity, preset: LightPresetEntity): PhotoEntity {
    val settings = JSONObject(preset.settingsJson)
    return photo.copy(
        brightness = settings.optDouble("brightness", 0.0).toFloat(),
        contrast = settings.optDouble("contrast", 1.0).toFloat(),
        saturation = settings.optDouble("saturation", 1.0).toFloat(),
        warmth = settings.optDouble("warmth", 0.0).toFloat(),
        highlights = settings.optDouble("highlights", 0.0).toFloat(),
        shadows = settings.optDouble("shadows", 0.0).toFloat(),
        blacks = settings.optDouble("blacks", 0.0).toFloat(),
        whites = settings.optDouble("whites", 0.0).toFloat(),
        dehaze = settings.optDouble("dehaze", 0.0).toFloat(),
        clarity = settings.optDouble("clarity", 0.0).toFloat(),
        texture = settings.optDouble("texture", 0.0).toFloat(),
        sharpen = settings.optDouble("sharpen", 0.0).toFloat(),
        vibrance = settings.optDouble("vibrance", 0.0).toFloat(),
        vignette = settings.optDouble("vignette", 0.0).toFloat(),
        colorMixJson = settings.optString("colorMixJson", "{}"),
        lightPreset = preset.name
    )
}

private val BUILT_IN_LIGHT_PRESETS = listOf(
    "Asli", "Natural", "Cerah", "Hangat", "Vivid", "Potret Lembut", "Karnaval", "Dramatis", "Matte", "Hitam Putih"
)

private fun applyBuiltInLightPreset(photo: PhotoEntity, name: String): PhotoEntity {
    val base = photo.copy(
        brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f,
        highlights = 0f, shadows = 0f, blacks = 0f, whites = 0f,
        dehaze = 0f, clarity = 0f, texture = 0f, sharpen = 0f,
        vibrance = 0f, vignette = 0f, colorMixJson = "{}",
        lightPreset = name
    )
    return when (name) {
        "Natural" -> base.copy(
            brightness = 0.05f, contrast = 1.06f, saturation = 1.07f, warmth = 4f,
            highlights = -14f, shadows = 18f, blacks = -5f, whites = 9f,
            dehaze = 5f, clarity = 7f, texture = 7f, sharpen = 14f,
            vibrance = 15f, vignette = 5f
        )
        "Cerah" -> base.copy(
            brightness = 0.15f, contrast = 1.03f, saturation = 1.05f, warmth = 2f,
            highlights = -18f, shadows = 28f, blacks = -6f, whites = 17f,
            dehaze = 2f, clarity = 4f, texture = 4f, sharpen = 12f,
            vibrance = 12f, vignette = 0f
        )
        "Hangat" -> base.copy(
            brightness = 0.04f, contrast = 1.08f, saturation = 1.12f, warmth = 22f,
            highlights = -20f, shadows = 16f, blacks = -5f, whites = 8f,
            dehaze = 1f, clarity = 7f, texture = 5f, sharpen = 10f,
            vibrance = 16f, vignette = 9f
        )
        "Vivid" -> base.copy(
            brightness = 0.02f, contrast = 1.16f, saturation = 1.24f, warmth = 0f,
            highlights = -12f, shadows = 10f, blacks = -12f, whites = 14f,
            dehaze = 10f, clarity = 16f, texture = 12f, sharpen = 20f,
            vibrance = 28f, vignette = 7f,
            colorMixJson = JSONObject()
                .put("orangeSaturation", 12).put("greenSaturation", 12)
                .put("yellowSaturation", 8).toString()
        )
        "Potret Lembut" -> base.copy(
            brightness = 0.08f, contrast = 0.97f, saturation = 0.98f, warmth = 8f,
            highlights = -24f, shadows = 24f, blacks = 5f, whites = 9f,
            dehaze = -3f, clarity = -12f, texture = -9f, sharpen = 8f,
            vibrance = 8f, vignette = 5f,
            colorMixJson = JSONObject()
                .put("orangeLuminance", 8).put("orangeSaturation", 4)
                .put("redSaturation", -4).toString()
        )
        "Karnaval" -> base.copy(
            brightness = 0.04f, contrast = 1.12f, saturation = 1.18f, warmth = 6f,
            highlights = -18f, shadows = 20f, blacks = -8f, whites = 12f,
            dehaze = 12f, clarity = 14f, texture = 12f, sharpen = 20f,
            vibrance = 26f, vignette = 4f,
            colorMixJson = JSONObject()
                .put("orangeSaturation", 10).put("orangeLuminance", 5)
                .put("greenSaturation", 14).put("greenLuminance", 4)
                .put("redSaturation", 8).put("yellowSaturation", 7).toString()
        )
        "Dramatis" -> base.copy(
            brightness = -0.03f, contrast = 1.25f, saturation = 1.05f, warmth = -4f,
            highlights = -24f, shadows = -8f, blacks = -18f, whites = 5f,
            dehaze = 18f, clarity = 19f, texture = 11f, sharpen = 22f,
            vibrance = 10f, vignette = 22f
        )
        "Matte" -> base.copy(
            brightness = 0.05f, contrast = 0.91f, saturation = 0.95f, warmth = 4f,
            highlights = -18f, shadows = 15f, blacks = 18f, whites = -5f,
            dehaze = -3f, clarity = -8f, texture = -5f, sharpen = 7f,
            vibrance = -5f, vignette = 6f
        )
        "Hitam Putih" -> base.copy(
            brightness = 0.02f, contrast = 1.14f, saturation = 0f, warmth = 0f,
            highlights = -18f, shadows = 12f, blacks = -10f, whites = 8f,
            dehaze = 6f, clarity = 12f, texture = 8f, sharpen = 18f,
            vibrance = 0f, vignette = 12f
        )
        else -> base
    }
}

@Composable
private fun PremiumEditorTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        color = if (selected) Color(0xFF292B50) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else Color(0xFF79839A), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) Color.White else Color(0xFF657089), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun WorkflowSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    resetValue: Float = 0f,
    onChange: (Float) -> Unit
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val latestResetValue by rememberUpdatedState(resetValue)
    val historyCallbacks = LocalWorkflowSliderHistory.current
    var gestureStarted by remember { mutableStateOf(false) }
    fun startGesture() {
        if (!gestureStarted) {
            gestureStarted = true
            historyCallbacks.onStart()
        }
    }
    fun finishGesture() {
        if (gestureStarted) {
            gestureStarted = false
            historyCallbacks.onFinish()
        }
    }
    val low = range.start
    val high = range.endInclusive
    val safeValue = value.coerceIn(low, high)
    val valueText = if ((high - low) <= 3f) "%.2f".format(safeValue) else "%.0f".format(safeValue)

    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF334155), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            Surface(
                onClick = {
                    startGesture()
                    latestOnChange(latestResetValue.coerceIn(low, high))
                    finishGesture()
                },
                color = Color(0xFFFFF1E7),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFAD9C4))
            ) {
                Text(
                    valueText,
                    color = Color(0xFFB9571C),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Slider(
            value = safeValue,
            onValueChange = {
                startGesture()
                latestOnChange(it.coerceIn(low, high))
            },
            onValueChangeFinished = { finishGesture() },
            modifier = Modifier.fillMaxWidth().height(30.dp),
            valueRange = low..high,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFF07824),
                activeTrackColor = Color(0xFFF07824),
                inactiveTrackColor = Color(0xFFE2E8F0),
                activeTickColor = Color(0xFFF07824),
                inactiveTickColor = Color(0xFFE2E8F0)
            )
        )
    }
}


private fun editorPreviewMatrix(photo: PhotoEntity): ColorMatrix {
    val saturation = photo.saturation.coerceIn(0f, 2f)
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    val contrast = photo.contrast.coerceIn(0.5f, 1.8f)
    val offset = photo.brightness.coerceIn(-0.6f, 0.6f) * 255f
    val warmth = photo.warmth.coerceIn(-100f, 100f) * 0.55f
    return ColorMatrix(floatArrayOf(
        contrast * (r + saturation), contrast * g, contrast * b, 0f, offset + warmth,
        contrast * r, contrast * (g + saturation), contrast * b, 0f, offset,
        contrast * r, contrast * g, contrast * (b + saturation), 0f, offset - warmth,
        0f, 0f, 0f, 1f, 0f
    ))
}


private fun brushStrokesFromJson(json: String): List<CutoutBrushStroke> = runCatching {
    val array = JSONArray(json)
    buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val encodedPoints = item.optJSONArray("points") ?: JSONArray()
            val points = buildList {
                for (j in 0 until encodedPoints.length()) {
                    val point = encodedPoints.optJSONArray(j) ?: continue
                    if (point.length() >= 2) add(point.optDouble(0).toFloat() to point.optDouble(1).toFloat())
                }
            }
            if (points.isNotEmpty()) add(
                CutoutBrushStroke(
                    points = points,
                    erase = item.optBoolean("erase", false),
                    widthFraction = item.optDouble("width", 0.035).toFloat(),
                    softness = item.optDouble("softness", 0.65).toFloat(),
                    filled = item.optBoolean("filled", false)
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun brushStrokesToJson(strokes: List<CutoutBrushStroke>): String {
    val array = JSONArray()
    strokes.forEach { stroke ->
        val points = JSONArray()
        stroke.points.forEach { point -> points.put(JSONArray().put(point.first.toDouble()).put(point.second.toDouble())) }
        array.put(
            JSONObject()
                .put("erase", stroke.erase)
                .put("width", stroke.widthFraction.toDouble())
                .put("softness", stroke.softness.toDouble())
                .put("filled", stroke.filled)
                .put("points", points)
        )
    }
    return array.toString()
}

private fun buildFaceContourMaskStrokes(
    faceContour: List<PointF>,
    protectedContours: List<List<PointF>>,
    imageWidth: Int,
    imageHeight: Int,
    faceBounds: android.graphics.Rect
): List<CutoutBrushStroke> {
    if (imageWidth <= 0 || imageHeight <= 0) return emptyList()

    val outline = if (faceContour.size >= 8) {
        faceContour
    } else {
        // Contour mode may omit outlines on small or angled faces. Use a conservative
        // inset oval from the detector's face bounds as a usable fallback.
        val left = faceBounds.left + faceBounds.width() * 0.12f
        val right = faceBounds.right - faceBounds.width() * 0.12f
        val top = faceBounds.top + faceBounds.height() * 0.12f
        val bottom = faceBounds.bottom - faceBounds.height() * 0.10f
        if (right <= left || bottom <= top) return emptyList()
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f
        val ry = (bottom - top) / 2f
        (0 until 64).map { index ->
            val angle = (index * 2.0 * Math.PI / 64.0)
            PointF(cx + kotlin.math.cos(angle).toFloat() * rx, cy + kotlin.math.sin(angle).toFloat() * ry)
        }
    }

    fun normalizedPolygon(points: List<PointF>, scale: Float = 1f): List<Pair<Float, Float>> {
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        return points.map { point ->
            val x = centerX + (point.x - centerX) * scale
            val y = centerY + (point.y - centerY) * scale
            (x / imageWidth.toFloat()).coerceIn(0f, 1f) to
                (y / imageHeight.toFloat()).coerceIn(0f, 1f)
        }
    }

    val facePolygon = normalizedPolygon(outline, 0.92f)
    if (facePolygon.size < 8) return emptyList()
    val minX = facePolygon.minOf { it.first }
    val maxX = facePolygon.maxOf { it.first }
    val minY = facePolygon.minOf { it.second }
    val maxY = facePolygon.maxOf { it.second }
    if ((maxX - minX) * imageWidth < 55f || (maxY - minY) * imageHeight < 70f) return emptyList()

    val mask = mutableListOf(
        CutoutBrushStroke(
            points = facePolygon,
            erase = false,
            widthFraction = 0.010f,
            softness = 0.72f,
            filled = true
        )
    )
    protectedContours.forEach { contour ->
        if (contour.size >= 5) {
            val isEye = contour.size >= 15
            mask.add(
                CutoutBrushStroke(
                    points = normalizedPolygon(contour, if (isEye) 1.28f else 1.22f),
                    erase = true,
                    widthFraction = 0.006f,
                    softness = 0.18f,
                    filled = true
                )
            )
        }
    }
    return mask
}

private fun documentDisplayName(context: android.content.Context, uri: android.net.Uri, fallback: String): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}
