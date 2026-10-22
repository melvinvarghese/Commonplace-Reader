package im.commonplace.reader.ui

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.MainActivity
import im.commonplace.reader.data.*
import im.commonplace.reader.notebook.*
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@im.commonplace.reader.notebook.NotebookProof
@RunWith(AndroidJUnit4::class)
class NotebookLayoutTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun largeTextLightAndDarkKeepSaveAndConflictActionsReachable() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        var dark by mutableStateOf(false)
        var saved=false
        var reloaded=false
        val original=EntryEntity("synthetic",EntryKind.THOUGHT,null,null,null,"Current saved\n".repeat(70),null,null,null,null,1,1,2,null)
        val draft=NotebookDraftEntity("synthetic",false,EntryKind.THOUGHT,null,null,null,NotebookFields(body="Local draft\n".repeat(60)),1,"session",1,"token",1)
        val editor=NotebookEditor(draft,status=NotebookSaveStatus.SAVE_FAILED,conflict=original,error="Saved entry changed")
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                    CommonplaceTheme(darkTheme=dark) {
                        NotebookEditorScreen(PaddingValues(),editor,original,false,{}, {saved=true}, {Job().apply {complete()}}, {}, {}, {reloaded=true}, {})
                    }
                }
            } }
            compose.onNodeWithText("Retry Save").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
            assertTrue(saved)
            compose.onNodeWithText("Discard draft and reload saved entry").performScrollTo().assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("Current saved\n".repeat(70)).assertExists()
            compose.runOnUiThread { dark=true }
            compose.onNodeWithText("Retry Save").assertIsDisplayed()
            compose.onNodeWithText("Discard draft and reload saved entry").performScrollTo().performClick()
            assertTrue(reloaded)
        }
    }
}
