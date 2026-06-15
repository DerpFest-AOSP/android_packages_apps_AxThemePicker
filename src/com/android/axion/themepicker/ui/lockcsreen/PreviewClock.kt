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

package com.android.axion.themepicker.ui.lockscreen

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.axion.themepicker.utils.math.scaleRatio
import java.util.Calendar
import kotlin.math.cbrt
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay

val Context.previewScale: Float
    get() {
        val displayMetrics = resources.displayMetrics
        val sw =
            minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density

        val isTablet = sw >= 600f
        val baseMultiplier = if (isTablet) 0.24f else 0.42f
        val baseDp = if (isTablet) 600f else 420f

        val dpRatio = sw / baseDp
        val adjustedMultiplier =
            if (isTablet) {
                baseMultiplier * cbrt(dpRatio.toDouble()).toFloat()
            } else {
                baseMultiplier * sqrt(dpRatio)
            }

        val maxScale = if (isTablet) 0.32f else 0.55f
        return adjustedMultiplier.coerceIn(0.22f, maxScale)
    }

fun Modifier.scaledLayout(scale: Float, overrideWidth: Dp = Dp.Unspecified): Modifier =
    this.layout { measurable, constraints ->
        val expandedMaxW =
            if (overrideWidth != Dp.Unspecified) {
                overrideWidth.roundToPx()
            } else {
                (constraints.maxWidth / scale).roundToInt()
            }
        val expandedMaxH = (constraints.maxHeight / scale).roundToInt()
        val childConstraints =
            constraints.copy(
                minWidth = 0,
                maxWidth = expandedMaxW,
                minHeight = 0,
                maxHeight = expandedMaxH,
            )

        val placeable = measurable.measure(childConstraints)
        val scaledWidth = (placeable.width * scale).roundToInt()
        val scaledHeight = (placeable.height * scale).roundToInt()
        layout(scaledWidth, scaledHeight) {
            placeable.placeWithLayer(
                (scaledWidth - placeable.width) / 2,
                (scaledHeight - placeable.height) / 2,
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
        }
    }

@Composable
fun PreviewClock(isPreview: Boolean, isRegionDark: Boolean = true) {
    val context = LocalContext.current
    val scale = if (isPreview) context.previewScale else context.scaleRatio
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000L)
        }
    }

    val textColor = if (isRegionDark) Color.White else Color.Black
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember { DateFormat.getMediumDateFormat(context) }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 12.dp * scale)
                .scaledLayout(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeFormat.format(currentTime.time),
            color = textColor,
            fontSize = (72 * scale).sp.coerceAtLeast(32.sp),
            fontWeight = FontWeight.Light,
        )
        Text(
            text = dateFormat.format(currentTime.time),
            color = textColor.copy(alpha = 0.85f),
            fontSize = (16 * scale).sp.coerceAtLeast(12.sp),
        )
    }
}
