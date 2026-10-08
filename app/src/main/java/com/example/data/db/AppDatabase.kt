package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.db.dao.*

@Database(
    entities = [
        ProjectEntity::class,
        ClipEntity::class,
        AudioTrackEntity::class,
        TextOverlayEntity::class,
        SubtitleEntity::class,
        VfxEntity::class,
        TransitionEntity::class,
        StickerEntity::class,
        MediaLibraryEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun textOverlayDao(): TextOverlayDao
    abstract fun stickerDao(): StickerDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun vfxDao(): VfxDao
    abstract fun transitionDao(): TransitionDao
    abstract fun mediaLibraryDao(): MediaLibraryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vfx` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `vfxId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `thumbUrl` TEXT NOT NULL,
                        `intensity` REAL NOT NULL,
                        `isPremium` INTEGER NOT NULL,
                        `clipId` TEXT,
                        `isEnabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vfx_projectId` ON `vfx` (`projectId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transitions` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `transitionId` TEXT NOT NULL,
                        `fromClipId` TEXT NOT NULL,
                        `toClipId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transitions_projectId` ON `transitions` (`projectId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stickers` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `remoteUrl` TEXT NOT NULL,
                        `isGif` INTEGER NOT NULL,
                        `positionX` REAL NOT NULL,
                        `positionY` REAL NOT NULL,
                        `scale` REAL NOT NULL,
                        `rotation` REAL NOT NULL,
                        `opacity` REAL NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `animationIn` TEXT NOT NULL,
                        `animationOut` TEXT NOT NULL,
                        `animationDurationMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_projectId` ON `stickers` (`projectId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `clips` ADD COLUMN `thumbnailPath` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `clips` ADD COLUMN `fileSizeBytes` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `clips` ADD COLUMN `originalName` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `clips` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `vfx_effects` ADD COLUMN `startTimeMs` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `vfx_effects` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 3000")
                } catch (_: Exception) {}
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "boti_video_editor.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
