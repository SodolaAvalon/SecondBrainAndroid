package com.lifeos.secondbrain.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.nio.charset.StandardCharsets

@Database(
    entities = [NoteEntity::class, NoteFtsEntity::class, PendingOperationEntity::class, SyncMetaEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val DATABASE_NAME = "second-brain.db"

        /**
         * Adds the recurring-task columns.
         *
         * A real migration is required rather than a destructive rebuild: `pending_operations` can
         * hold captures that exist nowhere else yet, and the notes table holds the offline view of
         * Drive. Wiping the file to accommodate a schema change would destroy the only copy of data
         * this app is responsible for protecting.
         *
         * Three nullable columns with no default is a metadata-only change in SQLite, so this cannot
         * fail on existing rows.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `start` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `repeat` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `last_completed` TEXT DEFAULT NULL")
            }
        }

        fun create(context: Context, passphrase: String): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(StandardCharsets.UTF_8))
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4)
                // No fallbackToDestructiveMigration on purpose. It would silently drop the database
                // on any future schema mismatch — including pending_operations, whose rows may be the
                // only copy of something not yet written back to Drive. Every future schema change
                // must ship its own migration.
                .build()
        }
    }
}
