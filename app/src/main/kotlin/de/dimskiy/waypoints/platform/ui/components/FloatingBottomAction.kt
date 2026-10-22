package de.dimskiy.waypoints.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** A button and subtle bottom fade; apply the supplied padding INSIDE scrolling content. */
@Composable
fun FloatingBottomAction(label: String, onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    var clearance by remember { mutableStateOf(80.dp) }
    Box(modifier.fillMaxSize()) {
        content(PaddingValues(bottom = clearance))
        val background = MaterialTheme.colorScheme.background
        // The fade starts precisely at the measured button top, including its bottom margin.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(clearance - 16.dp)
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background.copy(alpha = .24f)))))
        Button(onClick = { focus.clearFocus(); onClick() }, enabled = enabled, shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .onSizeChanged { clearance = with(density) { it.height.toDp() } + 32.dp }
                .heightIn(min = 48.dp)) {
            Text(label)
        }
    }
}
