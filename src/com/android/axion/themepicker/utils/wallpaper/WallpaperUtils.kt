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

package com.android.axion.themepicker.utils.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.LruCache
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.themepicker.data.model.EffectConfig
import com.android.axion.themepicker.data.model.WallpaperCategory
import com.android.axion.themepicker.data.model.WallpaperInfo
import com.android.axion.themepicker.data.model.ZoomProperties
import com.android.axion.themepicker.utils.effects.applyAtmosphereEffect
import com.android.axion.themepicker.utils.effects.applyGlassEffect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.*
import kotlin.math.*
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser

private val TAG = "WallpaperUtils"

private val BACKGROUNDS_PKG_NAME = "com.android.backgrounds"
private const val EFFECTS_PKG = "com.android.axion.wallpapereffects"

private val bitmapCache =
    LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())

private val MAIN_HANDLER by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }

class DrawablePainter(val drawable: Drawable) : Painter(), RememberObserver {
    private var drawInvalidateTick by mutableStateOf(0)
    private var drawableIntrinsicSize by mutableStateOf(drawable.intrinsicSize)

    private val callback: Drawable.Callback by lazy {
        object : Drawable.Callback {
            override fun invalidateDrawable(d: Drawable) {

                drawInvalidateTick++

                drawableIntrinsicSize = drawable.intrinsicSize
            }

            override fun scheduleDrawable(d: Drawable, what: Runnable, time: Long) {
                MAIN_HANDLER.postAtTime(what, time)
            }

            override fun unscheduleDrawable(d: Drawable, what: Runnable) {
                MAIN_HANDLER.removeCallbacks(what)
            }
        }
    }

    init {
        if (drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0) {

            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
    }

    override fun onRemembered() {
        drawable.callback = callback
        drawable.setVisible(true, true)
        if (drawable is Animatable) drawable.start()
    }

    override fun onAbandoned() = onForgotten()

    override fun onForgotten() {
        if (drawable is Animatable) drawable.stop()
        drawable.setVisible(false, false)
        drawable.callback = null
    }

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }

    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
        return drawable.setLayoutDirection(
            when (layoutDirection) {
                LayoutDirection.Ltr -> View.LAYOUT_DIRECTION_LTR
                LayoutDirection.Rtl -> View.LAYOUT_DIRECTION_RTL
            }
        )
    }

    override val intrinsicSize: Size
        get() = drawableIntrinsicSize

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawInvalidateTick

            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())

            canvas.withSave { drawable.draw(canvas.nativeCanvas) }
        }
    }
}

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter =
    remember(drawable) {
        when (drawable) {
            null -> EmptyPainter
            is BitmapDrawable -> BitmapPainter(drawable.bitmap.asImageBitmap())
            is ColorDrawable -> ColorPainter(Color(drawable.color))
            else -> DrawablePainter(drawable.mutate())
        }
    }

private val Drawable.intrinsicSize: Size
    get() =
        when {
            intrinsicWidth >= 0 && intrinsicHeight >= 0 -> {
                Size(width = intrinsicWidth.toFloat(), height = intrinsicHeight.toFloat())
            }
            else -> Size.Unspecified
        }

internal object EmptyPainter : Painter() {
    override val intrinsicSize: Size
        get() = Size.Unspecified

    override fun DrawScope.onDraw() {}
}

fun getWallpaperBitmapForEffects(context: Context): Bitmap? {
    val wm = WallpaperManager.getInstance(context)

    if (wm.wallpaperInfo?.packageName == EFFECTS_PKG) {
        readEffectsWallpaperBitmap(context)?.let { return it }
    }

    for (flag in listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)) {
        try {
            wm.getWallpaperFile(flag)?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)?.let { return it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read wallpaper file for flag=$flag", e)
        }
    }

    return try {
        val drawable =
            wm.getDrawable(WallpaperManager.FLAG_SYSTEM)
                ?: wm.getDrawable(WallpaperManager.FLAG_LOCK)
        drawable?.toBitmap()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load wallpaper drawable for effects", e)
        null
    }
}

