package com.example.reelbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun get(profileId: Long, mediaId: String): ProgressEntity?

    @Upsert
    suspend fun upsert(entity: ProgressEntity)

    @Query("DELETE FROM progress WHERE profileId = :profileId AND titleId = :titleId")
    suspend fun deleteTitle(profileId: Long, titleId: String)
}

@Dao
interface MyListDao {
    @Query("SELECT * FROM my_list WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observe(profileId: Long): Flow<List<MyListEntity>>

    @Upsert
    suspend fun upsert(entity: MyListEntity)

    @Query("DELETE FROM my_list WHERE profileId = :profileId AND titleId = :titleId")
    suspend fun delete(profileId: Long, titleId: String)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches WHERE profileId = :profileId ORDER BY searchedAt DESC LIMIT :limit")
    fun observe(profileId: Long, limit: Int): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsert(entity: RecentSearchEntity)

    /** Keep only the newest [keep] searches for the profile. */
    @Query(
        "DELETE FROM recent_searches WHERE profileId = :profileId AND query NOT IN " +
            "(SELECT query FROM recent_searches WHERE profileId = :profileId ORDER BY searchedAt DESC LIMIT :keep)",
    )
    suspend fun trim(profileId: Long, keep: Int)

    @Query("DELETE FROM recent_searches WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}
