package com.example.reelbox.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into the bundled avatar palette (see ui/profiles). */
    val avatar: Int,
    val isKids: Boolean,
    val createdAt: Long,
)

/** Watch progress of one playable item (a movie, or one episode) for one profile. */
@Entity(
    tableName = "progress",
    primaryKeys = ["profileId", "mediaId"],
    indices = [Index("profileId", "titleId")],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class ProgressEntity(
    val profileId: Long,
    /** episodeId for an episode, titleId for a movie. */
    val mediaId: String,
    val titleId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "my_list",
    primaryKeys = ["profileId", "titleId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class MyListEntity(val profileId: Long, val titleId: String, val addedAt: Long)

@Entity(
    tableName = "recent_searches",
    primaryKeys = ["profileId", "query"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class RecentSearchEntity(val profileId: Long, val query: String, val searchedAt: Long)
