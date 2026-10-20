package im.commonplace.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStateTest {
    @Test
    fun initialStateIsEmpty() {
        val viewModel = LibraryViewModel()

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }
}
