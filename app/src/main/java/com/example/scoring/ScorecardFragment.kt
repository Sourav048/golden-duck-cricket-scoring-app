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

class ScorecardFragment : Fragment() {
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_scorecard, container, false)
        viewPager = v.findViewById(R.id.viewPagerInnings)
        tabLayout = v.findViewById(R.id.tabLayoutInnings)

        setupInningsTabs()
        return v
    }

    private fun setupInningsTabs() {
        val act = activity as? ScoringProvider
        val match = act?.match ?: return

        if (viewPager?.adapter == null) {
            viewPager?.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return InningsScorecardFragment.newInstance(position == 0)
                }
                override fun getItemCount(): Int = 2
            }
        }

        val i1 = match.firstInnings

        tabLayout?.let { tl ->
            viewPager?.let { vp ->
                TabLayoutMediator(tl, vp) { tab, position ->
                    if (position == 0) {
                        tab.text = i1?.battingTeam ?: "Team 1"
                    } else {
                        tab.text = i1?.bowlingTeam ?: "Team 2"
                    }
                }.attach()
            }
        }

        // Default to 2nd innings ONLY if it is currently in progress (active and not complete)
        if (match.secondInnings != null && !match.secondInnings!!.isComplete && viewPager?.currentItem == 0) {
            viewPager?.setCurrentItem(1, false)
        } else if (viewPager?.currentItem == 1 && (match.secondInnings == null || match.secondInnings!!.isComplete)) {
            // Fallback to 1st innings if match is finished or 2nd hasn't started
            viewPager?.setCurrentItem(0, false)
        }
    }

    fun notifyInningsChange() {
        if (viewPager != null) {
            setupInningsTabs()
        }
    }

    fun updateUI() {
        if (!isAdded) return
        
        // Ensure innings tabs are set up if they weren't (e.g. after configuration change)
        setupInningsTabs()

        for (f in childFragmentManager.fragments) {
            if (f is InningsScorecardFragment && f.isAdded) {
                f.updateUI()
            }
        }
    }
}