fun getCurrentWallpaperBitmap(context: Context, isHome: Boolean = true): Bitmap? {
    val wm = WallpaperManager.getInstance(context)

    if (isHome && wm.wallpaperInfo != null) {
        readEffectsWallpaperBitmap(context)?.let {
            return it
        }
    }

    return try {
        resolveWallpaperDrawable(context, wm, isHome)?.toBitmap()
    } catch (e: Exception) {
        null
    }
}

fun getCurrentWallpaperDrawable(context: Context, isHome: Boolean = true): Drawable? {
    val wm = WallpaperManager.getInstance(context)

    if (isHome && wm.wallpaperInfo != null) {
        readEffectsWallpaperBitmap(context)?.let {
            return BitmapDrawable(context.resources, it)
        }
    }

    return try {
        resolveWallpaperDrawable(context, wm, isHome)
    } catch (e: Exception) {
        null
    }
}

private fun resolveWallpaperDrawable(context: Context, wm: WallpaperManager, isHome: Boolean): Drawable? {
    val flag = if (isHome) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
    return wm.getDrawable(flag)
        ?: (if (!isHome) getLockWallpaperBitmap(wm)?.let { BitmapDrawable(context.resources, it) } else null)
        ?: wm.drawable ?: wm.getBuiltInDrawable()
}

private fun getLockWallpaperBitmap(wm: WallpaperManager): Bitmap? =
    wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
    }

private fun readEffectsWallpaperBitmap(context: Context): Bitmap? {
    return try {
        val effectsCtx = context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val deCtx = effectsCtx.createDeviceProtectedStorageContext()
        val file = File(deCtx.filesDir, "wallpaper.jpg")
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read wallpaper from effects DE storage", e)
        null
    }
}

fun getForegroundBitmap(context: Context): Bitmap? {
    return try {
        val effectsCtx = context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val deCtx = effectsCtx.createDeviceProtectedStorageContext()
        val file = File(deCtx.filesDir, "effect_foreground.png")
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read foreground from effects DE storage", e)
        null
    }
}

fun Bitmap.toCompressedStream(): ByteArrayInputStream {
    val baos = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, baos)
    return ByteArrayInputStream(baos.toByteArray())
}

