package pixiviz.mobile.data.local

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

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey val illustId: Long,
    val title: String,
    val thumbnailUrl: String,
    val userName: String,
    val userId: Long,
    val pageCount: Int,
    val xRestrict: Int,
    val viewedAt: Long,
)

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<HistoryEntry>

    @Query("DELETE FROM history WHERE illustId = :illustId")
    suspend fun delete(illustId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    fun count(): Flow<Int>
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class PixivizDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    companion object {
        fun build(context: Context): PixivizDatabase =
            Room.databaseBuilder(context, PixivizDatabase::class.java, "pixiviz.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
