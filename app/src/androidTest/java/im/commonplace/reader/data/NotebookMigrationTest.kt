package im.commonplace.reader.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookMigrationTest {
    private lateinit var context: Context
    private lateinit var name: String
    private var opened: CommonplaceDatabase? = null
    private val source = "00000000-0000-0000-0000-000000000001"
    private val location = "  {\"version\":99, \"future\":\"ലോകം é\"}  "
    private val text = "  ചിന്ത — 世界\né\n  "

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "notebook-migration-${UUID.randomUUID()}.db"
    }
    @After fun cleanup() { opened?.close(); context.deleteDatabase(name) }

    private fun seedV1(): Map<String, List<List<String?>>> {
        val schemaPath = "im.commonplace.reader.data.CommonplaceDatabase/1.json"
        val stream = javaClass.classLoader!!.getResourceAsStream(schemaPath)
            ?: context.assets.open(schemaPath)
        val schema = stream.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) for (j in 0 until indices.length())
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                        db.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("seed only")
                }).build())
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO sources VALUES (?, 'പുസ്തകം', 'Éva 王', 'EPUB', 'content://fixture/book', ?, 'imports/assets/book.epub', 'MISSING', 10, 11)", arrayOf(source, "a".repeat(64)))
        db.execSQL("INSERT INTO entries VALUES ('highlight', 'HIGHLIGHT', ?, ?, NULL, ?, ?, 'Original title', NULL, -1, 12, 13)", arrayOf(source, location, text, "Quotation\n世界"))
        db.execSQL("INSERT INTO entries VALUES ('annotation', 'ANNOTATION', NULL, NULL, '', ?, NULL, 'Orphan attribution', '', NULL, 14, 15)", arrayOf(text))
        db.execSQL("INSERT INTO bookmarks VALUES ('bookmark', ?, ?, 16)", arrayOf(source, location))
        db.execSQL("INSERT INTO reading_state VALUES (?, ?, 17)", arrayOf(source, location))
        db.execSQL("INSERT INTO reader_drafts VALUES (?, ?, ?, NULL, 18)", arrayOf(source, "opaque draft capture", text))
        val snapshot = snapshot(db)
        helper.close()
        return snapshot
    }

    private fun snapshot(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        listOf("sources", "entries", "bookmarks", "reading_state", "reader_drafts").associateWith { table ->
            val columns = if (table == "entries")
                "id,kind,sourceId,location,title,body,quotation,attributionTitle,attributionAuthors,colorArgb,createdAt,modifiedAt" else "*"
            db.query("SELECT $columns FROM $table ORDER BY 1").use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
            }
        }

    private fun open(): SupportSQLiteDatabase {
        opened = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2).build()
        return opened!!.openHelper.writableDatabase
    }

    @Test fun v1MigrationPreservesEveryExistingValueAndConstraint() {
        val before = seedV1()
        val db = open()
        assertEquals(2, db.version)
        assertEquals(before, snapshot(db))
        db.query("SELECT revision,lastNotebookCommitId FROM entries").use { c ->
            while (c.moveToNext()) { assertEquals(0L, c.getLong(0)); assertTrue(c.isNull(1)) }
        }
        db.execSQL("DELETE FROM sources WHERE id = ?", arrayOf(source))
        db.query("SELECT sourceId,quotation,body,location FROM entries WHERE id='highlight'").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
            assertEquals("Quotation\n世界", c.getString(1)); assertEquals(text, c.getString(2)); assertEquals(location, c.getString(3))
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        db.query("PRAGMA index_list(sources)").use { c ->
            var uniqueChecksum = false
            while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == "index_sources_checksum")
                uniqueChecksum = c.getInt(c.getColumnIndexOrThrow("unique")) == 1
            assertTrue(uniqueChecksum)
        }
    }

    @Test fun freshAndMigratedV2SchemasMatch() {
        seedV1()
        val migrated = open()
        fun rows(db: SupportSQLiteDatabase, query: String) = db.query(query).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
        }
        fun definitions(db: SupportSQLiteDatabase) = rows(db, "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name").associate { row ->
            val table = row.single()!!
            table to listOf(rows(db, "PRAGMA table_info($table)"), rows(db, "PRAGMA foreign_key_list($table)"),
                rows(db, "PRAGMA index_list($table)").map { it.drop(1) }.sortedBy { it.first() })
        }
        val expected = definitions(migrated)
        opened!!.close(); opened = null; context.deleteDatabase(name)
        assertEquals(expected, definitions(open()))
    }

    @Test fun migrationFailureNeverFallsBackToDeletion() {
        val before = seedV1()
        val broken = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("DROP TABLE entries"); error("injected failure") }
        }
        val db = Room.databaseBuilder(context, CommonplaceDatabase::class.java, name).addMigrations(broken).build()
        var rejected = false
        try { db.openHelper.writableDatabase } catch (_: IllegalStateException) { rejected = true } finally { db.close() }
        assertTrue("The upgrade must actually attempt and reject the failed migration", rejected)
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        raw.use { sql ->
            assertEquals(1, sql.version)
            sql.rawQuery("SELECT body FROM entries WHERE id='annotation'", null).use { c -> assertTrue(c.moveToFirst()); assertEquals(before.getValue("entries")[0][5], c.getString(0)) }
        }
    }
}
