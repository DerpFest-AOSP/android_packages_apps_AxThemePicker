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

package com.android.axion.themepicker.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class Screen : Parcelable {
    @Parcelize object Main : Screen()

    @Parcelize object ColorsSettings : Screen()

    @Parcelize object WallpaperGallery : Screen()

    @Parcelize object WallpaperEffects : Screen()

    @Parcelize object AppGrid : Screen()

    @Parcelize object IconShapes : Screen()

    @Parcelize object ThemedIcons : Screen()

    @Parcelize
    data class WallpaperCrop(
        val sourceUri: Uri? = null,
        val drawableRes: Int = 0,
        val targetFlags: Int = 0,
    ) : Screen()

    @Parcelize data class WallpaperPreview(val targetFlags: Int = 0) : Screen()

    @Parcelize
    data class Lockscreen(
        val wallpaper: WallpaperInfo? = null,
        val entryPoint: EntryPoint = EntryPoint.DEFAULT,
    ) : Screen()

    @Parcelize
    enum class EntryPoint : Parcelable {
        DEFAULT,
        WIDGETS,
        SHORTCUTS,
        CLOCK,
    }
}
