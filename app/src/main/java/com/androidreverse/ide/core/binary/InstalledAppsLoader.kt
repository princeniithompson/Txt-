package com.androidreverse.ide.core.binary

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val sourceDir: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val splitCount: Int,
    val icon: ImageBitmap?
)

class InstalledAppsLoader(private val context: Context) {

    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appMap = LinkedHashMap<String, ApplicationInfo>()

        // 1. All launcher apps visible through <queries>
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        for (ri in resolveInfos) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            appMap[ai.packageName] = ai
        }

        // 2. Also merge visible installed applications
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        for (ai in installed) {
            appMap.putIfAbsent(ai.packageName, ai)
        }

        val result = ArrayList<InstalledAppInfo>(appMap.size)
        for (ai in appMap.values) {
            val sourcePath = ai.sourceDir ?: continue
            val apkFile = File(sourcePath)
            if (!apkFile.canRead()) continue

            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(ai.packageName)
            val version = runCatching {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(ai.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(ai.packageName, 0)
                }
                pInfo.versionName.orEmpty()
            }.getOrDefault("")
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val splitCount = ai.splitSourceDirs?.size ?: 0
            val iconBitmap = runCatching { drawableToImageBitmap(pm.getApplicationIcon(ai)) }.getOrNull()

            result.add(
                InstalledAppInfo(
                    packageName = ai.packageName,
                    appName = label,
                    versionName = version,
                    sourceDir = sourcePath,
                    apkSizeBytes = apkFile.length(),
                    isSystemApp = isSystem,
                    splitCount = splitCount,
                    icon = iconBitmap
                )
            )
        }

        result.sortedWith(compareBy<InstalledAppInfo> { it.isSystemApp }.thenBy { it.appName.lowercase() })
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
