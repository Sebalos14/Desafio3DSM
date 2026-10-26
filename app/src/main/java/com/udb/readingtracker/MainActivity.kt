package com.udb.readingtracker

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.udb.readingtracker.databinding.ActivityMainBinding
import com.udb.readingtracker.databinding.ItemBookBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DatabaseHelper

    private val year: Int get() = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SessionManager.getUid(this) == null) {
            goToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = DatabaseHelper(this)

        binding.btnGoal.setOnClickListener { showGoalDialog() }
        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, BookFormActivity::class.java))
        }
        binding.btnLibrary.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
        binding.btnStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        binding.btnLogout.setOnClickListener {
            SessionManager.logout(this)
            goToLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.getUid(this) != null && ::binding.isInitialized) refresh()
    }

    private fun refresh() {
        val uid = SessionManager.getUid(this) ?: return
        val name = SessionManager.getDisplayNameOrEmail(this)
        binding.tvGreeting.text = "¡Hola, $name!"

        // Meta anual
        val goal = db.getGoal(uid, year)
        val finished = db.getFinishedInYear(uid, year).size
        binding.tvGoalTitle.text = "Meta de lectura $year"
        if (goal == null) {
            binding.tvGoalInfo.text = "Aún no has definido tu meta anual.\nTerminados este año: $finished"
            binding.progressGoal.progress = 0
            binding.btnGoal.text = "Definir meta"
        } else {
            val remaining = (goal - finished).coerceAtLeast(0)
            val extra = if (finished >= goal) "\n¡Meta cumplida! 🎉" else ""
            binding.tvGoalInfo.text =
                "Meta: $goal libros  •  Terminados: $finished  •  Faltan: $remaining$extra"
            binding.progressGoal.progress = ((finished * 100) / goal).coerceAtMost(100)
            binding.btnGoal.text = "Modificar meta"
        }

        // Libros que se están leyendo
        binding.llReading.removeAllViews()
        val reading = db.getReadingBooks(uid)
        binding.tvNoReading.visibility = if (reading.isEmpty()) View.VISIBLE else View.GONE
        for (book in reading) {
            val item = ItemBookBinding.inflate(LayoutInflater.from(this), binding.llReading, false)
            bindBook(item, book)
            item.root.setOnClickListener {
                startActivity(
                    Intent(this, BookFormActivity::class.java)
                        .putExtra(BookFormActivity.EXTRA_BOOK_ID, book.id)
                )
            }
            binding.llReading.addView(item.root)
        }
    }

    private fun showGoalDialog() {
        val uid = SessionManager.getUid(this) ?: return
        val til = TextInputLayout(this).apply {
            hint = "Libros a leer en $year"
        }
        val input = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            db.getGoal(uid, year)?.let { setText(it.toString()) }
        }
        til.addView(input)

        val container = FrameLayout(this).apply {
            val padH = (24 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, 0)
            addView(til)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Meta anual de lectura ($year)")
            .setView(container)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text?.toString().orEmpty()
                val error = Validators.goal(text)
                if (error != null) {
                    til.error = error
                } else {
                    til.error = null
                    db.setGoal(uid, year, text.trim().toInt())
                    Toast.makeText(this, "Meta guardada", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    refresh()
                }
            }
        }
        dialog.show()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
