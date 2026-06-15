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

package com.android.axion.themepicker.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.axion.themepicker.data.model.Screen
import com.android.axion.themepicker.data.model.Screen.EntryPoint
import com.android.axion.themepicker.data.model.WallpaperInfo
import com.android.axion.themepicker.utils.wallpaper.loadWallpapers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainScreenViewModel : ViewModel() {

    var launchedDirectToEffects: Boolean = false
        private set

    private val _wallpapers = MutableStateFlow<List<WallpaperInfo>>(emptyList())
    val wallpapers: StateFlow<List<WallpaperInfo>> = _wallpapers

    private val _selectedTab = MutableStateFlow(1)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen

    private val _screenStack = mutableListOf<Screen>()

    private val _isNavigatingBack = MutableStateFlow(false)
    val isNavigatingBack: StateFlow<Boolean> = _isNavigatingBack

    fun initialize(context: Context) {
        if (_wallpapers.value.isNotEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = withContext(Dispatchers.IO) { loadWallpapers(appContext) }
            _wallpapers.value = loaded
            _isLoading.value = false
        }
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun navigateTo(screen: Screen) {
        _isNavigatingBack.value = false
        _screenStack.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    fun goBack() {
        _isNavigatingBack.value = true

        if (_screenStack.isNotEmpty()) {
            _currentScreen.value = _screenStack.removeAt(_screenStack.lastIndex)
        } else {
            _isNavigatingBack.value = false
            _currentScreen.value = Screen.Main
        }
    }

    fun resetToMain() {
        _isNavigatingBack.value = false
        _currentScreen.value = Screen.Main
        _screenStack.clear()
        Log.d("MainScreenViewModel", "reset to main!")
    }

    fun onOpenColorsSettings() {
        navigateTo(Screen.ColorsSettings)
    }

    fun onOpenAppGrid() {
        navigateTo(Screen.AppGrid)
    }

    fun onOpenIconShapes() {
        navigateTo(Screen.IconShapes)
    }

    fun onOpenThemedIcons() {
        navigateTo(Screen.ThemedIcons)
    }

    fun onOpenGallery() {
        navigateTo(Screen.WallpaperGallery)
    }

    fun onOpenWallpaperEffects() {
        navigateTo(Screen.WallpaperEffects)
    }

    fun openWallpaperEffectsDirect() {
        launchedDirectToEffects = true
        _isNavigatingBack.value = false
        _screenStack.clear()
        _currentScreen.value = Screen.WallpaperEffects
    }

    var pendingPreviewBitmap: Bitmap? = null
        private set

    fun onOpenWallpaperCrop(sourceUri: Uri? = null, drawableRes: Int = 0, targetFlags: Int = 0) {
        navigateTo(
            Screen.WallpaperCrop(
                sourceUri = sourceUri,
                drawableRes = drawableRes,
                targetFlags = targetFlags,
            )
        )
    }

    fun onCropCompleted(croppedBitmap: Bitmap, targetFlags: Int = 0) {
        pendingPreviewBitmap = croppedBitmap
        navigateTo(Screen.WallpaperPreview(targetFlags = targetFlags))
    }

    fun onOpenLockscreenPreview(
        wallpaper: WallpaperInfo? = null,
        entryPoint: EntryPoint = EntryPoint.DEFAULT,
    ) {
        navigateTo(Screen.Lockscreen(wallpaper, entryPoint))
        Log.d("MainScreenViewModel", "entryPoint=$entryPoint")
    }
}