fun applyWallpaper(
    context: Context,
    lockscreenBitmap: Bitmap?,
    homescreenBitmap: Bitmap?,
    lockscreenSelected: Boolean,
    homescreenSelected: Boolean,
    cropHints: Map<Point, Rect>? = null,
) {

    val primaryCropHint =
        cropHints?.let { hints ->
            val primarySize = DisplayHelper.getWallpaperDisplaySize(context)
            hints[primarySize] ?: hints.values.firstOrNull()
        }

    WallpaperManager.getInstance(context).apply {
        try {
            homescreenBitmap
                ?.takeIf { homescreenSelected }
                ?.let { bitmap ->
                    val flags =
                        if (lockscreenSelected)
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                        else WallpaperManager.FLAG_SYSTEM

                    if (cropHints != null && cropHints.isNotEmpty()) {
                        try {
                            setStreamWithCrops(
                                bitmap.toCompressedStream(), cropHints, true, flags
                            )
                        } catch (e: NoSuchMethodError) {
                            Log.w(
                                TAG,
                                "setStreamWithCrops not available, falling back to setStream",
                            )
                            setStream(
                                bitmap.toCompressedStream(), primaryCropHint, true, flags
                            )
                        }
                    } else {
                        setStream(bitmap.toCompressedStream(), null, false, flags)
                    }
                }

            lockscreenBitmap
                ?.takeIf { lockscreenSelected && !homescreenSelected }
                ?.let { bitmap ->
                    if (cropHints != null && cropHints.isNotEmpty()) {
                        try {
                            setStreamWithCrops(
                                bitmap.toCompressedStream(), cropHints, true,
                                WallpaperManager.FLAG_LOCK
                            )
                        } catch (e: NoSuchMethodError) {
                            setStream(
                                bitmap.toCompressedStream(), primaryCropHint, true,
                                WallpaperManager.FLAG_LOCK
                            )
                        }
                    } else {
                        setStream(
                            bitmap.toCompressedStream(), null, false,
                            WallpaperManager.FLAG_LOCK
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper", e)
        }
    }
}

fun loadWallpapers(context: Context): List<WallpaperInfo> {
    val result = mutableListOf<WallpaperInfo>()

    try {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(BACKGROUNDS_PKG_NAME)
        val xmlId = res.getIdentifier("wallpapers", "xml", BACKGROUNDS_PKG_NAME)
        Log.d(TAG, "XML id = $xmlId")

        if (xmlId == 0) return emptyList()

        val parser = res.getXml(xmlId)
        val parsedItems = mutableListOf<Triple<String, Int, Int>>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "static-wallpaper") {
                val id = parser.getAttributeValue(null, "id") ?: ""
                val drawableRes = parser.getAttributeResourceValue(null, "src", 0)
                val titleRes = parser.getAttributeResourceValue(null, "title", 0)
                parsedItems.add(Triple(id, drawableRes, titleRes))
            }
            eventType = parser.next()
        }

        for ((id, drawableRes, titleRes) in parsedItems) {
            try {
                val title = if (titleRes != 0) res.getString(titleRes) else null
                if (drawableRes != 0) {
                    result.add(WallpaperInfo(id, title, drawableRes))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load wallpaper for id=$id", e)
            }
        }

        Log.d(TAG, "Parsed ${result.size} wallpapers")
        return result.asReversed().take(8)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "Backgrounds package not found", e)
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing wallpapers", e)
    }

    return emptyList()
}

fun getWallpaperDrawable(context: Context, resId: Int): Drawable? {
    if (resId == -1) return null
    val pm = context.packageManager
    val res = runCatching { pm.getResourcesForApplication(BACKGROUNDS_PKG_NAME) }.getOrNull()
    val drawable = runCatching { res?.getDrawable(resId, null) }.getOrNull()
    return drawable
}

fun decodeSampledBitmapFromUri(context: Context, uri: Uri, targetSize: Point? = null): Bitmap? {
    return try {
        val target = targetSize ?: DisplayHelper.getWallpaperDisplaySize(context)
        val reqWidth = target.x
        val reqHeight = target.y

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val (width, height) = options.outWidth to options.outHeight
        if (width <= 0 || height <= 0) return null

        options.inSampleSize = calculateSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val decoded =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

        val baos = ByteArrayOutputStream()
        decoded.compress(Bitmap.CompressFormat.PNG, 100, baos)
        decoded.recycle()

        val bytes = baos.toByteArray()
        baos.close()

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "Out of memory decoding bitmap", e)
        null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decode bitmap", e)
        null
    }
}

private fun calculateSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    val (srcWidth, srcHeight) = options.outWidth to options.outHeight
    var inSampleSize = 1

    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    val bytesPerPixel = 4
    val maxHeap = Runtime.getRuntime().maxMemory() / 4
    while ((srcWidth * srcHeight * bytesPerPixel / inSampleSize.toDouble().pow(2)) > maxHeap) {
        inSampleSize *= 2
    }

    return inSampleSize
}

class BitmapProcessor(private val context: Context) {
    private val cache = mutableMapOf<String, Bitmap>()

    fun processBitmap(source: Bitmap, config: EffectConfig, cacheKey: String? = null): Bitmap {
        val key = cacheKey ?: "${source.hashCode()}_${config.hashCode()}"

        cache[key]?.let {
            return it
        }

        var result = source.copy(Bitmap.Config.ARGB_8888, true)
        if (config.atmosphere) {
            result = applyAtmosphereEffect(context, result)
        }

        if (config.glass) {
            result = applyGlassEffect(context, result)
        }

        cache[key] = result
        return result
    }

    fun clearCache() {
        cache.clear()
    }
}

