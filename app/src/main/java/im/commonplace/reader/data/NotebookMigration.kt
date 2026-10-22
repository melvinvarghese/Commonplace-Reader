package im.commonplace.reader.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries_new (
            id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, sourceId TEXT, location TEXT,
            title TEXT, body TEXT, quotation TEXT, attributionTitle TEXT, attributionAuthors TEXT,
            colorArgb INTEGER, createdAt INTEGER NOT NULL, modifiedAt INTEGER NOT NULL,
            revision INTEGER NOT NULL DEFAULT 0, lastNotebookCommitId TEXT,
            FOREIGN KEY(sourceId) REFERENCES sources(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
        db.execSQL("""INSERT INTO entries_new (id,kind,sourceId,location,title,body,quotation,
            attributionTitle,attributionAuthors,colorArgb,createdAt,modifiedAt)
            SELECT id,kind,sourceId,location,title,body,quotation,attributionTitle,attributionAuthors,
            colorArgb,createdAt,modifiedAt FROM entries""")
        db.execSQL("DROP TABLE entries")
        db.execSQL("ALTER TABLE entries_new RENAME TO entries")
        db.execSQL("CREATE INDEX index_entries_sourceId ON entries(sourceId)")
        db.execSQL("""CREATE TABLE notebook_drafts (
            targetId TEXT NOT NULL PRIMARY KEY, isNew INTEGER NOT NULL, kind TEXT NOT NULL,
            sourceId TEXT, sourceTitleSnapshot TEXT, sourceAuthorsSnapshot TEXT,
            baseEntryRevision INTEGER,
            sessionId TEXT NOT NULL, draftRevision INTEGER NOT NULL, commitToken TEXT NOT NULL,
            modifiedAt INTEGER NOT NULL, title TEXT, body TEXT, colorArgb INTEGER)""")
    }
}
