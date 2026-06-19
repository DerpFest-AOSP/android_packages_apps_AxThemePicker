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

package com.android.axion.themepicker.ui.gallery

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.themepicker.ui.components.DetailScaffold
import com.android.axion.themepicker.R
import com.android.axion.themepicker.data.model.GalleryState
import com.android.axion.themepicker.data.model.WallpaperCategory
import com.android.axion.themepicker.data.model.WallpaperInfo
import com.android.axion.themepicker.ui.components.ScreenTransition
import com.android.axion.themepicker.ui.theme.*
import com.android.axion.themepicker.utils.math.sdp
import com.android.axion.themepicker.utils.wallpaper.getOriginalWallpaperUri
import com.android.axion.themepicker.utils.wallpaper.loadAllCategories
import com.android.axion.themepicker.utils.wallpaper.rememberBitmap
import com.android.axion.themepicker.viewmodel.MainScreenViewModel
import com.android.axion.themepicker.viewmodel.WallpaperGalleryViewModel

@Composable
fun WallpaperGalleryScreen(
    galleryViewModel: WallpaperGalleryViewModel = viewModel(),
    mainScreenViewModel: MainScreenViewModel = viewModel(),
    onSelectPhoto: () -> Unit,
) {
    val context = LocalContext.current

    val onWallpaperSelected: (WallpaperInfo) -> Unit = { wallpaper ->
        mainScreenViewModel.onOpenWallpaperCrop(drawableRes = wallpaper.drawableRes)
    }
    val colors = MaterialTheme.colorScheme

    val galleryState by galleryViewModel.currentGalleryState.collectAsStateWithLifecycle()
    val isNavigatingBack by galleryViewModel.isNavigatingBack.collectAsStateWithLifecycle()

    var categories by remember { mutableStateOf<List<WallpaperCategory>>(emptyList()) }
    var latestWallpapers by remember { mutableStateOf<List<WallpaperInfo>>(emptyList()) }

    val currentState = galleryState
    val overviewTitle = stringResource(R.string.header_wallpaper_gallery)
    val collectionsTitle = stringResource(R.string.header_collections)

    val headerTitle by
        remember(currentState) {
            derivedStateOf {
                when (currentState) {
                    is GalleryState.Overview -> overviewTitle
                    is GalleryState.CategoryList -> collectionsTitle
                    is GalleryState.CategoryDetail -> currentState.category.title
                }
            }
        }

    LaunchedEffect(Unit) {
        val loadedCategories = loadAllCategories(context)
        categories = loadedCategories
        latestWallpapers = loadedCategories.flatMap { it.wallpapers }.asReversed().take(12)
    }

    BackHandler(enabled = true) { galleryViewModel.goBack { mainScreenViewModel.resetToMain() } }

    DetailScaffold(
        title = headerTitle,
        onBackClick = { galleryViewModel.goBack { mainScreenViewModel.resetToMain() } },
        containerColor = colors.surfaceContainer,
    ) { paddingValues ->
        val back = isNavigatingBack || galleryState is GalleryState.Overview

        ScreenTransition(
            targetState = galleryState,
            isNavigatingBack = back,
            modifier = Modifier.padding(paddingValues).padding(bottom = 24.sdp),
        ) { state ->
            when (state) {
                is GalleryState.Overview ->
                    OverviewContent(
                        latestWallpapers = latestWallpapers,
                        onEditCurrent = {
                            getOriginalWallpaperUri(context)?.let {
                                mainScreenViewModel.onOpenWallpaperCrop(sourceUri = it)
                            } ?: mainScreenViewModel.onOpenWallpaperCrop()
                        },
                        onSelectPhoto = onSelectPhoto,
                        onOpenLiveWallpapers = { launchLiveWallpaperPicker(context) },
                        onMoreClick = {
                            galleryViewModel.navigateTo(GalleryState.CategoryList(categories))
                        },
                        onWallpaperSelected = onWallpaperSelected,
                    )
                is GalleryState.CategoryList ->
                    CategoryListContent(
                        categories = state.categories,
                        onCategoryClick = { category ->
                            galleryViewModel.navigateTo(GalleryState.CategoryDetail(category))
                        },
                    )
                is GalleryState.CategoryDetail ->
                    CategoryDetailContent(
                        category = state.category,
                        onWallpaperSelected = onWallpaperSelected,
                    )
            }
        }
    }
}

private fun GalleryStateSaver() =
    Saver<GalleryState, Any>(
        save = { state ->
            when (state) {
                is GalleryState.Overview -> "overview"
                is GalleryState.CategoryList -> "categoryList"
                is GalleryState.CategoryDetail -> "categoryDetail:${state.category.id}"
            }
        },
        restore = { value ->
            when {
                value == "overview" -> GalleryState.Overview
                value == "categoryList" -> GalleryState.CategoryList(emptyList())
                value.toString().startsWith("categoryDetail:") -> {
                    val id = value.toString().substringAfter("categoryDetail:")
                    GalleryState.CategoryDetail(WallpaperCategory(id, id, emptyList()))
                }
                else -> GalleryState.Overview
            }
        },
    )

