package com.faulk.appkiller.data

// A container for holding both lists of apps.
data class CategorizedApps(
    val userApps: List<AppInfo>,
    val systemApps: List<AppInfo>
)