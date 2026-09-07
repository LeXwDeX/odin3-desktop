#!/usr/bin/env python3
"""Execute migration SQL and Kotlin contracts. No physical-device claims."""
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/odin/desktop"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"


def resources(directory):
    return {item.attrib["name"]: (item.text or "").strip('"')
            for item in ET.parse(ROOT / f"app/src/main/res/{directory}/strings.xml").getroot()}


en = resources("values")
nontranslatable = {item.attrib["name"] for item in ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
                  if item.get("translatable") == "false"}
for directory in ("values-b+zh+Hans", "values-b+zh+Hant", "values-ja"):
    translated = resources(directory)
    assert en.keys() - nontranslatable == translated.keys(), f"Translation keys differ: {directory}"
    for key, text in translated.items():
        assert sorted(re.findall(r"%\d+\$[sdf]", en[key])) == sorted(re.findall(r"%\d+\$[sdf]", text)), (directory, key)
        assert en[key] and text, f"Empty translation: {directory}/{key}"
for key in en.keys() - nontranslatable:
    assert not re.search(r"[\u4e00-\u9fff]", en[key]), f"Chinese leaked into English fallback: {key}"
assert resources("values-b+zh+Hans") == resources("values-b+zh+Hant"), "Chinese script fallbacks differ"
print(f"PASS: {len(en) - len(nontranslatable)} English, Chinese and Japanese keys; matching format arguments")

# The v3 schema is an independent historical contract. Populate default and user-created
# tabs, explicit sorting and shader JSON, then run the actual production 3 -> 4 -> 5 SQL.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE tabs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
 sortOrder INTEGER NOT NULL, isDefault INTEGER NOT NULL, isGameTab INTEGER NOT NULL, iconKey TEXT);
CREATE TABLE app_mappings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tabId INTEGER NOT NULL,
 packageName TEXT NOT NULL, sortOrder INTEGER NOT NULL, customLabel TEXT, isHidden INTEGER NOT NULL,
 FOREIGN KEY(tabId) REFERENCES tabs(id) ON DELETE CASCADE);
CREATE TABLE app_shader_configs (packageName TEXT NOT NULL PRIMARY KEY, isEnabled INTEGER NOT NULL,
 presetId TEXT NOT NULL, isDynamic INTEGER NOT NULL, scanlineIntensity REAL NOT NULL,
 phosphorIntensity REAL NOT NULL, vignetteIntensity REAL NOT NULL, animationSpeed REAL NOT NULL,
 effectsJson TEXT NOT NULL DEFAULT '');
""")
tabs = [(1, "游戏与模拟器", 2, 0, 1, None), (2, "系统应用", 1, 0, 0, None),
        (3, "全部应用", 0, 1, 0, None), (14, "My games 中文", 3, 0, 1, "custom"),
        (15, "系统应用", 4, 0, 0, None), (16, "全部应用", 5, 0, 0, None)]
db.executemany("INSERT INTO tabs VALUES (?, ?, ?, ?, ?, ?)", tabs)
db.execute("INSERT INTO app_mappings VALUES (31, 14, 'org.example.game', 7, 'Keep my label', 0)")
db.execute("INSERT INTO app_shader_configs VALUES ('org.example.game', 1, 'custom', 0, .4, .2, .3, 1, ?)",
           ('{"version":1,"family":"OPENGL","enableCRT":true}',))
before = {table: db.execute(f"SELECT * FROM {table}").fetchall()
          for table in ("tabs", "app_mappings", "app_shader_configs")}
source = (SOURCE / "data/db/OdinDatabase.kt").read_text()
migration = source.split("val MIGRATION_3_4 =", 1)[1].split("val MIGRATION_4_5 =", 1)[0]
sql = re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration)
assert len(sql) == 5, "Review the migration harness when its shape changes"
for statement in sql:
    db.execute(statement)
after = db.execute("SELECT id, name, sortOrder, isDefault, isGameTab, iconKey FROM tabs").fetchall()
assert after == before["tabs"], "Migration changed original tab data"
for table in ("app_mappings", "app_shader_configs"):
    assert db.execute(f"SELECT * FROM {table}").fetchall() == before[table], table
assert db.execute("SELECT kind, usesDefaultName FROM tabs ORDER BY id").fetchall() == [
    ("games", 1), ("system", 1), ("all_apps", 1), ("custom", 0), ("custom", 0), ("all_apps", 0)]
# The retired table alone is removed.
for statement in re.findall(r'db\.execSQL\("([^"\n]+)"\)',
                            source.split("val MIGRATION_4_5 =", 1)[1].split("fun getDatabase", 1)[0]):
    db.execute(statement)
assert db.execute("SELECT name FROM sqlite_master WHERE name = 'app_shader_configs'").fetchall() == []
assert db.execute("SELECT id, name, sortOrder, isDefault, isGameTab, iconKey FROM tabs").fetchall() == before["tabs"]
assert db.execute("SELECT * FROM app_mappings").fetchall() == before["app_mappings"]
schema = json.loads((ROOT / "app/schemas/com.odin.desktop.data.db.OdinDatabase/5.json").read_text())
fresh = sqlite3.connect(":memory:")
for entity in schema["database"]["entities"]:
    table = entity["tableName"]
    fresh.execute(entity["createSql"].replace("${TABLE_NAME}", table))
    assert fresh.execute(f"PRAGMA table_info({table})").fetchall() == db.execute(f"PRAGMA table_info({table})").fetchall(), table
print("PASS: production migration retains IDs, names, ordering, mappings and labels; removes retired settings; matches Room schema 5")

dao = (SOURCE / "data/dao/TabDao.kt").read_text()
def query_for(method):
    match = re.search(r'@Query\("([^"\n]+)"\)\s+suspend fun ' + method + r'\(', dao)
    assert match, method
    return match[1]

db.execute(query_for("setDefaultTab"), {"defaultTabId": 14})
db.execute(query_for("deleteTabById"), {"tabId": 14})
assert db.execute("SELECT isDefault FROM tabs WHERE id = 14").fetchone() == (1,), "Stale UI deleted the new default tab"
db.execute(query_for("deleteTabById"), {"tabId": 3})
assert db.execute("SELECT kind FROM tabs WHERE id = 3").fetchone() == ("all_apps",), "Deleted protected all-apps tab"
db.execute(query_for("setDefaultTab"), {"defaultTabId": 999})
assert db.execute("SELECT id FROM tabs WHERE isDefault = 1").fetchall() == [(14,)], "Missing tab cleared the current default"
print("PASS: actual DAO queries preserve protected tabs and reject stale/invalid default actions")


def jar(group, name, version):
    matches = list((CACHE / group / name / version).glob("*/*.jar"))
    if not matches:
        raise SystemExit(f"Build the app first; missing {name}:{version}")
    return matches[0]


compiler = [jar("org.jetbrains.kotlin", name, version) for name, version in [
    ("kotlin-compiler-embeddable", "2.0.0"), ("kotlin-stdlib", "2.0.0"),
    ("kotlin-script-runtime", "2.0.0"), ("kotlin-reflect", "1.6.10")]]
compiler.append(jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"))
annotations = next((CACHE / "org.jetbrains/annotations").glob("*/*/*.jar"))
compiler.append(annotations)
java = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")) / "bin/java"

ids = {key: index for index, key in enumerate(en)}
stubs = {
    "room": """package androidx.room
