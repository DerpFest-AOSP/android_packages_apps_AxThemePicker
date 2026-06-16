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

package com.android.axion.themepicker.ui.effects

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.wallpaper.WallpaperDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PersistableBundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.R
import com.android.axion.themepicker.ui.restartApp
import com.android.axion.themepicker.utils.effects.*
import com.android.axion.themepicker.utils.wallpaper.getWallpaperBitmapForEffects
import com.android.axion.themepicker.viewmodel.MainScreenViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "WallpaperEffects"
private const val EFFECTS_PKG = "com.android.axion.wallpapereffects"

private val DEFAULT_PORTRAIT_COLOR = 0xFF1A1A2E.toInt()

private enum class WallpaperEffect {
    NONE,
    ATMOSPHERE,
    GLASS,
    WEATHER,
    MAGIC_PORTRAIT,
    CINEMATIC,
}

private enum class EffectTab {
    SHAPE,
    WEATHER,
    CINEMATIC,
    ATMOSPHERE,
    GLASS,
}

private data class EffectConfig(
    val effect: WallpaperEffect,
    val defaultFlags: Int = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
    val showTargetDialog: Boolean = false,
)

private val tabConfigs =
    mapOf(
        EffectTab.SHAPE to
            EffectConfig(
                effect = WallpaperEffect.MAGIC_PORTRAIT,
                defaultFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
            ),
        EffectTab.WEATHER to
            EffectConfig(effect = WallpaperEffect.WEATHER, showTargetDialog = true),
        EffectTab.CINEMATIC to
            EffectConfig(effect = WallpaperEffect.CINEMATIC, showTargetDialog = true),
        EffectTab.ATMOSPHERE to
            EffectConfig(
                effect = WallpaperEffect.ATMOSPHERE,
                defaultFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
            ),
        EffectTab.GLASS to EffectConfig(effect = WallpaperEffect.GLASS, showTargetDialog = true),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperEffectsScreen(mainScreenViewModel: MainScreenViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = MaterialTheme.colorScheme

    var activeEffectComponent by remember { mutableStateOf<ComponentName?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(EffectTab.SHAPE) }
    var isApplying by remember { mutableStateOf(false) }
    var applyResultMessage by remember { mutableStateOf<String?>(null) }
    var applySuccess by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    var weatherType by rememberSaveable { mutableStateOf("auto") }
    var weatherIntensity by rememberSaveable { mutableStateOf(0.7f) }

    var portraitShape by rememberSaveable { mutableStateOf(0) }
    var portraitColor by rememberSaveable { mutableStateOf(DEFAULT_PORTRAIT_COLOR) }
    var portraitLstar by rememberSaveable { mutableStateOf(45f) }

    val config = tabConfigs[selectedTab]!!

    val navigateBack: () -> Unit = {
        if (mainScreenViewModel.launchedDirectToEffects) {
            (context as? Activity)?.finish()
        } else {
            mainScreenViewModel.goBack()
        }
    }

    LaunchedEffect(Unit) {
        if (activeEffectComponent == null) {
            val wm = WallpaperManager.getInstance(context)
            activeEffectComponent = wm.wallpaperInfo?.component
        }
    }

    var photoReady by rememberSaveable { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val availableColors =
        remember(selectedPhotoUri) { extractWallpaperColors(context, selectedPhotoUri) }

    LaunchedEffect(availableColors) {
        if (availableColors.isNotEmpty() && portraitColor == DEFAULT_PORTRAIT_COLOR) {
            portraitColor = availableColors.first()
        }
    }

    var livePreviewReady by remember { mutableStateOf(false) }
    var engineShown by remember { mutableStateOf(false) }
    val surfaceViewRef = remember { mutableStateOf<SurfaceView?>(null) }
    val connectionRef = remember { mutableStateOf<WallpaperEffectConnection?>(null) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) {
            uri ->
            if (uri != null) {
                selectedPhotoUri = uri
            } else {
                navigateBack()
            }
        }

    LaunchedEffect(Unit) {
        if (!photoReady) {
            val success =
                withContext(Dispatchers.IO) { saveCurrentWallpaperForEffects(context) }
            if (success) {
                photoReady = true
            } else {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    LaunchedEffect(selectedPhotoUri) {
        val uri = selectedPhotoUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(context)
            if (wm.wallpaperInfo == null) {
                saveOriginalWallpaper(context)
            }
            saveSelectedPhotoForEffects(context, uri)
        }
        photoReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
            connectionRef.value?.disconnect()
            connectionRef.value = null
        }
    }

    LaunchedEffect(
        photoReady,
        surfaceViewRef.value,
        selectedTab,
        portraitShape,
        portraitColor,
        portraitLstar,
        weatherType,
        weatherIntensity,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        if (!photoReady) return@LaunchedEffect
        val sv = surfaceViewRef.value ?: return@LaunchedEffect

        connectionRef.value?.disconnect()
        connectionRef.value = null
        livePreviewReady = false
        engineShown = false

        val effect = tabConfigs[selectedTab]!!.effect
        val serviceName = getServiceName(effect) ?: return@LaunchedEffect
        val component = ComponentName(EFFECTS_PKG, "$EFFECTS_PKG.service.$serviceName")
        val description =
            buildPreviewDescription(
                effect,
                portraitShape,
                portraitColor,
                portraitLstar,
                weatherType,
                weatherIntensity,
            )

        Log.d(TAG, "Preview: tab=$selectedTab, service=$serviceName")

        delay(300)

        val conn = WallpaperEffectConnection(context, component, description)
        connectionRef.value = conn
        conn.setListener(
            object : WallpaperEffectConnection.Listener {
                override fun onEngineShown() {
                    Log.d(TAG, "Preview: live engine shown for $serviceName")

                    engineShown = true
                }
            }
        )
        conn.connect(sv)
    }

    LaunchedEffect(engineShown, selectedTab) {
        if (!engineShown) {
            livePreviewReady = false
            return@LaunchedEffect
        }
        val effect = tabConfigs[selectedTab]!!.effect
        val processingDelay =
            when (effect) {
                WallpaperEffect.MAGIC_PORTRAIT -> 3000L
                WallpaperEffect.CINEMATIC -> 2500L
                WallpaperEffect.WEATHER -> 1500L
                WallpaperEffect.ATMOSPHERE -> 1500L
                WallpaperEffect.GLASS -> 1000L
                WallpaperEffect.NONE -> 0L
            }
        delay(processingDelay)
        livePreviewReady = true
    }

    val onApplyClick: () -> Unit = {
        if (config.showTargetDialog) {
            showTargetDialog = true
        } else {
            isApplying = true
            applyEffect(
                context,
                config.effect,
                config.defaultFlags,
                weatherType,
                weatherIntensity,
                portraitShape,
                portraitColor,
                portraitLstar,
                onComponentChanged = { activeEffectComponent = it },
            ) { success ->
                isApplying = false
                if (success) applySuccess = true
                applyResultMessage =
                    if (success) context.getString(R.string.effect_applied)
                    else context.getString(R.string.effect_service_not_found)
            }
        }
    }

    val onRemoveEffect: () -> Unit = {
        isApplying = true
        applyEffect(
            context,
            WallpaperEffect.NONE,
            WallpaperManager.FLAG_SYSTEM,
            weatherType,
            weatherIntensity,
            portraitShape,
            portraitColor,
            portraitLstar,
            onComponentChanged = { activeEffectComponent = it },
        ) { success ->
            isApplying = false
            applyResultMessage =
                if (success) context.getString(R.string.effect_removed)
                else context.getString(R.string.effect_service_not_found)
        }
    }

    if (showTargetDialog) {
        EffectTargetDialog(
            onDismiss = { showTargetDialog = false },
            onSelect = { flags ->
                showTargetDialog = false
                isApplying = true
                applyEffect(
                    context,
                    config.effect,
                    flags,
                    weatherType,
                    weatherIntensity,
                    portraitShape,
                    portraitColor,
                    portraitLstar,
                    onComponentChanged = { activeEffectComponent = it },
                ) { success ->
                    isApplying = false
                    if (success) applySuccess = true
                    applyResultMessage =
                        if (success) context.getString(R.string.effect_applied)
                        else context.getString(R.string.effect_service_not_found)
                }
            },
        )
    }

    ApplyingEffectDialog(
        isApplying = isApplying,
        resultMessage = applyResultMessage,
        onDismissResult = {
            applyResultMessage = null
            if (applySuccess) {
                (context as? Activity)?.restartApp()
            }
        },
    )

    BackHandler { navigateBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    surfaceViewRef.value = sv
                    Log.d(TAG, "SurfaceView created")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = !livePreviewReady,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text =
                            stringResource(
                                if (engineShown) R.string.effect_processing
                                else if (photoReady) R.string.effect_connecting
                                else R.string.effect_preparing
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ComposeColor.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    controlsVisible = !controlsVisible
                }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter =
                fadeIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it },
            exit =
                fadeOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it },
        ) {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.wallpaper_effects_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    val removeInteraction = remember { MutableInteractionSource() }
                    val removePressed by removeInteraction.collectIsPressedAsState()
                    val removeScale by
                        animateFloatAsState(
                            targetValue = if (removePressed) 0.96f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "removeScale",
                        )
                    FilledTonalIconButton(
                        onClick = onRemoveEffect,
                        enabled = !isApplying,
                        interactionSource = removeInteraction,
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = removeScale
                                scaleY = removeScale
                            },
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                        alpha = 0.5f
                                    ),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor =
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                        alpha = 0.2f
                                    ),
                                disabledContentColor =
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.HideImage,
                            contentDescription = stringResource(R.string.effect_remove),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val applyInteraction = remember { MutableInteractionSource() }
                    val applyPressed by applyInteraction.collectIsPressedAsState()
                    val applyScale by
                        animateFloatAsState(
                            targetValue = if (applyPressed) 0.96f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "applyScale",
                        )
                    FilledIconButton(
                        onClick = onApplyClick,
                        enabled = !isApplying,
                        interactionSource = applyInteraction,
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = applyScale
                                scaleY = applyScale
                            },
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor =
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                                disabledContentColor =
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.effect_apply),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = ComposeColor.Transparent,
                        titleContentColor = ComposeColor.White,
                        navigationIconContentColor = ComposeColor.White,
                        actionIconContentColor = ComposeColor.White,
                    ),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
                fadeIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit =
                fadeOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val tabEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(tabEffectsSpec) togetherWith fadeOut(tabEffectsSpec)
                        },
                        label = "tabContent",
                    ) { tab ->
                        when (tab) {
                            EffectTab.SHAPE ->
                                ShapeTabContent(
                                    selectedShape = portraitShape,
                                    onShapeChange = { portraitShape = it },
                                    selectedColor = portraitColor,
                                    onColorChange = { portraitColor = it },
                                    availableColors = availableColors,
                                    lstarValue = portraitLstar,
                                    onLstarChange = { portraitLstar = it },
                                )
                            EffectTab.WEATHER ->
                                WeatherTabContent(
                                    weatherType = weatherType,
                                    onWeatherTypeChange = { weatherType = it },
                                    intensity = weatherIntensity,
                                    onIntensityChange = { weatherIntensity = it },
                                )
                            EffectTab.CINEMATIC ->
                                SimpleEffectContent(
                                    icon = Icons.Default.Theaters,
                                    description =
                                        stringResource(R.string.effect_cinematic_description),
                                )
                            EffectTab.ATMOSPHERE ->
                                SimpleEffectContent(
                                    icon = Icons.Default.WaterDrop,
                                    description =
                                        stringResource(R.string.effect_atmosphere_description),
                                )
                            EffectTab.GLASS ->
                                SimpleEffectContent(
                                    icon = Icons.Default.GridView,
                                    description = stringResource(R.string.effect_glass_description),
                                )
                        }
                    }

                    EffectTabSelector(selectedTab = selectedTab, onTabSelect = { selectedTab = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EffectTabSelector(selectedTab: EffectTab, onTabSelect: (EffectTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tabs =
        listOf(
            EffectTab.SHAPE to R.string.tab_shape,
            EffectTab.WEATHER to R.string.tab_weather,
            EffectTab.CINEMATIC to R.string.tab_cinematic,
            EffectTab.ATMOSPHERE to R.string.tab_atmosphere,
            EffectTab.GLASS to R.string.tab_glass,
        )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        tabs.forEach { (tab, labelRes) ->
            val isSelected = selectedTab == tab
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by
                animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "tabScale",
                )
            val bgColor by
                animateColorAsState(
                    targetValue =
                        if (isSelected) colors.secondaryContainer else colors.surfaceContainerHigh,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "tabBg",
                )
            val contentColor by
                animateColorAsState(
                    targetValue =
                        if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "tabContent",
                )

            Surface(
                onClick = { onTabSelect(tab) },
                interactionSource = interactionSource,
                shape = CircleShape,
                color = bgColor,
                contentColor = contentColor,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Text(
                    text = stringResource(labelRes),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style =
                        if (isSelected)
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        else MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeTabContent(
    selectedShape: Int,
    onShapeChange: (Int) -> Unit,
    selectedColor: Int,
    onColorChange: (Int) -> Unit,
    availableColors: List<Int>,
    lstarValue: Float,
    onLstarChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shapes =
        listOf(
            0 to Pair(R.drawable.ic_shape_pill, R.string.portrait_shape_oval),
            1 to Pair(R.drawable.ic_shape_square, R.string.portrait_shape_square),
            2 to Pair(R.drawable.ic_shape_arch, R.string.portrait_shape_arch),
            3 to Pair(R.drawable.ic_shape_cookie_4, R.string.portrait_shape_clover),
            4 to Pair(R.drawable.ic_shape_cookie_6, R.string.portrait_shape_flower),
        )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(shapes) { (type, iconAndLabel) ->
                val isSelected = selectedShape == type
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by
                    animateFloatAsState(
                        targetValue =
                            when {
                                isPressed -> 0.96f
                                isSelected -> 1.05f
                                else -> 1f
                            },
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "shapeScale",
                    )
                val borderColor by
                    animateColorAsState(
                        targetValue = if (isSelected) colors.primary else ComposeColor.Transparent,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "shapeBorder",
                    )
                val bgColor by
                    animateColorAsState(
                        targetValue =
                            if (isSelected) colors.primaryContainer
                            else colors.surfaceContainerHigh,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "shapeBg",
                    )
                val iconTint by
                    animateColorAsState(
                        targetValue =
                            if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "shapeIcon",
                    )

                Box(
                    modifier =
                        Modifier.size(56.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(2.dp, borderColor, CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) {
                                onShapeChange(type)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconAndLabel.first),
                        contentDescription = stringResource(iconAndLabel.second),
                        modifier = Modifier.size(28.dp),
                        tint = iconTint,
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(availableColors) { color ->
                val isSelected = selectedColor == color
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by
                    animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "colorScale",
                    )
                val borderColor by
                    animateColorAsState(
                        targetValue = if (isSelected) colors.primary else colors.outlineVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "colorBorder",
                    )
                val borderWidth by
                    animateDpAsState(
                        targetValue = if (isSelected) 3.dp else 1.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "colorBorderWidth",
                    )

                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(ComposeColor(color))
                            .border(borderWidth, borderColor, CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) {
                                onColorChange(color)
                            }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.portrait_brightness_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Slider(
                value = lstarValue,
                onValueChange = onLstarChange,
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors =
                    SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.secondaryContainer,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeatherTabContent(
    weatherType: String,
    onWeatherTypeChange: (String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val types =
        listOf(
            "auto" to Pair(Icons.Default.AutoMode, R.string.weather_type_auto),
            "rain" to Pair(Icons.Default.WaterDrop, R.string.weather_type_rain),
            "snow" to Pair(Icons.Default.AcUnit, R.string.weather_type_snow),
            "fog" to Pair(Icons.Default.Cloud, R.string.weather_type_fog),
            "clouds" to Pair(Icons.Default.CloudQueue, R.string.weather_type_clouds),
            "sun" to Pair(Icons.Default.WbSunny, R.string.weather_type_sun),
        )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(types) { (value, iconAndLabel) ->
                val isSelected = weatherType == value
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by
                    animateFloatAsState(
                        targetValue =
                            when {
                                isPressed -> 0.96f
                                isSelected -> 1.05f
                                else -> 1f
                            },
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "weatherScale",
                    )
                val bgColor by
                    animateColorAsState(
                        targetValue =
                            if (isSelected) colors.primaryContainer
                            else colors.surfaceContainerHigh,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "weatherBg",
                    )
                val contentColor by
                    animateColorAsState(
                        targetValue = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "weatherContent",
                    )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Box(
                        modifier =
                            Modifier.size(52.dp).clip(CircleShape).background(bgColor).clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) {
                                onWeatherTypeChange(value)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = iconAndLabel.first,
                            contentDescription = stringResource(iconAndLabel.second),
                            modifier = Modifier.size(24.dp),
                            tint = contentColor,
                        )
                    }
                    Text(
                        text = stringResource(iconAndLabel.second),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.weather_intensity_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Slider(
                value = intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors =
                    SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.secondaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun SimpleEffectContent(icon: ImageVector, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EffectTargetDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.effect_apply_to),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetOptionButton(
                    icon = Icons.Default.Home,
                    label = stringResource(R.string.effect_home_screen_only),
                    onClick = { onSelect(WallpaperManager.FLAG_SYSTEM) },
                )
                TargetOptionButton(
                    icon = Icons.Default.Lock,
                    label = stringResource(R.string.effect_lock_screen_only),
                    onClick = { onSelect(WallpaperManager.FLAG_LOCK) },
                )
                TargetOptionButton(
                    icon = Icons.Default.Smartphone,
                    label = stringResource(R.string.effect_home_and_lock),
                    onClick = {
                        onSelect(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TargetOptionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "targetScale",
        )

    FilledTonalButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApplyingEffectDialog(
    isApplying: Boolean,
    resultMessage: String?,
    onDismissResult: () -> Unit,
) {
    val showDialog = isApplying || resultMessage != null
    if (!showDialog) return

    val colors = MaterialTheme.colorScheme

    if (resultMessage != null) {
        LaunchedEffect(resultMessage) {
            delay(1500)
            onDismissResult()
        }
    }

    BasicAlertDialog(onDismissRequest = { if (resultMessage != null) onDismissResult() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val defaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
                val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = resultMessage != null,
                    transitionSpec = {
                        (fadeIn(defaultSpatial) +
                            scaleIn(slowSpatial, initialScale = 0.6f)) togetherWith
                            (fadeOut(fastEffects) + scaleOut(fastEffects, targetScale = 0.8f))
                    },
                    label = "applyState",
                ) { hasResult ->
                    if (hasResult) {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = resultMessage ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = colors.primary,
                            )
                            Text(
                                text = stringResource(R.string.effect_applying),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractWallpaperColors(context: Context, photoUri: Uri? = null): List<Int> {
    val result = mutableListOf<Int>()
    try {
        val wallpaperColors =
            if (photoUri != null) {

                val inputStream = context.contentResolver.openInputStream(photoUri)
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val colors = WallpaperColors.fromBitmap(bitmap)
                    bitmap.recycle()
                    colors
                } else null
            } else {

                val wm = WallpaperManager.getInstance(context)
                wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }
        wallpaperColors?.primaryColor?.toArgb()?.let { result.add(it) }
        wallpaperColors?.secondaryColor?.toArgb()?.let { result.add(it) }
        wallpaperColors?.tertiaryColor?.toArgb()?.let { result.add(it) }
    } catch (_: Exception) {}

    result.addAll(
        listOf(
            0xFF1A1A2E.toInt(),
            0xFF2D2D2D.toInt(),
            0xFF8B0000.toInt(),
            0xFF004D40.toInt(),
            0xFF1B3A4B.toInt(),
        )
    )
    return result.distinct().take(8)
}

private fun getServiceName(effect: WallpaperEffect): String? =
    when (effect) {
        WallpaperEffect.ATMOSPHERE -> "AtmosphereService"
        WallpaperEffect.GLASS -> "GlassService"
        WallpaperEffect.WEATHER -> "WeatherService"
        WallpaperEffect.MAGIC_PORTRAIT -> "MagicPortraitService"
        WallpaperEffect.CINEMATIC -> "CinematicService"
        WallpaperEffect.NONE -> null
    }

private fun buildPreviewDescription(
    effect: WallpaperEffect,
    portraitShape: Int,
    portraitColor: Int,
    portraitLstar: Float,
    weatherType: String,
    weatherIntensity: Float,
): WallpaperDescription {
    val content =
        PersistableBundle().apply {
            putString("effect_type", effect.name)
            when (effect) {
                WallpaperEffect.MAGIC_PORTRAIT -> {
                    putInt("portrait_shape", portraitShape)
                    putInt("portrait_color", portraitColor)
                    putDouble("portrait_lstar", portraitLstar.toDouble())
                }
                WallpaperEffect.WEATHER -> {
                    putString("weather_type", weatherType)
                    putDouble("weather_intensity", weatherIntensity.toDouble())
                }
                else -> {}
            }
        }
    return WallpaperDescription.Builder().setContent(content).build()
}

private fun applyEffect(
    context: Context,
    effect: WallpaperEffect,
    flags: Int,
    weatherType: String,
    weatherIntensity: Float,
    portraitShape: Int,
    portraitColor: Int,
    portraitLstar: Float,
    onComponentChanged: (ComponentName?) -> Unit,
    onResult: (Boolean) -> Unit,
) {
    try {
        val wm = WallpaperManager.getInstance(context)
        val cr = context.contentResolver

        when (effect) {
            WallpaperEffect.WEATHER -> {
                Settings.Secure.putString(cr, "ax_effect_weather_type", weatherType)
                Settings.Secure.putString(
                    cr,
                    "ax_effect_weather_intensity",
                    weatherIntensity.toString(),
                )
            }
            WallpaperEffect.MAGIC_PORTRAIT -> {
                Settings.Secure.putInt(cr, "ax_effect_portrait_shape", portraitShape)
                Settings.Secure.putInt(cr, "ax_effect_portrait_color", portraitColor)
                Settings.Secure.putString(cr, "ax_effect_portrait_lstar", portraitLstar.toString())
            }
            else -> {}
        }

        when (effect) {
            WallpaperEffect.NONE -> {
                onComponentChanged(null)

                try {
                    val effectsCtx =
                        context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
                    val deCtx = effectsCtx.createDeviceProtectedStorageContext()
                    val file = File(deCtx.filesDir, "original_wallpaper.jpg")
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            wm.setBitmap(
                                bitmap,
                                null,
                                false,
                                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                            )
                            bitmap.recycle()
                            Log.d(TAG, "Restored original wallpaper from DE storage")
                        } else {
                            wm.clear()
                        }
                    } else {
                        wm.clear()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore wallpaper, clearing", e)
                    try {
                        wm.clear()
                    } catch (_: Exception) {}
                }
                onResult(true)
            }
            else -> {

                val serviceName = getServiceName(effect) ?: return
                val component = ComponentName(EFFECTS_PKG, "$EFFECTS_PKG.service.$serviceName")

                val currentSystemComponent = wm.wallpaperInfo?.component
                if (currentSystemComponent != component) {
                    val content =
                        PersistableBundle().apply {
                            putString("effect_type", effect.name)
                            when (effect) {
                                WallpaperEffect.MAGIC_PORTRAIT -> {
                                    putInt("portrait_shape", portraitShape)
                                    putInt("portrait_color", portraitColor)
                                    putDouble("portrait_lstar", portraitLstar.toDouble())
                                }
                                WallpaperEffect.WEATHER -> {
                                    putString("weather_type", weatherType)
                                    putDouble("weather_intensity", weatherIntensity.toDouble())
                                }
                                else -> {}
                            }
                        }
                    val description =
                        WallpaperDescription.Builder()
                            .setComponent(component)
                            .setTitle(serviceName)
                            .setContent(content)
                            .build()
                    wm.setWallpaperComponentWithDescription(
                        description,
                        flags,
                        UserHandle.myUserId(),
                    )
                    onComponentChanged(component)
                }

                try {
                    context.sendBroadcast(
                        Intent("com.android.axion.wallpapereffects.RELOAD_WALLPAPER")
                            .setPackage(EFFECTS_PKG)
                    )
                } catch (_: Exception) {}

                onResult(true)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to apply effect", e)
        onResult(false)
    }
}

private fun saveOriginalWallpaper(context: Context) {
    try {
        val bitmap = getWallpaperBitmapForEffects(context) ?: return
        val effectsCtx = context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val deCtx = effectsCtx.createDeviceProtectedStorageContext()
        val filesDir = deCtx.filesDir
        filesDir.mkdirs()
        val file = File(filesDir, "original_wallpaper.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        Log.d(TAG, "Saved original wallpaper to ${file.absolutePath}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save original wallpaper", e)
    }
}

private fun saveSelectedPhotoForEffects(context: Context, uri: Uri) {
    try {
        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: run {
                    Log.e(TAG, "Failed to open selected photo URI")
                    return
                }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode selected photo")
            return
        }

        val effectsCtx = context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val deCtx = effectsCtx.createDeviceProtectedStorageContext()
        val filesDir = deCtx.filesDir
        filesDir.mkdirs()

        val file = File(filesDir, "wallpaper.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        File(filesDir, "effect_foreground.png").delete()

        bitmap.recycle()
        Log.d(TAG, "Saved selected photo to ${file.absolutePath}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save selected photo", e)
    }
}

private fun saveCurrentWallpaperForEffects(context: Context): Boolean {
    try {
        val bitmap = getWallpaperBitmapForEffects(context) ?: return false
        val effectsCtx = context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val deCtx = effectsCtx.createDeviceProtectedStorageContext()
        val filesDir = deCtx.filesDir
        filesDir.mkdirs()

        val file = File(filesDir, "wallpaper.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        val wm = WallpaperManager.getInstance(context)
        if (wm.wallpaperInfo == null) {
            val originalFile = File(filesDir, "original_wallpaper.jpg")
            if (!originalFile.exists()) {
                file.copyTo(originalFile, overwrite = true)
            }
        }

        File(filesDir, "effect_foreground.png").delete()
        bitmap.recycle()
        Log.d(TAG, "Saved current wallpaper for effects to ${file.absolutePath}")
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save current wallpaper for effects", e)
        return false
    }
}
