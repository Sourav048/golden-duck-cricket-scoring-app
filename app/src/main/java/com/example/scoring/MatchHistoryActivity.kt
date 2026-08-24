package com.example.scoring

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MatchHistoryActivity : BaseActivity() {
    private var viewPager: ViewPager2? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_match_history)

            findViewById<View>(R.id.btnBackHistory).setOnClickListener { finish() }

            val tabLayout = findViewById<TabLayout>(R.id.tabLayoutMatches)
            viewPager = findViewById(R.id.viewPagerMatches)

            viewPager?.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return MatchListFragment.newInstance(position)
                }
                override fun getItemCount(): Int = 4
            }

            val titles = arrayOf("Live", "Completed", "In Progress", "Abandoned")
            val pager = viewPager ?: return
            TabLayoutMediator(tabLayout, pager) { tab, position ->
                tab.text = titles[position]
            }.attach()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Match History initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for preferred tab (e.g. redirected from a finished match)
        val prefs = getSharedPreferences("match_history_prefs", MODE_PRIVATE)
        val preferredTab = prefs.getInt("preferred_tab", -1)
        if (preferredTab != -1) {
            viewPager?.post {
                viewPager?.setCurrentItem(preferredTab, false)
            }
            // Clear the preference so it doesn't happen every time we resume
            prefs.edit().remove("preferred_tab").apply()
        }
    }
}
