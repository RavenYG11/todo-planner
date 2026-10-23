package id.rama.todoplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY dateEpochDay, startMinutes")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities")
    suspend fun getAllOnce(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityEntity): Long

    @Update
    suspend fun update(entity: ActivityEntity)

    @Delete
    suspend fun delete(entity: ActivityEntity)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY year, quarter, id")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets")
    suspend fun getAllOnce(): List<TargetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TargetEntity): Long

    @Update
    suspend fun update(entity: TargetEntity)

    @Delete
    suspend fun delete(entity: TargetEntity)

    @Query("DELETE FROM targets")
    suspend fun deleteAll()
}
