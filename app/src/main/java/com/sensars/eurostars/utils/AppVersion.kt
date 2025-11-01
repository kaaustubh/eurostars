package com.sensars.eurostars.utils

import android.content.Context
import android.content.pm.PackageManager

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long
)

/**
 * Get the current app version information
 */
fun getAppVersionInfo(context: Context): AppVersionInfo {
    val packageManager = context.packageManager
    val packageName = context.packageName
    
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        AppVersionInfo(
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        )
    } catch (e: PackageManager.NameNotFoundException) {
        AppVersionInfo("Unknown", 0L)
    }
}

