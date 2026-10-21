package im.commonplace.reader.reader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import im.commonplace.reader.data.ReaderPreferences
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ReaderPreferencesTest {
    @Test fun darkReadingSurvivesStoreRestart() = runBlocking {
        val directory = Files.createTempDirectory("reader-preferences").toFile()
        val file = File(directory, "reader.preferences_pb")
        var job = SupervisorJob()
        var data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        assertFalse(ReaderPreferences(data).observeDarkReading().first())
        ReaderPreferences(data).setDarkReading(true)
        job.cancelAndJoin()
        job = SupervisorJob()
        data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try { assertTrue(ReaderPreferences(data).observeDarkReading().first()) }
        finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
}
