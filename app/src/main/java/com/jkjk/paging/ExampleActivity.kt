package com.jkjk.paging

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.jkjk.pagingscrollview.OnPageChangeListener
import com.jkjk.pagingscrollview.PagingScrollView
import kotlinx.android.synthetic.main.activity_example.*

class ExampleActivity : AppCompatActivity(), View.OnClickListener, OnPageChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example)
        first.setOnClickListener(this)
        third.setOnClickListener(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ExampleAdapter(layoutInflater)
    }

    override fun onResume() {
        super.onResume()
        scrollView.addOnPageChangeListener(this)
    }

    override fun onPause() {
        scrollView.removeOnPageChangeListener(this)
        super.onPause()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.first -> scrollView.goToNextPage()
            R.id.third -> scrollView.goToLastPage()
        }
    }

    override fun onPageChange(view: PagingScrollView, newPage: Int) {
        Toast.makeText(this, "You've scrolled to page $newPage", Toast.LENGTH_SHORT).show()
    }

    class ExampleAdapter(private val layoutInflater: LayoutInflater) : RecyclerView.Adapter<ExampleAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(layoutInflater.inflate(R.layout.item_example, parent, false))
        }

        override fun getItemCount(): Int {
            return 10
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        }
    }
}
