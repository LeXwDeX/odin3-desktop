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

@Database(
    entities = [TabEntity::class, AppMappingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun appMappingDao(): AppMappingDao

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
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 首次安装预置常用掌机分类 Tab
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopulateDefaults(database.tabDao())
                    }
                }
            }

            private suspend fun prepopulateDefaults(tabDao: TabDao) {
                val defaultTabs = listOf(
                    TabEntity(name = "游戏与模拟器", sortOrder = 0, isDefault = true, isGameTab = true),
                    TabEntity(name = "系统工具", sortOrder = 1, isDefault = false, isGameTab = false),
                    TabEntity(name = "全部应用", sortOrder = 2, isDefault = false, isGameTab = false)
                )
                tabDao.insertTabs(defaultTabs)
            }
        }
    }
}
