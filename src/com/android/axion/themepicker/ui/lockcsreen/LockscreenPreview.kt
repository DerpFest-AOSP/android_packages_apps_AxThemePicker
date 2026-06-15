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

import android.app.Activity
import android.app.WallpaperColors
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.android.axion.themepicker.R
import com.android.axion.themepicker.data.model.Screen.EntryPoint
import com.android.axion.themepicker.ui.components.CommonBottomSheet
import com.android.axion.themepicker.ui.lockscreen.widgets.*
import com.android.axion.themepicker.utils.math.scaleRatio
import com.android.axion.themepicker.utils.wallpaper.getCurrentWallpaperBitmap
import com.android.axion.themepicker.utils.wallpaper.getForegroundBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockscreenPreview(
    isPreview: Boolean = false,
    wallpaperBitmap: Bitmap? = null,
    modifier: Modifier = Modifier,
    entryPoint: EntryPoint = EntryPoint.DEFAULT,
    onEditWallpaper: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val wallpaper = wallpaperBitmap ?: getCurrentWallpaperBitmap(context, false) ?: return
    val foreground: Bitmap? = remember(wallpaper) { getForegroundBitmap(context) }

    val isRegionDark by
        produceState(true, wallpaper) {
            value =
                withContext(Dispatchers.Default) {
                    val colors = WallpaperColors.fromBitmap(wallpaper)
                    (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0
                }
        }

    var showPicker by remember { mutableStateOf(false) }
    var showAffordancePicker by remember { mutableStateOf<AffordanceSlot?>(null) }
    var resizeTarget by remember { mutableStateOf<GridWidgetItem?>(null) }
    var widgetItems by remember { mutableStateOf(loadWidgets(context)) }
    val dragDropState = remember { WidgetDragDropState() }
    val scale = if (isPreview) context.previewScale else context.scaleRatio
    val coroutineScope = rememberCoroutineScope()

    var affordanceSelections by remember { mutableStateOf<List<AffordanceSelection>>(emptyList()) }
    var affordanceList by remember { mutableStateOf<List<AffordanceInfo>>(emptyList()) }

    val displayMetrics = context.resources.displayMetrics
    val sw = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
    val isTablet = sw >= 600f

    val hostManager = remember { if (!isPreview) WidgetHostManager(context) else null }
    var hostViews by remember { mutableStateOf<Map<Int, AppWidgetHostView>>(emptyMap()) }

    var settingsVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        if (!isTablet) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        hostManager?.startListening()

        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    settingsVersion++
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("lockscreen_widgets_config"),
            false,
            observer,
            UserHandle.USER_CURRENT,
        )

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            hostManager?.stopListening()
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(settingsVersion) {
        val mgr = hostManager ?: return@LaunchedEffect

        if (settingsVersion > 0) {
            val newItems = loadWidgets(context)
            if (newItems != widgetItems) {
                widgetItems = newItems
            }
        }

        val views = mutableMapOf<Int, AppWidgetHostView>()
        widgetItems.forEach { item ->
            if (item.appWidgetId >= 0) {

                val existing = hostViews[item.appWidgetId]
                if (existing != null) {
                    views[item.appWidgetId] = existing
                } else {
                    val view = mgr.createView(item.appWidgetId)
                    if (view != null) {
                        updateWidgetViewSize(mgr, view, item, scale)
                        views[item.appWidgetId] = view
                    }
                }
            }
        }
        hostViews = views

        repeat(MAX_VIEW_REFRESH_RETRIES) {
            delay(VIEW_REFRESH_INTERVAL_MS)
            if (!isActive) return@LaunchedEffect
            val needsRefresh =
                hostViews.entries.filter { (_, view) ->
                    (view as? LockscreenAppWidgetHostView)?.hasRealViews == false
                }
            if (needsRefresh.isEmpty()) return@LaunchedEffect

            val updated = mutableMapOf<Int, AppWidgetHostView>()
            for ((id, _) in needsRefresh) {
                val item = widgetItems.find { it.appWidgetId == id } ?: continue
                val newView = mgr.createView(id) ?: continue
                if ((newView as? LockscreenAppWidgetHostView)?.hasRealViews == true) {
                    updateWidgetViewSize(mgr, newView, item, scale)
                    updated[id] = newView
                }
            }
            if (updated.isNotEmpty()) {
                hostViews = hostViews + updated
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            AffordanceRepository.observeSelections(context).collect { affordanceSelections = it }
        }
        launch { AffordanceRepository.observeAffordances(context).collect { affordanceList = it } }
    }

    var entryPointConsumed by remember { mutableStateOf(false) }

    LaunchedEffect(entryPoint, affordanceList) {
        if (entryPointConsumed) return@LaunchedEffect
        when (entryPoint) {
            EntryPoint.WIDGETS -> {
                showPicker = true
                entryPointConsumed = true
            }
            EntryPoint.SHORTCUTS -> {
                if (affordanceList.isNotEmpty()) {
                    showAffordancePicker = AffordanceSlot.BOTTOM_START
                    entryPointConsumed = true
                }
            }
            EntryPoint.CLOCK -> {
                entryPointConsumed = true
            }
            else -> {
                entryPointConsumed = true
            }
        }
    }

    fun updateWidgets(newItems: List<GridWidgetItem>) {

        val removedIds =
            widgetItems
                .filter { old ->
                    old.appWidgetId >= 0 && newItems.none { it.appWidgetId == old.appWidgetId }
                }
                .map { it.appWidgetId }
        if (removedIds.isNotEmpty()) {
            hostViews = hostViews - removedIds.toSet()
        }
        widgetItems = newItems
        if (!isPreview) saveWidgets(context, newItems)
    }

    fun handleWidgetSelected(selected: GridWidgetItem) {

        val intent =
            Intent().apply {
                setClassName("com.android.systemui", "com.android.systemui.lockscreen.KeyguardWidgetConfigActivity")
                putExtra("extra_provider", selected.provider)
                putExtra("extra_cell_x", selected.cellX)
                putExtra("extra_cell_y", selected.cellY)
                putExtra("extra_span_x", selected.spanX)
                putExtra("extra_span_y", selected.spanY)
            }
        context.startActivity(intent)
    }

    fun handleWidgetResized(resized: GridWidgetItem) {
        val newItems =
            widgetItems.map { item -> if (item.provider == resized.provider) resized else item }
        updateWidgets(newItems)

        val mgr = hostManager
        if (mgr != null && resized.appWidgetId >= 0) {
            val newView = mgr.createView(resized.appWidgetId)
            if (newView != null) {
                updateWidgetViewSize(mgr, newView, resized, scale)
                hostViews = hostViews + (resized.appWidgetId to newView)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = isTablet && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val wallpaperImageBitmap = remember(wallpaper) { wallpaper.asImageBitmap() }

    Box(modifier = modifier) {
        Image(
            bitmap = wallpaperImageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (isLandscape) {
            LandscapeLayout(
                isPreview = isPreview,
                isRegionDark = isRegionDark,
                scale = scale,
                widgetItems = widgetItems,
                hostViews = if (isPreview) emptyMap() else hostViews,
                dragDropState = dragDropState,
                affordanceSelections = affordanceSelections,
                affordanceList = affordanceList,
                activeAffordanceSlot = showAffordancePicker,
                onAffordanceSlotClicked = { slot -> showAffordancePicker = slot },
                onRemoveWidget = { if (!isPreview) updateWidgets(widgetItems - it) },
                onPickWidget = { if (!isPreview) showPicker = true },
                onResizeWidget = if (!isPreview) ::handleWidgetResized else null,
                onConfigureWidget = if (!isPreview) { widget -> launchWidgetConfigure(context, widget) } else null,
                onWidgetsMoved =
                    if (!isPreview) { newWidgets -> updateWidgets(newWidgets) } else null,
                onEditWallpaper = onEditWallpaper,
                foregroundBitmap = foreground,
            )
        } else {
            PortraitLayout(
                isPreview = isPreview,
                isRegionDark = isRegionDark,
                scale = scale,
                widgetItems = widgetItems,
                hostViews = if (isPreview) emptyMap() else hostViews,
                dragDropState = dragDropState,
                affordanceSelections = affordanceSelections,
                affordanceList = affordanceList,
                activeAffordanceSlot = showAffordancePicker,
                onAffordanceSlotClicked = { slot -> showAffordancePicker = slot },
                onRemoveWidget = { if (!isPreview) updateWidgets(widgetItems - it) },
                onPickWidget = { if (!isPreview) showPicker = true },
                onResizeWidget = if (!isPreview) ::handleWidgetResized else null,
                onConfigureWidget = if (!isPreview) { widget -> launchWidgetConfigure(context, widget) } else null,
                onWidgetsMoved =
                    if (!isPreview) { newWidgets -> updateWidgets(newWidgets) } else null,
                onEditWallpaper = onEditWallpaper,
                foregroundBitmap = foreground,
            )
        }

        if (!isPreview) {
            WidgetPickerBottomSheet(
                visible = showPicker,
                widgets = widgetItems,
                onDismiss = { showPicker = false },
                onSelect = { selected -> handleWidgetSelected(selected) },
            )

            if (affordanceList.isNotEmpty()) {
                showAffordancePicker?.let { slot ->
                    val currentId =
                        affordanceSelections.firstOrNull { it.slotId == slot.slotId }
                            ?.affordanceId

                    AffordancePickerSheet(
                        visible = true,
                        currentSlot = slot,
                        currentAffordanceId = currentId,
                        affordances = affordanceList,
                        onDismiss = { showAffordancePicker = null },
                        onSelect = { selected ->
                            coroutineScope.launch {
                                AffordanceRepository.selectAffordance(
                                    context,
                                    slot.slotId,
                                    selected.id,
                                )
                            }
                        },
                        onRemove = {
                            coroutineScope.launch {
                                AffordanceRepository.unselectAll(context, slot.slotId)
                            }
                        },
                    )
                }
            }

            WidgetResizeSheet(
                target = resizeTarget,
                widgets = widgetItems,
                onResize = { resized ->
                    handleWidgetResized(resized)
                    resizeTarget = null
                },
                onDismiss = { resizeTarget = null },
            )
        }
    }
}

private fun updateWidgetViewSize(
    hostManager: WidgetHostManager,
    view: AppWidgetHostView,
    item: GridWidgetItem,
    scale: Float,
) {
    val cellDp = Dimens.WidgetCellSize.value * scale
    val gapDp = Dimens.WidgetCellGap.value * scale
    val wDp = (item.spanX * cellDp + (item.spanX - 1).coerceAtLeast(0) * gapDp).toInt()
    val hDp = (item.spanY * cellDp + (item.spanY - 1).coerceAtLeast(0) * gapDp).toInt()
    hostManager.updateSize(view, wDp, hDp)

    val density = view.context.resources.displayMetrics.density
    val cornerRadiusPx = Dimens.WidgetCellCorner.value * scale * density
    hostManager.setCornerRadius(view, cornerRadiusPx)
}

@Composable
private fun PortraitLayout(
    isPreview: Boolean,
    isRegionDark: Boolean,
    scale: Float,
    widgetItems: List<GridWidgetItem>,
    hostViews: Map<Int, AppWidgetHostView>,
    dragDropState: WidgetDragDropState,
    affordanceSelections: List<AffordanceSelection>,
    affordanceList: List<AffordanceInfo>,
    activeAffordanceSlot: AffordanceSlot?,
    onAffordanceSlotClicked: (AffordanceSlot) -> Unit,
    onRemoveWidget: (GridWidgetItem) -> Unit,
    onPickWidget: () -> Unit,
    onResizeWidget: ((GridWidgetItem) -> Unit)?,
    onConfigureWidget: ((GridWidgetItem) -> Unit)?,
    onWidgetsMoved: ((List<GridWidgetItem>) -> Unit)?,
    onEditWallpaper: (() -> Unit)? = null,
    foregroundBitmap: Bitmap? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = Dimens.ClockTopPadding * scale * 1.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            PreviewClock(isPreview, isRegionDark)

            if (foregroundBitmap != null) {
                Image(
                    bitmap = foregroundBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        if (!isPreview) Spacer(modifier = Modifier.height(Dimens.ClockSpacer * scale))
        WidgetGrid(
            isPreview = isPreview,
            widgets = widgetItems,
            onRemove = onRemoveWidget,
            onPickWidget = onPickWidget,
            onConfigure = onConfigureWidget,
            onResizeWidget = onResizeWidget,
            dragDropState = if (!isPreview) dragDropState else null,
            onWidgetsMoved = onWidgetsMoved,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!isPreview) {
            DummyNotifications(isPreview = false, scale = scale)
            if (onEditWallpaper != null) {
                Spacer(modifier = Modifier.height(8.dp * scale))
                WallpaperPill(onClick = onEditWallpaper, scale = scale)
            }
        }
        Spacer(modifier = Modifier.height(8.dp * scale))
        AffordanceOverlay(
            isPreview = isPreview,
            scale = scale,
            selections = affordanceSelections,
            affordances = affordanceList,
            activeSlot = activeAffordanceSlot,
            onSlotClicked = onAffordanceSlotClicked,
        )
        Spacer(modifier = Modifier.height(24.dp * scale))
    }
}

@Composable
private fun LandscapeLayout(
    isPreview: Boolean,
    isRegionDark: Boolean,
    scale: Float,
    widgetItems: List<GridWidgetItem>,
    hostViews: Map<Int, AppWidgetHostView>,
    dragDropState: WidgetDragDropState,
    affordanceSelections: List<AffordanceSelection>,
    affordanceList: List<AffordanceInfo>,
    activeAffordanceSlot: AffordanceSlot?,
    onAffordanceSlotClicked: (AffordanceSlot) -> Unit,
    onRemoveWidget: (GridWidgetItem) -> Unit,
    onPickWidget: () -> Unit,
    onResizeWidget: ((GridWidgetItem) -> Unit)?,
    onConfigureWidget: ((GridWidgetItem) -> Unit)?,
    onWidgetsMoved: ((List<GridWidgetItem>) -> Unit)?,
    onEditWallpaper: (() -> Unit)? = null,
    foregroundBitmap: Bitmap? = null,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(1f).fillMaxHeight().padding(top = Dimens.ClockTopPadding * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                PreviewClock(isPreview, isRegionDark)

                if (foregroundBitmap != null) {
                    Image(
                        bitmap = foregroundBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (!isPreview) Spacer(modifier = Modifier.height(Dimens.ClockSpacer * scale))
            WidgetGrid(
                isPreview = isPreview,
                widgets = widgetItems,
                onRemove = onRemoveWidget,
                onPickWidget = onPickWidget,
                onConfigure = onConfigureWidget,
                onResizeWidget = onResizeWidget,
                dragDropState = if (!isPreview) dragDropState else null,
                onWidgetsMoved = onWidgetsMoved,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!isPreview && onEditWallpaper != null) {
                WallpaperPill(onClick = onEditWallpaper, scale = scale)
                Spacer(modifier = Modifier.height(8.dp * scale))
            }
            AffordanceOverlay(
                isPreview = isPreview,
                scale = scale,
                selections = affordanceSelections,
                affordances = affordanceList,
                activeSlot = activeAffordanceSlot,
                onSlotClicked = onAffordanceSlotClicked,
            )
            Spacer(modifier = Modifier.height(24.dp * scale))
        }

        if (!isPreview) {
            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .padding(top = Dimens.ClockTopPadding * scale),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DummyNotifications(isPreview = false, scale = scale)
            }
        }
    }
}

@Composable
private fun WallpaperPill(onClick: () -> Unit, scale: Float) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp * scale),
        color = Color.Transparent,
        border = BorderStroke(1.dp * scale, Color.White.copy(alpha = 0.6f)),
    ) {
        Text(
            text = stringResource(R.string.wallpaper),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp * scale, vertical = 8.dp * scale),
        )
    }
}

@Composable
private fun WidgetResizeSheet(
    target: GridWidgetItem?,
    widgets: List<GridWidgetItem>,
    onResize: (GridWidgetItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val noSpaceMsg = stringResource(R.string.no_space_for_size)

    CommonBottomSheet(
        visible = target != null,
        title = stringResource(R.string.resize_widget),
        heightFraction = 0.35f,
        onDismiss = onDismiss,
    ) {
        if (target == null) return@CommonBottomSheet

        val sizeRows = listOf(listOf(1 to 1, 2 to 1, 4 to 1), listOf(1 to 2, 2 to 2, 4 to 2))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sizeRows.forEach { rowSizes ->
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowSizes.forEach { (spanX, spanY) ->
                        val isCurrent = spanX == target.spanX && spanY == target.spanY
                        val newPos =
                            if (isCurrent) null
                            else
                                findAvailablePositionExcluding(
                                    widgets,
                                    target.provider,
                                    spanX,
                                    spanY,
                                    target.cellX,
                                    target.cellY,
                                )
                        val canFit = isCurrent || newPos != null

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large)
                                    .clickable {
                                        if (isCurrent) {
                                            onDismiss()
                                        } else if (newPos != null) {
                                            onResize(
                                                target.copy(
                                                    cellX = newPos.first,
                                                    cellY = newPos.second,
                                                    spanX = spanX,
                                                    spanY = spanY,
                                                )
                                            )
                                        } else {
                                            Toast.makeText(context, noSpaceMsg, Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }
                                    .padding(8.dp),
                        ) {
                            val previewW = (32 * spanX).dp
                            val previewH = (32 * spanY).dp
                            Box(
                                modifier =
                                    Modifier.size(width = previewW, height = previewH)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            when {
                                                isCurrent -> colors.primary
                                                canFit -> colors.primaryContainer
                                                else -> colors.surfaceContainerHigh
                                            }
                                        )
                                        .then(
                                            if (isCurrent)
                                                Modifier.border(
                                                    2.dp,
                                                    colors.primary,
                                                    MaterialTheme.shapes.medium,
                                                )
                                            else Modifier
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${spanX}\u00D7${spanY}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        when {
                                            isCurrent -> colors.onPrimary
                                            canFit -> colors.onPrimaryContainer
                                            else -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_VIEW_REFRESH_RETRIES = 10
private const val VIEW_REFRESH_INTERVAL_MS = 2000L
private const val WIDGET_CONFIGURE_REQUEST = 4242

private fun launchWidgetConfigure(context: Context, widget: GridWidgetItem) {
    if (widget.appWidgetId < 0) {
        Log.w("AxConfigure", "widget has no appWidgetId")
        Toast.makeText(context, "Configure unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val info = widget.providerInfo(context)
    val configure = info?.configure
    if (configure == null) {
        Log.w("AxConfigure", "provider has no configure activity id=${widget.appWidgetId}")
        Toast.makeText(context, "Configure unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val activity = context as? Activity
    val intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.appWidgetId)
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    Log.d(
        "AxConfigure",
        "launch id=${widget.appWidgetId} configure=$configure activity=${activity != null}",
    )
    try {
        if (activity != null) {
            activity.startActivityForResult(intent, WIDGET_CONFIGURE_REQUEST)
        } else {
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Log.e("AxConfigure", "launch failed", e)
        Toast.makeText(context, "Configure unavailable: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
