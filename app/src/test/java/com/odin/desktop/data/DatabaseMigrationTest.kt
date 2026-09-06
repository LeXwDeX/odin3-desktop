package com.odin.desktop.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.odin.desktop.data.db.OdinDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val databaseName = "migration-test.db"
    private var database: OdinDatabase? = null

    @After fun close() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun upgradeRemovesOnlyRetiredSettingsAndPreservesLauncherData() {
        // Build the installed version's schema independently of current entities.
        val schema = javaClass.getResourceAsStream(
            "/com.odin.desktop.data.db.OdinDatabase/4.json"
        )!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO tabs VALUES (14, '我的收藏', 7, 1, 1, 'custom', 'custom', 0)")
            old.execSQL("INSERT INTO tabs VALUES (3, '全部应用', 0, 0, 0, NULL, 'all_apps', 1)")
            old.execSQL("INSERT INTO tabs VALUES (70, 'Removed earlier', 8, 0, 0, NULL, 'custom', 0)")
            old.execSQL("DELETE FROM tabs WHERE id = 70")
            old.execSQL("INSERT INTO app_mappings VALUES (31, 14, 'org.example.game', 9, '自定义名称', 1)")
            old.execSQL("INSERT INTO app_shader_configs VALUES ('org.example.game', 1, 'custom', 0, .4, .2, .3, 1, 'invalid legacy JSON')")
            old.version = 4
        }

        val room = Room.databaseBuilder(context, OdinDatabase::class.java, databaseName)
            .addMigrations(OdinDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build().also { database = it }
        // Opening through Room validates the migrated schema and identity hash.
        val upgraded = room.openHelper.writableDatabase
        assertEquals(5, upgraded.version)
        upgraded.query("SELECT name FROM sqlite_master WHERE name = 'app_shader_configs'").use {
            assertEquals(0, it.count)
        }
        upgraded.query("SELECT * FROM tabs ORDER BY id").use {
            assertEquals(2, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("all_apps", it.getString(it.getColumnIndexOrThrow("kind")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("usesDefaultName")))
            assertTrue(it.moveToNext())
            assertEquals(14, it.getInt(it.getColumnIndexOrThrow("id")))
            assertEquals("我的收藏", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals(7, it.getInt(it.getColumnIndexOrThrow("sortOrder")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("isDefault")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("isGameTab")))
            assertEquals("custom", it.getString(it.getColumnIndexOrThrow("iconKey")))
            assertEquals("custom", it.getString(it.getColumnIndexOrThrow("kind")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("usesDefaultName")))
        }
        upgraded.query("SELECT * FROM app_mappings").use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals(31, it.getInt(it.getColumnIndexOrThrow("id")))
            assertEquals(14, it.getInt(it.getColumnIndexOrThrow("tabId")))
            assertEquals("org.example.game", it.getString(it.getColumnIndexOrThrow("packageName")))
            assertEquals(9, it.getInt(it.getColumnIndexOrThrow("sortOrder")))
            assertEquals("自定义名称", it.getString(it.getColumnIndexOrThrow("customLabel")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("isHidden")))
        }
        upgraded.execSQL("INSERT INTO tabs (name, sortOrder, isDefault, isGameTab) VALUES ('Next', 8, 0, 0)")
        upgraded.query("SELECT id FROM tabs WHERE name = 'Next'").use {
            assertTrue(it.moveToFirst())
            assertEquals(71, it.getInt(0))
        }
    }

    @Test fun freshInstallCreatesOnlyCurrentFeatureTables() {
        val room = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries().build().also { database = it }
        room.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('tabs', 'app_mappings', 'app_shader_configs') ORDER BY name"
        ).use {
            val tables = buildList { while (it.moveToNext()) add(it.getString(0)) }
            assertEquals(listOf("app_mappings", "tabs"), tables)
        }
    }
}
