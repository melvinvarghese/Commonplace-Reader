package im.commonplace.reader

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

class ReaderActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }
}
