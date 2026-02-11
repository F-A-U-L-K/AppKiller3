package com.faulk.appkiller.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.faulk.appkiller.ui.MainActivity

class AppKillerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AppKillerService"
        const val ACTION_START_KILL = "ACTION_START_KILL"
        const val ACTION_ABORT_KILL = "ACTION_ABORT_KILL"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"

        const val ACTION_PROGRESS_UPDATE = "com.faulk.appkiller.PROGRESS_UPDATE"
        const val ACTION_KILL_PROCESS_FINISHED = "com.faulk.appkiller.KILL_FINISHED"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isKilling = false

    private val killQueue = mutableListOf<String>()
    private val packageNamesToAppNames = mutableMapOf<String, String>()
    private var totalAppsToKill = 0
    private var currentAppRetries = 0

    private val timeoutRunnable = Runnable { handleTimeout() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_KILL -> {
                val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES)
                if (packages != null && !isKilling) {
                    startKillingProcess(packages)
                }
            }
            ACTION_ABORT_KILL -> {
                finishKillingProcess(isAborted = true)
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isKilling) return
        
        // FIX: Added safer null check for rootInActiveWindow
        val rootNode = rootInActiveWindow ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Step 2: Look for "OK" or "Force stop" confirmation dialog
            // Note: Different Android versions use different IDs for the OK button
            val okButton = findNode(rootNode, "OK", "android:id/button1")
            if (okButton != null && okButton.isEnabled) {
                handler.removeCallbacks(timeoutRunnable)
                okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed({ proceedToNextApp() }, 500)
                return
            }

            // Step 1: Look for "Force stop" button on App Info page
            val forceStopButton = findNode(rootNode, "Force stop", "com.android.settings:id/force_stop_button")
            if (forceStopButton != null && forceStopButton.isEnabled) {
                handler.removeCallbacks(timeoutRunnable)
                forceStopButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(timeoutRunnable, 2000)
                return
            }
        }
    }

    private fun startKillingProcess(packages: ArrayList<String>) {
        isKilling = true
        killQueue.clear()
        killQueue.addAll(packages)
        totalAppsToKill = killQueue.size
        currentAppRetries = 0
        buildAppNameMap()
        openNextAppSettings()
    }

    private fun openNextAppSettings() {
        handler.removeCallbacks(timeoutRunnable)
        if (killQueue.isEmpty()) {
            finishKillingProcess(isAborted = false)
            return
        }

        val packageName = killQueue.first()
        broadcastProgress()

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        handler.postDelayed(timeoutRunnable, 5000)
    }

    private fun proceedToNextApp() {
        handler.removeCallbacks(timeoutRunnable)
        if (killQueue.isNotEmpty()) {
            killQueue.removeAt(0)
        }
        openNextAppSettings()
    }

    private fun handleTimeout() {
        if (currentAppRetries < 1) {
            currentAppRetries++
            openNextAppSettings()
        } else {
            proceedToNextApp()
        }
    }

    private fun finishKillingProcess(isAborted: Boolean) {
        if (!isKilling && !isAborted) return
        isKilling = false
        killQueue.clear()
        handler.removeCallbacksAndMessages(null)
        broadcastFinish()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String, resourceId: String): AccessibilityNodeInfo? {
        // Search by ID first (more reliable)
        val byId = root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull { it.isEnabled }
        if (byId != null) return byId

        // Search by text
        val byText = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isEnabled }
        return byText
    }

    private fun buildAppNameMap() {
        packageNamesToAppNames.clear()
        killQueue.forEach { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageNamesToAppNames[pkg] = packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageNamesToAppNames[pkg] = pkg
            }
        }
    }

    private fun broadcastProgress() {
        val currentPackage = killQueue.firstOrNull() ?: return
        val appName = packageNamesToAppNames[currentPackage] ?: currentPackage
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra("current_app", appName)
            putExtra("current_count", (totalAppsToKill - killQueue.size) + 1)
            putExtra("total_count", totalAppsToKill)
            // FIX: Important for Android 14+
            setPackage(packageName) 
        }
        sendBroadcast(intent)
    }

    private fun broadcastFinish() {
        val intent = Intent(ACTION_KILL_PROCESS_FINISHED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        finishKillingProcess(isAborted = true)
    }
}
