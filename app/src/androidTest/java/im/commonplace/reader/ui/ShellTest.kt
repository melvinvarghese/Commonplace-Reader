package im.commonplace.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun launchAndNavigate() {
        // Establish this empty-library case independently of real import/reader cases.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<im.commonplace.reader.CommonplaceApplication>()
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { app.container.database.clearAllTables() }
        // The class-based AndroidX resolver falls back to the test APK on API 37.
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText("Library").assertIsDisplayed()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("No books yet").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("No books yet").assertIsDisplayed()
            composeRule.onNodeWithText("Import PDF or EPUB").assertIsDisplayed()
            composeRule.onNodeWithText("Settings & data").performClick()
            composeRule.onNodeWithText("Offline and private").assertIsDisplayed()
            composeRule.onNodeWithText("Commonplace").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("No entries yet").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("New entry").assertIsDisplayed()
        }
    }
}
