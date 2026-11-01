package id.reang.studiofoto.nativeapp

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val uri: String,
    val name: String,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observePhotos(projectId: Long): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhoto(photoId: Long): PhotoEntity?
    @Insert suspend fun insertProject(project: ProjectEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPhoto(photo: PhotoEntity): Long
    @Update suspend fun updatePhoto(photo: PhotoEntity)
    @Query("SELECT COUNT(*) FROM projects") suspend fun projectCount(): Int
}

@Database(entities = [ProjectEntity::class, PhotoEntity::class], version = 1, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao
    companion object {
        @Volatile private var INSTANCE: PhotoDatabase? = null
        fun get(context: Context): PhotoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhotoDatabase::class.java, "reang-studio-native.db")
                .build().also { INSTANCE = it }
        }
    }
}
