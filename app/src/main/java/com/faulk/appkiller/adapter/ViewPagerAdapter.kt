package com.faulk.appkiller.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.faulk.appkiller.data.AppType
import com.faulk.appkiller.ui.AppListFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AppListFragment.newInstance(AppType.USER)
            else -> AppListFragment.newInstance(AppType.SYSTEM)
        }
    }
}