package com.udb.readingtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

/**
 * Gestiona la sesión activa del usuario (compatible con Firebase Auth y respaldo local
 * cuando el proveedor Email/Password aún no está habilitado en Firebase Console).
 */
object SessionManager {
    private const val PREFS = "reading_tracker_session"
    private const val KEY_UID = "uid"
    private const val KEY_EMAIL = "email"

    fun getUid(context: Context): String? {
        FirebaseAuth.getInstance().currentUser?.uid?.let { return it }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UID, null)
    }

    fun getDisplayNameOrEmail(context: Context): String {
        val fbUser = FirebaseAuth.getInstance().currentUser
        if (fbUser != null) {
            return fbUser.displayName?.takeIf { it.isNotBlank() } ?: fbUser.email ?: "lector"
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null) ?: "lector"
    }

    fun saveSession(context: Context, uid: String, email: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UID, uid)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun logout(context: Context) {
        FirebaseAuth.getInstance().signOut()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

/**
 * Persistencia local con SQLite. Todas las consultas filtran por user_id (uid de usuario),
 * por lo que cada usuario solo ve sus propios datos.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                genre TEXT NOT NULL,
                total_pages INTEGER NOT NULL,
                pages_read INTEGER NOT NULL,
                status TEXT NOT NULL,
                rating INTEGER,
                finished_date TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goals (
                user_id TEXT NOT NULL,
                year INTEGER NOT NULL,
                goal INTEGER NOT NULL,
                PRIMARY KEY (user_id, year)
            )
            """.trimIndent()
        )
        createUsersTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            createUsersTable(db)
        }
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                email TEXT PRIMARY KEY,
                password TEXT NOT NULL,
                uid TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    // ---------- Usuarios locales (respaldo si Firebase Auth no está configurado) ----------

    fun registerLocalUser(email: String, password: String): String? {
        val cleanEmail = email.trim().lowercase()
        val exists = readableDatabase.query(
            "users", arrayOf("uid"), "email = ?", arrayOf(cleanEmail),
            null, null, null
        ).use { it.moveToFirst() }
        if (exists) return null

        val uid = "local_" + UUID.nameUUIDFromBytes(cleanEmail.toByteArray()).toString()
        val values = ContentValues().apply {
            put("email", cleanEmail)
            put("password", password)
            put("uid", uid)
        }
        writableDatabase.insert("users", null, values)
        return uid
    }

    fun loginLocalUser(email: String, password: String): String? {
        val cleanEmail = email.trim().lowercase()
        return readableDatabase.query(
            "users", arrayOf("uid"), "email = ? AND password = ?",
            arrayOf(cleanEmail, password), null, null, null
        ).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    // ---------- Libros ----------

    private fun Book.toValues() = ContentValues().apply {
        put("user_id", userId)
        put("title", title)
        put("author", author)
        put("genre", genre)
        put("total_pages", totalPages)
        put("pages_read", pagesRead)
        put("status", status)
        if (rating != null) put("rating", rating) else putNull("rating")
        if (finishedDate != null) put("finished_date", finishedDate) else putNull("finished_date")
    }

    private fun Cursor.toBook() = Book(
        id = getLong(getColumnIndexOrThrow("id")),
        userId = getString(getColumnIndexOrThrow("user_id")),
        title = getString(getColumnIndexOrThrow("title")),
        author = getString(getColumnIndexOrThrow("author")),
        genre = getString(getColumnIndexOrThrow("genre")),
        totalPages = getInt(getColumnIndexOrThrow("total_pages")),
        pagesRead = getInt(getColumnIndexOrThrow("pages_read")),
        status = getString(getColumnIndexOrThrow("status")),
        rating = getColumnIndexOrThrow("rating").let { if (isNull(it)) null else getInt(it) },
        finishedDate = getColumnIndexOrThrow("finished_date").let { if (isNull(it)) null else getString(it) }
    )

    fun insertBook(book: Book): Long = writableDatabase.insert("books", null, book.toValues())

    fun updateBook(book: Book): Int = writableDatabase.update(
        "books", book.toValues(), "id = ? AND user_id = ?",
        arrayOf(book.id.toString(), book.userId)
    )

    fun deleteBook(id: Long, userId: String): Int =
        writableDatabase.delete("books", "id = ? AND user_id = ?", arrayOf(id.toString(), userId))

    fun getBook(id: Long, userId: String): Book? =
        readableDatabase.query(
            "books", null, "id = ? AND user_id = ?", arrayOf(id.toString(), userId),
            null, null, null
        ).use { if (it.moveToFirst()) it.toBook() else null }

    private fun queryBooks(selection: String, args: Array<String>, orderBy: String): List<Book> =
        readableDatabase.query("books", null, selection, args, null, null, orderBy).use { c ->
            val list = mutableListOf<Book>()
            while (c.moveToNext()) list += c.toBook()
            list
        }

    fun getBooks(userId: String): List<Book> =
        queryBooks("user_id = ?", arrayOf(userId), "id DESC")

    fun getReadingBooks(userId: String): List<Book> =
        queryBooks("user_id = ? AND status = ?", arrayOf(userId, Constants.STATUS_READING), "id DESC")

    fun getFinishedInYear(userId: String, year: Int): List<Book> =
        queryBooks(
            "user_id = ? AND status = ? AND finished_date LIKE ?",
            arrayOf(userId, Constants.STATUS_FINISHED, "$year-%"),
            "finished_date ASC, id ASC"
        )

    // ---------- Meta anual ----------

    fun getGoal(userId: String, year: Int): Int? =
        readableDatabase.query(
            "goals", arrayOf("goal"), "user_id = ? AND year = ?",
            arrayOf(userId, year.toString()), null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0) else null }

    fun setGoal(userId: String, year: Int, goal: Int) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("year", year)
            put("goal", goal)
        }
        writableDatabase.insertWithOnConflict("goals", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val DB_NAME = "readings.db"
        private const val DB_VERSION = 1
    }
}
