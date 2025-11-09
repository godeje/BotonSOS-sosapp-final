package com.sos.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sos.app.R
import com.sos.app.data.local.CredentialStorage
import com.sos.app.data.local.SessionManager
import com.sos.app.data.remote.AuthRepository
import com.sos.app.data.remote.AuthApi
import com.sos.app.data.model.AuthResult
import com.sos.app.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.CoroutineScope
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

  private lateinit var binding: ActivityRegisterBinding
  private val retrofit = Retrofit.Builder()
    .baseUrl("https://relay.jegode.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
  private val repository = AuthRepository(retrofit.create(AuthApi::class.java))

  private lateinit var sessionManager: SessionManager
  private lateinit var credentialStorage: CredentialStorage

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRegisterBinding.inflate(layoutInflater)
    setContentView(binding.root)

    sessionManager = SessionManager(this)
    credentialStorage = CredentialStorage(this)

    setupPlanSpinner()

    binding.createAccountButton.setOnClickListener {
      attemptRegister()
    }
  }

  private fun setupPlanSpinner() {
    val plans = resources.getStringArray(R.array.plan_options).toList()
    binding.planSpinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, plans)
    binding.planSpinner.setSelection(0)
    binding.planSpinner.isEnabled = false
    binding.planSpinner.isClickable = false
  }

  private fun attemptRegister() {
    val alias = binding.aliasInput.text?.toString()?.trim().orEmpty()
    val email = binding.registerEmailInput.text?.toString()?.trim().orEmpty()
    val password = binding.registerPasswordInput.text?.toString().orEmpty()

    when {
      alias.isEmpty() -> {
        binding.aliasInputLayout.error = getString(R.string.error_alias_empty)
      }
      !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
        binding.aliasInputLayout.error = null
        binding.registerEmailInputLayout.error = getString(R.string.error_invalid_email)
      }
      password.length < 8 -> {
        binding.aliasInputLayout.error = null
        binding.registerEmailInputLayout.error = null
        binding.registerPasswordInputLayout.error = getString(R.string.error_invalid_password)
      }
      else -> {
        binding.aliasInputLayout.error = null
        binding.registerEmailInputLayout.error = null
        binding.registerPasswordInputLayout.error = null
        performRegister(alias, email, password)
      }
    }
  }

  private fun performRegister(alias: String, email: String, password: String) {
    setLoading(true)
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        repository.register(alias, email, password)
      }

      setLoading(false)

      when (result) {
        is AuthResult.Success -> {
          sessionManager.saveAlias(alias)
          sessionManager.saveEmail(email)
          sessionManager.savePlan("Individual")
          credentialStorage.saveCredentials(email, password)
          performAutoLogin(email, password, alias)
        }
        is AuthResult.Error -> {
          Toast.makeText(this@RegisterActivity, "Error al registrar: ${result.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  private fun performAutoLogin(email: String, password: String, alias: String) {
    setLoading(true)
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        repository.login(email, password)
      }

      setLoading(false)

      when (result) {
        is AuthResult.Success -> {
          val session = result.data
          sessionManager.saveSession(
            token = session.token,
            alias = session.alias ?: alias,
            email = session.email ?: email
          )
          Toast.makeText(this@RegisterActivity, "✅ Registro exitoso. Iniciando sesión...", Toast.LENGTH_SHORT).show()

          CoroutineScope(Dispatchers.IO).launch {
            try {
              val emailSync = sessionManager.getEmail() ?: return@launch
              val url = URL("https://relay.jegode.com/contact/get")
              val conn = url.openConnection() as HttpURLConnection
              conn.requestMethod = "POST"
              conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
              conn.doOutput = true
              val json = """{"email":"$emailSync"}"""
              conn.outputStream.use { it.write(json.toByteArray()) }
              if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                val contacto = jsonObj.optString("contacto", "")
                if (contacto.isNotBlank()) {
                  sessionManager.saveEmergencyContact(contacto)
                }
              }
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }

          navigateToHome()
        }
        is AuthResult.Error -> {
          Toast.makeText(this@RegisterActivity, "Registro exitoso. Por favor inicia sesión.", Toast.LENGTH_SHORT).show()
          navigateToLogin()
        }
      }
    }
  }

  private fun navigateToLogin() {
    startActivity(Intent(this, LoginActivity::class.java))
    finish()
  }

  private fun setLoading(isLoading: Boolean) {
    binding.registerProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    binding.createAccountButton.isEnabled = !isLoading
    binding.createAccountButton.text = if (isLoading) {
      getString(R.string.creating_account)
    } else {
      getString(R.string.register_button)
    }
  }

  private fun navigateToHome() {
    startActivity(Intent(this, HomeActivity::class.java))
    finish()
  }
}
