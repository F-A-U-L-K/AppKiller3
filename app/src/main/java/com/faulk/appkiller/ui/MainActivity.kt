package com.faulk.appkiller.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.faulk.appkiller.adapter.ViewPagerAdapter
import com.faulk.appkiller.databinding.ActivityMainBinding
import com.faulk.appkiller.service.AppKillerAccessibilityService
import com.faulk.appkiller.viewmodel.AppKillerViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AppKillerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupObservers()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndLoadApps()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "User Apps" else "System Apps"
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Lazy-load system apps when the user swipes to the tab.
                if (position == 1) {
                    viewModel.loadSystemApps()
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.categorizedApps.observe(this) { categorized ->
            val userCount = categorized.userApps.count { it.isSelected }
            val systemCount = categorized.systemApps.count { it.isSelected }
            val total = userCount + systemCount

            binding.textAppCount.text = "$userCount User & $systemCount System Apps Selected"
            binding.btnKillSelected.isEnabled = total > 0

            val hasAnyApps = categorized.userApps.isNotEmpty() || categorized.systemApps.isNotEmpty()
            binding.emptyView.visibility = if (hasAnyApps || viewModel.isLoadingUserApps.value == true) View.GONE else View.VISIBLE
        }

        viewModel.isLoadingUserApps.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        // Note: We don't show a main progress bar for system apps as they load in their own tab.
    }

    private fun setupClickListeners() {
        binding.btnKillSelected.setOnClickListener {
            val categorized = viewModel.categorizedApps.value ?: return@setOnClickListener
            val selectedPackages = (categorized.userApps.filter { it.isSelected } +
                    categorized.systemApps.filter { it.isSelected })
                .map { it.packageName }

            if (selectedPackages.isEmpty()) {
                Snackbar.make(binding.root, "No apps selected.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start the Killing Progress Activity and pass the list to the service
            val intent = Intent(this, KillingProgressActivity::class.java).apply {
                putStringArrayListExtra(AppKillerAccessibilityService.EXTRA_PACKAGES, ArrayList(selectedPackages))
            }
            startActivity(intent)
        }
        
        binding.btnRefresh.setOnClickListener {
            viewModel.loadUserApps()
            // The system apps list will be refreshed if the user navigates to it again.
        }
    }

    private fun checkPermissionsAndLoadApps() {
        when {
            !hasUsageStatsPermission() -> showPermissionDialog(
                "Usage Access Required",
                "App Killer needs 'Usage Access' to find recently used apps. Please grant the permission in the next screen.",
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
            !isAccessibilityServiceEnabled() -> showPermissionDialog(
                "Accessibility Service Required",
                "App Killer needs this core permission to automate the hibernation process. Your data is not collected.",
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
            else -> {
                // Permissions are granted, apps will be loaded by the ViewModel's init block.
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${AppKillerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }

    private fun showPermissionDialog(title: String, message: String, action: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ -> startActivity(Intent(action)) }
            .setCancelable(false)
            .show()
    }
}