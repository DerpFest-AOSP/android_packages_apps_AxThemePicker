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

package com.android.axion.themepicker.ui.app

import android.app.Activity
import android.app.WallpaperManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.data.model.NavigationDestination
import com.android.axion.themepicker.data.model.Screen
import com.android.axion.themepicker.data.model.WallpaperInfo
import com.android.axion.themepicker.ui.colors.ColorsSettingsScreen
import com.android.axion.themepicker.ui.components.DeferredScreen
import com.android.axion.themepicker.ui.components.ExpressiveScaffold
import com.android.axion.themepicker.ui.effects.WallpaperEffectsScreen
import com.android.axion.themepicker.ui.gallery.WallpaperGalleryScreen
import com.android.axion.themepicker.ui.lockscreen.LockscreenPreview
import com.android.axion.themepicker.ui.preview.WallpaperPreviewScreen
import com.android.axion.themepicker.ui.restartApp
import com.android.axion.themepicker.ui.sections.LockscreenSection
import com.android.axion.themepicker.ui.sections.StyleSection
import com.android.axion.themepicker.ui.sections.WallpaperSection
import com.android.axion.themepicker.ui.themes.AppGridSettingsScreen
import com.android.axion.themepicker.ui.themes.IconShapesScreen
import com.android.axion.themepicker.ui.themes.ThemedIconsScreen
import com.android.axion.themepicker.ui.wallpaperset.WallpaperCropScreen
import com.android.axion.themepicker.ui.wallpaperset.computeDisplayCropHints
import com.android.axion.themepicker.utils.wallpaper.applyWallpaper
import com.android.axion.themepicker.utils.wallpaper.getCurrentWallpaperBitmap
import com.android.axion.themepicker.utils.wallpaper.getOriginalWallpaperUri
import com.android.axion.themepicker.viewmodel.MainScreenViewModel
import com.android.axion.themepicker.viewmodel.WallpaperGalleryViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemePickerApp(
    mainScreenViewModel: MainScreenViewModel = viewModel(),
    galleryViewModel: WallpaperGalleryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val currentScreen by mainScreenViewModel.currentScreen.collectAsStateWithLifecycle()
    val wallpapers by mainScreenViewModel.wallpapers.collectAsStateWithLifecycle()
    val isNavigatingBack by mainScreenViewModel.isNavigatingBack.collectAsStateWithLifecycle()

    var currentDestinationIndex by rememberSaveable { mutableIntStateOf(0) }

    val currentDestination =
        NavigationDestination.destinations.getOrElse(currentDestinationIndex) {
            NavigationDestination.Wallpaper
        }

    LaunchedEffect(Unit) {
        mainScreenViewModel.initialize(context)
        galleryViewModel.initialize()
    }

    val screenFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val screenSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState.isHeavyScreen || initialState.isHeavyScreen) {
                fadeIn(screenFadeSpec) togetherWith fadeOut(screenFadeSpec)
            } else {
                val direction = if (isNavigatingBack) SlideDirection.End else SlideDirection.Start
                (slideIntoContainer(towards = direction, animationSpec = screenSlideSpec) +
                    fadeIn(animationSpec = screenFadeSpec)) togetherWith
                    (slideOutOfContainer(towards = direction, animationSpec = screenSlideSpec) +
                        fadeOut(animationSpec = screenFadeSpec)) using
                    SizeTransform(clip = false)
            }
        },
        label = "screen_transition",
    ) { screen ->
        if (screen is Screen.Main) {
            MainNavigationScaffold(
                currentDestination = currentDestination,
                onDestinationSelected = { dest ->
                    val index = NavigationDestination.destinations.indexOf(dest)
                    if (index >= 0) currentDestinationIndex = index
                },
                wallpapers = wallpapers,
                mainScreenViewModel = mainScreenViewModel,
            )
        } else {
            DetailScreenContent(
                screen = screen,
                mainScreenViewModel = mainScreenViewModel,
                galleryViewModel = galleryViewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainNavigationScaffold(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    wallpapers: List<WallpaperInfo>,
    mainScreenViewModel: MainScreenViewModel,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val photoPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let { mainScreenViewModel.onOpenWallpaperCrop(sourceUri = it) }
        }

    BackHandler { activity?.finish() }

    ExpressiveScaffold(
        currentDestination = currentDestination,
        onDestinationSelected = onDestinationSelected,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val slideSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
            val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    val forward = initialState.route < targetState.route
                    val enterOffset = if (forward) { it: Int -> it / 4 } else { it: Int -> -it / 4 }
                    val exitOffset = if (forward) { it: Int -> -it / 4 } else { it: Int -> it / 4 }

                    (fadeIn(fadeSpec) +
                        slideInHorizontally(
                            initialOffsetX = enterOffset,
                            animationSpec = slideSpec,
                        )) togetherWith
                        (fadeOut(fadeSpec) +
                            slideOutHorizontally(
                                targetOffsetX = exitOffset,
                                animationSpec = slideSpec,
                            )) using
                        SizeTransform(clip = false)
                },
                label = "section_transition",
            ) { destination ->
                when (destination) {
                    NavigationDestination.Wallpaper -> {
                        WallpaperSection(
                            wallpapers = wallpapers,
                            onWallpaperSelected = { wallpaper ->
                                mainScreenViewModel.onOpenWallpaperCrop(
                                    drawableRes = wallpaper.drawableRes
                                )
                            },
                            onEditCurrent = {
                                getOriginalWallpaperUri(context)?.let {
                                    mainScreenViewModel.onOpenWallpaperCrop(sourceUri = it)
                                } ?: mainScreenViewModel.onOpenWallpaperCrop()
                            },
                            onOpenGallery = mainScreenViewModel::onOpenGallery,
                            onSelectPhoto = { photoPickerLauncher.launch("image/*") },
                            onOpenEffects = mainScreenViewModel::onOpenWallpaperEffects,
                        )
                    }
                    NavigationDestination.Style -> {
                        StyleSection(
                            onOpenColors = mainScreenViewModel::onOpenColorsSettings,
                            onOpenAppGrid = mainScreenViewModel::onOpenAppGrid,
                            onOpenIconShapes = mainScreenViewModel::onOpenIconShapes,
                            onOpenThemedIcons = mainScreenViewModel::onOpenThemedIcons,
                        )
                    }
                    NavigationDestination.Lockscreen -> {
                        LockscreenSection(
                            onOpenFullPreview = { entryPoint ->
                                mainScreenViewModel.onOpenLockscreenPreview(entryPoint = entryPoint)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreenContent(
    screen: Screen,
    mainScreenViewModel: MainScreenViewModel,
    galleryViewModel: WallpaperGalleryViewModel,
) {
    val context = LocalContext.current

    when (screen) {
        is Screen.Main -> {}

        is Screen.ColorsSettings -> {
            BackHandler { mainScreenViewModel.resetToMain() }
            ColorsSettingsScreen()
        }

        is Screen.WallpaperGallery -> {
            val photoPickerLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { mainScreenViewModel.onOpenWallpaperCrop(sourceUri = it) }
                }

            WallpaperGalleryScreen(
                galleryViewModel = galleryViewModel,
                onSelectPhoto = { photoPickerLauncher.launch("image/*") },
            )
        }

        is Screen.WallpaperEffects -> {
            WallpaperEffectsScreen(mainScreenViewModel = mainScreenViewModel)
        }

        is Screen.AppGrid -> {
            BackHandler { mainScreenViewModel.goBack() }
            AppGridSettingsScreen(mainScreenViewModel = mainScreenViewModel)
        }

        is Screen.IconShapes -> {
            BackHandler { mainScreenViewModel.goBack() }
            IconShapesScreen(mainScreenViewModel = mainScreenViewModel)
        }

        is Screen.ThemedIcons -> {
            BackHandler { mainScreenViewModel.goBack() }
            ThemedIconsScreen(mainScreenViewModel = mainScreenViewModel)
        }

        is Screen.WallpaperCrop -> {
            BackHandler { mainScreenViewModel.goBack() }
            DeferredScreen {
                WallpaperCropScreen(
                    imageUri = screen.sourceUri,
                    drawableRes = screen.drawableRes,
                    onNext = { croppedBitmap ->
                        mainScreenViewModel.onCropCompleted(croppedBitmap, screen.targetFlags)
                    },
                    onCancel = { mainScreenViewModel.goBack() },
                )
            }
        }

        is Screen.WallpaperPreview -> {
            BackHandler { mainScreenViewModel.goBack() }
            DeferredScreen {
                WallpaperPreviewScreen(
                    wallpaperBitmap = mainScreenViewModel.pendingPreviewBitmap,
                    targetFlags = screen.targetFlags,
                    onApply = { bitmap, flags ->
                        val lockSelected = (flags and WallpaperManager.FLAG_LOCK) != 0
                        val homeSelected = (flags and WallpaperManager.FLAG_SYSTEM) != 0
                        val cropHints =
                            computeDisplayCropHints(context, bitmap.width, bitmap.height)
                        applyWallpaper(
                            context = context,
                            lockscreenBitmap = bitmap,
                            homescreenBitmap = bitmap,
                            lockscreenSelected = lockSelected,
                            homescreenSelected = homeSelected,
                            cropHints = cropHints,
                        )
                    },
                    onBack = { mainScreenViewModel.goBack() },
                    onApplySuccess = { (context as? Activity)?.restartApp() },
                )
            }
        }

        is Screen.Lockscreen -> {
            BackHandler { mainScreenViewModel.goBack() }
            DeferredScreen {
                LockscreenPreview(
                    isPreview = false,
                    wallpaperBitmap = getCurrentWallpaperBitmap(context, false),
                    modifier = Modifier.fillMaxSize(),
                    entryPoint = screen.entryPoint,
                    onEditWallpaper = {
                        val uri = getOriginalWallpaperUri(context, isHome = false)
                        mainScreenViewModel.onOpenWallpaperCrop(
                            sourceUri = uri,
                            targetFlags = WallpaperManager.FLAG_LOCK,
                        )
                    },
                )
            }
        }
    }
}

private val Screen.isHeavyScreen: Boolean
    get() =
        this is Screen.Lockscreen || this is Screen.WallpaperCrop || this is Screen.WallpaperPreview
