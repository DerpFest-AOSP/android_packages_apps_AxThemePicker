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

package com.android.axion.themepicker.ui.mainscreen

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.R
import com.android.axion.themepicker.data.model.OptionCircle
import com.android.axion.themepicker.data.model.Screen.EntryPoint
import com.android.axion.themepicker.ui.components.OptionIcon
import com.android.axion.themepicker.ui.components.PieIcon
import com.android.axion.themepicker.viewmodel.MainScreenViewModel

@Composable
fun ScreenOptions(
    isHome: Boolean,
    modifier: Modifier = Modifier,
    mainScreenViewModel: MainScreenViewModel = viewModel(),
) {
    val context = LocalContext.current
    val colorsStr = stringResource(R.string.colors)
    val appGridStr = stringResource(R.string.app_grid)
    val iconShapesStr = stringResource(R.string.icon_shape_title)
    val widgetsStr = stringResource(R.string.widgets)
    val shortcutsStr = stringResource(R.string.shortcuts)
    val moreStr = stringResource(R.string.more)

    val options =
        if (isHome)
            listOf(
                OptionCircle(colorsStr, null),
                OptionCircle(appGridStr, Icons.Default.GridView),
                OptionCircle(iconShapesStr, Icons.Default.CropSquare),
            )
        else
            listOf(
                OptionCircle(widgetsStr, Icons.Default.Widgets),
                OptionCircle(shortcutsStr, Icons.Default.Shortcut),
                OptionCircle(moreStr, Icons.Default.MoreHoriz),
            )
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        options.forEach { option ->
            Box(
                modifier =
                    Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                val name = option.name
                                if (isHome) {
                                    when (name) {
                                        colorsStr -> mainScreenViewModel.onOpenColorsSettings()
                                        appGridStr -> mainScreenViewModel.onOpenAppGrid()
                                        iconShapesStr -> mainScreenViewModel.onOpenIconShapes()
                                    }
                                } else {
                                    when (name) {
                                        widgetsStr ->
                                            mainScreenViewModel.onOpenLockscreenPreview(
                                                entryPoint = EntryPoint.WIDGETS
                                            )
                                        shortcutsStr ->
                                            mainScreenViewModel.onOpenLockscreenPreview(
                                                entryPoint = EntryPoint.SHORTCUTS
                                            )
                                        moreStr -> {
                                            runCatching {
                                                val intent =
                                                    Intent(Settings.ACTION_LOCKSCREEN_SETTINGS)
                                                        .apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                context.startActivity(intent)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (option.icon == null) PieIcon() else OptionIcon(option.icon)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        option.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
