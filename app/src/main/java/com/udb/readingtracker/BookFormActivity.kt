package com.udb.readingtracker

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.udb.readingtracker.databinding.ActivityBookFormBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }

    private lateinit var binding: ActivityBookFormBinding
    private lateinit var db: DatabaseHelper
    private lateinit var uid: String
    private var editing: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = SessionManager.getUid(this) ?: run { finish(); return }
        db = DatabaseHelper(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.spinnerGenre.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Constants.GENRES)
        binding.spinnerStatus.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Constants.STATUSES)

        // La calificación solo se muestra cuando el estado es "Terminado".
        binding.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val status = Constants.STATUSES[pos]
                val finished = status == Constants.STATUS_FINISHED
                binding.llRating.visibility = if (finished) View.VISIBLE else View.GONE
                if (finished) {
                    val totalText = binding.etTotal.text?.toString()?.trim().orEmpty()
                    if (totalText.toIntOrNull()?.let { it > 0 } == true) {
                        binding.etRead.setText(totalText)
                    }
                } else {
                    binding.ratingBar.rating = 0f
                    binding.tvRatingError.visibility = View.GONE
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId != -1L) {
            editing = db.getBook(bookId, uid)
        }
        val book = editing
        if (book != null) {
            binding.tvFormTitle.text = "Editar libro"
            binding.etTitle.setText(book.title)
            binding.etAuthor.setText(book.author)
            binding.spinnerGenre.setSelection(Constants.GENRES.indexOf(book.genre).coerceAtLeast(0))
            binding.etTotal.setText(book.totalPages.toString())
            binding.etRead.setText(book.pagesRead.toString())
            val statusIndex = Constants.STATUSES.indexOf(book.status).coerceAtLeast(0)
            binding.spinnerStatus.setSelection(statusIndex)
            binding.llRating.visibility =
                if (book.status == Constants.STATUS_FINISHED) View.VISIBLE else View.GONE
            binding.ratingBar.rating = (book.rating ?: 0).toFloat()
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            binding.tvFormTitle.text = "Agregar libro"
            binding.etRead.setText("0")
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun save() {
        val title = binding.etTitle.text.toString()
        val author = binding.etAuthor.text.toString()
        val total = binding.etTotal.text.toString()
        val status = binding.spinnerStatus.selectedItem as String
        val genre = binding.spinnerGenre.selectedItem as String
        val rating = binding.ratingBar.rating.toInt()

        // Si el estado es Terminado y el total es válido, sincronizar páginas leídas = total
        if (status == Constants.STATUS_FINISHED && total.trim().toIntOrNull()?.let { it > 0 } == true) {
            binding.etRead.setText(total.trim())
        }
        val read = binding.etRead.text.toString()

        // Limpiar errores previos
        binding.tilTitle.error = null
        binding.tilAuthor.error = null
        binding.tilTotal.error = null
        binding.tilRead.error = null
        binding.tvRatingError.visibility = View.GONE

        val errors = Validators.book(title, author, total, read, status, rating)
        if (errors.isNotEmpty()) {
            for (e in errors) {
                when (e.field) {
                    Validators.F_TITLE -> binding.tilTitle.error = e.message
                    Validators.F_AUTHOR -> binding.tilAuthor.error = e.message
                    Validators.F_TOTAL -> binding.tilTotal.error = e.message
                    Validators.F_READ -> binding.tilRead.error = e.message
                    Validators.F_RATING -> {
                        binding.tvRatingError.text = e.message
                        binding.tvRatingError.visibility = View.VISIBLE
                    }
                }
            }
            Toast.makeText(this, errors.joinToString("\n") { "• ${it.message}" }, Toast.LENGTH_LONG).show()
            return
        }

        val totalPages = total.trim().toInt()
        val finished = status == Constants.STATUS_FINISHED
        // Un libro terminado se considera 100% leído.
        val pagesRead = if (finished) totalPages else read.trim().toInt()
        // Fecha de término: se guarda al marcar como Terminado (se conserva si ya existía).
        val finishedDate = if (finished) {
            editing?.finishedDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        } else null

        val book = Book(
            id = editing?.id ?: 0,
            userId = uid,
            title = title.trim(),
            author = author.trim(),
            genre = genre,
            totalPages = totalPages,
            pagesRead = pagesRead,
            status = status,
            rating = if (finished) rating else null,
            finishedDate = finishedDate
        )

        if (editing == null) db.insertBook(book) else db.updateBook(book)
        Toast.makeText(this, "Libro guardado", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val book = editing ?: return
        AlertDialog.Builder(this)
            .setTitle("Eliminar libro")
            .setMessage("¿Eliminar \"${book.title}\" de tu biblioteca?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.deleteBook(book.id, uid)
                Toast.makeText(this, "Libro eliminado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
