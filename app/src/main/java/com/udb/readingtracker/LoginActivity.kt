package com.udb.readingtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.udb.readingtracker.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya hay sesión activa, ir directo a la pantalla principal.
        if (SessionManager.getUid(this) != null) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = DatabaseHelper(this)

        binding.btnLogin.setOnClickListener { login() }
        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        val emailError = Validators.email(email)
        val passError = Validators.password(password)
        binding.tilEmail.error = emailError
        binding.tilPassword.error = passError
        if (emailError != null || passError != null) return

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: "fb_$email"
                SessionManager.saveSession(this, uid, email)
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                // Si el usuario fue registrado en el respaldo local o Firebase devuelve CONFIGURATION_NOT_FOUND
                val localUid = db.loginLocalUser(email, password)
                if (localUid != null) {
                    SessionManager.saveSession(this, localUid, email)
                    goToMain()
                    return@addOnFailureListener
                }

                val rawMsg = e.message.orEmpty()
                val msg = when {
                    rawMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                        rawMsg.contains("internal error", ignoreCase = true) ->
                        "Correo o contraseña incorrectos"
                    e is FirebaseAuthInvalidUserException ||
                        e is FirebaseAuthInvalidCredentialsException ->
                        "Correo o contraseña incorrectos"
                    else -> "No se pudo iniciar sesión: ${e.localizedMessage}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
