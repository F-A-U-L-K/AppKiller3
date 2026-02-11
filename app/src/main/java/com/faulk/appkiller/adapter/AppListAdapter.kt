package com.faulk.appkiller.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.faulk.appkiller.data.AppInfo
import com.faulk.appkiller.databinding.ItemAppBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppListAdapter(
    private val onAppChecked: (AppInfo, Boolean) -> Unit,
    private val onManageClicked: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding, onAppChecked, onManageClicked)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        private val onAppChecked: (AppInfo, Boolean) -> Unit,
        private val onManageClicked: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.appName.text = appInfo.appName
            binding.appIcon.setImageDrawable(appInfo.icon)
            binding.lastUsed.text = formatLastUsed(appInfo.lastUsedTimestamp)
            
            // The isSelected state from the data model drives the checkbox.
            binding.appCheckbox.setOnCheckedChangeListener(null)
            binding.appCheckbox.isChecked = appInfo.isSelected
            binding.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onAppChecked(appInfo, isChecked)
            }

            binding.btnManage.setOnClickListener {
                onManageClicked(appInfo)
            }
        }

        private fun formatLastUsed(timestamp: Long): String {
            if (timestamp == 0L) return "Last used: Unknown"
            val diff = System.currentTimeMillis() - timestamp
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            if (minutes < 1) return "Last used: Just now"
            if (minutes < 60) return "Last used: $minutes min ago"
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            if (hours < 24) return "Last used: $hours h ago"
            return "Last used: ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))}"
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}