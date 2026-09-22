package now.link.mastigias.ui.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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

internal fun isListScrolled(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

@Composable
fun LibraryFab(
    lazyListState: LazyListState,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isScrolled by remember(lazyListState) {
        derivedStateOf {
            isListScrolled(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        }
    }

    val enterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val enterSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val exitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val exitSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    FloatingActionButton(
        onClick = {
            if (isScrolled) {
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            } else {
                if (!isSyncing) {
                    onRefresh()
                }
            }
        },
        shape = MaterialTheme.shapes.largeIncreased,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = isScrolled,
            transitionSpec = {
                (fadeIn(animationSpec = enterEffects) + scaleIn(animationSpec = enterSpatial))
                    .togetherWith(
                        fadeOut(animationSpec = exitEffects) + scaleOut(animationSpec = exitSpatial)
                    )
            },
            label = "LibraryFabTransition"
        ) { scrolled ->
            if (scrolled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Scroll to top",
                    modifier = Modifier.rotate(90f)
                )
            } else {
                RefreshFabIcon(isSyncing = isSyncing)
            }
        }
    }
}

@Composable
private fun RefreshFabIcon(
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "FabSyncRotationTransition")
        val syncRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "FabSyncRotationAngle"
        )
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Syncing media",
            modifier = modifier.rotate(syncRotation)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh library",
            modifier = modifier
        )
    }
}
