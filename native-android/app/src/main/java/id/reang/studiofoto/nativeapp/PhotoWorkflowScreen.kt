package id.reang.studiofoto.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private data class WorkflowStage(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val workflowStages = listOf(
    WorkflowStage("Cahaya", "Atur pencahayaan & warna", Icons.Default.Tune),
    WorkflowStage("Cutout", "Pisahkan objek dari latar", Icons.Default.ContentCut),
    WorkflowStage("Template", "Atur desain & background", Icons.Default.Layers),
    WorkflowStage("Enhance", "Tingkatkan kualitas objek", Icons.Default.AutoAwesome),
    WorkflowStage("Export", "Siapkan hasil akhir", Icons.Default.FileDownload)
)

@Composable
fun PhotoWorkflowScreen(
    photo: PhotoEntity,
    onBack: () -> Unit,
    onSave: (Float, Float, Float) -> Unit
) {
    var stage by rememberSaveable(photo.id) { mutableIntStateOf(0) }
    var brightness by remember(photo.id) { mutableFloatStateOf(photo.brightness) }
    var contrast by remember(photo.id) { mutableFloatStateOf(photo.contrast) }
    var saturation by remember(photo.id) { mutableFloatStateOf(photo.saturation) }
    var selectedTemplate by rememberSaveable(photo.id) { mutableIntStateOf(0) }
    var enhanceMode by rememberSaveable(photo.id) { mutableStateOf("Natural") }
    var enhanceStrength by rememberSaveable(photo.id) { mutableFloatStateOf(50f) }

    Column(Modifier.fillMaxSize().background(Color(0xFF090D17))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = if (stage > 0) ({ stage-- }) else onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Studio Editor", color = Color.White, fontWeight = FontWeight.Bold)
                Text("TAHAP ${stage + 1} DARI 5 • ${workflowStages[stage].title.uppercase()}", color = Color(0xFF9AA8BC), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = {
                onSave(brightness, contrast, saturation)
            }) { Text("Simpan", color = Color(0xFFB6A7FF)) }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(workflowStages.size) { index ->
                val item = workflowStages[index]
                val active = stage == index
                val passed = index < stage
                Surface(
                    modifier = Modifier.clickable { if (index <= stage) stage = index },
                    color = when { active -> Color(0xFF39305D); passed -> Color(0xFF173B3B); else -> Color(0xFF141C2B) },
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, contentDescription = null, tint = if (active) Color.White else if (passed) Color(0xFF6EE7C1) else Color(0xFF9AA8BC), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(item.title, color = if (active) Color.White else Color(0xFFB7C2D3), style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xFF05070D)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(workflowColorMatrix(brightness, contrast, saturation))
            )
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(10.dp),
                color = Color(0xB3080D17), shape = RoundedCornerShape(18.dp)
            ) {
                Text(photo.name, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), maxLines = 1)
            }
        }

        Surface(
            color = Color(0xFF141C2B),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth().weight(1.08f)
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(workflowStages[stage].title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(workflowStages[stage].subtitle, color = Color(0xFF9AA8BC), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${stage + 1}/5", color = Color(0xFFB6A7FF), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                when (stage) {
                    0 -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C1320)).padding(4.dp)) {
                            Text("Cahaya", color = Color.White, modifier = Modifier.weight(1f).background(Color(0xFF39305D), RoundedCornerShape(9.dp)).padding(9.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            Text("Warna", color = Color(0xFF9AA8BC), modifier = Modifier.weight(1f).padding(9.dp), textAlign = TextAlign.Center)
                        }
                        WorkflowSlider("Brightness", brightness, -0.5f..0.5f) { brightness = it }
                        WorkflowSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                        WorkflowSlider("Saturation", saturation, 0f..2f) { saturation = it }
                        TextButton(onClick = { brightness = 0f; contrast = 1f; saturation = 1f }, modifier = Modifier.align(Alignment.End)) { Text("Reset", color = Color(0xFFB6A7FF)) }
                    }
                    1 -> WorkflowInfoPanel(
                        icon = Icons.Default.ContentCut,
                        title = "Cutout objek & latar",
                        body = "Tahap ini akan memisahkan subjek dari background dengan tepi rambut, pakaian, dan detail tetap terjaga. Hasil cutout transparan akan diteruskan ke tahap Template.",
                        note = "Mesin cutout AI fal.ai belum dihubungkan pada build native ini."
                    )
                    2 -> {
                        WorkflowInfoPanel(
                            icon = Icons.Default.Layers,
                            title = "Template & komposisi",
                            body = "Pilih ukuran desain, background milik Anda, lalu atur posisi dan skala objek karnaval, foto, logo, serta tulisan.",
                            note = "Editor layer dan pemilih background akan diaktifkan pada tahap implementasi berikutnya."
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("A3 Landscape", "A4 Portrait", "Bebas")) { label ->
                                FilterChip(selected = selectedTemplate == listOf("A3 Landscape", "A4 Portrait", "Bebas").indexOf(label), onClick = { selectedTemplate = listOf("A3 Landscape", "A4 Portrait", "Bebas").indexOf(label) }, label = { Text(label) })
                            }
                        }
                    }
                    3 -> {
                        WorkflowInfoPanel(
                            icon = Icons.Default.AutoAwesome,
                            title = "Enhance objek",
                            body = "Tingkatkan detail objek karnaval tanpa menggambar ulang atau mengubah identitas wajah asli.",
                            note = "Pilihan model dan pemrosesan fal.ai belum aktif di native build ini."
                        )
                        Text("Model pilihan", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Natural", "Jernih", "Detail Wajah", "HDR").forEach { mode ->
                                FilterChip(selected = enhanceMode == mode, onClick = { enhanceMode = mode }, label = { Text(mode, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                        WorkflowSlider("Kekuatan enhance", enhanceStrength, 0f..100f) { enhanceStrength = it }
                    }
                    4 -> WorkflowInfoPanel(
                        icon = Icons.Default.FileDownload,
                        title = "Siap untuk export",
                        body = "Alur akhir yang dituju: hasil foto dengan cahaya, cutout, template, dan enhance disatukan lalu diekspor dalam kualitas tinggi.",
                        note = "Export JPEG/PNG ke galeri, resolusi cetak 300 DPI, dan pemrosesan batch akan menyusul. Saat ini tombol Simpan hanya menyimpan pengaturan cahaya."
                    )
                }

                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { if (stage > 0) stage-- else onBack() },
                        modifier = Modifier.weight(0.85f),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Kembali") }
                    Button(
                        onClick = {
                            if (stage < 4) {
                                if (stage == 0) onSave(brightness, contrast, saturation)
                                stage++
                            } else {
                                onSave(brightness, contrast, saturation)
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text(if (stage == 4) "Simpan cahaya & tutup" else "Lanjut: ${workflowStages[(stage + 1).coerceAtMost(4)].title}")
                        Spacer(Modifier.width(5.dp))
                        Icon(if (stage == 4) Icons.Default.Check else Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowInfoPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    note: String
) {
    Surface(color = Color(0xFF0D1523), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF39305D)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            Text(body, color = Color(0xFFD6DFED), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(9.dp))
            Text(note, color = Color(0xFFFFC76A), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WorkflowSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("%.2f".format(value), color = Color(0xFFB6A7FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun workflowColorMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    val offset = brightness * 255f
    return ColorMatrix(floatArrayOf(
        contrast * (r + saturation), contrast * g, contrast * b, 0f, offset,
        contrast * r, contrast * (g + saturation), contrast * b, 0f, offset,
        contrast * r, contrast * g, contrast * (b + saturation), 0f, offset,
        0f, 0f, 0f, 1f, 0f
    ))
}
