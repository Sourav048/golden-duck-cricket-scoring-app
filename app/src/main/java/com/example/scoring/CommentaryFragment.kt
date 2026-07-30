package com.example.scoring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoring.MainActivity.CommentaryAdapter

class CommentaryFragment : Fragment() {
    private var rv: RecyclerView? = null
    private var adapter: CommentaryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_commentary, container, false)
        rv = v.findViewById(R.id.rvFullCommentary)
        rv?.layoutManager = LinearLayoutManager(context)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewModel =
            ViewModelProvider(requireActivity())[ScoringViewModel::class.java]
        viewModel.commentary.observe(
            viewLifecycleOwner,
            Observer { entries: MutableList<CommentaryEntry?>? ->
                updateUI()
            })
        updateUI()
    }

    fun updateUI() {
        if (!isAdded || rv == null || activity == null) return
        val act = activity as ScoringProvider?
        if (act != null && act.commentary != null) {
            if (adapter == null) {
                adapter = CommentaryAdapter(act.commentary ?: return, act)
                rv?.adapter = adapter
            } else {
                adapter?.notifyDataSetChanged()
            }
        }
    }
}
