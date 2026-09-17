package now.link.mastigias.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.launch

@Composable
fun FloatingScrollToTop(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 4
) {
    val coroutineScope = rememberCoroutineScope()
    val isVisible by remember(lazyListState, threshold) {
        derivedStateOf { lazyListState.firstVisibleItemIndex > threshold }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            },
            shape = MaterialTheme.shapes.largeIncreased,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            // Arrow rotated 90 degrees points up!
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Scroll to top",
                modifier = Modifier.rotate(90f)
            )
        }
    }
}
