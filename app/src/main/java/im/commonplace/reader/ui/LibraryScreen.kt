package im.commonplace.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val viewModel = viewModel<LibraryViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            end = 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A HOME FOR THE IDEAS YOU KEEP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Your library",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "A quiet place for the books you’re reading.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "library-empty-card" },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                when (val current = state) {
                    LibraryUiState.Empty -> EmptyLibraryState()
                    LibraryUiState.Loading -> LoadingLibraryState()
                    is LibraryUiState.Error -> ErrorLibraryState(current.message)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OpenBookMark()
        Spacer(Modifier.height(22.dp))
        Text(
            text = "No books yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your reading room is ready when you are.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "IMPORT ARRIVES NEXT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingLibraryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ErrorLibraryState(message: String) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Library unavailable", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OpenBookMark() {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(width = 74.dp, height = 54.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        val x = size.width / 2f
        val top = size.height * .14f
        val bottom = size.height * .84f
        val fold = androidx.compose.ui.graphics.Path().apply {
            moveTo(8f, top)
            quadraticTo(x * .72f, top - 2f, x, top + 7f)
            quadraticTo(x * 1.28f, top - 2f, size.width - 8f, top)
            lineTo(size.width - 8f, bottom)
            quadraticTo(x * 1.26f, bottom - 5f, x, bottom + 1f)
            quadraticTo(x * .74f, bottom - 5f, 8f, bottom)
            close()
        }
        drawPath(fold, color)
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(x, top + 7f),
            androidx.compose.ui.geometry.Offset(x, bottom + 1f),
            strokeWidth = 1.5.dp.toPx(),
        )
        listOf(.34f, .49f, .64f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(
                color.copy(alpha = .55f),
                androidx.compose.ui.geometry.Offset(14f, y),
                androidx.compose.ui.geometry.Offset(x - 6f, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color.copy(alpha = .55f),
                androidx.compose.ui.geometry.Offset(x + 6f, y),
                androidx.compose.ui.geometry.Offset(size.width - 14f, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}
