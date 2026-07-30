package com.example.scoring

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.abs

class StatsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_stats_main)

            val tabLayout = findViewById<TabLayout>(R.id.tabLayoutStats)
            val viewPager = findViewById<ViewPager2>(R.id.viewPagerStats)

            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return when (position) {
                        0 -> StatsFragment.newInstance("Batting")
                        1 -> StatsFragment.newInstance("Bowling")
                        2 -> StatsFragment.newInstance("Fielding")
                        3 -> StatsFragment.newInstance("Ranking")
                        else -> StatsFragment.newInstance("Batting")
                    }
                }
                override fun getItemCount(): Int = 4
            }

            val tabs = arrayOf("Batting", "Bowling", "Fielding", "Ranking")
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = tabs[position]
            }.attach()

            viewPager.setPageTransformer { page, position ->
                when {
                    position < -1 -> page.alpha = 0f
                    position <= 0 -> {
                        page.alpha = 1f
                        page.translationX = 0f
                        page.translationZ = 0f
                        page.scaleX = 1f
                        page.scaleY = 1f
                    }
                    position <= 1 -> {
                        page.alpha = 1f - position
                        page.translationX = page.width * -position
                        page.translationZ = -1f
                        val scaleFactor = 0.75f + (1 - 0.75f) * (1 - abs(position))
                        page.scaleX = scaleFactor
                        page.scaleY = scaleFactor
                    }
                    else -> page.alpha = 0f
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Stats initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
