package id.reang.studiofoto.nativeapp

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "light_presets", indices = [Index(value = ["name"], unique = true)])
data class LightPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val settingsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "studio_assets", indices = [Index("kind")])
data class StudioAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val localPath: String,
    val kind: String,
    val createdAt: Long = System.currentTimeMillis()
)
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
    @ColumnInfo(defaultValue = "42") val templateWidthCm: Float = 42f,
    @ColumnInfo(defaultValue = "29.7") val templateHeightCm: Float = 29.7f,
    @ColumnInfo(defaultValue = "300") val exportDpi: Int = 300,
    @ColumnInfo(defaultValue = "'A3 Landscape'") val templateName: String = "A3 Landscape",
    @ColumnInfo(defaultValue = "''") val eventText: String = "",
    @ColumnInfo(defaultValue = "''") val eventDate: String = "",
    @ColumnInfo(defaultValue = "'png'") val exportFormat: String = "png",
    @ColumnInfo(defaultValue = "'Natural'") val enhanceMode: String = "Natural",
    @ColumnInfo(defaultValue = "50") val enhanceStrength: Int = 50,
    @ColumnInfo(defaultValue = "0") val highlights: Float = 0f,
    @ColumnInfo(defaultValue = "0") val shadows: Float = 0f,
    @ColumnInfo(defaultValue = "0") val blacks: Float = 0f,
    @ColumnInfo(defaultValue = "0") val dehaze: Float = 0f,
    @ColumnInfo(defaultValue = "0") val clarity: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vibrance: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vignette: Float = 0f,
    @ColumnInfo(defaultValue = "1") val backgroundScale: Float = 1f,
    @ColumnInfo(defaultValue = "0") val backgroundX: Float = 0f,
    @ColumnInfo(defaultValue = "0") val backgroundY: Float = 0f,
    @ColumnInfo(defaultValue = "'{}'") val colorMixJson: String = "{}",
    @ColumnInfo(defaultValue = "NULL") val localAdjustmentPath: String? = null,
    @ColumnInfo(defaultValue = "0") val texture: Float = 0f,
    @ColumnInfo(defaultValue = "0") val sharpen: Float = 0f,
    @ColumnInfo(defaultValue = "0") val whites: Float = 0f,
    @ColumnInfo(defaultValue = "'Asli'") val lightPreset: String = "Asli",
    @ColumnInfo(defaultValue = "0") val isCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val localBrushBasePath: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val localBrushHistoryJson: String = "[]",
    @ColumnInfo(defaultValue = "NULL") val cutoutBrushBasePath: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val cutoutBrushHistoryJson: String = "[]"
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM light_presets ORDER BY createdAt DESC")
    fun observeLightPresets(): Flow<List<LightPresetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLightPreset(preset: LightPresetEntity)
    @Query("SELECT * FROM studio_assets ORDER BY createdAt DESC")
    fun observeAssets(): Flow<List<StudioAssetEntity>>
    @Insert suspend fun insertAsset(asset: StudioAssetEntity): Long
    @Query("DELETE FROM studio_assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: Long)
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observePhotos(projectId: Long): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhoto(photoId: Long): PhotoEntity?
    @Insert suspend fun insertProject(project: ProjectEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPhoto(photo: PhotoEntity): Long
    @Update suspend fun updatePhoto(photo: PhotoEntity)
    @Query("DELETE FROM photos WHERE id = :photoId") suspend fun deletePhoto(photoId: Long)
    @Query("DELETE FROM projects WHERE id = :projectId") suspend fun deleteProject(projectId: Long)
    @Query("SELECT COUNT(*) FROM projects") suspend fun projectCount(): Int
}

@Database(entities = [ProjectEntity::class, PhotoEntity::class, StudioAssetEntity::class, LightPresetEntity::class], version = 8, exportSchema = false)
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
                db.execSQL("ALTER TABLE photos ADD COLUMN templateWidthCm REAL NOT NULL DEFAULT 42")
                db.execSQL("ALTER TABLE photos ADD COLUMN templateHeightCm REAL NOT NULL DEFAULT 29.7")
                db.execSQL("ALTER TABLE photos ADD COLUMN exportDpi INTEGER NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE photos ADD COLUMN templateName TEXT NOT NULL DEFAULT 'A3 Landscape'")
                db.execSQL("ALTER TABLE photos ADD COLUMN eventText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photos ADD COLUMN eventDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photos ADD COLUMN exportFormat TEXT NOT NULL DEFAULT 'png'")
                db.execSQL("ALTER TABLE photos ADD COLUMN enhanceMode TEXT NOT NULL DEFAULT 'Natural'")
                db.execSQL("ALTER TABLE photos ADD COLUMN enhanceStrength INTEGER NOT NULL DEFAULT 50")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN highlights REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN shadows REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN blacks REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN dehaze REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN clarity REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN vibrance REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN vignette REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN backgroundScale REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE photos ADD COLUMN backgroundX REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN backgroundY REAL NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS studio_assets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, localPath TEXT NOT NULL, kind TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_studio_assets_kind ON studio_assets(kind)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN colorMixJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE photos ADD COLUMN localAdjustmentPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN texture REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN sharpen REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN whites REAL NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN lightPreset TEXT NOT NULL DEFAULT 'Asli'")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS light_presets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, settingsJson TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_light_presets_name ON light_presets(name)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN localBrushBasePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN localBrushHistoryJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE photos ADD COLUMN cutoutBrushBasePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE photos ADD COLUMN cutoutBrushHistoryJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
        fun get(context: Context): PhotoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, PhotoDatabase::class.java, "reang-studio-native.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build().also { INSTANCE = it }
        }
    }
}
