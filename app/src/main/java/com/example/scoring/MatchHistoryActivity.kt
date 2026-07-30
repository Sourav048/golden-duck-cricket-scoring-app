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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_match_history)

            findViewById<View>(R.id.btnBackHistory).setOnClickListener { finish() }

            val tabLayout = findViewById<TabLayout>(R.id.tabLayoutMatches)
            val viewPager = findViewById<ViewPager2>(R.id.viewPagerMatches)

            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return MatchListFragment.newInstance(position)
                }
                override fun getItemCount(): Int = 3
            }

            val titles = arrayOf("Completed", "In Progress", "Abandoned")
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = titles[position]
            }.attach()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Match History initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
