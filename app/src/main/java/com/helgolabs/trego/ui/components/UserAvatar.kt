package com.helgolabs.trego.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.ui.theme.colorPrimarySets
import com.helgolabs.trego.ui.theme.colorSecondarySets
import kotlin.math.absoluteValue

@Composable
fun UserAvatar(
    username: String,
    modifier: Modifier = Modifier
) {
    // Extract initials (up to 2 characters)
    val initials = remember(username) {
        when {
            username.isEmpty() -> "?"
            username.contains(" ") -> {
                // For names with spaces, take first letter of first and last word
                val parts = username.split(" ")
                val first = parts.first().take(1)
                val last = parts.last().take(1)
                "$first$last"
            }
            else -> username.take(1) // Just take the first letter for single names
        }.uppercase()
    }

    // Generate a consistent seed for this username
    val seed = remember(username) {
        username.hashCode().absoluteValue
    }

    // Generate gradient colors based on seed
    // Using Material 3 color tokens to ensure compatibility
    val colorPairs = remember(seed) {
        val baseIndex = seed % colorPrimarySets.size
        val secondaryIndex = (seed / 2) % colorSecondarySets.size

        Pair(
            colorPrimarySets[baseIndex],
            colorSecondarySets[secondaryIndex]
        )
    }

    // Create the gradient avatar
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(colorPairs.first, colorPairs.second)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AddPeopleAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add People",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}