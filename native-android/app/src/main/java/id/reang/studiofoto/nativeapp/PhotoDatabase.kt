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
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "NULL") val localPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val cutoutPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val enhancedPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val backgroundPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val overlayPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val logoPath: String? = null,
    @ColumnInfo(defaultValue = "NULL") val aiCompositePath: String? = null,
    @ColumnInfo(defaultValue = "0") val warmth: Float = 0f,
    @ColumnInfo(defaultValue = "0") val objectX: Float = 0f,
    @ColumnInfo(defaultValue = "0") val objectY: Float = 0f,
    @ColumnInfo(defaultValue = "1") val objectScale: Float = 1f,
    @ColumnInfo(defaultValue = "29.7") val templateWidthCm: Float = 29.7f,
    @ColumnInfo(defaultValue = "42") val templateHeightCm: Float = 42f,
    @ColumnInfo(defaultValue = "300") val exportDpi: Int = 300,
    @ColumnInfo(defaultValue = "'A3 Landscape'") val templateName: String = "A3 Landscape",
    @ColumnInfo(defaultValue = "''") val eventText: String = "",
    @ColumnInfo(defaultValue = "''") val eventDate: String = "",
    @ColumnInfo(defaultValue = "'png'") val exportFormat: String = "png",
    @ColumnInfo(defaultValue = "'Natural'") val enhanceMode: String = "Natural",
    @ColumnInfo(defaultValue = "50") val enhanceStrength: Int = 50
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

@Database(entities = [ProjectEntity::class, PhotoEntity::class], version = 2, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao
    companion object {
        @Volatile private var INSTANCE: PhotoDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN localPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN cutoutPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN enhancedPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN backgroundPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN overlayPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN logoPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN aiCompositePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN warmth REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN objectX REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN objectY REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN objectScale REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE photos ADD COLUMN templateWidthCm REAL NOT NULL DEFAULT 29.7")
                db.execSQL("ALTER TABLE photos ADD COLUMN templateHeightCm REAL NOT NULL DEFAULT 42")
                db.execSQL("ALTER TABLE photos ADD COLUMN exportDpi INTEGER NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE photos ADD COLUMN templateName TEXT NOT NULL DEFAULT 'A3 Landscape'")
                db.execSQL("ALTER TABLE photos ADD COLUMN eventText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photos ADD COLUMN eventDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photos ADD COLUMN exportFormat TEXT NOT NULL DEFAULT 'png'")
                db.execSQL("ALTER TABLE photos ADD COLUMN enhanceMode TEXT NOT NULL DEFAULT 'Natural'")
                db.execSQL("ALTER TABLE photos ADD COLUMN enhanceStrength INTEGER NOT NULL DEFAULT 50")
            }
        }
        fun get(context: Context): PhotoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhotoDatabase::class.java, "reang-studio-native.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
