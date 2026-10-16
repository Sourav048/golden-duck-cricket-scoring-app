package com.goldenduck.scoring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MatchInfoFragment : Fragment() {
    private var tvVenue: TextView? = null
    private var tvBall: TextView? = null
    private var tvToss: TextView? = null
    private var tvRules: TextView? = null
    private var tvS1: TextView? = null
    private var tvE1: TextView? = null
    private var tvS2: TextView? = null
    private var tvE2: TextView? = null

    // Rule Badges
    private var tvRuleWide: TextView? = null
    private var tvRuleFreeHit: TextView? = null
    private var tvRuleBye: TextView? = null
    private var tvRuleOverthrow: TextView? = null
    private var tvRuleLastMan: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_info, container, false)
        tvVenue = v.findViewById(R.id.tvInfoVenue)
        tvBall = v.findViewById(R.id.tvInfoBallType)
        tvToss = v.findViewById(R.id.tvInfoToss)
        tvRules = v.findViewById(R.id.tvInfoRules)
        tvS1 = v.findViewById(R.id.tvInnings1Start)
        tvE1 = v.findViewById(R.id.tvInnings1End)
        tvS2 = v.findViewById(R.id.tvInnings2Start)
        tvE2 = v.findViewById(R.id.tvInnings2End)

        tvRuleWide = v.findViewById(R.id.tvRuleWide)
        tvRuleFreeHit = v.findViewById(R.id.tvRuleFreeHit)
        tvRuleBye = v.findViewById(R.id.tvRuleBye)
        tvRuleOverthrow = v.findViewById(R.id.tvRuleOverthrow)
        tvRuleLastMan = v.findViewById(R.id.tvRuleLastMan)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewModel = ViewModelProvider(requireActivity())[ScoringViewModel::class.java]
        viewModel.match.observe(viewLifecycleOwner) { match ->
            updateUI(match)
        }

        val act = activity as? ScoringProvider
        act?.match?.let { updateUI(it) }
    }

    fun updateUI(match: Match?) {
        if (view == null || activity == null || match == null) return

        tvVenue?.text = if (match.venue.isNullOrBlank()) "Not Specified" else match.venue
        tvBall?.text = if (match.ballType.isNullOrBlank()) "Not Specified" else match.ballType

        val tossW = match.tossWinner ?: "Unknown"
        val tossD = match.tossDecision ?: "Unknown"
        tvToss?.text = "$tossW won and opted to $tossD"

        setupRuleBadge(tvRuleWide, match.ruleRunsOnWide)
        setupRuleBadge(tvRuleFreeHit, match.ruleFreeHit)
        setupRuleBadge(tvRuleBye, match.ruleRunsOnBye)
        setupRuleBadge(tvRuleOverthrow, match.ruleOverthrow)
        setupRuleBadge(tvRuleLastMan, match.ruleEveryPlayerBats)

        val rules = StringBuilder("Match Conditions:\n")
        rules.append("• Runs on Wides/No-Balls: ${if (match.ruleRunsOnWide) "YES" else "NO"}\n")
        rules.append("• Free Hit after No Ball: ${if (match.ruleFreeHit) "YES" else "NO"}\n")
        rules.append("• Byes/Leg-Byes Allowed: ${if (match.ruleRunsOnBye) "YES" else "NO"}\n")
        rules.append("• Overthrows allowed: ${if (match.ruleOverthrow) "YES" else "NO"}\n")
        rules.append("• Last Man Stand: ${if (match.ruleEveryPlayerBats) "YES" else "NO"}")
        tvRules?.text = rules.toString()

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        tvS1?.text = formatTime(match.firstInningsStartTime, sdf)
        tvE1?.text = formatTime(match.firstInningsEndTime, sdf)
        tvS2?.text = formatTime(match.secondInningsStartTime, sdf)
        tvE2?.text = formatTime(match.secondInningsEndTime, sdf)
    }

    private fun setupRuleBadge(tv: TextView?, allowed: Boolean) {
        val tvView = tv ?: return
        val ctx = context ?: return
        if (allowed) {
            tvView.text = "YES"
            tvView.setBackgroundResource(R.drawable.bg_pill_green)
            tvView.setTextColor(ContextCompat.getColor(ctx, R.color.pill_green_text))
        } else {
            tvView.text = "NO"
            tvView.setBackgroundResource(R.drawable.bg_pill_red)
            tvView.setTextColor(ContextCompat.getColor(ctx, R.color.pill_red_text))
        }
    }

    private fun formatTime(t: Long, sdf: SimpleDateFormat): String {
        return if (t > 0) sdf.format(Date(t)) else "-"
    }
}
