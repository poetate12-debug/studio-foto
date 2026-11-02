package id.reang.studiofoto.nativeapp

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

private val editorStages = listOf("Cahaya", "Cutout", "Template", "Enhance", "Export")

@Composable
fun PhotoWorkflowScreen(
    photo: PhotoEntity,
    falApiKey: String,
    onFalApiKeyChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: (PhotoEntity) -> Unit,
    onExportAll: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable(photo.id) { mutableIntStateOf(0) }
    var edited by remember(photo.id) { mutableStateOf(photo) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Foto disimpan di penyimpanan aplikasi.") }
    var cutoutModel by rememberSaveable(photo.id) { mutableStateOf("bria") }
    var cutoutMode by rememberSaveable(photo.id) { mutableStateOf("cutout") }
    var backgroundPrompt by rememberSaveable(photo.id) { mutableStateOf("taman bunga elegan, cahaya sore lembut, latar realistis") }
    var blurStrength by rememberSaveable(photo.id) { mutableFloatStateOf(16f) }
    var enhanceModel by rememberSaveable(photo.id) { mutableStateOf("topaz-hifi") }
    var scale by rememberSaveable(photo.id) { mutableIntStateOf(2) }
    var objectPrompt by rememberSaveable(photo.id) { mutableStateOf("") }
    var exportFormat by rememberSaveable(photo.id) { mutableStateOf(photo.exportFormat) }
    var exportDpi by rememberSaveable(photo.id) { mutableIntStateOf(photo.exportDpi) }
    fun persist() { onSave(edited) }
    fun update(transform: (PhotoEntity) -> PhotoEntity) { edited = transform(edited) }

    val chooseBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "backgrounds").absolutePath }
                .onSuccess { p -> update { it.copy(backgroundPath = p, aiCompositePath = null) }; persist(); status = "Background tersimpan." }
                .onFailure { status = it.message ?: "Gagal mengimpor background." }
        }
    }
    val chooseOverlay = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "overlays").absolutePath }
                .onSuccess { p -> update { it.copy(overlayPath = p) }; persist(); status = "Template PNG dipasang." }
                .onFailure { status = it.message ?: "Gagal mengimpor template PNG." }
        }
    }
    val chooseLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { NativeFiles.importUri(context, uri, "logos").absolutePath }
                .onSuccess { p -> update { it.copy(logoPath = p) }; persist(); status = "Logo dipasang." }
                .onFailure { status = it.message ?: "Gagal mengimpor logo." }
        }
    }

    var preview by remember(edited.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(edited.localPath, edited.cutoutPath, edited.enhancedPath, edited.aiCompositePath, edited.backgroundPath,
        edited.overlayPath, edited.logoPath, edited.brightness, edited.contrast, edited.saturation, edited.warmth,
        edited.objectX, edited.objectY, edited.objectScale, edited.eventText, edited.eventDate, stage) {
        delay(140)
        preview = withContext(Dispatchers.IO) { runCatching { NativeImageEngine.render(edited, 640, 900) }.getOrNull() }
    }

    fun runAi(action: String) {
        val key = falApiKey.trim()
        if (key.length < 12) { status = "Masukkan API key fal.ai dahulu. Key hanya digunakan selama aplikasi terbuka."; return }
        val source = edited.localPath ?: return
        scope.launch {
            working = true
            status = "Menyiapkan foto untuk AI…"
            try {
                when (action) {
                    "cutout", "blur" -> {
                        val model = when (cutoutModel) {
                            "birefnet" -> "fal-ai/birefnet"
                            "rmbg" -> "fal-ai/imageutils/rembg"
                            else -> "fal-ai/bria/background/remove"
                        }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                        if (cutoutModel == "birefnet") input.put("model", "General Use (Heavy)")
                        val result = FalAiClient.run(context, model, input, key) { status = it }
                        if (action == "cutout") {
                            update { it.copy(cutoutPath = result.localPath, enhancedPath = null, aiCompositePath = null) }
                            status = "Cutout transparan selesai dan tersimpan."
                        } else {
                            val bokeh = NativeImageEngine.makeBokeh(context, source, result.localPath, blurStrength.roundToInt())
                            update { it.copy(aiCompositePath = bokeh, cutoutPath = result.localPath, enhancedPath = null) }
                            status = "Efek bokeh selesai."
                        }
                        persist()
                    }
                    "generate" -> {
                        require(backgroundPrompt.isNotBlank()) { "Isi deskripsi background AI." }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", backgroundPrompt)
                            .put("negative_prompt", "changed face, changed identity, different person, changed clothing, extra limbs, distorted hands, text, watermark, low quality")
                            .put("refine_prompt", true).put("fast", true).put("num_images", 1)
                        val result = FalAiClient.run(context, "fal-ai/bria/background/replace", input, key) { status = it }
                        update { it.copy(aiCompositePath = result.localPath, cutoutPath = null, enhancedPath = null) }
                        persist()
                        status = "Background AI selesai. Sumber asli tetap aman."
                    }
                    "enhance" -> {
                        val src = edited.cutoutPath ?: source
                        val image = NativeFiles.imageDataUri(src, 2200)
                        val model: String
                        val input: JSONObject
                        when (enhanceModel) {
                            "esrgan" -> {
                                model = "fal-ai/esrgan"
                                input = JSONObject().put("image_url", image).put("scale", scale)
                                    .put("model", if (scale == 4) "RealESRGAN_x4plus" else "RealESRGAN_x2plus")
                                    .put("face", false).put("output_format", "png")
                            }
                            "recraft" -> {
                                model = "fal-ai/recraft/upscale/crisp"
                                input = JSONObject().put("image_url", image).put("enable_safety_checker", true)
                            }
                            else -> {
                                model = "topaz/upscale/image/precision"
                                input = JSONObject().put("image_url", image)
                                    .put("model", if (enhanceModel == "topaz-standard") "Standard V2" else "High Fidelity V3")
                                    .put("upscale_factor", scale).put("output_format", "png")
                                    .put("subject_detection", "Foreground").put("face_enhancement", false)
                                    .put("sharpen", 0.32).put("denoise", 0.16).put("enable_safety_checker", true)
                            }
                        }
                        val result = FalAiClient.run(context, model, input, key) { status = it }
                        val merged = NativeImageEngine.mergeEnhancement(context, src, result.localPath, edited.enhanceStrength)
                        update { it.copy(enhancedPath = merged, aiCompositePath = null) }
                        persist()
                        status = "Enhance selesai. Kekuatan " + edited.enhanceStrength + "% diterapkan."
                    }
                    "object" -> {
                        require(objectPrompt.isNotBlank()) { "Tuliskan objek yang ingin dihapus." }
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", objectPrompt).put("model", "high_quality").put("mask_expansion", 15)
                        val result = FalAiClient.run(context, "fal-ai/object-removal", input, key) { status = it }
                        update { it.copy(aiCompositePath = result.localPath, enhancedPath = null) }
                        persist()
                        status = "Hapus objek selesai. Periksa hasil."
                    }
                    "gaze" -> {
                        val prompt = "Naturally improve visible face clarity and fine details with realistic eyes and natural skin. Preserve exact identity, facial structure, age, expression, hairstyle, skin tone, clothing, pose, number of people and scene. Do not reshape, beautify or replace faces. For unclear or small faces leave unchanged. Photorealistic, subtle and faithful to the original."
                        val input = JSONObject().put("image_url", NativeFiles.imageDataUri(source))
                            .put("prompt", prompt).put("num_inference_steps", 24).put("guidance_scale", 2.5)
                            .put("num_images", 1).put("enable_safety_checker", true).put("output_format", "jpeg")
                        val result = FalAiClient.run(context, "fal-ai/flux-kontext/dev", input, key) { status = it }
                        update { it.copy(aiCompositePath = result.localPath, enhancedPath = null) }
                        persist()
                        status = "Pemrosesan wajah selesai. Periksa identitas secara manual."
                    }
                }
            } catch (e: Exception) {
                status = e.message ?: "Proses gagal. Periksa internet, API key, izin model, dan saldo fal.ai."
            } finally { working = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF090D17))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stage > 0) stage-- else onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Studio Editor", color = Color.White, fontWeight = FontWeight.Bold)
                Text("TAHAP " + (stage + 1) + "/5 · " + editorStages[stage].uppercase(), color = Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { persist(); status = "Perubahan disimpan." }) { Text("Simpan", color = Color(0xFFB6A7FF)) }
        }
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(editorStages.size) { i -> FilterChip(selected = stage == i, onClick = { persist(); stage = i }, label = { Text(editorStages[i]) }) }
        }
        Box(Modifier.weight(0.9f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(20.dp)).background(Color(0xFF05070D)), contentAlignment = Alignment.Center) {
            val image = preview
            if (image != null) androidx.compose.foundation.Image(image.asImageBitmap(), contentDescription = edited.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else AsyncImage(model = edited.localPath ?: edited.uri, contentDescription = edited.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Surface(Modifier.align(Alignment.BottomCenter).padding(9.dp), color = Color(0xB3080D17), shape = RoundedCornerShape(16.dp)) {
                Text(edited.name, color = Color.White, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), maxLines = 1)
            }
        }
        Surface(color = Color(0xFF141C2B), shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp), modifier = Modifier.fillMaxWidth().weight(1.25f)) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 12.dp)) {
                Text(editorStages[stage], color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                when (stage) {
                    0 -> {
                        WorkflowSlider("Brightness", edited.brightness, -0.5f..0.5f) { v -> update { it.copy(brightness = v) } }
                        WorkflowSlider("Contrast", edited.contrast, 0.5f..1.8f) { v -> update { it.copy(contrast = v) } }
                        WorkflowSlider("Saturation", edited.saturation, 0f..2f) { v -> update { it.copy(saturation = v) } }
                        WorkflowSlider("Warmth", edited.warmth, -100f..100f) { v -> update { it.copy(warmth = v) } }
                        TextButton(onClick = { update { it.copy(brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f) } }) { Text("Reset cahaya & warna") }
                    }
                    1 -> {
                        Text("Mode cutout", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("cutout" to "Transparan", "blur" to "Bokeh", "generate" to "Background AI").forEach { (k, label) ->
                                FilterChip(selected = cutoutMode == k, onClick = { cutoutMode = k }, label = { Text(label) })
                            }
                        }
                        Text("Model", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("rmbg" to "RMBG", "birefnet" to "BiRefNet", "bria" to "Bria").forEach { (k, label) ->
                                FilterChip(selected = cutoutModel == k, onClick = { cutoutModel = k }, label = { Text(label) })
                            }
                        }
                        if (cutoutMode == "blur") WorkflowSlider("Kekuatan blur", blurStrength, 1f..30f) { blurStrength = it }
                        if (cutoutMode == "generate") OutlinedTextField(backgroundPrompt, { backgroundPrompt = it }, label = { Text("Deskripsi background") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(falApiKey, onFalApiKeyChange, label = { Text("API key fal.ai (sesi ini)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { runAi(cutoutMode) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentCut, contentDescription = null); Spacer(Modifier.width(6.dp))
                            Text(if (working) "Memproses…" else when (cutoutMode) { "blur" -> "Buat efek bokeh"; "generate" -> "Buat background AI"; else -> "Hapus background" })
                        }
                    }
                    2 -> {
                        Text("Ukuran desain", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(edited.templateName == "A3 Landscape", { update { it.copy(templateName = "A3 Landscape", templateWidthCm = 42f, templateHeightCm = 29.7f) } }, label = { Text("A3") })
                            FilterChip(edited.templateName == "A4 Portrait", { update { it.copy(templateName = "A4 Portrait", templateWidthCm = 21f, templateHeightCm = 29.7f) } }, label = { Text("A4") })
                            FilterChip(edited.templateName == "Bebas", { update { it.copy(templateName = "Bebas") } }, label = { Text("Bebas") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { chooseBackground.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Background") }
                            OutlinedButton(onClick = { chooseOverlay.launch(arrayOf("image/png")) }, modifier = Modifier.weight(1f)) { Text("Template PNG") }
                        }
                        OutlinedButton(onClick = { chooseLogo.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Tambah logo / ornamen") }
                        WorkflowSlider("Ukuran objek", edited.objectScale, 0.3f..1.7f) { v -> update { it.copy(objectScale = v) } }
                        WorkflowSlider("Posisi horizontal", edited.objectX, -40f..40f) { v -> update { it.copy(objectX = v) } }
                        WorkflowSlider("Posisi vertikal", edited.objectY, -40f..40f) { v -> update { it.copy(objectY = v) } }
                        OutlinedTextField(edited.eventText, { text -> update { it.copy(eventText = text) } }, label = { Text("Tulisan acara") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(edited.eventDate, { date -> update { it.copy(eventDate = date) } }, label = { Text("Tanggal acara") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        TextButton(onClick = { update { it.copy(backgroundPath = null, overlayPath = null, logoPath = null, aiCompositePath = null) } }) { Text("Hapus background dan layer") }
                    }
                    3 -> {
                        Text("Model peningkatan objek", color = Color.White, fontWeight = FontWeight.SemiBold)
                        listOf("topaz-hifi" to "Topaz High Fidelity", "topaz-standard" to "Topaz Standard", "esrgan" to "Real-ESRGAN", "recraft" to "Recraft Crisp").forEach { (k, label) ->
                            FilterChip(selected = enhanceModel == k, onClick = { enhanceModel = k }, label = { Text(label) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Scale", color = Color.White, modifier = Modifier.weight(1f))
                            FilterChip(scale == 2, { scale = 2 }, label = { Text("2×") })
                            FilterChip(scale == 4, { scale = 4 }, label = { Text("4×") })
                        }
                        WorkflowSlider("Kekuatan enhance", edited.enhanceStrength.toFloat(), 0f..100f) { v -> update { it.copy(enhanceStrength = v.roundToInt()) } }
                        OutlinedTextField(falApiKey, onFalApiKeyChange, label = { Text("API key fal.ai (sesi ini)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { runAi("enhance") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "Memproses…" else "Tingkatkan objek") }
                        Divider()
                        Text("Alat AI tambahan", color = Color.White, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(objectPrompt, { objectPrompt = it }, label = { Text("Objek yang ingin dihapus") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedButton(onClick = { runAi("object") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Hapus objek dengan AI") }
                        OutlinedButton(onClick = { runAi("gaze") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Perbaiki detail wajah / arah mata") }
                        Text("Model generatif dapat mengubah detail wajah. Periksa identitas secara manual.", color = Color(0xFFFFC76A), style = MaterialTheme.typography.labelSmall)
                    }
                    4 -> {
                        Text("Ukuran: " + edited.templateWidthCm + " × " + edited.templateHeightCm + " cm · " + edited.templateName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Resolusi cetak", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(150, 200, 300).forEach { d -> FilterChip(exportDpi == d, { exportDpi = d; update { it.copy(exportDpi = d) } }, label = { Text("$d DPI") }) }
                        }
                        Text("Format", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(exportFormat == "png", { exportFormat = "png"; update { it.copy(exportFormat = "png") } }, label = { Text("PNG") })
                            FilterChip(exportFormat == "jpeg", { exportFormat = "jpeg"; update { it.copy(exportFormat = "jpeg") } }, label = { Text("JPEG") })
                        }
                        Button(onClick = {
                            scope.launch {
                                working = true
                                persist()
                                status = "Merender file cetak…"
                                runCatching { NativeImageEngine.export(context, edited.copy(exportDpi = exportDpi, exportFormat = exportFormat), exportFormat, exportDpi) }
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
                    Button(onClick = { persist(); if (stage < 4) stage++ else onBack() }, enabled = !working, modifier = Modifier.weight(1.3f)) {
                        Text(if (stage == 4) "Selesai" else "Lanjut")
                    }
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
            Text("%.0f".format(value), color = Color(0xFFB6A7FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