fun applyZoomToBitmap(
    source: Bitmap,
    zoom: ZoomProperties,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap {
    if (zoom.scale <= 1f) return source

    val scaledWidth = (source.width * zoom.scale).toInt()
    val scaledHeight = (source.height * zoom.scale).toInt()

    val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

    if (scaledWidth <= targetWidth || scaledHeight <= targetHeight) {
        val safeWidth = targetWidth.coerceAtMost(scaledBitmap.width)
        val safeHeight = targetHeight.coerceAtMost(scaledBitmap.height)
        return Bitmap.createBitmap(scaledBitmap, 0, 0, safeWidth, safeHeight)
    }

    val centerX = scaledWidth / 2
    val centerY = scaledHeight / 2

    val maxCropX = (scaledWidth - targetWidth).coerceAtLeast(0)
    val maxCropY = (scaledHeight - targetHeight).coerceAtLeast(0)

    val cropX = (centerX - targetWidth / 2 - zoom.offsetX).toInt().coerceIn(0, maxCropX)

    val cropY = (centerY - targetHeight / 2 - zoom.offsetY).toInt().coerceIn(0, maxCropY)

    return Bitmap.createBitmap(
        scaledBitmap,
        cropX,
        cropY,
        targetWidth.coerceAtMost(scaledBitmap.width),
        targetHeight.coerceAtMost(scaledBitmap.height),
    )
}

@Composable
fun rememberBitmap(drawableRes: Int, targetSizeWidth: Dp, targetSizeHeight: Dp): Bitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { targetSizeWidth.roundToPx() }
    val targetHeightPx = with(density) { targetSizeHeight.roundToPx() }

    return produceState<Bitmap?>(initialValue = null, drawableRes) {
            bitmapCache.get(drawableRes)?.let {
                value = it
                return@produceState
            }

            val bmp =
                withContext(Dispatchers.IO) {
                    getWallpaperDrawable(context, drawableRes)?.toBitmap()?.let { original ->
                        val targetAspectRatio = targetWidthPx.toFloat() / targetHeightPx
                        val bmpAspectRatio = original.width.toFloat() / original.height

                        val (cropWidth, cropHeight, cropLeft, cropTop) =
                            if (bmpAspectRatio > targetAspectRatio) {
                                val h = original.height
                                val w = (h * targetAspectRatio).toInt()
                                Quad(w, h, (original.width - w) / 2, 0)
                            } else {
                                val w = original.width
                                val h = (w / targetAspectRatio).toInt()
                                Quad(w, h, 0, (original.height - h) / 2)
                            }

                        val cropped =
                            Bitmap.createBitmap(
                                original,
                                cropLeft.coerceAtLeast(0),
                                cropTop.coerceAtLeast(0),
                                cropWidth.coerceAtLeast(1),
                                cropHeight.coerceAtLeast(1),
                            )

                        val scaled =
                            Bitmap.createScaledBitmap(cropped, targetWidthPx, targetHeightPx, true)

                        val outputStream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                    }
                }

            bmp?.let { bitmapCache.put(drawableRes, it) }
            value = bmp
        }
        .value
}

