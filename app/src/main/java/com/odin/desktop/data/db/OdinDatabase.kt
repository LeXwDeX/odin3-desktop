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
    version = 3,
    exportSchema = false
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

        fun getDatabase(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_desktop.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 首次安装或表创建时直接执行原生 SQL 预置常用掌机分类 Tab
                db.execSQL("INSERT OR IGNORE INTO tabs (name, sortOrder, isDefault, isGameTab) VALUES ('游戏与模拟器', 0, 1, 1)")
                db.execSQL("INSERT OR IGNORE INTO tabs (name, sortOrder, isDefault, isGameTab) VALUES ('系统应用', 1, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO tabs (name, sortOrder, isDefault, isGameTab) VALUES ('全部应用', 2, 0, 0)")
            }
        }
    }
}
