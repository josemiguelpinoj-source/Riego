package com.example.riego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.riego.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : AppCompatActivity() {

    // ViewBinding para acceso seguro y tipado a las vistas del layout
    private lateinit var binding: ActivityMainBinding

    // Cliente y selector de Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient

    // Launcher para capturar el resultado del flujo de autenticación de Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val email = account.email ?: account.displayName ?: "Usuario Google"
            Toast.makeText(this, getString(R.string.login_google_success), Toast.LENGTH_SHORT).show()
            navegarBienvenida(email)
        } catch (e: ApiException) {
            manejarErrorGoogle(e)
        }
    }

    // Contador de intentos fallidos de inicio de sesión
    private var intentosFallidos: Int = 0
    private val maxIntentos: Int = 3

    companion object {
        // Constantes para persistencia con SharedPreferences
        private const val PREFS_NAME = "RiegoPrefs"
        private const val KEY_RECORDAR = "recordar_usuario"
        private const val KEY_USUARIO = "usuario_guardado"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inflado de la vista utilizando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajuste de márgenes para respetar las barras del sistema (Edge to Edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configuración de Google Sign-In
        configurarGoogleSignIn()

        // Cargar usuario guardado si la opción 'Recordarme' estaba activa previamente
        cargarPreferenciasUsuario()

        // Asegurar que exista al menos un usuario demo inicial si la base de datos está vacía
        inicializarUsuarioDemo()

        // Configuración de listeners de clics
        binding.btnIngresar.setOnClickListener {
            procesarIngreso()
        }

        binding.btnLimpiar.setOnClickListener {
            limpiarCampos()
        }

        binding.btnIrARegistro.setOnClickListener {
            val intent = Intent(this, RegistroActivity::class.java)
            startActivity(intent)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            iniciarSesionGoogle()
        }
    }

    private fun inicializarUsuarioDemo() {
        if (!RegistroActivity.estaRegistrado(this, "admin@riego.cl")) {
            RegistroActivity.registrarUsuario(this, "Administrador Riego", "admin@riego.cl", "123456")
        }
    }

    /**
     * Inicializa las opciones de Google Sign-In
     */
    private fun configurarGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    /**
     * Lanza el selector de cuentas de Google
     */
    private fun iniciarSesionGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    /**
     * Gestiona excepciones de Google Sign-In (por ejemplo, si falta SHA-1 o configuración en Google Console)
     */
    private fun manejarErrorGoogle(e: ApiException) {
        val codigo = e.statusCode
        val mensaje = e.localizedMessage ?: "Error desconocido"

        // Códigos típicos cuando falta registrar la app o huella SHA-1 en Google Cloud Console (10: DEVELOPER_ERROR, 12500)
        if (codigo == 10 || codigo == 12500) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.login_google_dialog_title))
                .setMessage(getString(R.string.login_google_dialog_message, codigo))
                .setPositiveButton(getString(R.string.btn_dialog_test)) { _, _ ->
                    val emailPrueba = getString(R.string.login_google_dialog_simulated_user)
                    Toast.makeText(
                        this,
                        getString(R.string.login_google_simulated, emailPrueba),
                        Toast.LENGTH_LONG
                    ).show()
                    navegarBienvenida(emailPrueba)
                }
                .setNegativeButton(getString(R.string.btn_dialog_cancel), null)
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.login_google_error, codigo, mensaje),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Navega hacia BienvenidaActivity pasando el identificador de usuario
     */
    private fun navegarBienvenida(usuario: String) {
        val intent = Intent(this, BienvenidaActivity::class.java)
        intent.putExtra("usuario", usuario)
        startActivity(intent)
    }

    /**
     * Carga las credenciales guardadas en SharedPreferences si 'Recordarme' estaba activo
     */
    private fun cargarPreferenciasUsuario() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recordar = prefs.getBoolean(KEY_RECORDAR, false)
        if (recordar) {
            val usuarioGuardado = prefs.getString(KEY_USUARIO, "") ?: ""
            binding.edtUsuario.setText(usuarioGuardado)
            binding.chkRecordarme.isChecked = true
        }
    }

    /**
     * Valida los campos del formulario y procesa el inicio de sesión
     */
    private fun procesarIngreso() {
        // Verificar si el usuario ha sido bloqueado por superar los intentos permitidos
        if (intentosFallidos >= maxIntentos) {
            Toast.makeText(this, getString(R.string.login_error_locked), Toast.LENGTH_LONG).show()
            return
        }

        val usuario = binding.edtUsuario.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()
        val recordarme = binding.chkRecordarme.isChecked

        var esValido = true

        // Validación del campo de usuario / correo
        if (usuario.isEmpty()) {
            binding.tilUsuario.error = getString(R.string.login_error_empty_user)
            esValido = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(usuario).matches()) {
            binding.tilUsuario.error = getString(R.string.login_error_invalid_email)
            esValido = false
        } else {
            binding.tilUsuario.error = null
        }

        // Validación del campo de contraseña
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_error_empty_password)
            esValido = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.login_error_short_password)
            esValido = false
        } else {
            binding.tilPassword.error = null
        }

        if (!esValido) {
            intentosFallidos++
            val restantes = maxIntentos - intentosFallidos
            if (intentosFallidos >= maxIntentos) {
                Toast.makeText(this, getString(R.string.login_error_locked), Toast.LENGTH_LONG).show()
                binding.btnIngresar.isEnabled = false
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.login_error_attempt_warning, intentosFallidos, maxIntentos),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // Validar si el usuario está registrado en el sistema
        if (!RegistroActivity.estaRegistrado(this, usuario)) {
            intentosFallidos++
            binding.tilUsuario.error = getString(R.string.login_error_user_not_found)
            Toast.makeText(this, getString(R.string.login_error_user_not_found), Toast.LENGTH_LONG).show()
            return
        }

        // Validar que la contraseña coincida con la registrada
        if (!RegistroActivity.validarCredenciales(this, usuario, password)) {
            intentosFallidos++
            binding.tilPassword.error = getString(R.string.login_error_wrong_password)
            val restantes = maxIntentos - intentosFallidos
            if (intentosFallidos >= maxIntentos) {
                Toast.makeText(this, getString(R.string.login_error_locked), Toast.LENGTH_LONG).show()
                binding.btnIngresar.isEnabled = false
            } else {
                Toast.makeText(this, getString(R.string.login_error_wrong_password), Toast.LENGTH_LONG).show()
            }
            return
        }

        // Inicio de sesión exitoso
        intentosFallidos = 0
        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()

        // Guardar o eliminar el usuario de SharedPreferences según el checkbox
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_RECORDAR, recordarme)
            if (recordarme) {
                putString(KEY_USUARIO, usuario)
            } else {
                remove(KEY_USUARIO)
            }
            apply()
        }

        // Iniciar BienvenidaActivity pasando el usuario como extra
        val intent = Intent(this, BienvenidaActivity::class.java)
        intent.putExtra("usuario", usuario)
        startActivity(intent)
    }

    /**
     * Limpia los campos de entrada y restablece los errores y el checkbox
     */
    private fun limpiarCampos() {
        binding.edtUsuario.setText("")
        binding.tilUsuario.error = null
        binding.edtPassword.setText("")
        binding.tilPassword.error = null
        binding.chkRecordarme.isChecked = false
        binding.edtUsuario.requestFocus()
    }
}