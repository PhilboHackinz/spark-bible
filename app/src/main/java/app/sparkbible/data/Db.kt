package app.sparkbible.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "moments")
data class MomentEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val verses: Int,
    val ref: String,
    val energy: String,
)

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey val id: String,
    val kind: Int,
    val at: Long,
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val note: String,
    val created: Long,
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val id: Int = 1,
    val trailBook: Int = 42,
    val trailChapter: Int = 1,
    val trailVerse: Int = 1,
    val openBook: Int = -1,
    val openChapter: Int = 1,
    val openVerse: Int = 1,
    val openCount: Int = 0,
    val openEnergy: String = "LOW",
    val openMode: String = "READ",
    val openOrigin: String = "trail",
    val openPrompt: String = "",
    val openLabel: String = "",
    val sparkDoneOn: String = "",
    val lastNotified: String = "",
)

@Dao
interface SparkDao {
    @Query("SELECT * FROM moments ORDER BY at DESC")
    fun moments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments")
    suspend fun momentsOnce(): List<MomentEntity>

    @Query("SELECT * FROM plants ORDER BY at ASC")
    fun plants(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM highlights ORDER BY created DESC")
    fun highlights(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM progress WHERE id = 1")
    fun progress(): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE id = 1")
    suspend fun progressOnce(): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(row: ProgressEntity)

    @Insert
    suspend fun insertMoment(row: MomentEntity)

    @Insert
    suspend fun insertPlant(row: PlantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHighlight(row: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: String)

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun highlight(id: String): HighlightEntity?
}

@Database(
    entities = [MomentEntity::class, PlantEntity::class, HighlightEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SparkDb : RoomDatabase() {
    abstract fun dao(): SparkDao

    companion object {
        fun create(context: Context): SparkDb =
            Room.databaseBuilder(context, SparkDb::class.java, "spark.db").build()
    }
}
