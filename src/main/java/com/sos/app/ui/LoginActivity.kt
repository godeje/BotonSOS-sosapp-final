package com.sos.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sos.app.R
import com.sos.app.data.local.CredentialStorage
import com.sos.app.data.local.SessionManager
import com.sos.app.data.remote.AuthRepository
import com.sos.app.data.remote.AuthApi
import com.sos.app.data.model.AuthResult
import com.sos.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.sos.app.fcm.MyFirebaseService
import kotlinx.coroutines.CoroutineScope
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLoginBinding
  private lateinit var sessionManager: SessionManager
  private lateinit var credentialStorage: CredentialStorage

  private val retrofit = Retrofit.Builder()
    .baseUrl("https://relay.jegode.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
  private val repository = AuthRepository(retrofit.create(AuthApi::class.java))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    sessionManager = SessionManager(this)
    credentialStorage = CredentialStorage(this)

    if (sessionManager.isLoggedIn) {
      navigateToHome()
      return
    }

    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // 🔥 Obtener token FCM al iniciar la app
    MyFirebaseService.obtenerToken()

    restoreRememberedCredentials()

    binding.loginButton.setOnClickListener { attemptLogin() }
    binding.createAccountButton.setOnClickListener {
      startActivity(Intent(this, RegisterActivity::class.java))
    }
    binding.forgotPasswordButton.setOnClickListener {
      startActivity(Intent(this, ForgotPasswordActivity::class.java))
    }
  }

  override fun onResume() {
    super.onResume()
    if (sessionManager.isLoggedIn) {
      navigateToHome()
    }
  }

  private fun attemptLogin() {
    val email = binding.emailInput.text?.toString()?.trim().orEmpty()
    val password = binding.passwordInput.text?.toString().orEmpty()
    val remember = binding.rememberCheckBox.isChecked

    binding.emailInputLayout.error = null
    binding.passwordInputLayout.error = null

    when {
      !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
        binding.emailInputLayout.error = getString(R.string.error_invalid_email)
        binding.passwordInputLayout.error = null
      }
      password.length < 8 -> {
        binding.passwordInputLayout.error = getString(R.string.error_invalid_password)
      }
      else -> {
        performLogin(email, password, remember)
      }
    }
  }

  private fun performLogin(email: String, password: String, remember: Boolean) {
    setLoading(true)
    lifecycleScope.launch {
      val result = try {
        withContext(Dispatchers.IO) {
          repository.login(email, password)
        }
      } catch (e: Exception) {
        Log.e("API_DEBUG", "💥 Error al conectar: ${e.message}", e)
        setLoading(false)
        Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
        return@launch
      }

      Log.d("API_DEBUG", "📡 Resultado: $result")
      setLoading(false)

      when (result) {
        is AuthResult.Success -> {
          val session = result.data
          sessionManager.saveSession(
            token = session.token,
            alias = session.alias ?: sessionManager.getAlias() ?: email.substringBefore("@"),
            email = session.email ?: email
          )
          if (remember) {
            credentialStorage.saveCredentials(email, password)
            Toast.makeText(this@LoginActivity, getString(R.string.remember_toast_saved), Toast.LENGTH_SHORT).show()
          } else {
            credentialStorage.clear()
          }
          Toast.makeText(this@LoginActivity, "✅ Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

          // 🔁 Sync emergency contact before opening HomeActivity
          try {
            val email = sessionManager.getEmail() ?: ""
            if (email.isNotEmpty()) {
              val url = URL("https://relay.jegode.com/contact/get")
              val conn = url.openConnection() as HttpURLConnection
              conn.requestMethod = "POST"
              conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
              conn.doOutput = true
              val json = """{"email":"$email"}"""
              conn.outputStream.use { it.write(json.toByteArray()) }
              if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("CONTACT_SYNC", "Server response (sync): " + response)
                val jsonObj = org.json.JSONObject(response)
                val contacto = jsonObj.optString("contacto", "")
                if (contacto.isNotEmpty()) {
                  sessionManager.saveEmergencyContact(contacto)
                }
              }
            }
          } catch (e: Exception) {
            android.util.Log.e("CONTACT_SYNC", "Error syncing contact: ${e.message}")
          }

          navigateToHome()

        }
        is AuthResult.Error -> {
          Toast.makeText(this@LoginActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
          Log.e("API_DEBUG", "❌ Error: ${result.message}")
        }
      }
    }
  }

  private fun restoreRememberedCredentials() {
    val rememberedEmail = credentialStorage.getEmail()
    val rememberedPassword = credentialStorage.getPassword()

    if (!rememberedEmail.isNullOrBlank() && !rememberedPassword.isNullOrBlank()) {
      binding.emailInput.setText(rememberedEmail)
      binding.passwordInput.setText(rememberedPassword)
      binding.rememberCheckBox.isChecked = true
    }
  }

  private fun setLoading(isLoading: Boolean) {
    binding.loginProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    binding.loginButton.isEnabled = !isLoading
    binding.loginButton.text = if (isLoading) {
      getString(R.string.logging_in)
    } else {
      getString(R.string.login_button)
    }
  }

  private fun navigateToHome() {
    startActivity(Intent(this, HomeActivity::class.java))
    finish()
  }
}
