package com.udb.readingtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.udb.readingtracker.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = DatabaseHelper(this)

        binding.btnRegister.setOnClickListener { register() }
        binding.tvGoLogin.setOnClickListener { finish() }
    }

    private fun register() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirm.text.toString()

        val emailError = Validators.email(email)
        val passError = Validators.password(password)
        val confirmError = Validators.confirmPassword(password, confirm)
        binding.tilEmail.error = emailError
        binding.tilPassword.error = passError
        binding.tilConfirm.error = confirmError
        if (emailError != null || passError != null || confirmError != null) return

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: "fb_$email"
                SessionManager.saveSession(this, uid, email)
                Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val rawMsg = e.message.orEmpty()
                // Si en Firebase Console aún no está habilitado Email/Password (CONFIGURATION_NOT_FOUND),
                // se registra automáticamente en la base de datos local para no bloquear el uso.
                if (rawMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                    rawMsg.contains("internal error", ignoreCase = true)
                ) {
                    val localUid = db.registerLocalUser(email, password)
                    if (localUid == null) {
                        Toast.makeText(this, "Ya existe una cuenta con ese correo", Toast.LENGTH_LONG).show()
                    } else {
                        SessionManager.saveSession(this, localUid, email)
                        Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                        goToMain()
                    }
                    return@addOnFailureListener
                }

                val msg = when (e) {
                    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo"
                    else -> "No se pudo crear la cuenta: ${e.localizedMessage}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }
}
