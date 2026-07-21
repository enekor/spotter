package com.n3k0chan.spotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.ui.nav.SpotterNavGraph
import com.n3k0chan.spotter.ui.nav.TopLevelRoute
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme

@Composable
fun SpotterApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelRoute.entries.any { it.route == currentRoute }

    // Obtenemos el estilo de la app de los settings
    val settings by com.n3k0chan.spotter.di.ServiceLocator.settings.state.collectAsStateWithLifecycle()

    com.n3k0chan.spotter.ui.theme.SpotterTheme(appThemeStyle = settings.appThemeStyle) {
        Scaffold(
            containerColor = SpotterTheme.colors.bg,
            bottomBar = {
                if (showBottomBar) {
                    SpotterBottomNav(
                    activeRoute = currentRoute,
                    onSelect = { tab ->
                        val isCurrent = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        if (!isCurrent) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        SpotterNavGraph(
            navController = navController,
            modifier = Modifier.padding(padding),
        )
    }
    }
}

@Composable
private fun SpotterBottomNav(activeRoute: String?, onSelect: (TopLevelRoute) -> Unit) {
    val c = SpotterTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface),
    ) {
        HorizontalDivider(color = c.border, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(80.dp)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TopLevelRoute.entries.forEach { tab ->
                val selected = activeRoute == tab.route
                NavItem(
                    label = stringResource(tab.labelRes),
                    icon = iconFor(tab, selected),
                    selected = selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) c.primarySoft else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) c.primary else c.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            label,
            style = SpotterText.small.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) c.text else c.textMuted,
        )
    }
}

private fun iconFor(tab: TopLevelRoute, selected: Boolean): ImageVector = when (tab) {
    TopLevelRoute.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    TopLevelRoute.Workout -> if (selected) Icons.Filled.FitnessCenter else Icons.Outlined.FitnessCenter
    TopLevelRoute.History -> if (selected) Icons.Filled.History else Icons.Outlined.History
    TopLevelRoute.Stats -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
}
