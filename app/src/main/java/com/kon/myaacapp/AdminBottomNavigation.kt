package com.kon.myaacapp

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun AdminBottomNavigation(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier.height(64.dp),
        windowInsets = WindowInsets(0.dp)
    ) {
        NavigationBarItem(
            selected = selectedTab == AdminTab.HOME,
            onClick = { onTabSelected(AdminTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_home)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SETTINGS,
            onClick = { onTabSelected(AdminTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_tiles)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.STATISTICS,
            onClick = { onTabSelected(AdminTab.STATISTICS) },
            icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_statistics)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SYSTEM,
            onClick = { onTabSelected(AdminTab.SYSTEM) },
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_system)) }
        )
    }
}