package im.commonplace.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import im.commonplace.reader.ui.CommonplaceApp
import im.commonplace.reader.ui.CommonplaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommonplaceTheme {
                CommonplaceApp()
            }
        }
    }
}
