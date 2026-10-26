package com.udb.readingtracker

object Constants {
    val GENRES = listOf("Novela", "Ciencia ficción", "Historia", "Desarrollo personal")

    const val STATUS_TO_READ = "Por leer"
    const val STATUS_READING = "Leyendo"
    const val STATUS_FINISHED = "Terminado"
    val STATUSES = listOf(STATUS_TO_READ, STATUS_READING, STATUS_FINISHED)
}

data class Book(
    val id: Long = 0,
    val userId: String,
    val title: String,
    val author: String,
    val genre: String,
    val totalPages: Int,
    val pagesRead: Int,
    val status: String,
    val rating: Int?,
    val finishedDate: String?
) {
    val progress: Int
        get() = if (totalPages > 0) (pagesRead * 100) / totalPages else 0
}
