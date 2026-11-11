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

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    /** Moves the status only from one of [from]; returns rows changed (0 if it was e.g. paused or deleted meanwhile). */
    @Query("UPDATE downloads SET status = :to, updatedAt = :now WHERE mediaId = :mediaId AND status IN (:from)")
    suspend fun transition(mediaId: String, from: List<String>, to: String, now: Long): Int

    /** Progress only, never the status, so a pause/cancel that raced with the worker isn't overwritten. */
    @Query("UPDATE downloads SET bytesDone = :bytesDone, updatedAt = :now WHERE mediaId = :mediaId")
    suspend fun progress(mediaId: String, bytesDone: Long, now: Long)

    @Query("DELETE FROM downloads WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
