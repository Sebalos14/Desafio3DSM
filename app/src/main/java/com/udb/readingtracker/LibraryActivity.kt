package com.udb.readingtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.udb.readingtracker.databinding.ActivityLibraryBinding

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: BookAdapter
    private var uid: String? = null
    private var allBooks: List<Book> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = SessionManager.getUid(this)
        if (uid == null) {
            finish()
            return
        }
        db = DatabaseHelper(this)

        binding.btnBack.setOnClickListener { finish() }

        adapter = BookAdapter { book ->
            startActivity(
                Intent(this, BookFormActivity::class.java)
                    .putExtra(BookFormActivity.EXTRA_BOOK_ID, book.id)
            )
        }
        binding.recyclerBooks.layoutManager = LinearLayoutManager(this)
        binding.recyclerBooks.adapter = adapter

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            applyFilter()
        }

        binding.btnAddBook.setOnClickListener {
            startActivity(Intent(this, BookFormActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val id = uid ?: return
        allBooks = db.getBooks(id)
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = when (binding.chipGroupFilter.checkedChipId) {
            R.id.chipReading -> allBooks.filter { it.status == Constants.STATUS_READING }
            R.id.chipToRead -> allBooks.filter { it.status == Constants.STATUS_TO_READ }
            R.id.chipFinished -> allBooks.filter { it.status == Constants.STATUS_FINISHED }
            else -> allBooks
        }
        adapter.submit(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerBooks.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.tvCount.text = if (filtered.size == allBooks.size) {
            "${allBooks.size} libro(s) en tu biblioteca"
        } else {
            "Mostrando ${filtered.size} de ${allBooks.size} libro(s)"
        }
    }
}
