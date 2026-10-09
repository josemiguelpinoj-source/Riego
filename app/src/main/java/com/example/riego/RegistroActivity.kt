package com.example.riego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.riego.databinding.ActivityRegistroBinding

class RegistroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding

    companion object {
        const val PREFS_USUARIOS = "UsuariosRiegoPrefs"
        const val KEY_PREFIX_PASS = "pass_"
        const val KEY_PREFIX_NOMBRE = "nombre_"
        const val KEY_LISTA_EMAILS = "lista_usuarios_registrados"

        /**
         * Verifica si un correo está registrado en el sistema
         */
        fun estaRegistrado(context: Context, email: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_USUARIOS, Context.MODE_PRIVATE)
            val emails = prefs.getStringSet(KEY_LISTA_EMAILS, emptySet()) ?: emptySet()
            return emails.contains(email.lowercase().trim())
        }

        /**
         * Valida credenciales de usuario registrado
         */
        fun validarCredenciales(context: Context, email: String, pass: String): Boolean {
            val emailNormalizado = email.lowercase().trim()
            if (!estaRegistrado(context, emailNormalizado)) return false

            val prefs = context.getSharedPreferences(PREFS_USUARIOS, Context.MODE_PRIVATE)
            val passGuardada = prefs.getString(KEY_PREFIX_PASS + emailNormalizado, null)
            return passGuardada == pass
        }

        /**
         * Obtiene el nombre del usuario registrado
         */
        fun obtenerNombre(context: Context, email: String): String {
            val emailNormalizado = email.lowercase().trim()
            val prefs = context.getSharedPreferences(PREFS_USUARIOS, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PREFIX_NOMBRE + emailNormalizado, "") ?: emailNormalizado
        }

        /**
         * Guarda un nuevo usuario en el sistema
         */
        fun registrarUsuario(context: Context, nombre: String, email: String, pass: String) {
            val emailNormalizado = email.lowercase().trim()
            val prefs = context.getSharedPreferences(PREFS_USUARIOS, Context.MODE_PRIVATE)
            val emailsActuales = (prefs.getStringSet(KEY_LISTA_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
            emailsActuales.add(emailNormalizado)

            prefs.edit()
                .putStringSet(KEY_LISTA_EMAILS, emailsActuales)
                .putString(KEY_PREFIX_PASS + emailNormalizado, pass)
                .putString(KEY_PREFIX_NOMBRE + emailNormalizado, nombre)
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnRegistrar.setOnClickListener {
            procesarRegistro()
        }

        binding.btnVolverLogin.setOnClickListener {
            finish()
        }
    }

    private fun procesarRegistro() {
        val nombre = binding.edtNombre.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString()
        val confirmarPassword = binding.edtConfirmarPassword.text.toString()

        var esValido = true

        // Validación de nombre
        if (nombre.isEmpty()) {
            binding.tilNombre.error = getString(R.string.register_error_empty_name)
            esValido = false
        } else {
            binding.tilNombre.error = null
        }

        // Validación de correo electrónico (debe incluir dominio con punto, ej: .com o .cl)
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.register_error_empty_email)
            esValido = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || !email.contains(".") || email.substringAfterLast(".", "").length < 2) {
            binding.tilEmail.error = getString(R.string.register_error_invalid_email)
            esValido = false
        } else if (estaRegistrado(this, email)) {
            binding.tilEmail.error = getString(R.string.register_error_already_exists)
            esValido = false
        } else {
            binding.tilEmail.error = null
        }

        // Validación de contraseña (mínimo 6 caracteres)
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_error_empty_password)
            esValido = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.register_error_short_password)
            esValido = false
        } else {
            binding.tilPassword.error = null
        }

        // Validación de confirmación de contraseña
        if (confirmarPassword.isEmpty()) {
            binding.tilConfirmarPassword.error = "Confirma tu contraseña"
            esValido = false
        } else if (password != confirmarPassword) {
            binding.tilConfirmarPassword.error = getString(R.string.register_error_password_mismatch)
            esValido = false
        } else {
            binding.tilConfirmarPassword.error = null
        }

        if (esValido) {
            binding.progressBarRegistro.visibility = View.VISIBLE
            binding.btnRegistrar.isEnabled = false

            // Guardar usuario registrado en la base de datos local
            registrarUsuario(this, nombre, email, password)

            Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()

            // Navegar directamente a la pantalla de bienvenida con la sesión iniciada
            val intent = Intent(this, BienvenidaActivity::class.java)
            intent.putExtra("usuario", email)
            startActivity(intent)
            finish()
        }
    }
}
