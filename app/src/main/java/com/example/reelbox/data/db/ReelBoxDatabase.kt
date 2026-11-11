package com.example.reelbox.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, ProgressEntity::class, MyListEntity::class, RecentSearchEntity::class, DownloadEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class ReelBoxDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun progress(): ProgressDao
    abstract fun myList(): MyListDao
    abstract fun recentSearches(): RecentSearchDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun create(context: Context): ReelBoxDatabase =
            Room.databaseBuilder(context, ReelBoxDatabase::class.java, "reelbox.db").build()
    }
}
