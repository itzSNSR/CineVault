package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteEntity::class, HistoryEntity::class, RecentSearch::class, FavoriteChannel::class], version = 2, exportSchema = false)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao

    companion object {
        @Volatile
        private var INSTANCE: MovieDatabase? = null

        fun getDatabase(context: Context): MovieDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MovieDatabase::class.java,
                    "moviebox_database"
                )
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE recent_search (source TEXT NOT NULL, normalized TEXT NOT NULL, term TEXT NOT NULL, searchedAt INTEGER NOT NULL, PRIMARY KEY(source, normalized))")
                        db.execSQL("CREATE TABLE favorite_channel (channelKey TEXT NOT NULL PRIMARY KEY, country TEXT NOT NULL, name TEXT NOT NULL)")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
