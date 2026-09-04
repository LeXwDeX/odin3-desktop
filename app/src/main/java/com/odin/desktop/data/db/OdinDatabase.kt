package com.odin.desktop.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.odin.desktop.data.dao.AppMappingDao
import com.odin.desktop.data.dao.TabDao
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity

import com.odin.desktop.shader.dao.AppShaderConfigDao
import com.odin.desktop.shader.model.AppShaderConfigEntity

@Database(
    entities = [TabEntity::class, AppMappingEntity::class, AppShaderConfigEntity::class],
    version = 4,
    exportSchema = true
)
abstract class OdinDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun appMappingDao(): AppMappingDao
    abstract fun appShaderConfigDao(): AppShaderConfigDao

    companion object {
        @Volatile
        private var INSTANCE: OdinDatabase? = null

        // Version 2 introduced only app_shader_configs; tabs and mappings stay intact.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `app_shader_configs` (
                        `packageName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `presetId` TEXT NOT NULL,
                        `isDynamic` INTEGER NOT NULL,
                        `scanlineIntensity` REAL NOT NULL,
                        `phosphorIntensity` REAL NOT NULL,
                        `vignetteIntensity` REAL NOT NULL,
                        `animationSpeed` REAL NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_shader_configs` ADD COLUMN `effectsJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tabs ADD COLUMN kind TEXT NOT NULL DEFAULT 'custom'")
                db.execSQL("ALTER TABLE tabs ADD COLUMN usesDefaultName INTEGER NOT NULL DEFAULT 0")
                // Preserve names, IDs, ordering and mappings. Only legacy built-in labels
                // acquire localized display names; subsequent renames keep their exact text.
                db.execSQL("UPDATE tabs SET kind = 'all_apps', usesDefaultName = CASE WHEN id = 3 THEN 1 ELSE 0 END WHERE name = '全部应用'")
                db.execSQL("UPDATE tabs SET kind = 'system', usesDefaultName = 1 WHERE id = 2 AND name IN ('系统应用', '系统工具')")
                db.execSQL("UPDATE tabs SET kind = 'games', usesDefaultName = 1 WHERE id = 1 AND name = '游戏与模拟器'")
            }
        }

        fun getDatabase(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_desktop.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