fun loadAllCategories(context: Context): List<WallpaperCategory> {
    val categories = mutableListOf<WallpaperCategory>()

    try {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(BACKGROUNDS_PKG_NAME)
        val xmlId = res.getIdentifier("wallpapers", "xml", BACKGROUNDS_PKG_NAME)

        if (xmlId == 0) return emptyList()

        val parser = res.getXml(xmlId)
        var currentCategory: String? = null
        var currentTitle: String? = null
        val currentWallpapers = mutableListOf<WallpaperInfo>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "category" -> {
                            if (currentCategory != null && currentWallpapers.isNotEmpty()) {
                                categories.add(
                                    WallpaperCategory(
                                        id = currentCategory,
                                        title = currentTitle ?: currentCategory,
                                        wallpapers = currentWallpapers.toList(),
                                    )
                                )
                                currentWallpapers.clear()
                            }

                            currentCategory = parser.getAttributeValue(null, "id")
                            val titleRes = parser.getAttributeResourceValue(null, "title", 0)
                            currentTitle =
                                if (titleRes != 0) {
                                    try {
                                        res.getString(titleRes)
                                    } catch (e: Exception) {
                                        currentCategory
                                    }
                                } else {
                                    currentCategory
                                }
                        }
                        "static-wallpaper" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val drawableRes = parser.getAttributeResourceValue(null, "src", 0)
                            val titleRes = parser.getAttributeResourceValue(null, "title", 0)

                            if (drawableRes != 0) {
                                val title =
                                    if (titleRes != 0) {
                                        try {
                                            res.getString(titleRes)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null

                                currentWallpapers.add(WallpaperInfo(id, title, drawableRes))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (currentCategory != null && currentWallpapers.isNotEmpty()) {
            categories.add(
                WallpaperCategory(
                    id = currentCategory,
                    title = currentTitle ?: currentCategory,
                    wallpapers = currentWallpapers.toList(),
                )
            )
        }

        Log.d(TAG, "Loaded ${categories.size} categories")
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "Backgrounds package not found", e)
    } catch (e: Exception) {
        Log.e(TAG, "Error loading categories", e)
    }

    return categories
}

fun centerCrop(context: Context, bmp: Bitmap?, targetSize: Point? = null): Bitmap? {
    if (bmp == null) return null

    val target = targetSize ?: DisplayHelper.getWallpaperDisplaySize(context)
    val targetWidth = target.x
    val targetHeight = target.y

    val srcWidth = bmp.width.toFloat()
    val srcHeight = bmp.height.toFloat()

    val scale = maxOf(targetWidth / srcWidth, targetHeight / srcHeight)

    val scaledWidth = scale * srcWidth
    val scaledHeight = scale * srcHeight

    val left = (scaledWidth - targetWidth) / 2f
    val top = (scaledHeight - targetHeight) / 2f

    val matrix = Matrix().apply { setScale(scale, scale) }

    val scaledBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

    val croppedBmp =
        Bitmap.createBitmap(
            scaledBmp,
            left.toInt().coerceAtLeast(0),
            top.toInt().coerceAtLeast(0),
            targetWidth.coerceAtMost(scaledBmp.width - left.toInt()),
            targetHeight.coerceAtMost(scaledBmp.height - top.toInt()),
        )

    if (scaledBmp != bmp) scaledBmp.recycle()

    return croppedBmp
}

fun getOriginalWallpaperUri(context: Context, isHome: Boolean = true): Uri? {
    val wm = WallpaperManager.getInstance(context)
    val flag = if (isHome) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK

    if (isHome && wm.wallpaperInfo != null) {
        try {
            val effectsCtx =
                context.createPackageContext(EFFECTS_PKG, Context.CONTEXT_IGNORE_SECURITY)
            val deCtx = effectsCtx.createDeviceProtectedStorageContext()
            val file = File(deCtx.filesDir, "wallpaper.jpg")
            if (file.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    copyToLocalTemp(context, file),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read effects wallpaper for edit", e)
        }
    }

    try {
        val pfd = wm.getWallpaperFile(flag)
        if (pfd != null) {
            pfd.use { fd ->
                val bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                if (bitmap != null) {
                    val fileName = if (isHome) "temp_wallpaper_original.jpg" else "temp_lock_wallpaper_original.jpg"
                    val tempFile = File(context.filesDir, fileName)
                    tempFile.outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    bitmap.recycle()
                    return FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile,
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to get original wallpaper file for flag=$flag", e)
    }

    return null
}

private fun copyToLocalTemp(context: Context, source: File): File {
    val dest = File(context.filesDir, "temp_wallpaper_original.jpg")
    source.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
    return dest
}

fun launchWallpaperPreview(context: Context, bitmap: Bitmap) {
    Log.d(TAG, "launchWallpaperPreview: bitmap=${bitmap.width}x${bitmap.height}")
    try {
        val file = File(context.filesDir, "temp_wallpaper.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Log.d(TAG, "launchWallpaperPreview: saved to ${file.absolutePath}, size=${file.length()}")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Log.d(TAG, "launchWallpaperPreview: uri=$uri")
        launchWallpaperPreviewFromUri(context, uri)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch wallpaper preview", e)
    }
}

fun launchWallpaperPreviewFromUri(context: Context, uri: Uri) {
    Log.d(TAG, "launchWallpaperPreviewFromUri: uri=$uri")
    try {
        val intent =
            Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch wallpaper set", e)
    }
}

fun launchWallpaperPreviewFromRes(context: Context, drawableRes: Int) {
    val drawable = getWallpaperDrawable(context, drawableRes) ?: return
    val bitmap = drawable.toBitmap()
    launchWallpaperPreview(context, bitmap)
}

private data class Quad(val width: Int, val height: Int, val left: Int, val top: Int)
