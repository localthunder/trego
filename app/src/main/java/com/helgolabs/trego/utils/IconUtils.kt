package com.helgolabs.trego.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.runtime.Composable

class IconUtils {
    // Helper function to determine appropriate icon based on payment category
    @Composable
    private fun getCategoryIcon(category: String?): androidx.compose.ui.graphics.vector.ImageVector {
        return when (category?.lowercase()) {
            "food", "restaurant", "dining" -> Icons.Outlined.Restaurant
            "groceries", "supermarket" -> Icons.Outlined.ShoppingCart
            "transport", "transportation" -> Icons.Outlined.DirectionsCar
            "entertainment" -> Icons.Outlined.MovieCreation
            "utilities" -> Icons.Outlined.Receipt
            "rent", "housing" -> Icons.Outlined.Home
            "travel" -> Icons.Outlined.FlightTakeoff
            "healthcare", "medical" -> Icons.Outlined.LocalHospital
            "shopping" -> Icons.Outlined.ShoppingBag
            else -> Icons.Outlined.Receipt // Default icon
        }
    }
}