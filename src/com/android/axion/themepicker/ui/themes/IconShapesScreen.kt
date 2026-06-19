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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
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
import com.android.axion.themepicker.ui.theme.LocalAdaptiveLayoutInfo
import com.android.axion.themepicker.utils.math.scaleRatio
import com.android.axion.themepicker.viewmodel.GridSettingsViewModel
import com.android.axion.themepicker.viewmodel.MainScreenViewModel
import com.android.customization.model.grid.ShapeOptionModel

private const val PATH_SIZE = 100f

@Composable
fun IconShapesScreen(
    mainScreenViewModel: MainScreenViewModel,
    gridViewModel: GridSettingsViewModel = viewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val scale = LocalContext.current.scaleRatio
    val layoutInfo = LocalAdaptiveLayoutInfo.current

    val shapeOptions by gridViewModel.shapeOptions.collectAsStateWithLifecycle()
    val selectedShapeKey by gridViewModel.selectedShapeKey.collectAsStateWithLifecycle()
    val isApplying by gridViewModel.isApplying.collectAsStateWithLifecycle()

    BackHandler { mainScreenViewModel.goBack() }

    DetailScaffold(
        title = stringResource(R.string.icon_shape_title),
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
                    ShapePreviewLarge(
                        shapeOptions = shapeOptions,
                        selectedShapeKey = selectedShapeKey,
                    )
                }

                Column(
                    modifier =
                        Modifier.weight(0.6f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ShapeOptionsCard(
                        shapeOptions = shapeOptions,
                        selectedShapeKey = selectedShapeKey,
                        isApplying = isApplying,
                        onSelectShape = { gridViewModel.selectShape(it) },
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp * scale),
                    contentAlignment = Alignment.Center,
                ) {
                    ShapePreviewLarge(
                        shapeOptions = shapeOptions,
                        selectedShapeKey = selectedShapeKey,
                    )
                }

                ShapeOptionsCard(
                    shapeOptions = shapeOptions,
                    selectedShapeKey = selectedShapeKey,
                    isApplying = isApplying,
                    onSelectShape = { gridViewModel.selectShape(it) },
                    modifier = Modifier.padding(horizontal = 16.dp * scale),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ShapePreviewLarge(shapeOptions: List<ShapeOptionModel>, selectedShapeKey: String?) {
    val colors = MaterialTheme.colorScheme
    val selectedShape = shapeOptions.firstOrNull { it.key == selectedShapeKey }

    Card(
        modifier = Modifier.size(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (selectedShape != null) {
                ShapePreviewCanvas(
                    path = selectedShape.path,
                    color = colors.primary,
                    modifier = Modifier.size(120.dp),
                )
            }
        }
    }
}

@Composable
private fun ShapeOptionsCard(
    shapeOptions: List<ShapeOptionModel>,
    selectedShapeKey: String?,
    isApplying: Boolean,
    onSelectShape: (ShapeOptionModel) -> Unit,
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
                text = stringResource(R.string.icon_shape_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp * scale),
            )

            if (shapeOptions.isEmpty()) {
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
                    items(count = shapeOptions.size, key = { shapeOptions[it].key }) { index ->
                        val option = shapeOptions[index]
                        ShapeOptionItem(
                            option = option,
                            isSelected = option.key == selectedShapeKey,
                            isApplying = isApplying,
                            onClick = { onSelectShape(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeOptionItem(
    option: ShapeOptionModel,
    isSelected: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shapeColor = if (isSelected) colors.primary else colors.onSurface.copy(alpha = 0.5f)
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
        ShapePreviewCanvas(path = option.path, color = shapeColor, modifier = Modifier.size(40.dp))
        Text(
            text = option.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShapePreviewCanvas(path: String, color: Color, modifier: Modifier = Modifier) {
    val shapePath = remember(path) { PathParser.createPathFromPathData(path) }

    Canvas(modifier = modifier) {
        val scaleFactor = size.width / PATH_SIZE
        val transformedPath = Path(shapePath)
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(scaleFactor, scaleFactor)
        transformedPath.transform(scaleMatrix)

        drawContext.canvas.nativeCanvas.drawPath(
            transformedPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                style = Paint.Style.FILL
            },
        )
    }
}
