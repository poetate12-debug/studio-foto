package id.reang.studiofoto.nativeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val AppBg = Color(0xFF090D17)
private val Panel = Color(0xFF141C2B)
private val PanelSoft = Color(0xFF1B2638)
private val Muted = Color(0xFF9AA8BC)
private val White = Color(0xFFF4F7FC)
private val Violet = Color(0xFF9274FF)
private val Cyan = Color(0xFF51D7E8)
private val Gold = Color(0xFFFFC76A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 23)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 23)
        setContent {
            val colors = darkColorScheme(
                primary = Violet,
                secondary = Cyan,
                background = AppBg,
                surface = Panel,
                onPrimary = Color.White,
                onBackground = White,
                onSurface = White
            )
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = AppBg) { ReangStudioApp() }
            }
        }
    }
}

@Composable
private fun ReangStudioApp(vm: StudioViewModel = viewModel()) {
    val projects by vm.projects.collectAsState(initial = emptyList())
    var activeProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var activePhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNewProject by rememberSaveable { mutableStateOf(false) }
    var projectName by rememberSaveable { mutableStateOf("") }
    var falApiKey by remember { mutableStateOf("") }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.ensureStarterProject() }

    val importPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val project = activeProject ?: return@rememberLauncherForActivityResult
        scope.launch {
            val rows = uris.mapNotNull { uri ->
                runCatching {
                    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    } ?: "Foto"
                    val local = NativeFiles.importUri(context, uri)
                    ImportedPhoto(uri.toString(), local.absolutePath, name)
                }.onFailure { error ->
                    snackbar.showSnackbar("Gagal mengimpor foto: ${error.message ?: "file tidak dapat dibaca"}")
                }.getOrNull()
            }
            if (rows.isNotEmpty()) {
                vm.addPhotos(project.id, rows)
                snackbar.showSnackbar("${rows.size} foto disalin ke penyimpanan aplikasi.")
            }
        }
    }

    if (activePhoto != null) {
        PhotoWorkflowScreen(
            photo = activePhoto!!,
            falApiKey = falApiKey,
            onFalApiKeyChange = { falApiKey = it },
            onBack = { activePhoto = null },
            onSave = { updated ->
                vm.savePhoto(updated)
                activePhoto = updated
            },
            onExportAll = {
                val projectId = activeProject?.id
                if (projectId != null) {
                    vm.exportAll(projectId) { saved, total ->
                        scope.launch { snackbar.showSnackbar("Ekspor selesai: $saved dari $total foto.") }
                    }
                }
            }
        )
        return
    }

    if (activeProject != null) {
        val photos by vm.photos(activeProject!!.id).collectAsState(initial = emptyList())
        ProjectDetailScreen(
            project = activeProject!!,
            photos = photos,
            onBack = { activeProject = null },
            onImport = { importPhotos.launch(arrayOf("image/*")) },
            onPhotoClick = { activePhoto = it }
        )
        return
    }

    Scaffold(
        containerColor = AppBg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF101725),
                contentColor = White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Beranda") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text("Proyek") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                    label = { Text("Workspace") },
                    colors = navColors()
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                projects = projects,
                onNewProject = { showNewProject = true },
                onOpenProjects = { selectedTab = 1 },
                onOpenProject = { activeProject = it },
                onWorkspace = { selectedTab = 2 }
            )
            1 -> ProjectsScreen(
                projects = projects,
                onNewProject = { showNewProject = true },
                onOpenProject = { activeProject = it }
            )
            else -> WorkspaceScreen(
                onNotice = { title ->
                    scope.launch { snackbar.showSnackbar("$title sedang disiapkan untuk versi native berikutnya") }
                }
            )
        }
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            containerColor = Panel,
            title = { Text("Buat proyek baru", color = White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nama acara / proyek") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectName.isNotBlank()) {
                            vm.createProject(projectName)
                            projectName = ""
                            showNewProject = false
                            selectedTab = 1
                        }
                    },
                    enabled = projectName.isNotBlank()
                ) { Text("Buat proyek") }
            },
            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = Color(0xFFB6A7FF),
    indicatorColor = Color(0xFF43366F),
    unselectedIconColor = Muted,
    unselectedTextColor = Muted
)

