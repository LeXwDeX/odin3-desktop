package com.odin.desktop.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.odin.desktop.data.dao.AppMappingDao
import com.odin.desktop.data.dao.TabDao
import com.odin.desktop.data.entity.AppMappingEntity
import com.odin.desktop.data.entity.TabEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.odin.desktop.shader.dao.AppShaderConfigDao
import com.odin.desktop.shader.model.AppShaderConfigEntity

@Database(
    entities = [TabEntity::class, AppMappingEntity::class, AppShaderConfigEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun appMappingDao(): AppMappingDao
    abstract fun appShaderConfigDao(): AppShaderConfigDao

    companion object {
        @Volatile
        private var INSTANCE: OdinDatabase? = null

        fun getDatabase(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_desktop.db"
                )
                    .fallbackToDestructiveMigration()
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
