package com.faulk.appkiller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.faulk.appkiller.adapter.AppAdapter
import com.faulk.appkiller.databinding.FragmentAppListBinding
import com.faulk.appkiller.viewmodel.AppKillerViewModel

class AppListFragment : Fragment() {

    // ViewBinding in Fragments requires a null check to avoid memory leaks
    private var _binding: FragmentAppListBinding? = null
    private val binding get() = _binding!!

    // FIX: Using activityViewModels() ensures this Fragment shares the same 
    // data instance as MainActivity
    private val viewModel: AppKillerViewModel by activityViewModels()
    
    private lateinit var appAdapter: AppAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        // FIX: Ensure you are passing the click listener correctly to the adapter
        appAdapter = AppAdapter { app ->
            viewModel.toggleAppSelection(app)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appAdapter
        }
    }

    private fun setupObservers() {
        val position = arguments?.getInt(ARG_POSITION) ?: 0

        // FIX: Added explicit types to clear the "Cannot infer type" errors
        viewModel.categorizedApps.observe(viewLifecycleOwner) { categorized ->
            val appsList = if (position == 0) categorized.userApps else categorized.systemApps
            appAdapter.submitList(appsList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Critical for cleaning up memory
    }

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int): AppListFragment {
            val fragment = AppListFragment()
            val args = Bundle()
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }
}