@Composable
private fun HomeScreen(
    projects: List<ProjectEntity>,
    onNewProject: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit,
    onWorkspace: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { BrandHeader() }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF493A91), Color(0xFF273F68), Color(0xFF14566A))))
                    .padding(22.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(20.dp)) {
                        Text("CREATIVE PHOTO WORKSPACE", color = Color.White, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Karya terbaik\nberawal dari sini.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, lineHeight = MaterialTheme.typography.headlineMedium.lineHeight)
                    Spacer(Modifier.height(8.dp))
                    Text("Kelola foto acara, sempurnakan warna, dan siapkan desain cetak dalam satu studio.", color = Color(0xFFE3E9FA), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenProjects,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF26365A)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Mulai mengedit", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onNewProject,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Proyek")
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(104.dp)
                        .offset(x = 36.dp, y = (-44).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(72.dp)
                        .offset(x = 24.dp, y = 32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF70DCEA).copy(alpha = 0.16f))
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("PROYEK", projects.size.toString(), Icons.Default.FolderOpen, Violet, Modifier.weight(1f))
                StatCard("STUDIO", "NATIVE", Icons.Default.PhoneAndroid, Cyan, Modifier.weight(1f))
                StatCard("MODE", "BETA", Icons.Default.AutoFixHigh, Gold, Modifier.weight(1f))
            }
        }
        item {
            SectionHeading("Akses cepat", "Pilih alur kerja")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("Edit foto", "Cahaya & warna", Icons.Default.Tune, Violet, onOpenProjects, Modifier.weight(1f))
                QuickTile("Template", "Desain acara", Icons.Default.Layers, Cyan, onWorkspace, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("AI Enhance", "Peningkatan foto", Icons.Default.AutoAwesome, Gold, onWorkspace, Modifier.weight(1f))
                QuickTile("Pengaturan", "Workspace studio", Icons.Default.Settings, Color(0xFF74B7FF), onWorkspace, Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("Proyek terbaru", "Lanjutkan pekerjaan")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenProjects) { Text("Lihat semua", color = Color(0xFFB6A7FF)) }
            }
            Spacer(Modifier.height(8.dp))
            if (projects.isEmpty()) {
                EmptyState("Belum ada proyek", "Buat proyek pertama untuk mulai mengelola foto.")
            } else {
                projects.take(3).forEach { project ->
                    ProjectRow(project = project, onClick = { onOpenProject(project) })
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
        item {
            Surface(color = Color(0xFF111B2A), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Migrasi native sedang berjalan", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Fitur editor lengkap Reang akan dipindahkan bertahap.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Violet, Color(0xFF4A9FE6)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("REANG", color = White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, letterSpacing = 2.sp)
            Text("STUDIO FOTO", color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
        }
        Surface(color = Color(0xFF24223B), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Cyan))
                Spacer(Modifier.width(6.dp))
                Text("NATIVE BETA", color = Color(0xFFD0C7FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, color = White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text(title, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.height(13.dp))
            Text(title, color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onNewProject: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Proyek Anda", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Semua sesi foto dan pekerjaan Anda.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalIconButton(onClick = onNewProject, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF302951), contentColor = Color(0xFFD0C7FF))) {
                Icon(Icons.Default.Add, contentDescription = "Proyek baru")
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, tint = Violet, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${projects.size} proyek tersimpan", color = White, fontWeight = FontWeight.Bold)
                    Text("Pilih proyek untuk membuka galeri foto.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (projects.isEmpty()) {
            EmptyState("Belum ada proyek", "Tekan tombol + untuk membuat proyek baru.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(project = project, onClick = { onOpenProject(project) }, large = true)
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, onClick: () -> Unit, large: Boolean = false) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(large.let { if (it) 22.dp else 18.dp })
    ) {
        Row(Modifier.fillMaxWidth().padding(if (large) 17.dp else 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (large) 54.dp else 46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6554B5), Color(0xFF267C91)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Proyek foto • Buka galeri", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun WorkspaceScreen(onNotice: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text("Workspace", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Perangkat kerja Reang Studio Foto.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3C3471), Color(0xFF1B5366))))
                    .padding(18.dp)
            ) {
                Column {
                    Text("CREATIVE TOOLS", color = Color(0xFFD7D0FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Semua alat kreatif dalam satu tempat.", color = White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Modul berikut sedang dipindahkan dari aplikasi Reang versi sebelumnya.", color = Color(0xFFD5DFEF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { WorkspaceRow("Template & layout", "A3, A4, background, layer, teks", Icons.Default.Layers, Violet) { onNotice("Template & layout") } }
        item { WorkspaceRow("AI Photo Tools", "Enhance, cutout, hapus objek, bokeh", Icons.Default.AutoAwesome, Gold) { onNotice("AI Photo Tools") } }
        item { WorkspaceRow("Masking & kuas", "Seleksi area dan kontrol lokal", Icons.Default.Brush, Cyan) { onNotice("Masking & kuas") } }
        item { WorkspaceRow("RAW & ekspor cetak", "Pengembangan RAW dan output resolusi tinggi", Icons.Default.Print, Color(0xFF74B7FF)) { onNotice("RAW & ekspor cetak") } }
        item {
            Surface(color = Color(0xFF151E2D), shape = RoundedCornerShape(17.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Muted)
                    Spacer(Modifier.width(10.dp))
                    Text("Tombol modul ini belum menjalankan proses edit. Fungsinya akan diaktifkan bertahap setelah mesin editor native selesai.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.Bold)
                Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = Color(0xFF29243E), shape = RoundedCornerShape(20.dp)) {
                Text("SEGERA", color = Color(0xFFC8BCFF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ProjectDetailScreen(
    project: ProjectEntity,
    photos: List<PhotoEntity>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = White) }
            Column(Modifier.weight(1f)) {
                Text(project.name, color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("GALERI PROYEK", color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            }
            Surface(color = Color(0xFF25213B), shape = RoundedCornerShape(12.dp)) {
                Text("${photos.size} FOTO", color = Color(0xFFD0C7FF), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF342F62), Color(0xFF17495D))))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kelola foto acara", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Masukkan foto untuk mulai menyunting.", color = Color(0xFFD4DFEF), style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onImport, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Color(0xFF29264B))) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Tambah")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Foto dalam proyek", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("Pilih untuk edit", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        if (photos.isEmpty()) {
            EmptyState("Belum ada foto", "Tekan Tambah untuk memilih foto dari galeri.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(photos, key = { it.id }) { photo ->
                    Card(onClick = { onPhotoClick(photo) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = photo.uri, contentDescription = photo.name, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(photo.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Buka editor cahaya & warna", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Violet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(photo: PhotoEntity, onBack: () -> Unit, onSave: (Float, Float, Float) -> Unit) {
    var brightness by remember(photo.id) { mutableFloatStateOf(photo.brightness) }
    var contrast by remember(photo.id) { mutableFloatStateOf(photo.contrast) }
    var saturation by remember(photo.id) { mutableFloatStateOf(photo.saturation) }
    var selectedEditorTab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Tutup editor", tint = White) }
            Column(Modifier.weight(1f)) {
                Text("Editor foto", color = White, fontWeight = FontWeight.Bold)
                Text("REANG • PHOTO LAB", color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
            }
            Button(
                onClick = { onSave(brightness, contrast, saturation) },
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Simpan")
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color(0xFF05070D)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(photoColorMatrix(brightness, contrast, saturation))
            )
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                color = Color(0x99080D17),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("PREVIEW • ${photo.name}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            color = Panel,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Penyesuaian", color = White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Atur karakter foto dengan kontrol manual.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        brightness = 0f
                        contrast = 1f
                        saturation = 1f
                    }) { Text("Reset", color = Color(0xFFB6A7FF)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF0C1320)).padding(4.dp)) {
                    EditorTab("Cahaya", selectedEditorTab == 0, Modifier.weight(1f)) { selectedEditorTab = 0 }
                    EditorTab("Warna", selectedEditorTab == 1, Modifier.weight(1f)) { selectedEditorTab = 1 }
                }
                Spacer(Modifier.height(9.dp))
                if (selectedEditorTab == 0) {
                    AdjustmentSlider("Brightness", brightness, -0.5f..0.5f) { brightness = it }
                    AdjustmentSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                } else {
                    AdjustmentSlider("Saturation", saturation, 0f..2f) { saturation = it }
                }
            }
        }
    }
}

@Composable
private fun EditorTab(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Color(0xFF39305D) else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(title, color = if (selected) Color.White else Muted, modifier = Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            Text("%.2f".format(value), color = Color(0xFFB6A7FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
        Box(Modifier.size(68.dp).clip(CircleShape).background(PanelSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFFB6A7FF), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
