package im.commonplace.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.commonplace.reader.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchAndNavigate() {
        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithText("No books yet").assertIsDisplayed()
        composeRule.onNodeWithText("Settings & data").performClick()
        composeRule.onNodeWithText("Offline and private").assertIsDisplayed()
    }
}
