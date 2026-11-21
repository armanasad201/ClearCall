package app.clearcall.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observe(id: String): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecordingEntity)

    @Query("UPDATE recordings SET customTitle = :title WHERE id = :id")
    suspend fun rename(id: String, title: String?)

    @Query("UPDATE recordings SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query(
        "UPDATE recordings SET phoneNumber = :number, contactName = :name, direction = :direction WHERE id = :id",
    )
    suspend fun updateCallInfo(id: String, number: String?, name: String?, direction: CallDirection)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()

    @Query("SELECT * FROM recordings WHERE starred = 0 AND startedAt < :cutoff")
    suspend fun getUnstarredOlderThan(cutoff: Long): List<RecordingEntity>
}

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "clearcall.db").build()
    }
}
