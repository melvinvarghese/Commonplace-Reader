package im.commonplace.reader.reader.pdf

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import im.commonplace.reader.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Resource contract only: this is not native PDF rendering evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfThemeTest {
    @Test fun readerThemeResolvesPinnedViewerTextAttributes() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val themed = ContextThemeWrapper(base, R.style.AndroidxPdfReaderTheme)
        // beta01 pdf_viewer_fragment.xml's error TextView dereferences these attributes.
        val colors = themed.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOnSurface))
        try { assertNotNull(colors.getColorStateList(0)) } finally { colors.recycle() }
        val appearance = TypedValue()
        assertTrue(themed.theme.resolveAttribute(com.google.android.material.R.attr.textAppearanceBodyMedium, appearance, true))
        assertTrue(appearance.resourceId != 0)
    }
}
