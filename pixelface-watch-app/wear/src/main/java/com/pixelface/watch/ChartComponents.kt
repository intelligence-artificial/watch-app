package com.pixelface.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon

@Composable
fun ChartTabBar(
    activeTab: String,
    onNavigateToHr: () -> Unit,
    onNavigateToSteps: () -> Unit,
    onNavigateToCal: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
    ) {
        ChartTabIcon(
            icon = Icons.Filled.Favorite,
            isActive = activeTab == "hr",
            activeColor = Color(0xFFFF3366),
            onClick = onNavigateToHr
        )
        ChartTabIcon(
            icon = Icons.Filled.DirectionsWalk,
            isActive = activeTab == "steps",
            activeColor = Color(0xFF00D68F),
            onClick = onNavigateToSteps
        )
        ChartTabIcon(
            icon = Icons.Filled.LocalFireDepartment,
            isActive = activeTab == "cal",
            activeColor = Color(0xFFFF6B35),
            onClick = onNavigateToCal
        )
    }
}

@Composable
private fun ChartTabIcon(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(enabled = !isActive, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isActive) activeColor else Color.White.copy(alpha = 0.25f)
        )
    }
}
