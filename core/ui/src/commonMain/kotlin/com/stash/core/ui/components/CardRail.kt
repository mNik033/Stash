package com.stash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stash.core.ui.theme.StashTheme
import kotlinx.coroutines.launch

/**
 * The canonical Home horizontal rail: Spacer + header with optional `<`/`>` scroll buttons + a [LazyRow]
 * of cards. Matches the existing discovery-row rhythm (contentPadding 20dp,
 * inter-card gap 12dp) so every rail on Home reads as one system.
 */
@Composable
fun CardRail(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val extendedColors = StashTheme.extendedColors
    val showArrows = listState.canScrollBackward || listState.canScrollForward

    Column(modifier) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (actionText != null && onActionClick != null) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onActionClick)
                            .padding(4.dp),
                    )
                    if (showArrows) Spacer(Modifier.width(4.dp))
                }
                if (showArrows) {
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollBy(-420f) } },
                        enabled = listState.canScrollBackward,
                        modifier = Modifier
                            .size(28.dp)
                            .background(extendedColors.glassBackground, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Scroll left",
                            modifier = Modifier.size(18.dp),
                            tint = if (listState.canScrollBackward) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollBy(420f) } },
                        enabled = listState.canScrollForward,
                        modifier = Modifier
                            .size(28.dp)
                            .background(extendedColors.glassBackground, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Scroll right",
                            modifier = Modifier.size(18.dp),
                            tint = if (listState.canScrollForward) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                    }
                }
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * The cover (and width) of a Home rail card: the mix and playlist cards, Liked Songs and Recently
 * played, so every card rail on Home steps at one size. Was 140 dp.
 */
val HomeRailCardSize = 120.dp

/** The corners of a Home rail card's cover. */
val HomeRailCoverShape = RoundedCornerShape(10.dp)
