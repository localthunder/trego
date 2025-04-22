package com.helgolabs.trego.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A stylish circular button with double chevron that appears when the user has scrolled down
 * and allows them to quickly scroll back to the top of a LazyColumn.
 *
 * @param listState The LazyListState of the list to control
 * @param modifier Additional modifier for the button
 * @param threshold How far the user needs to scroll before the button appears (in items)
 * @param backgroundColor Background color of the button (default is primary color)
 * @param contentColor Color of the chevron icons (default is onPrimary color)
 * @param topIcon First (top) icon to display (default is KeyboardArrowUp)
 * @param bottomIcon Second (bottom) icon to display (default is KeyboardArrowUp)
 */
@Composable
fun ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 2,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    topIcon: ImageVector = Icons.Filled.KeyboardArrowUp,
    bottomIcon: ImageVector = Icons.Filled.KeyboardArrowUp,
) {
    // Derived state to determine if the button should be visible
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= threshold
        }
    }

    // Coroutine scope for smooth scrolling
    val coroutineScope = rememberCoroutineScope()

    // Animation wrapping the button
    AnimatedVisibility(
        visible = showButton,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            modifier = modifier
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .size(32.dp)
                .clickable {
                    coroutineScope.launch {
                        // Smooth scroll to top when clicked
                        listState.animateScrollToItem(0)
                    }
                },
            color = backgroundColor,
            shape = CircleShape,
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Top chevron slightly offset up
                Icon(
                    imageVector = topIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.offset(y = (-3).dp)
                )

                // Bottom chevron slightly offset down
                Icon(
                    imageVector = bottomIcon,
                    contentDescription = "Scroll to top",
                    tint = contentColor,
                    modifier = Modifier.offset(y = 3.dp)
                )
            }
        }
    }
}