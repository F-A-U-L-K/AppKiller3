package com.faulk.appkiller.ui

import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.faulk.appkiller.adapter.AppListAdapter
import com.faulk.appkiller.data.AppType
import com.faulk.appkiller.databinding.FragmentAppListBinding
import com.faulk.appkiller.viewmodel.AppKillerViewModel

class AppListFragment : Fragment() {

    private var _binding: FragmentAppListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppKillerViewModel by activityViewModels()
    private lateinit var appAdapter: AppListAdapter
    private var appType: AppType = AppType.USER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appType = AppType.valueOf(it.getString(ARG_APP_TYPE) ?: "USER")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        appAdapter = AppListAdapter(
            onAppChecked = { appInfo, isChecked ->
                viewModel.updateAppSelection(appInfo, isChecked)
            },
            onManageClicked = { appInfo ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", appInfo.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewFragment.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.categorizedApps.observe(viewLifecycleOwner) { categorized ->
            val listToShow = if (appType == AppType.USER) categorized.userApps else categorized.systemApps
            appAdapter.submitList(listToShow)
            val allSelected = listToShow.isNotEmpty() && listToShow.all { it.isSelected }
            binding.checkboxSelectAllFragment.isChecked = allSelected
            binding.emptyViewList.visibility = if(listToShow.isEmpty()) View.VISIBLE else View.GONE
        }
        
        val loadingLiveData = if (appType == AppType.USER) viewModel.isLoadingUserApps else viewModel.isLoadingSystemApps
        loadingLiveData.observe(viewLifecycleOwner) { isLoading ->
             binding.progressBarList.visibility = if (isLoading) View.VISIBLE else View.GONE
             if(isLoading) binding.emptyViewList.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.checkboxSelectAllFragment.setOnClickListener {
            viewModel.toggleSelectAll(appType, binding.checkboxSelectAllFragment.isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_APP_TYPE = "app_type"
        fun newInstance(appType: AppType) = AppListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_APP_TYPE, appType.name)
            }
        }
    }
}