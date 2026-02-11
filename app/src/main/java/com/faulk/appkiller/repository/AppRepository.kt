package com.faulk.appkiller.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.faulk.appkiller.data.AppInfo
import com.faulk.appkiller.data.AppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val ourPackageName: String = context.packageName

    // A set of critical system packages that should not be killed to avoid instability.
    private val criticalSystemPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "android",
        packageManager.getInstallerPackageName("com.android.vending"), // Google Play Store
        "com.google.android.gms" // Google Play Services
    ).filterNotNull().toSet()

    suspend fun getApps(appType: AppType): List<AppInfo> = withContext(Dispatchers.IO) {
        // 1. Get usage stats for the last day to find recently used apps.
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours window
        val usageStatsMap = usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .associateBy { it.packageName }

        // 2. Get all installed applications.
        val allInstalledApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        // 3. Filter, map, and enrich the app list.
        val appInfoList = allInstalledApps.mapNotNull { app ->
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val currentAppType = if (isSystemApp) AppType.SYSTEM else AppType.USER

            // Skip if it's not the type we're currently fetching.
            if (currentAppType != appType) return@mapNotNull null
            // Skip our own app.
            if (app.packageName == ourPackageName) return@mapNotNull null

            val usageStats: UsageStats? = usageStatsMap[app.packageName]

            // We only want to list apps that have been used, to represent "running" or "cached" apps.
            if (usageStats?.lastTimeUsed == 0L) return@mapNotNull null

            AppInfo(
                appName = app.loadLabel(packageManager).toString(),
                packageName = app.packageName,
                icon = app.loadIcon(packageManager),
                lastUsedTimestamp = usageStats?.lastTimeUsed ?: 0L,
                type = currentAppType,
                // De-select critical apps by default.
                isSelected = !criticalSystemPackages.contains(app.packageName)
            )
        }

        // 4. Sort the list by last time used, descending (most recent first).
        return@withContext appInfoList.sortedByDescending { it.lastUsedTimestamp }
    }
}
