package com.sos.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import com.sos.app.R
import com.sos.app.SettingsActivity
import com.sos.app.data.local.SessionManager
import com.sos.app.data.model.SosRequest
import com.sos.app.data.network.ApiClient
import com.sos.app.databinding.ActivityHomeBinding
import com.sos.app.services.SosSendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

  private lateinit var binding: ActivityHomeBinding
  private lateinit var sessionManager: SessionManager
  private val apiService = ApiClient.instance
  private val vibrator by lazy { getSystemService<Vibrator>() }

  private lateinit var pulseInactiveAnimation: Animation
  private lateinit var pulseActiveAnimation: Animation

  private var isSosActivated = false
  private var pollingJob: Job? = null

  // ✅ WebSocket
  private lateinit var webSocket: WebSocket
  private val client = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
  private val wsUrl = "wss://relay.jegode.com"

  // ✅ Permisos de ubicación
  private val locationPermission = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
  )
  private val REQUEST_LOCATION = 501
  private var pendingLocationAction: (() -> Unit)? = null

  // ✅ Receptor local de eventos SOS
  private lateinit var sosReceiver: BroadcastReceiver

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sessionManager = SessionManager(this)
    if (!sessionManager.isLoggedIn) {
      navigateToLogin()
      return
    }

    binding = ActivityHomeBinding.inflate(layoutInflater)
    setContentView(binding.root)
    instance = this

    pulseInactiveAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_inactive)
    pulseActiveAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_active)

    binding.settingsButton.setOnClickListener {
      startActivity(Intent(this, SettingsActivity::class.java))
    }

    binding.sosButton.setOnClickListener {
      setSosState(!isSosActivated, triggeredByUser = true)
    }

    binding.sosButton.setOnLongClickListener {
      Toast.makeText(this, "Verificando conexión...", Toast.LENGTH_SHORT).show()
      lifecycleScope.launch {
        try {
          val response = withContext(Dispatchers.IO) {
            apiService.sendSos(
              SosRequest(
                alias = sessionManager.getAlias() ?: "Invitado",
                email = sessionManager.getEmail() ?: "anon@example.com",
                contacto = sessionManager.getEmergencyContact() ?: "",
                deviceId = "android-${Build.MODEL}",
                lat = 0.0,
                lon = 0.0,
                estado = "ping",
                ts = System.currentTimeMillis()
              )
            )
          }
          if (response.isSuccessful && response.body()?.ok == true) {
            Toast.makeText(this@HomeActivity, "Servidor activo ✅", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@HomeActivity, "Servidor sin respuesta ❌", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          Toast.makeText(this@HomeActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
      }
      true
    }

    connectWebSocket()

    // ✅ Escucha broadcast de SOS_EVENT
    sosReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        val estado = intent?.getStringExtra("estado")
        if (estado == "SOS") {
          updateSosButton(true)
          vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
      }
    }
    LocalBroadcastManager.getInstance(this)
      .registerReceiver(sosReceiver, IntentFilter("SOS_EVENT"))

    binding.sosButton.startAnimation(pulseInactiveAnimation)
    updateHeader()
  }

  // ✅ Conexión WebSocket
  private fun connectWebSocket() {
    val request = Request.Builder().url(wsUrl).build()
    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        runOnUiThread {
          Toast.makeText(this@HomeActivity, "🔗 WS conectado", Toast.LENGTH_SHORT).show()
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        try {
          val json = JSONObject(text)
          when (json.optString("tipo")) {
            "alerta" -> {
              updateSosButton(true)
              com.sos.app.utils.AlertUtils.startReceiverSignals(this@HomeActivity)
            }
            "clear" -> {
              updateSosButton(false)
              com.sos.app.utils.AlertUtils.stopAll(this@HomeActivity)
            }
            else -> {
              val estado = json.optString("estado")
              if (estado == "SOS") {
                updateSosButton(true)
                com.sos.app.utils.AlertUtils.startReceiverSignals(this@HomeActivity)
              }
              if (estado == "OK") {
                updateSosButton(false)
                com.sos.app.utils.AlertUtils.stopAll(this@HomeActivity)
              }
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        runOnUiThread {
          Toast.makeText(this@HomeActivity, "❌ WS cerrado", Toast.LENGTH_SHORT).show()
        }
        reconnectWebSocket()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        runOnUiThread {
          Toast.makeText(this@HomeActivity, "⚠️ Error WS: ${t.message}", Toast.LENGTH_SHORT).show()
        }
        reconnectWebSocket()
      }
    }

    webSocket = client.newWebSocket(request, listener)
  }

  private fun reconnectWebSocket() {
    lifecycleScope.launch {
      delay(5000)
      connectWebSocket()
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_LOCATION && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
      pendingLocationAction?.invoke()
    } else {
      Toast.makeText(this, "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show()
    }
    pendingLocationAction = null
  }

  private fun ensureLocationPermission(onGranted: () -> Unit) {
    val granted = locationPermission.all {
      ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    if (granted) {
      onGranted()
    } else {
      ActivityCompat.requestPermissions(this, locationPermission, REQUEST_LOCATION)
      pendingLocationAction = onGranted
    }
  }

  override fun onResume() {
    super.onResume()
    if (!sessionManager.isLoggedIn) {
      navigateToLogin()
      return
    }
    updateHeader()
    if (!isSosActivated) {
      binding.sosButton.startAnimation(pulseInactiveAnimation)
    }
  }

  private fun updateHeader() {
    val alias = sessionManager.getAlias()?.takeIf { it.isNotBlank() } ?: "Invitado"
    val plan = sessionManager.getPlan() ?: "Individual"
    val connectedMembers = if (sessionManager.getEmergencyContact().isNullOrBlank()) 1 else 2
    binding.userText.text = "Usuario: $alias"
    binding.planText.text = "Plan: $plan"
    binding.membersText.text = "Miembros conectados: $connectedMembers / 2"
  }

  // ✅ Sincronización visual completa con vibración
  private fun setSosState(active: Boolean, triggeredByUser: Boolean) {
    if (active == isSosActivated && triggeredByUser) return
    isSosActivated = active
    if (active) {
      // 🟢 EMISOR: solo cambia de verde a rojo sin parpadeo
      binding.sosButton.setBackgroundResource(R.drawable.sos_active)
      binding.sosButton.clearAnimation()
      vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))

      if (triggeredByUser) {
        ensureLocationPermission {
          SosSendService.start(this, sessionManager.getAlias(), sessionManager.getEmail())
        }
      }
    } else {
      binding.sosButton.setBackgroundResource(R.drawable.sos_inactive)
      binding.sosButton.clearAnimation()
      binding.sosButton.startAnimation(pulseInactiveAnimation)
    }
  }

  private fun navigateToLogin() {
    startActivity(Intent(this, LoginActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(sosReceiver)
      webSocket.close(1000, null)
    } catch (_: Exception) {}
  }

  companion object {
    private var instance: HomeActivity? = null

    fun updateSosButton(isActive: Boolean) {
      instance?.runOnUiThread {
        val ctx = instance ?: return@runOnUiThread
        val sosButton = ctx.binding.sosButton
        val pulseInactive = ctx.pulseInactiveAnimation
        val estroboAnim = AnimationUtils.loadAnimation(ctx, R.anim.estrobo)

        if (isActive) {
          // 🔴 RECEPTOR: cambia a rojo y activa parpadeo (estrobo)
          sosButton.setBackgroundResource(R.drawable.sos_active)
          sosButton.clearAnimation()
          sosButton.startAnimation(estroboAnim)
        } else {
          // 🟢 Receptor vuelve a verde y detiene parpadeo
          sosButton.setBackgroundResource(R.drawable.sos_inactive)
          sosButton.clearAnimation()
          sosButton.startAnimation(pulseInactive)
        }
      }
    }
  }
}
