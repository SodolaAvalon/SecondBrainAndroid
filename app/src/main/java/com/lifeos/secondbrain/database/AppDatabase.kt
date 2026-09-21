package com.lifeos.secondbrain.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.nio.charset.StandardCharsets

@Database(
    entities = [NoteEntity::class, NoteFtsEntity::class, PendingOperationEntity::class, SyncMetaEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val DATABASE_NAME = "second-brain.db"

        fun create(context: Context, passphrase: String): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(StandardCharsets.UTF_8))
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                // This database is a rebuildable cache. During pre-release schema changes, a
                // destructive local migration is safer than touching the Drive source of truth.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
