package id.reang.studiofoto.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PhotoDatabase.get(application).studioDao()
    val projects = dao.observeProjects()

    suspend fun ensureStarterProject() {
        if (dao.projectCount() == 0) dao.insertProject(ProjectEntity(name = "Proyek Pertama"))
    }
    fun photos(projectId: Long): Flow<List<PhotoEntity>> = dao.observePhotos(projectId)
    fun createProject(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) viewModelScope.launch { dao.insertProject(ProjectEntity(name = clean)) }
    }
    fun addPhotos(projectId: Long, items: List<Pair<String, String>>) {
        viewModelScope.launch {
            items.forEach { (uri, name) -> dao.insertPhoto(PhotoEntity(projectId = projectId, uri = uri, name = name)) }
        }
    }
    fun saveAdjustments(photo: PhotoEntity, brightness: Float, contrast: Float, saturation: Float) {
        viewModelScope.launch {
            dao.updatePhoto(photo.copy(brightness = brightness, contrast = contrast, saturation = saturation))
        }
    }
}
