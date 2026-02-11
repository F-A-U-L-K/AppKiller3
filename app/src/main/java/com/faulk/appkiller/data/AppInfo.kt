package com.faulk.appkiller.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val lastUsedTimestamp: Long,
    val type: AppType,
    // Critical system apps are unselected by default to prevent system instability.
    var isSelected: Boolean = true
)