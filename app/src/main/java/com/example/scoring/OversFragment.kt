package com.example.scoring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OversFragment : Fragment() {
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_overs, container, false)
        viewPager = v.findViewById(R.id.viewPagerTeams)
        tabLayout = v.findViewById(R.id.tabLayoutTeams)
        setupTeamTabs()
        return v
    }

    private fun setupTeamTabs() {
        val act = activity as? ScoringProvider
        val match = act?.match ?: return

        // 1st Innings team on left, 2nd Innings team on right
        val team1 = match.firstInnings?.battingTeam ?: match.teamA
        val team2 = if (team1 == match.teamA) match.teamB else match.teamA

        viewPager?.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return TeamOversListFragment.newInstance(position)
            }
            override fun getItemCount(): Int = 2
        }

        tabLayout?.let { tl ->
            viewPager?.let { vp ->
                TabLayoutMediator(tl, vp) { tab, position ->
                    tab.text = if (position == 0) team1 else team2
                }.attach()
            }
        }
        
        // Default to Index 1 (2nd Innings) ONLY if it is currently active and not complete
        val i2 = match.secondInnings
        if (i2 != null && !i2.isComplete) {
            viewPager?.setCurrentItem(1, false)
        } else {
            // Default to 1st innings for finished matches or matches still in 1st innings
            viewPager?.setCurrentItem(0, false)
        }
    }

    fun updateUI() {
        if (!isAdded) return
        
        // Ensure team tabs are set up if they weren't (e.g. after configuration change)
        setupTeamTabs()

        childFragmentManager.fragments.forEach {
            if (it is TeamOversListFragment) it.updateUI()
        }
    }
}
