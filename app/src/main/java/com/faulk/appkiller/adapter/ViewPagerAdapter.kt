package com.faulk.appkiller.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.faulk.appkiller.ui.AppListFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        // FIX: Pass the 'position' integer directly. 
        // AppListFragment will handle whether that means USER (0) or SYSTEM (1).
        return AppListFragment.newInstance(position)
    }
}
