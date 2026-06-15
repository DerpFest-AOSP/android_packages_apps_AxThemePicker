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

package com.android.axion.themepicker.ui.sections

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.themepicker.R
import com.android.axion.themepicker.ui.components.SettingsCard
import com.android.axion.themepicker.ui.theme.*

@Composable
fun StyleSection(
    onOpenColors: () -> Unit,
    onOpenAppGrid: () -> Unit,
    onOpenIconShapes: () -> Unit,
    onOpenThemedIcons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = LocalExpressiveDesign.current
    val layoutInfo = LocalAdaptiveLayoutInfo.current

    if (layoutInfo.isDualPane) {
        Row(
            modifier = modifier.fillMaxSize().padding(design.spacing.screenPaddingTablet),
            horizontalArrangement = Arrangement.spacedBy(design.spacing.large),
        ) {
            Column(
                modifier = Modifier.weight(0.5f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(design.spacing.medium),
            ) {
                StyleHeader()

                ProTip(text = stringResource(R.string.pro_tip_colors_message))
            }

            Column(
                modifier = Modifier.weight(0.5f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(design.spacing.medium),
            ) {
                ColorsCardContent(onClick = onOpenColors)

                AppGridCardContent(onClick = onOpenAppGrid)

                IconShapesCardContent(onClick = onOpenIconShapes)

                ThemedIconsCardContent(onClick = onOpenThemedIcons)
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(design.spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(design.spacing.medium),
        ) {
            StyleHeader()

            ColorsCardContent(onClick = onOpenColors)

            AppGridCardContent(onClick = onOpenAppGrid)

            IconShapesCardContent(onClick = onOpenIconShapes)

            ThemedIconsCardContent(onClick = onOpenThemedIcons)

            ProTip(text = stringResource(R.string.pro_tip_colors_message))
        }
    }
}

@Composable
private fun StyleHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "header_gradient")

    val gradientOffset by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "gradient_offset",
        )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier.width(50.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).drawBehind {
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(colors.primary, colors.tertiary, colors.primary),
                                startX = gradientOffset,
                            )
                    )
                }
        )

        Text(
            text = stringResource(R.string.personalize),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )

        Text(
            text = stringResource(R.string.make_your_device_uniquely_yours),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorsCardContent(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    SettingsCard(
        title = stringResource(R.string.colors),
        description = stringResource(R.string.wallpaper_colors_and_themes),
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier.size(32.dp)
                    .offset(x = (-8).dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.6f))
        )
        Box(
            modifier =
                Modifier.size(28.dp)
                    .offset(x = 8.dp, y = 8.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary.copy(alpha = 0.6f))
        )
        Box(
            modifier =
                Modifier.size(24.dp)
                    .offset(x = 6.dp, y = (-8).dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun AppGridCardContent(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    SettingsCard(
        title = stringResource(R.string.app_grid),
        description = stringResource(R.string.home_screen_layout),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        modifier =
                            Modifier.size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.8f))
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        modifier =
                            Modifier.size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun IconShapesCardContent(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    SettingsCard(
        title = stringResource(R.string.icon_shape_title),
        description = stringResource(R.string.icon_shape_description),
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(16.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.8f))
            )
            Box(
                modifier =
                    Modifier.size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.tertiary.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun ThemedIconsCardContent(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    SettingsCard(
        title = stringResource(R.string.themed_icons_title),
        description = stringResource(R.string.themed_icons_summary),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier =
                        Modifier.size(14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(alpha = 0.7f))
                )
                Box(
                    modifier =
                        Modifier.size(14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(alpha = 0.5f))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier =
                        Modifier.size(14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(alpha = 0.5f))
                )
                Box(
                    modifier =
                        Modifier.size(14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun ProTip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val design = LocalExpressiveDesign.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(design.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = CircleShape, color = colors.primary) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.padding(8.dp).size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pro_tip),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                )
            }
        }
    }
}
