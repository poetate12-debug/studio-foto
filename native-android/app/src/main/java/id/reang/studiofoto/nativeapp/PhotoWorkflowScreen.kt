package id.reang.studiofoto.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun PhotoWorkflowScreen(
    photo: PhotoEntity,
    assets: List<StudioAssetEntity>,
    falApiKey: String,
    onFalApiKeyChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: (PhotoEntity) -> Unit,
    onRegisterAsset: (String, String, String) -> Unit,
    onExportAll: () -> Unit
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
    var format by rememberSaveable(photo.id) { mutableStateOf(photo.exportFormat) }
    var dpi by rememberSaveable(photo.id) { mutableIntStateOf(photo.exportDpi) }
    var brushEnabled by rememberSaveable(photo.id) { mutableStateOf(false) }
    var brushErase by rememberSaveable(photo.id) { mutableStateOf(false) }
    var brushSize by rememberSaveable(photo.id) { mutableFloatStateOf(0.035f) }
    var brushStrokes by remember(photo.id) { mutableStateOf(emptyList<CutoutBrushStroke>()) }
    val brushAspect = remember(edited.cutoutPath, edited.localPath) {
        NativeImageEngine.imageAspect(edited.cutoutPath ?: edited.localPath)
    }

    fun persist() = onSave(edited)
    fun change(block: (PhotoEntity) -> PhotoEntity) { edited = block(edited) }

    var previewBitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        edited.localPath, edited.cutoutPath, edited.enhancedPath, edited.aiCompositePath,
        edited.backgroundPath, edited.overlayPath, edited.logoPath, edited.brightness,
        edited.contrast, edited.saturation, edited.warmth, edited.highlights, edited.shadows,
        edited.blacks, edited.dehaze, edited.clarity, edited.vibrance, edited.vignette,
        edited.objectX, edited.objectY, edited.objectScale, edited.backgroundScale,
        edited.backgroundX, edited.backgroundY, edited.eventText, edited.eventDate,
        edited.templateWidthCm, edited.templateHeightCm
    ) {
        delay(160)
        val landscape = edited.templateWidthCm >= edited.templateHeightCm
        val previewWidth = if (landscape) 900 else 640
        val previewHeight = if (landscape) 636 else 900
        previewBitmap = withContext(Dispatchers.IO) {
            runCatching { NativeImageEngine.render(edited, previewWidth, previewHeight) }.getOrNull()
        }
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

    fun runAi(action: String) {
        val key = falApiKey.trim()
        val source = edited.localPath
        if (key.length < 12) { status = "Masukkan API key fal.ai terlebih dahulu."; return }
        if (source.isNullOrBlank()) { status = "File asli belum tersedia. Impor ulang foto ini."; return }
        scope.launch {
            working = true
            status = "Menyiapkan permintaan AI…"
            try {
                when (action) {
                    "cutout", "blur" -> {
                        val model = when (cutoutModel) { "birefnet" -> "fal-ai/birefnet"; "rmbg" -> "fal-ai/imageutils/rembg"; else -> "fal-ai/bria/background/remove" }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                        if (cutoutModel == "birefnet") input.put("model", "General Use (Light)")
                        val result = FalAiClient.run(context, model, input, key) { status = it }
                        if (action == "cutout") {
                            change { it.copy(cutoutPath = result.localPath, enhancedPath = null, aiCompositePath = null) }
                            status = "Cutout berhasil. File transparan tersimpan."
                        } else {
                            val blurred = NativeImageEngine.makeBokeh(context, source, result.localPath, blurStrength.roundToInt())
                            change { it.copy(aiCompositePath = blurred, cutoutPath = result.localPath, enhancedPath = null) }
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
                        if (cutoutModel == "birefnet") removeInput.put("model", "General Use (Light)")
                        val cutout = FalAiClient.run(context, removeModel, removeInput, key) { status = it }
                        status = "Membuat background baru…"
                        val ratio = if (edited.templateWidthCm >= edited.templateHeightCm) "3:2" else "2:3"
                        val bgInput = JSONObject().put("prompt", prompt)
                            .put("negative_prompt", "person, people, face, body, human, text, watermark, logo, blurry, low quality")
                            .put("num_images", 1).put("aspect_ratio", ratio)
                        val bg = FalAiClient.run(context, "fal-ai/bria/text-to-image/base", bgInput, key) { status = it }
                        change { it.copy(backgroundPath = bg.localPath, cutoutPath = cutout.localPath, enhancedPath = null, aiCompositePath = null) }
                        persist()
                        status = "Background selesai. Objek asli tetap terpisah agar wajah tidak digambar ulang; atur posisinya di tahap Template."
                    }
                    "enhance" -> {
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
                        change { it.copy(enhancedPath = merged, aiCompositePath = null) }
                        persist()
                        status = "Enhance selesai dengan kekuatan " + edited.enhanceStrength + "%."
                    }
                    "object" -> {
                        require(objectPrompt.isNotBlank()) { "Tuliskan objek yang akan dihapus." }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", objectPrompt).put("model", "high_quality").put("mask_expansion", 15)
                        val result = FalAiClient.run(context, "fal-ai/object-removal", input, key) { status = it }
                        change { it.copy(aiCompositePath = result.localPath, enhancedPath = null) }
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

    Column(Modifier.fillMaxSize().background(Color(0xFF090D17))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stage > 0) stage-- else onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Studio Editor", color = Color.White, fontWeight = FontWeight.Bold)
                Text("TAHAP " + (stage + 1) + "/5 · " + listOf("CAHAYA", "CUTOUT", "TEMPLATE", "ENHANCE", "EXPORT")[stage], color = Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { persist(); status = "Perubahan disimpan." }) { Text("Simpan", color = Color(0xFFB6A7FF)) }
        }
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("Cahaya", "Cutout", "Template", "Enhance", "Export").size) { i ->
                val names = listOf("Cahaya", "Cutout", "Template", "Enhance", "Export")
                FilterChip(stage == i, { persist(); stage = i }, label = { Text(names[i]) })
            }
        }
        Box(Modifier.weight(0.9f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(20.dp)).background(Color(0xFF05070D)), contentAlignment = Alignment.Center) {
            val brushPreview = stage == 1 && brushEnabled && !edited.cutoutPath.isNullOrBlank()
            if (brushPreview) {
                AsyncImage(model = edited.cutoutPath, contentDescription = "Cutout transparan",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                val composedPreview = previewBitmap
                if (composedPreview != null) {
                    androidx.compose.foundation.Image(
                        composedPreview.asImageBitmap(),
                        contentDescription = edited.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AsyncImage(model = edited.localPath ?: edited.uri, contentDescription = edited.name,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(editorPreviewMatrix(edited)))
                }
            }
            if (brushPreview) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(brushErase, brushSize, brushAspect) {
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
                        detectDragGestures(
                            onDragStart = { point ->
                                normalized(point)?.let { uv ->
                                    brushStrokes = brushStrokes + CutoutBrushStroke(listOf(uv), brushErase, brushSize)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                normalized(change.position)?.let { uv ->
                                    val last = brushStrokes.lastOrNull()
                                    if (last != null && last.erase == brushErase && last.widthFraction == brushSize) {
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
                    brushStrokes.forEach { stroke ->
                        if (stroke.points.isNotEmpty()) {
                            val path = Path()
                            stroke.points.forEachIndexed { index, uv ->
                                val x = left + uv.first * contentW
                                val y = top + uv.second * contentH
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            val color = if (stroke.erase) Color(0xAAFF5555) else Color(0xAA55E7B5)
                            val lineWidth = stroke.widthFraction * minOf(contentW, contentH)
                            if (stroke.points.size == 1) {
                                drawCircle(color, lineWidth / 2f, Offset(left + stroke.points[0].first * contentW, top + stroke.points[0].second * contentH))
                            } else {
                                drawPath(path, color, style = Stroke(width = lineWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                            }
                        }
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).padding(9.dp), color = Color(0xB3080D17), shape = RoundedCornerShape(16.dp)) {
                Text(edited.name, color = Color.White, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), maxLines = 1)
            }
        }
        Surface(color = Color(0xFF141C2B), shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), modifier = Modifier.fillMaxWidth().weight(1.25f)) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 12.dp)) {
                Text(listOf("Cahaya", "Cutout", "Template", "Enhance", "Export")[stage], color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                when (stage) {
                    0 -> {
                        WorkflowSlider("Brightness", edited.brightness, -0.5f..0.5f) { v -> change { it.copy(brightness = v) } }
                        WorkflowSlider("Contrast", edited.contrast, 0.5f..1.8f) { v -> change { it.copy(contrast = v) } }
                        WorkflowSlider("Saturation", edited.saturation, 0f..2f) { v -> change { it.copy(saturation = v) } }
                        WorkflowSlider("Warmth", edited.warmth, -100f..100f) { v -> change { it.copy(warmth = v) } }
                        Divider()
                        Text("TONE & DETAIL", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Highlights", edited.highlights, -100f..100f) { v -> change { it.copy(highlights = v) } }
                        WorkflowSlider("Shadows", edited.shadows, -100f..100f) { v -> change { it.copy(shadows = v) } }
                        WorkflowSlider("Blacks", edited.blacks, -100f..100f) { v -> change { it.copy(blacks = v) } }
                        WorkflowSlider("Dehaze", edited.dehaze, -100f..100f) { v -> change { it.copy(dehaze = v) } }
                        WorkflowSlider("Clarity", edited.clarity, -100f..100f) { v -> change { it.copy(clarity = v) } }
                        WorkflowSlider("Vibrance", edited.vibrance, -100f..100f) { v -> change { it.copy(vibrance = v) } }
                        WorkflowSlider("Vignette", edited.vignette, 0f..100f) { v -> change { it.copy(vignette = v) } }
                        TextButton(onClick = { change { it.copy(brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f, highlights = 0f, shadows = 0f, blacks = 0f, dehaze = 0f, clarity = 0f, vibrance = 0f, vignette = 0f) } }) { Text("Reset semua cahaya") }
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
                        if (cutoutMode == "blur") WorkflowSlider("Kekuatan bokeh", blurStrength, 1f..30f) { blurStrength = it }
                        if (cutoutMode == "generate") OutlinedTextField(prompt, { prompt = it }, label = { Text("Deskripsi background") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(falApiKey, onFalApiKeyChange, label = { Text("API key fal.ai (sesi ini)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { runAi(cutoutMode) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "Memproses…" else "Jalankan " + cutoutMode) }
                        if (!edited.cutoutPath.isNullOrBlank()) {
                            Divider()
                            Text("Koreksi tepi cutout", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Aktifkan kuas, lalu sapukan pada foto transparan. Pulihkan mengambil piksel dari foto asli; Hapus membuat area transparan.", color = Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(!brushErase, { brushErase = false; brushEnabled = true }, label = { Text("Pulihkan") })
                                FilterChip(brushErase, { brushErase = true; brushEnabled = true }, label = { Text("Hapus") })
                                FilterChip(brushEnabled, { brushEnabled = !brushEnabled }, label = { Text(if (brushEnabled) "Kuas aktif" else "Kuas mati") })
                            }
                            WorkflowSlider("Ukuran kuas", brushSize * 100f, 1f..12f) { brushSize = it / 100f }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Button(onClick = {
                                    val source = edited.localPath
                                    val cutout = edited.cutoutPath
                                    if (source != null && cutout != null && brushStrokes.isNotEmpty()) {
                                        scope.launch {
                                            working = true
                                            status = "Menerapkan koreksi kuas…"
                                            runCatching { NativeImageEngine.applyCutoutBrush(context, source, cutout, brushStrokes) }
                                                .onSuccess { file ->
                                                    change { it.copy(cutoutPath = file, enhancedPath = null, aiCompositePath = null) }
                                                    persist()
                                                    brushStrokes = emptyList()
                                                    status = "Koreksi kuas tersimpan."
                                                }
                                                .onFailure { status = it.message ?: "Koreksi kuas gagal." }
                                            working = false
                                        }
                                    }
                                }, enabled = !working && brushStrokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Terapkan kuas") }
                                OutlinedButton(onClick = { brushStrokes = emptyList() }, enabled = brushStrokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Bersihkan coretan") }
                            }
                        }
                    }
                    2 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(edited.templateName == "A3 Landscape", { change { it.copy(templateName = "A3 Landscape", templateWidthCm = 42f, templateHeightCm = 29.7f) } }, label = { Text("A3 Landscape") })
                            FilterChip(edited.templateName == "A4 Portrait", { change { it.copy(templateName = "A4 Portrait", templateWidthCm = 21f, templateHeightCm = 29.7f) } }, label = { Text("A4 Portrait") })
                            FilterChip(edited.templateName == "Bebas", { change { it.copy(templateName = "Bebas") } }, label = { Text("Bebas") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { pickBackground.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Pilih background") }
                            OutlinedButton(onClick = { pickOverlay.launch(arrayOf("image/png")) }, modifier = Modifier.weight(1f)) { Text("Template PNG") }
                        }
                        OutlinedButton(onClick = { pickLogo.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Tambah logo / ornamen") }
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
                        WorkflowSlider("Zoom background", edited.backgroundScale, 0.5f..3f) { v -> change { it.copy(backgroundScale = v) } }
                        WorkflowSlider("Geser background X", edited.backgroundX, -100f..100f) { v -> change { it.copy(backgroundX = v) } }
                        WorkflowSlider("Geser background Y", edited.backgroundY, -100f..100f) { v -> change { it.copy(backgroundY = v) } }
                        Divider()
                        Text("ATUR OBJEK / SUBJEK", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        WorkflowSlider("Ukuran objek", edited.objectScale, 0.3f..1.7f) { v -> change { it.copy(objectScale = v) } }
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
                            Text("Upscale", color = Color.White, modifier = Modifier.weight(1f))
                            FilterChip(scale == 2, { scale = 2 }, label = { Text("2×") })
                            FilterChip(scale == 4, { scale = 4 }, label = { Text("4×") })
                        }
                        WorkflowSlider("Kekuatan enhance", edited.enhanceStrength.toFloat(), 0f..100f) { v -> change { it.copy(enhanceStrength = v.roundToInt()) } }
                        OutlinedTextField(falApiKey, onFalApiKeyChange, label = { Text("API key fal.ai (sesi ini)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { runAi("enhance") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Tingkatkan objek") }
                        Divider()
                        OutlinedTextField(objectPrompt, { objectPrompt = it }, label = { Text("Objek yang ingin dihapus") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedButton(onClick = { runAi("object") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Hapus objek dengan AI") }
                        OutlinedButton(onClick = { runAi("gaze") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Perbaiki detail wajah / arah mata") }
                        Text("Model generatif dapat mengubah detail wajah. Periksa identitas secara manual sebelum cetak.", color = Color(0xFFFFC76A), style = MaterialTheme.typography.labelSmall)
                    }
                    4 -> {
                        Text("Ukuran: " + edited.templateWidthCm + " × " + edited.templateHeightCm + " cm", color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        OutlinedButton(onClick = { persist(); onExportAll() }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Ekspor semua foto proyek") }
                    }
                }
                Spacer(Modifier.height(9.dp))
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, color = if (status.contains("gagal", true)) Color(0xFFFF9C9C) else Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (stage > 0) stage-- else onBack() }, modifier = Modifier.weight(1f)) { Text("Kembali") }
                    Button(onClick = { persist(); if (stage < 4) stage++ else onBack() }, enabled = !working, modifier = Modifier.weight(1.3f)) { Text(if (stage == 4) "Selesai" else "Lanjut") }
                }
            }
        }
    }
}

@Composable
private fun WorkflowSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("%.0f".format(value), color = Color(0xFFB6A7FF), style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
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


private fun documentDisplayName(context: android.content.Context, uri: android.net.Uri, fallback: String): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}
