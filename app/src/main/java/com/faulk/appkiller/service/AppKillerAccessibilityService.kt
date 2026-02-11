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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.faulk.appkiller.ui.MainActivity

class AppKillerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AppKillerService"
        const val ACTION_START_KILL = "ACTION_START_KILL"
        const val ACTION_ABORT_KILL = "ACTION_ABORT_KILL"
        const val EXTRA_PACKAGES = "EXTRA_PACKAGES"

        // Actions for broadcasting progress to the UI
        const val ACTION_PROGRESS_UPDATE = "ACTION_PROGRESS_UPDATE"
        const val ACTION_KILL_PROCESS_FINISHED = "ACTION_KILL_PROCESS_FINISHED"
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
                Log.d(TAG, "Abort command received.")
                finishKillingProcess(isAborted = true)
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isKilling || rootInActiveWindow == null) return

        // We are interested in window changes, which occur when App Info or dialogs appear.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            Log.d(TAG, "Event from package: ${event.packageName}, class: ${event.className}")

            // Step 2: Look for "OK" confirmation after clicking "Force Stop"
            // The confirmation dialog is usually a simple alert.
            val okButton = findNode(rootNode, "OK", "android:id/button1")
            if (okButton != null && okButton.isEnabled) {
                handler.removeCallbacks(timeoutRunnable) // Progress made
                Log.d(TAG, "Clicking 'OK' confirmation button.")
                okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // Give a short delay for the settings window to close before moving to the next app.
                handler.postDelayed({ proceedToNextApp() }, 500)
                rootNode.recycle()
                return
            }

            // Step 1: Look for the "Force Stop" button on the App Info screen.
            val forceStopButton = findNode(rootNode, "Force stop", "com.android.settings:id/force_stop_button")
            if (forceStopButton != null && forceStopButton.isEnabled) {
                handler.removeCallbacks(timeoutRunnable) // Progress made
                Log.d(TAG, "Clicking 'Force Stop' button.")
                forceStopButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // Set a new, shorter timeout for the "OK" dialog to appear.
                handler.postDelayed(timeoutRunnable, 2000)
                rootNode.recycle()
                return
            }
            rootNode.recycle()
        }
    }

    private fun startKillingProcess(packages: ArrayList<String>) {
        if (isKilling) return
        isKilling = true
        killQueue.clear()
        killQueue.addAll(packages)
        totalAppsToKill = killQueue.size
        currentAppRetries = 0
        buildAppNameMap()
        Log.d(TAG, "Starting kill process for ${killQueue.size} apps.")
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

        Log.d(TAG, "Opening settings for: $packageName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        // Set a timeout. If nothing happens in 5 seconds, we're likely stuck.
        handler.postDelayed(timeoutRunnable, 5000)
    }

    private fun proceedToNextApp() {
        handler.removeCallbacks(timeoutRunnable)
        currentAppRetries = 0
        if (killQueue.isNotEmpty()) {
            killQueue.removeAt(0)
        }
        openNextAppSettings()
    }

    private fun handleTimeout() {
        val stuckPackage = killQueue.firstOrNull() ?: "Unknown"
        Log.w(TAG, "Timeout while processing $stuckPackage. Retries: $currentAppRetries")
        if (currentAppRetries < 1) { // Retry once
            currentAppRetries++
            openNextAppSettings()
        } else {
            Log.e(TAG, "Max retries reached for $stuckPackage. Skipping.")
            proceedToNextApp()
        }
    }

    private fun finishKillingProcess(isAborted: Boolean) {
        if (!isKilling && !isAborted) return
        Log.d(TAG, "Finishing kill process. Aborted: $isAborted")
        isKilling = false
        killQueue.clear()
        packageNamesToAppNames.clear()
        handler.removeCallbacksAndMessages(null) // Clear all pending operations

        broadcastFinish()

        // Bring the main activity back to the front.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    // Helper to find a clickable node by text or resource ID, making it more robust.
    private fun findNode(root: AccessibilityNodeInfo, text: String, resourceId: String): AccessibilityNodeInfo? {
        // First try by text (case-insensitive)
        val byText = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isClickable }
        if (byText != null) return byText

        // Fallback to resource ID
        val byId = root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull { it.isClickable }
        if (byId != null) return byId

        return null
    }

    // Caches app names to avoid querying package manager repeatedly.
    private fun buildAppNameMap() {
        packageNamesToAppNames.clear()
        killQueue.forEach { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageNamesToAppNames[pkg] = packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageNamesToAppNames[pkg] = pkg // Fallback to package name
            }
        }
    }

    // --- Broadcasting to UI ---

    private fun broadcastProgress() {
        val currentPackage = killQueue.firstOrNull() ?: return
        val appName = packageNamesToAppNames[currentPackage] ?: currentPackage
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra("current_app", appName)
            putExtra("current_count", (totalAppsToKill - killQueue.size) + 1)
            putExtra("total_count", totalAppsToKill)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastFinish() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_KILL_PROCESS_FINISHED))
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
        finishKillingProcess(isAborted = true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "Accessibility service unbound.")
        finishKillingProcess(isAborted = true)
        return super.onUnbind(intent)
    }
}