annotation class Entity(val tableName: String)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class ColumnInfo(val defaultValue: String)
""",
    "annotation": "package androidx.annotation\nannotation class StringRes\n",
    "context": """package android.content
class Context(private val text: Map<Int, String>) { fun getString(id: Int): String = text.getValue(id) }
""",
    "resources": "package com.odin.desktop\nobject R { object string {\n" +
        "\n".join(f"const val {key} = {index}" for key, index in ids.items()) + "\n} }\n",
    "test": """package regression
import android.content.Context
import com.odin.desktop.R
import com.odin.desktop.data.entity.*
import com.odin.desktop.data.model.displayName
fun main() {
    val english = Context(mapOf(R.string.tab_all_apps to "All apps"))
    val chinese = Context(mapOf(R.string.tab_all_apps to "全部应用"))
    val japanese = Context(mapOf(R.string.tab_all_apps to "すべてのアプリ"))
    val builtin = TabEntity(id = 3, name = "全部应用", kind = TabKind.ALL_APPS, usesDefaultName = true)
    check(builtin.displayName(english) == "All apps")
    check(builtin.displayName(chinese) == "全部应用")
    check(builtin.displayName(japanese) == "すべてのアプリ")
    check(builtin.name == "全部应用")
    val renamed = builtin.copy(name = "My collection", usesDefaultName = false)
    check(renamed.displayName(english) == renamed.displayName(chinese))
    check(renamed.displayName(english) == renamed.displayName(japanese))
    check(TabAction.DELETE !in getAvailableTabActions(renamed, 1, 3))
    check(TabAction.DELETE in getAvailableTabActions(TabEntity(name = "All apps"), 1, 3))
    check(TabAction.DELETE !in getAvailableTabActions(TabEntity(name = "Home", isDefault = true), 0, 3))
    println("PASS: actual Kotlin tab identity and localized labels")
}
""",
}
with tempfile.TemporaryDirectory(prefix="odin-architecture-") as folder:
    folder = Path(folder)
    paths = []
    for name, content in stubs.items():
        path = folder / f"{name}.kt"
        path.write_text(content)
        paths.append(str(path))
    paths += [str(SOURCE / path) for path in (
        "data/entity/TabEntity.kt", "data/model/TabLabels.kt")]
    classes = folder / "classes"
    classes.mkdir()
    runtime = os.pathsep.join(map(str, [compiler[1], annotations, classes]))
    subprocess.run([str(java), "-cp", os.pathsep.join(map(str, compiler)),
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn",
                    "-classpath", runtime, "-d", str(classes), *paths], check=True)
    subprocess.run([str(java), "-cp", runtime, "regression.TestKt"], check=True)

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
application = manifest.find("application")
main = next(a for a in application.findall("activity") if a.get(android + "name") == ".ui.MainActivity")
assert main.get(android + "exported") == "true"
assert any(f.find(f"category[@{android}name='android.intent.category.HOME']") is not None
           and f.find(f"action[@{android}name='android.intent.action.MAIN']") is not None
           for f in main.findall("intent-filter")), "Default HOME registration was removed"
services = {s.get(android + "name") for s in application.findall("service")}
assert {".service.afk.AfkTileService", ".service.afk.AfkOverlayService"} <= services
assert not any(".service.fan." in service for service in services)
assert not any("shader" in c.get(android + "name", "").lower() for c in application)
assert not list((SOURCE / "shader").rglob("*.kt"))
assert not list((ROOT / "app/src/main/assets/shaders").rglob("*.*"))
print("PASS: default HOME and AFK components remain; retired components/assets are absent")
