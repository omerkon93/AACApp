package com.kon.myaacapp.ui.admin.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R
import com.kon.myaacapp.ui.admin.AdminTab

@Composable
fun AdminBottomNavigation(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {
    // OPTIMIZATION: Cache the WindowInsets to prevent JVM heap allocations on every recomposition
    val windowInsets = remember { WindowInsets(0.dp) }

    // OPTIMIZATION: Cache the modifier chain so Compose doesn't have to rebuild it
    val navModifier = remember { Modifier.height(64.dp) }

    // OPTIMIZATION: Memoize lambdas with 'onTabSelected' as the key.
    // This stabilizes the NavigationBarItem inputs, allowing the Compose compiler to skip
    // recomposing tabs that haven't changed their 'selected' state.
    val onHomeClick = remember(onTabSelected) { { onTabSelected(AdminTab.HOME) } }
    val onSettingsClick = remember(onTabSelected) { { onTabSelected(AdminTab.SETTINGS) } }
    val onStatisticsClick = remember(onTabSelected) { { onTabSelected(AdminTab.STATISTICS) } }
    val onSystemClick = remember(onTabSelected) { { onTabSelected(AdminTab.SYSTEM) } }

    NavigationBar(
        modifier = navModifier,
        windowInsets = windowInsets
    ) {
        NavigationBarItem(
            selected = selectedTab == AdminTab.HOME,
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_home)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SETTINGS,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_tiles)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.STATISTICS,
            onClick = onStatisticsClick,
            icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_statistics)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SYSTEM,
            onClick = onSystemClick,
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_system)) }
        )
    }
}