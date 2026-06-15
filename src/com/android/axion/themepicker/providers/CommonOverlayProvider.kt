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

package com.android.axion.themepicker.providers

import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.axion.themepicker.data.model.OverlayOption
import com.android.customization.model.ResourceConstants
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.themepicker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class CommonOverlayProvider(
    private val context: Context,
    private val overlayManager: OverlayManagerCompat,
    private val category: String,
) {
    private val packageManager: PackageManager = context.packageManager
    private val overlayPackages: List<String>
    private var activeOverlay: String?

    init {
        val packagesToOverlay = ResourceConstants.getPackagesToOverlay(context)
        overlayPackages =
            overlayManager.getOverlayPackagesForCategory(
                category,
                UserHandle.myUserId(),
                *packagesToOverlay,
            )
        activeOverlay =
            overlayManager.getEnabledPackageName(ResourceConstants.ANDROID_PACKAGE, category)
    }

    suspend fun loadOptions(): List<OverlayOption> =
        withContext(Dispatchers.IO) {
            val options = mutableListOf<OverlayOption>()

            options.add(createDefaultOption())

            val customOptions =
                overlayPackages.mapNotNull { overlayPackage ->
                    try {
                        val label =
                            packageManager
                                .getApplicationInfo(overlayPackage, 0)
                                .loadLabel(packageManager)
                                .toString()

                        OverlayOption(
                            packageName = overlayPackage,
                            label = label,
                            isActive = overlayPackage == activeOverlay,
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't load overlay $overlayPackage, will skip it", e)
                        null
                    }
                }

            options.addAll(customOptions.sortedBy { it.label })
            options
        }

    fun applyOverlay(option: OverlayOption): Boolean {
        return try {
            if (option.packageName == null) {
                disableAllOverlays()
            } else {
                overlayManager.setEnabledExclusiveInCategory(
                    option.packageName,
                    UserHandle.myUserId(),
                )
            }

            activeOverlay = option.packageName

            val persisted = persistOverlay(option)
            if (!persisted) {
                Log.w(TAG, "Overlay applied but not persisted: ${option.packageName}")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying overlay: ${option.packageName}", e)
            false
        }
    }

    private fun disableAllOverlays() {
        overlayPackages.forEach { overlay ->
            try {
                overlayManager.disableOverlay(overlay, UserHandle.myUserId())
            } catch (e: Exception) {
                Log.w(TAG, "Error disabling overlay: $overlay", e)
            }
        }
    }

    fun getActiveOverlay(): String? = activeOverlay

    private fun createDefaultOption(): OverlayOption {
        return OverlayOption(
            packageName = null,
            label = context.getString(R.string.default_theme_title),
            isActive = activeOverlay == null,
        )
    }

    private fun persistOverlay(option: OverlayOption): Boolean {
        val resolver = context.contentResolver
        val userId = UserHandle.myUserId()

        val value =
            Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                userId,
            )

        val json =
            try {
                if (value.isNullOrEmpty()) JSONObject() else JSONObject(value)
            } catch (e: JSONException) {
                Log.e(TAG, "Error parsing current settings value:\n${e.message}")
                return false
            }

        try {
            json.remove(category)
            option.packageName?.let { pkg -> json.put(category, pkg) }
            Settings.Secure.putStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                json.toString(),
                userId,
            )

            return true
        } catch (e: JSONException) {
            Log.e(TAG, "Error adding new settings value:\n${e.message}")
            return false
        }
    }

    companion object {
        private const val TAG = "CommonOverlayProvider"
    }
}
