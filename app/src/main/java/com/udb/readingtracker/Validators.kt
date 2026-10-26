package com.udb.readingtracker

import android.util.Patterns

data class FieldError(val field: String, val message: String)

object Validators {

    const val F_TITLE = "title"
    const val F_AUTHOR = "author"
    const val F_TOTAL = "total"
    const val F_READ = "read"
    const val F_RATING = "rating"

    fun email(email: String): String? = when {
        email.isBlank() -> "El correo es obligatorio"
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "El formato del correo no es válido"
        else -> null
    }

    fun password(password: String): String? = when {
        password.isEmpty() -> "La contraseña es obligatoria"
        password.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Debes confirmar la contraseña"
        password != confirm -> "Las contraseñas no coinciden"
        else -> null
    }

    fun goal(text: String): String? {
        val n = text.trim().toIntOrNull()
        return if (n == null || n <= 0) "La meta debe ser un número entero mayor que cero" else null
    }

    /** Devuelve la lista de errores (vacía si todo es válido). */
    fun book(
        title: String,
        author: String,
        totalPages: String,
        pagesRead: String,
        status: String,
        rating: Int
    ): List<FieldError> {
        val errors = mutableListOf<FieldError>()

        val t = title.trim()
        if (t.isEmpty()) errors += FieldError(F_TITLE, "El título es obligatorio")
        else if (t.length !in 2..60) errors += FieldError(F_TITLE, "El título debe tener entre 2 y 60 caracteres")

        val a = author.trim()
        if (a.isEmpty()) errors += FieldError(F_AUTHOR, "El autor es obligatorio")
        else if (a.length !in 2..60) errors += FieldError(F_AUTHOR, "El autor debe tener entre 2 y 60 caracteres")

        val total = totalPages.trim().toIntOrNull()
        if (total == null || total <= 0) {
            errors += FieldError(F_TOTAL, "El total de páginas debe ser un número entero mayor que cero")
        }

        val read = pagesRead.trim().toIntOrNull()
        if (read == null || read < 0) {
            errors += FieldError(F_READ, "Las páginas leídas deben ser un número entero mayor o igual a 0")
        } else if (total != null && total > 0 && read > total) {
            errors += FieldError(F_READ, "Las páginas leídas no pueden superar el total de páginas ($total)")
        }

        if (status == Constants.STATUS_FINISHED && rating !in 1..5) {
            errors += FieldError(F_RATING, "La calificación (1 a 5 estrellas) es obligatoria para un libro Terminado")
        }

        return errors
    }
}
