package com.faulk.appkiller.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.faulk.appkiller.databinding.ActivityKillingProgressBinding
import com.faulk.appkiller.service.AppKillerAccessibilityService

class KillingProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKillingProgressBinding

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AppKillerAccessibilityService.ACTION_PROGRESS_UPDATE -> {
                    val currentApp = intent.getStringExtra("current_app") ?: "Finishing..."
                    val current = intent.getIntExtra("current_count", 0)
                    val total = intent.getIntExtra("total_count", 0)
                    
                    // Updates the UI with the current progress
                    binding.textStatus.text = "Hibernating: $currentApp"
                    binding.textProgress.text = "$current / $total"
                }
                AppKillerAccessibilityService.ACTION_KILL_PROCESS_FINISHED -> {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKillingProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIX: The modern way to disable the back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing: User must use the Cancel button
            }
        })

        val killList = intent.getStringArrayListExtra(AppKillerAccessibilityService.EXTRA_PACKAGES)
        if (killList == null || killList.isEmpty()) {
            finish()
            return
        }

        val serviceIntent = Intent(this, AppKillerAccessibilityService::class.java).apply {
            action = AppKillerAccessibilityService.ACTION_START_KILL
            putStringArrayListExtra(AppKillerAccessibilityService.EXTRA_PACKAGES, killList)
        }
        startService(serviceIntent)
        
        binding.btnCancel.setOnClickListener {
            val stopIntent = Intent(this, AppKillerAccessibilityService::class.java).apply {
                action = AppKillerAccessibilityService.ACTION_ABORT_KILL
            }
            startService(stopIntent)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(AppKillerAccessibilityService.ACTION_PROGRESS_UPDATE)
            addAction(AppKillerAccessibilityService.ACTION_KILL_PROCESS_FINISHED)
        }
        // FIX: Changed from LocalBroadcastManager to standard registerReceiver
        // to avoid dependency issues on GitHub Runners
        registerReceiver(progressReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(progressReceiver)
    }
}
