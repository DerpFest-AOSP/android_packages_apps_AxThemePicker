/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.themepicker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.android.axion.themepicker.data.model.NavigationDestination
import com.android.axion.themepicker.ui.expressive.ExpressiveHeader
import com.android.axion.themepicker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveScaffold(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutInfo = LocalAdaptiveLayoutInfo.current
    val colors = MaterialTheme.colorScheme
    val design = LocalExpressiveDesign.current

    if (layoutInfo.showNavigationRail) {
        Row(
            modifier =
                modifier.fillMaxSize().background(colors.surfaceContainer).systemBarsPadding()
        ) {
            ExpressiveNavigationRail(
                currentDestination = currentDestination,
                onDestinationSelected = onDestinationSelected,
            )

            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                topBar()
                Box(modifier = Modifier.fillMaxSize()) {
                    content(PaddingValues(0.dp))
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd).padding(design.spacing.medium)
                    ) {
                        floatingActionButton()
                    }
                }
            }
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = {
                ExpressiveBottomBar(
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationSelected,
                )
            },
            floatingActionButton = floatingActionButton,
            containerColor = colors.surfaceContainer,
            content = content,
        )
    }
}

@Composable
private fun ExpressiveNavigationRail(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val design = LocalExpressiveDesign.current

    NavigationRail(
        modifier = modifier.fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        containerColor = Color.Transparent,
        contentColor = colors.onSurface,
        header = { Spacer(modifier = Modifier.height(48.dp)) },
    ) {
        NavigationDestination.destinations.forEach { destination ->
            val selected = currentDestination == destination

            NavigationRailItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = { ExpressiveNavIcon(destination = destination, selected = selected) },
                label = {
                    Text(text = destination.label, style = MaterialTheme.typography.labelMedium)
                },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = colors.onSecondaryContainer,
                        selectedTextColor = colors.onSurface,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                        indicatorColor = colors.secondaryContainer,
                    ),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExpressiveBottomBar(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val design = LocalExpressiveDesign.current

    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceBright,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
    ) {
        NavigationDestination.destinations.forEach { destination ->
            val selected = currentDestination == destination

            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = { ExpressiveNavIcon(destination = destination, selected = selected) },
                label = {
                    Text(text = destination.label, style = MaterialTheme.typography.labelMedium)
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onSecondaryContainer,
                        selectedTextColor = colors.onSurface,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                        indicatorColor = colors.secondaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun ExpressiveNavIcon(
    destination: NavigationDestination,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val design = LocalExpressiveDesign.current

    val scale by
        animateFloatAsState(
            targetValue = if (selected) 1.1f else 1f,
            animationSpec = design.motion.buttonPress.asSpring(),
            label = "nav_icon_scale",
        )

    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
        contentDescription = destination.label,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
fun DetailScaffold(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ExpressiveHeader(title = title, onBackClick = onBackClick) },
        containerColor = containerColor,
        content = content,
    )
}

@Composable
fun ExpressiveDetailScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        containerColor = colors.surfaceContainer,
        content = content,
    )
}
