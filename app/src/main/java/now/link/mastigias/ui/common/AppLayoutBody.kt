package now.link.mastigias.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout body that constrains content to a maximum width (default 900.dp)
 * and flanks wide viewports with surfaceContainerHighest gutter backgrounds,
 * matching markread's AppLayoutBody.
 */
@Composable
fun AppLayoutBody(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 900.dp,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        if (maxWidth <= maxContentWidth) {
            content()
        } else {
            val gutterColor = MaterialTheme.colorScheme.surfaceContainerHighest
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(gutterColor)
                )
                Box(
                    modifier = Modifier
                        .width(maxContentWidth)
                        .fillMaxHeight()
                ) {
                    content()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(gutterColor)
                )
            }
        }
    }
}
