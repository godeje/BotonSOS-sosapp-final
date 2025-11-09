package com.sos.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.sos.app.data.local.SessionManager
import com.sos.app.data.remote.AuthRepository
import com.sos.app.data.remote.AuthApi
import com.sos.app.databinding.ActivitySettingsBinding
import com.sos.app.ui.HomeActivity
import com.sos.app.ui.LoginActivity
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

  private lateinit var binding: ActivitySettingsBinding
  private lateinit var session: SessionManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivitySettingsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    session = SessionManager(this)

    // ✅ Mostrar el contacto local y sincronizar con servidor
    bindEmergencyContact()
    loadContactFromServer()

    // Encabezado
    val alias = session.getAlias() ?: "Invitado"
    val plan = session.getPlan() ?: "Individual"
    binding.settingsTitle.text = "Configuración"
    binding.settingsMessage.text = "Alias: $alias  •  Plan: $plan"

    // Palabra clave
    val keywords = listOf(
      "Auxilio por favor",
      "Socorro ayúdenme",
      "Ayuda es urgente",
      "Urge su ayuda",
      "Necesito auxilio ya"
    )
    val keywordGroup = binding.keywordGroup
    keywordGroup.removeAllViews()
    val savedKeyword = session.getKeyword()
    keywords.forEach { k ->
      val radio = RadioButton(this)
      radio.text = k
      radio.isChecked = k == savedKeyword
      radio.setOnClickListener {
        session.saveKeyword(k)
        Toast.makeText(this, "Palabra clave guardada", Toast.LENGTH_SHORT).show()
      }
      keywordGroup.addView(radio)
    }

    // Contacto de emergencia
    binding.btnSaveContact.setOnClickListener {
      val contact = binding.inputContact.text.toString().trim()
      val email = session.getEmail() ?: return@setOnClickListener

      if (contact.isNotEmpty()) {
        session.saveEmergencyContact(contact)
        updateEmergencyContactUi(contact)
        Thread {
          try {
            val url = URL("https://relay.jegode.com/contact/update")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            val json = """{"email":"$email","contacto":"$contact"}"""
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            runOnUiThread {
              if (code == 200)
                Toast.makeText(this, "Contacto sincronizado con el servidor", Toast.LENGTH_SHORT).show()
              else
                Toast.makeText(this, "Error al guardar ($code)", Toast.LENGTH_SHORT).show()
            }
          } catch (e: Exception) {
            runOnUiThread {
              Toast.makeText(this, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        }.start()
      }
    }

    binding.btnDeleteContact.setOnClickListener {
      val email = session.getEmail() ?: return@setOnClickListener
      session.saveEmergencyContact("")
      updateEmergencyContactUi(null)
      Thread {
        try {
          val url = URL("https://relay.jegode.com/contact/delete")
          val conn = url.openConnection() as HttpURLConnection
          conn.requestMethod = "POST"
          conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
          conn.doOutput = true
          val json = """{"email":"$email"}"""
          conn.outputStream.use { it.write(json.toByteArray()) }
          val code = conn.responseCode
          runOnUiThread {
            if (code == 200) {
              binding.inputContact.setText("")
              Toast.makeText(this, "Contacto eliminado del servidor", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(this, "Error al eliminar ($code)", Toast.LENGTH_SHORT).show()
            }
          }
        } catch (e: Exception) {
          runOnUiThread {
            Toast.makeText(this, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
          }
        }
      }.start()
    }

    // Historial
    binding.btnViewHistory.setOnClickListener {
      val list = session.getSosHistory()
      val msg = if (list.isEmpty()) "Sin alertas registradas" else list.joinToString("\n")
      AlertDialog.Builder(this)
        .setTitle("Historial de alertas")
        .setMessage(msg)
        .setPositiveButton("OK", null)
        .show()
    }

    binding.btnClearHistory.setOnClickListener {
      AlertDialog.Builder(this)
        .setTitle("Borrar historial")
        .setMessage("¿Seguro que quieres borrar el historial local?")
        .setPositiveButton("Sí") { _, _ ->
          session.clear()
          Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("No", null)
        .show()
    }

    // Cerrar sesión
    binding.btnLogout.setOnClickListener {
      session.clear()
      startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
      })
      finish()
    }

    binding.btnBackHome.setOnClickListener {
      startActivity(Intent(this, HomeActivity::class.java))
      finish()
    }

    // ✅ Eliminar cuenta del servidor
    binding.btnDeleteAccount.setOnClickListener {
      AlertDialog.Builder(this)
        .setTitle("Eliminar cuenta")
        .setMessage("¿Seguro? Esta acción es irreversible.")
        .setPositiveButton("Eliminar") { _, _ ->
          val email = session.getEmail()
          if (email != null) {
            CoroutineScope(Dispatchers.IO).launch {
              try {
                val retrofit = Retrofit.Builder()
                  .baseUrl("https://relay.jegode.com/")
                  .addConverterFactory(GsonConverterFactory.create())
                  .build()
                val api = retrofit.create(AuthApi::class.java)
                val repo = AuthRepository(api)

                val response = api.deleteAccount(mapOf("email" to email))
                val success = response.isSuccessful

                withContext(Dispatchers.Main) {
                  if (success) {
                    Toast.makeText(this@SettingsActivity, "Cuenta eliminada del servidor", Toast.LENGTH_SHORT).show()
                  } else {
                    Toast.makeText(this@SettingsActivity, "Error al eliminar en el servidor", Toast.LENGTH_SHORT).show()
                  }
                  session.clear()
                  startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                  finish()
                }
              } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                  Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
              }
            }
          } else {
            Toast.makeText(this, "Email no encontrado", Toast.LENGTH_SHORT).show()
          }
        }
        .setNegativeButton("Cancelar", null)
        .show()
    }
  }

  override fun onResume() {
    super.onResume()
    bindEmergencyContact()
  }

  // ✅ Nuevo bloque unificado y funcional
  private fun bindEmergencyContact() {
    val storedContact = session.getEmergencyContact()
    binding.inputContact.setText(storedContact.orEmpty())
    updateEmergencyContactUi(storedContact)
  }

  private fun updateEmergencyContactUi(contact: String?) {
    val hasContact = !contact.isNullOrBlank()
    binding.textEmergencyContact.text = if (hasContact) {
      "Contacto de emergencia: $contact"
    } else {
      "Contacto de emergencia: —"
    }
    binding.textMembers.text = "Miembros conectados: ${if (hasContact) "2/2" else "1/2"}"
  }

  private fun loadContactFromServer() {
    val email = session.getEmail() ?: return
    Thread {
      try {
        val url = URL("https://relay.jegode.com/contact/get")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        val json = """{"email":"$email"}"""
        conn.outputStream.use { it.write(json.toByteArray()) }
        if (conn.responseCode == 200) {
          val response = conn.inputStream.bufferedReader().use { it.readText() }
          val jsonObj = JSONObject(response)
          val contacto = jsonObj.optString("contacto", "")
          runOnUiThread {
            if (!contacto.isNullOrBlank()) {
              session.saveEmergencyContact(contacto)
              binding.inputContact.setText(contacto)
              updateEmergencyContactUi(contacto)
            } else {
              val stored = session.getEmergencyContact()
              binding.inputContact.setText(stored.orEmpty())
              updateEmergencyContactUi(stored)
            }
          }
        }
      } catch (e: Exception) {
        runOnUiThread {
          Toast.makeText(this, "Error al cargar contacto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
      }
    }.start()
  }
}