@Composable
private fun OverviewContent(
    latestWallpapers: List<WallpaperInfo>,
    onEditCurrent: () -> Unit,
    onSelectPhoto: () -> Unit,
    onOpenLiveWallpapers: () -> Unit,
    onMoreClick: () -> Unit,
    onWallpaperSelected: (WallpaperInfo) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(32.sdp),
        contentPadding = PaddingValues(bottom = 16.sdp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.sdp),
                verticalArrangement = Arrangement.spacedBy(16.sdp),
            ) {
                ExpressiveMainCard(
                    text = stringResource(R.string.edit_current_wallpaper),
                    description = stringResource(R.string.customize_active_wallpaper),
                    icon = Icons.Default.Edit,
                    onClick = onEditCurrent,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExpressiveActionCard(
                            text = stringResource(R.string.my_photos),
                            icon = Icons.Default.Photo,
                            onClick = onSelectPhoto,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ExpressiveActionCard(
                            text = stringResource(R.string.live_wallpapers),
                            icon = Icons.Default.ViewInAr,
                            onClick = onOpenLiveWallpapers,
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.sdp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.sdp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.sdp)) {
                        CategoryHeader(text = stringResource(R.string.latest_wallpapers))
                        CategorySubHeader(text = stringResource(R.string.recently_added_designs))
                    }

                    TextButton(
                        onClick = onMoreClick,
                        contentPadding = PaddingValues(horizontal = 12.sdp, vertical = 8.sdp),
                    ) {
                        Text(
                            text = stringResource(R.string.view_all),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(modifier = Modifier.width(4.sdp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.sdp),
                        )
                    }
                }

                GalleryGrid(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = 1000.sdp)
                            .padding(horizontal = 12.sdp),
                    items = latestWallpapers.take(12),
                    itemContent = { wallpaper ->
                        ExpressiveWallpaperThumbnail(
                            wallpaper = wallpaper as WallpaperInfo,
                            index = latestWallpapers.indexOf(wallpaper),
                            onClick = { onWallpaperSelected(wallpaper) },
                            modifier = Modifier.padding(8.sdp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpressiveMainCard(
    text: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.sdp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            label = "card_scale",
        )

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .height(140.sdp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .clickable(indication = null, interactionSource = interactionSource) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.sdp)) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.sdp),
                ) {
                    Text(
                        text = text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    CategorySubHeader(text = description)
                }

                Surface(
                    modifier = Modifier.size(56.sdp),
                    shape = CircleShape,
                    color = colors.surfaceContainer,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.size(28.sdp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveActionCard(text: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.sdp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "action_scale",
        )

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .clickable(indication = null, interactionSource = interactionSource) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.sdp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.sdp, Alignment.CenterVertically),
            ) {
                Surface(
                    modifier = Modifier.size(48.sdp),
                    shape = CircleShape,
                    color = colors.surfaceContainer,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.size(24.sdp),
                        )
                    }
                }
                Text(
                    text = text,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CategoryListContent(
    categories: List<WallpaperCategory>,
    onCategoryClick: (WallpaperCategory) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val size = categories.size

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.sdp),
        contentPadding = PaddingValues(vertical = 16.sdp, horizontal = 20.sdp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            CategorySubHeader(text = pluralStringResource(R.plurals.curated_categories, size, size))
        }

        items(categories) { category ->
            ExpressiveCategoryCard(category = category, onClick = { onCategoryClick(category) })
        }
    }
}

@Composable
private fun CategoryDetailContent(
    category: WallpaperCategory,
    onWallpaperSelected: (WallpaperInfo) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.sdp),
        contentPadding = PaddingValues(bottom = 16.sdp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.sdp)) {
                val size = category.wallpapers.size
                CategorySubHeader(
                    text = pluralStringResource(R.plurals.wallpaper_count, size, size)
                )
            }
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.sdp * 1.2f),
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
                verticalArrangement = Arrangement.spacedBy(8.sdp),
                contentPadding = PaddingValues(horizontal = 12.sdp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 5000.sdp),
            ) {
                itemsIndexed(category.wallpapers) { index, wallpaper ->
                    ExpressiveWallpaperThumbnail(
                        wallpaper = wallpaper,
                        index = index,
                        onClick = { onWallpaperSelected(wallpaper) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    items: List<Any>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: Any) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.sdp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier,
    ) {
        itemsIndexed(items) { index, item -> itemContent(item) }
    }
}

@Composable
private fun ExpressiveCategoryCard(category: WallpaperCategory, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.sdp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "category_scale",
        )

    val firstWallpaper = category.wallpapers.firstOrNull()
    val context = LocalContext.current
    val bitmap = firstWallpaper?.drawableRes?.let { rememberBitmap(it, 180.sdp, 180.sdp) }

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .height(180.sdp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .clickable(indication = null, interactionSource = interactionSource) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.5f),
                                    ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        )
            )

            Box(modifier = Modifier.fillMaxSize().padding(24.sdp)) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    CategoryHeader(text = category.title, color = Color.White)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val size = category.wallpapers.size
                        CategorySubHeader(
                            text = pluralStringResource(R.plurals.wallpaper_count, size, size),
                            color = Color.White,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.sdp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveWallpaperThumbnail(
    wallpaper: WallpaperInfo,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.sdp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "wallpaper_scale_$index",
        )

    val thumbnailSize = 120.sdp * 1.2f
    val bitmap = rememberBitmap(wallpaper.drawableRes, thumbnailSize, thumbnailSize)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .clickable(indication = null, interactionSource = interactionSource) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = wallpaper.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(colors.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(48.sdp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = (-0.5).sp,
    )
}

@Composable
private fun CategorySubHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
}

private fun launchLiveWallpaperPicker(context: Context) {
    try {
        val intent =
            Intent().apply {
                setClassName("com.android.wallpaper.livepicker", "com.android.wallpaper.livepicker.LiveWallpaperActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("WallpaperGallery", "Failed to launch live wallpaper picker", e)
    }
}
