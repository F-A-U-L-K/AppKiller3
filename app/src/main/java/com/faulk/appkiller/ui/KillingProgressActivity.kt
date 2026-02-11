package com.faulk.appkiller.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

        val killList = intent.getStringArrayListExtra(AppKillerAccessibilityService.EXTRA_PACKAGES)
        if (killList == null || killList.isEmpty()) {
            finish()
            return
        }

        // Send the list to the service to start the process
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
        LocalBroadcastManager.getInstance(this).registerReceiver(progressReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
    }

    // Prevent user from accidentally exiting. They must use the cancel button.
    override fun onBackPressed() {
        // Do nothing
    }
}