package com.udb.readingtracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.udb.readingtracker.databinding.ActivityStatsBinding
import java.util.Calendar

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val uid = SessionManager.getUid(this) ?: run { finish(); return }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val finished = DatabaseHelper(this).getFinishedInYear(uid, year)

        binding.tvYear.text = "Estadísticas $year"

        // Libros terminados por género (siempre se muestran los cuatro)
        val counts = Constants.GENRES.associateWith { g -> finished.count { it.genre == g } }
        binding.tvGenreCounts.text = counts.entries.joinToString("\n") { "• ${it.key}: ${it.value} libro(s)" }

        // Género más leído (en caso de empate se listan todos)
        val max = counts.values.maxOrNull() ?: 0
        binding.tvTopGenre.text = if (max == 0) {
            "Aún no hay libros terminados este año"
        } else {
            counts.filterValues { it == max }.keys.joinToString(" / ") + " ($max libro(s))"
        }

        // Total de páginas leídas en el año (libros terminados)
        binding.tvPages.text = "${finished.sumOf { it.totalPages }} páginas"

        // Mejor calificado: mayor calificación; en empate, el que se terminó primero
        val best = finished
            .filter { it.rating != null }
            .sortedWith(
                compareByDescending<Book> { it.rating }
                    .thenBy { it.finishedDate }
                    .thenBy { it.id }
            )
            .firstOrNull()
        binding.tvBest.text = if (best == null) {
            "Aún no hay libros calificados este año"
        } else {
            "${best.title}\nde ${best.author}\n" +
                "★".repeat(best.rating!!) + "☆".repeat(5 - best.rating) +
                " (${best.rating}/5) — terminado el ${best.finishedDate}"
        }
    }
}
