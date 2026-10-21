package im.commonplace.reader

import android.content.Context
import androidx.room.Room
import androidx.datastore.preferences.preferencesDataStore
import im.commonplace.reader.data.ReaderPreferences
import im.commonplace.reader.data.CommonplaceDatabase
import im.commonplace.reader.data.ReaderRepository
import im.commonplace.reader.imports.DocumentImporter

private val Context.readerDataStore by preferencesDataStore(name = "reader")

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    // Building the handle is lazy; Room opens/queries the database on its I/O executor.
    val database: CommonplaceDatabase by lazy {
        Room.databaseBuilder(applicationContext, CommonplaceDatabase::class.java, "commonplace.db")
            .build()
    }
    val readerPreferences: ReaderPreferences by lazy { ReaderPreferences(applicationContext.readerDataStore) }
    val readerRepository: ReaderRepository by lazy { ReaderRepository(database) }
    val documentImporter: DocumentImporter by lazy {
        DocumentImporter(applicationContext.contentResolver, applicationContext.filesDir, readerRepository)
    }
}
