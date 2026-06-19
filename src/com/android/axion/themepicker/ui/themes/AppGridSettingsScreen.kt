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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.themepicker.ui.themes

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.ui.components.DetailScaffold
import com.android.axion.themepicker.R
import com.android.axion.themepicker.ui.app.PreviewsPage
import com.android.axion.themepicker.ui.theme.LocalAdaptiveLayoutInfo
import com.android.axion.themepicker.utils.math.scaleRatio
import com.android.axion.themepicker.viewmodel.GridSettingsViewModel
import com.android.axion.themepicker.viewmodel.MainScreenViewModel
import com.android.customization.model.grid.GridOptionModel

private const val PATH_SIZE = 100f
private const val SPACE_BETWEEN_ICONS = 6f

@Composable
fun AppGridSettingsScreen(
    mainScreenViewModel: MainScreenViewModel,
    gridViewModel: GridSettingsViewModel = viewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val scale = LocalContext.current.scaleRatio
    val layoutInfo = LocalAdaptiveLayoutInfo.current

    val gridOptions by gridViewModel.gridOptions.collectAsStateWithLifecycle()
    val selectedGridKey by gridViewModel.selectedGridKey.collectAsStateWithLifecycle()
    val isApplying by gridViewModel.isApplying.collectAsStateWithLifecycle()

    val deviceAspectRatio = layoutInfo.screenWidthDp.value / layoutInfo.screenHeightDp.value

    val iconShapePath = remember {
        try {
            Resources.getSystem()
                .getString(
                    Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")
                )
        } catch (e: Exception) {
            "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z"
        }
    }

    BackHandler { mainScreenViewModel.goBack() }

    DetailScaffold(
        title = stringResource(R.string.app_grid_title),
        onBackClick = { mainScreenViewModel.goBack() },
        containerColor = colors.background,
    ) { paddingValues ->
        if (layoutInfo.isDualPane) {
            Row(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(
                    modifier =
                        Modifier.weight(0.4f)
                            .fillMaxHeight()
                            .padding(vertical = 24.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(colors.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp * scale),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxHeight(0.75f).aspectRatio(deviceAspectRatio),
                    ) {
                        PreviewsPage(
                            isHome = true,
                            refreshKey = selectedGridKey ?: "default",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier.weight(0.6f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GridOptionsCard(
                        gridOptions = gridOptions,
                        selectedGridKey = selectedGridKey,
                        iconShapePath = iconShapePath,
                        isApplying = isApplying,
                        onSelectGrid = { gridViewModel.selectGrid(it) },
                    )
                }
            }
        } else {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp * scale),
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewsPage(
                        isHome = true,
                        refreshKey = selectedGridKey ?: "default",
                        modifier =
                            Modifier.width(162.dp * scale)
                                .aspectRatio(deviceAspectRatio)
                                .clip(RoundedCornerShape(16.dp * scale)),
                    )
                }

                GridOptionsCard(
                    gridOptions = gridOptions,
                    selectedGridKey = selectedGridKey,
                    iconShapePath = iconShapePath,
                    isApplying = isApplying,
                    onSelectGrid = { gridViewModel.selectGrid(it) },
                    modifier = Modifier.padding(horizontal = 16.dp * scale),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GridOptionsCard(
    gridOptions: List<GridOptionModel>,
    selectedGridKey: String?,
    iconShapePath: String,
    isApplying: Boolean,
    onSelectGrid: (GridOptionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scale = LocalContext.current.scaleRatio

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp * scale)) {
            Text(
                text = stringResource(R.string.home_screen_layout_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp * scale),
            )

            if (gridOptions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp * scale),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.size(32.dp * scale))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(12.dp * scale),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale),
                ) {
                    items(count = gridOptions.size, key = { gridOptions[it].key }) { index ->
                        val option = gridOptions[index]
                        GridOptionItem(
                            option = option,
                            iconShapePath = iconShapePath,
                            isSelected = option.key == selectedGridKey,
                            isApplying = isApplying,
                            onClick = { onSelectGrid(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridOptionItem(
    option: GridOptionModel,
    iconShapePath: String,
    isSelected: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tileColor = if (isSelected) colors.primary else colors.onSurface.copy(alpha = 0.5f)
    val backgroundColor = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh
    val textColor = if (isSelected) colors.primary else colors.onSurface

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = !isApplying) { onClick() }
                .background(backgroundColor)
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GridTileCanvas(
            cols = option.cols,
            rows = option.rows,
            path = iconShapePath,
            color = tileColor,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = option.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GridTileCanvas(
    cols: Int,
    rows: Int,
    path: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shapePath = remember(path) { PathParser.createPathFromPathData(path) }

    Canvas(modifier = modifier) {
        val longestSide = maxOf(rows, cols)
        val cellSize = size.width / longestSide
        val scaleFactor = (cellSize - 2 * SPACE_BETWEEN_ICONS * density) / PATH_SIZE

        val xOffset = (size.width - cols * cellSize) / 2
        val yOffset = (size.height - rows * cellSize) / 2

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = xOffset + c * cellSize + SPACE_BETWEEN_ICONS * density
                val y = yOffset + r * cellSize + SPACE_BETWEEN_ICONS * density

                val transformedPath = Path(shapePath)
                val scaleMatrix = Matrix()
                scaleMatrix.setScale(scaleFactor, scaleFactor)
                transformedPath.transform(scaleMatrix)

                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.translate(x, y)
                drawContext.canvas.nativeCanvas.drawPath(
                    transformedPath,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color.toArgb()
                        style = Paint.Style.FILL
                    },
                )
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }
}
