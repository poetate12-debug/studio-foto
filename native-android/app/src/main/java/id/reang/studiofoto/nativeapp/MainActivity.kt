package id.reang.studiofoto.nativeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101216)) { ReangStudioApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReangStudioApp(vm: StudioViewModel = viewModel()) {
    val projects by vm.projects.collectAsState(initial = emptyList())
    var activeProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var activePhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    var showNewProject by rememberSaveable { mutableStateOf(false) }
    var projectName by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.ensureStarterProject() }
    val importPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val project = activeProject ?: return@rememberLauncherForActivityResult
        val rows = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: "Foto"
            uri.toString() to name
        }
        vm.addPhotos(project.id, rows)
    }

    if (activePhoto != null) {
        EditorScreen(
            photo = activePhoto!!,
            onBack = { activePhoto = null },
            onSave = { b, c, s ->
                vm.saveAdjustments(activePhoto!!, b, c, s)
                activePhoto = activePhoto!!.copy(brightness = b, contrast = c, saturation = s)
            }
        )
        return
    }

    if (activeProject != null) {
        val photos by vm.photos(activeProject!!.id).collectAsState(initial = emptyList())
        Scaffold(
            containerColor = Color(0xFF101216),
            topBar = {
                TopAppBar(
                    title = { Text(activeProject!!.name, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { activeProject = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Button(onClick = { importPhotos.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Tambahkan foto")
                }
                Spacer(Modifier.height(16.dp))
                if (photos.isEmpty()) EmptyState("Belum ada foto", "Tambahkan foto dari galeri perangkat.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        Card(
                            onClick = { activePhoto = photo },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF20252D))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = photo.uri, contentDescription = photo.name, modifier = Modifier.size(76.dp), contentScale = ContentScale.Crop)
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(photo.name, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text("Buka editor", color = Color(0xFFADB7C5), style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFADB7C5))
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF101216),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REANG", color = Color.White, fontWeight = FontWeight.Black)
                        Text("STUDIO FOTO", color = Color(0xFF9CA8B8), style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showNewProject = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Proyek baru", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202A38)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF9BC7FF))
                    Spacer(Modifier.height(12.dp))
                    Text("Studio foto native", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Workspace Android untuk mengelola dan mengedit foto acara.", color = Color(0xFFBAC5D3))
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, tint = Color.White, contentDescription = null)
                Text("  Proyek Anda", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${projects.size} proyek", color = Color(0xFF9CA8B8))
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    Card(onClick = { activeProject = project }, colors = CardDefaults.cardColors(containerColor = Color(0xFF20252D))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Collections, tint = Color(0xFF9BC7FF), contentDescription = null)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Buka galeri proyek", color = Color(0xFF9CA8B8), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("Buat proyek") },
            text = {
                OutlinedTextField(value = projectName, onValueChange = { projectName = it }, label = { Text("Nama proyek") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    vm.createProject(projectName)
                    projectName = ""
                    showNewProject = false
                }) { Text("Buat") }
            },
            dismissButton = { OutlinedButton(onClick = { showNewProject = false }) { Text("Batal") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(photo: PhotoEntity, onBack: () -> Unit, onSave: (Float, Float, Float) -> Unit) {
    var brightness by remember(photo.id) { mutableStateOf(photo.brightness) }
    var contrast by remember(photo.id) { mutableStateOf(photo.contrast) }
    var saturation by remember(photo.id) { mutableStateOf(photo.saturation) }

    Scaffold(
        containerColor = Color(0xFF101216),
        topBar = {
            TopAppBar(
                title = { Text("Editor foto", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(photoColorMatrix(brightness, contrast, saturation))
            )
            Spacer(Modifier.height(12.dp))
            Text(photo.name, color = Color.White, maxLines = 1)
            AdjustmentSlider("Brightness", brightness, -0.5f..0.5f) { brightness = it }
            AdjustmentSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
            AdjustmentSlider("Saturation", saturation, 0f..2f) { saturation = it }
            Button(onClick = { onSave(brightness, contrast, saturation) }, modifier = Modifier.fillMaxWidth()) {
                Text("Simpan penyesuaian")
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White)
            Text("%.2f".format(value), color = Color(0xFF9CA8B8))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun photoColorMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val c = contrast
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    val offset = brightness * 255f
    return ColorMatrix(floatArrayOf(
        c * (r + saturation), c * g, c * b, 0f, offset,
        c * r, c * (g + saturation), c * b, 0f, offset,
        c * r, c * g, c * (b + saturation), 0f, offset,
        0f, 0f, 0f, 1f, 0f
    ))
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF9CA8B8), modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color(0xFF9CA8B8), style = MaterialTheme.typography.bodySmall)
    }
}
