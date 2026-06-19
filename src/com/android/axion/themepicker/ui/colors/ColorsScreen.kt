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

package com.android.axion.themepicker.ui.colors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.ui.components.DetailScaffold
import com.android.axion.themepicker.R
import com.android.axion.themepicker.ui.preview.CalculatorPreview
import com.android.axion.themepicker.ui.preview.QuickSettingsPreview
import com.android.axion.themepicker.ui.preview.WorkspacePreview
import com.android.axion.themepicker.ui.theme.LocalAdaptiveLayoutInfo
import com.android.axion.themepicker.utils.math.sdp
import com.android.axion.themepicker.viewmodel.MainScreenViewModel

@Composable
fun ColorsSettingsScreen(mainScreenViewModel: MainScreenViewModel = viewModel()) {
    val colors = MaterialTheme.colorScheme
    val layoutInfo = LocalAdaptiveLayoutInfo.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    DetailScaffold(
        title = stringResource(id = R.string.colors_title),
        onBackClick = { mainScreenViewModel.resetToMain() },
        containerColor = colors.background,
    ) { paddingValues ->
        if (layoutInfo.isDualPane) {
            Row(
                modifier =
                    Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PreviewCarousel(
                    pagerState = pagerState,
                    isDualPane = true,
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                )

                Column(
                    modifier =
                        Modifier.weight(0.6f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                ) {
                    ColorsSectionHeader(modifier = Modifier.padding(bottom = 16.dp))
                    BasicColorsSettings()
                }
            }
        } else {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
            ) {
                PreviewCarousel(pagerState = pagerState, modifier = Modifier.padding(top = 16.dp))

                ColorsSectionHeader(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )

                BasicColorsSettings()
            }
        }
    }
}

@Composable
private fun PreviewCarousel(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    isDualPane: Boolean = false,
) {
    if (isDualPane) {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerticalPager(
                state = pagerState,
                pageSize = PageSize.Fill,
                contentPadding = PaddingValues(vertical = 16.dp),
                pageSpacing = 8.dp,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.largeIncreased)) {
                    when (page) {
                        0 -> WorkspacePreview()
                        1 -> CalculatorPreview()
                        2 -> QuickSettingsPreview()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pagerState.pageCount) { index ->
                    Box(
                        modifier =
                            Modifier.size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                    )
                }
            }
        }
    } else {
        val previewWidth = 162.sdp
        val previewHeight = 320.sdp

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(previewWidth),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
            modifier = modifier.height(previewHeight),
        ) { page ->
            Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)) {
                when (page) {
                    0 -> WorkspacePreview()
                    1 -> CalculatorPreview()
                    2 -> QuickSettingsPreview()
                }
            }
        }
    }
}

@Composable
fun ColorsSectionHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(id = R.string.customize_palette_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )

        Text(
            text = stringResource(id = R.string.customize_palette_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
