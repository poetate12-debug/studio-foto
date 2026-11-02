package id.reang.studiofoto.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch

data class ImportedPhoto(val originalUri: String, val localPath: String, val name: String)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PhotoDatabase.get(application).studioDao()
    val projects = dao.observeProjects()
    private val copyingIds = ConcurrentHashMap.newKeySet<Long>()

    suspend fun ensureStarterProject() {
        if (dao.projectCount() == 0) dao.insertProject(ProjectEntity(name = "Proyek Pertama"))
    }

    fun photos(projectId: Long): Flow<List<PhotoEntity>> = dao.observePhotos(projectId)

    fun createProject(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) viewModelScope.launch { dao.insertProject(ProjectEntity(name = clean)) }
    }

    fun addPhotos(projectId: Long, items: List<ImportedPhoto>) {
        viewModelScope.launch {
            items.forEach { item ->
                dao.insertPhoto(
                    PhotoEntity(
                        projectId = projectId,
                        uri = item.originalUri,
                        localPath = item.localPath,
                        name = item.name
                    )
                )
            }
        }
    }

    fun savePhoto(photo: PhotoEntity) {
        viewModelScope.launch { dao.updatePhoto(photo) }
    }

    fun ensureLocalCopies(photos: List<PhotoEntity>) {
        photos.filter { it.localPath.isNullOrBlank() && copyingIds.add(it.id) }.forEach { photo ->
            viewModelScope.launch {
                try {
                    val local = NativeFiles.importUri(getApplication(), Uri.parse(photo.uri))
                    dao.updatePhoto(photo.copy(localPath = local.absolutePath))
                } catch (_: Exception) {
                    // Existing provider URI may no longer be readable; keep the project row intact.
                } finally {
                    copyingIds.remove(photo.id)
                }
            }
        }
    }

    fun exportAll(projectId: Long, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val photos = dao.observePhotos(projectId).first()
            var saved = 0
            photos.forEach { photo ->
                runCatching {
                    NativeImageEngine.export(getApplication(), photo, photo.exportFormat, photo.exportDpi)
                }.onSuccess { saved++ }
            }
            onComplete(saved, photos.size)
        }
    }
}
