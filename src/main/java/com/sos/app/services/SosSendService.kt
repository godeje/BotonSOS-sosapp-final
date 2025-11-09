package com.sos.app.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sos.app.data.local.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SosSendService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val sessionManager by lazy { SessionManager(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alias = intent?.getStringExtra(EXTRA_ALIAS) ?: sessionManager.getAlias() ?: "Invitado"
        val email = intent?.getStringExtra(EXTRA_EMAIL) ?: sessionManager.getEmail() ?: "anon@example.com"
        val contacto = sessionManager.getEmergencyContact() ?: ""

        // Inicia foreground (silencioso)
        startForeground(NOTIFICATION_ID, buildSilentNotification())

        scope.launch {
            val location = getCurrentLocation()
            if (location != null) {
                sendSos(alias, email, contacto, location)
            } else {
                Log.w(TAG, "No se pudo obtener ubicación")
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        val fused = LocationServices.getFusedLocationProviderClient(this@SosSendService)
        val token = CancellationTokenSource()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ubicación", e)
            null
        } finally {
            token.cancel()
        }
    }

    // ✅ Envío del SOS directo por WebSocket
    private suspend fun sendSos(alias: String, email: String, contacto: String, location: Location) {
        try {
            val wsUrl = "wss://relay.jegode.com"
            val client = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val request = Request.Builder().url(wsUrl).build()
            val ws = client.newWebSocket(request, object : WebSocketListener() {})

            // 🧩 Registrar al usuario en el canal WS antes de enviar SOS
            val registerJson = JSONObject().apply {
                put("tipo", "register")
                put("email", email)
            }
            ws.send(registerJson.toString())

            // 🕒 Esperar un momento para que el backend procese el registro
            Thread.sleep(500)

            // 🚨 Enviar alerta SOS
            val payload = JSONObject().apply {
                put("alias", alias)
                put("email", email)
                put("contacto", contacto)
                put("lat", location.latitude)
                put("lon", location.longitude)
                put("estado", "SOS")
                put("ts", System.currentTimeMillis())
            }

            ws.send(payload.toString())

            // 🕒 Esperar un segundo para garantizar envío antes de cerrar
            Thread.sleep(1000)

            ws.close(1000, null)

            Log.i(TAG, "✅ SOS enviado por WebSocket: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando SOS vía WS", e)
        }
    }

    private fun buildSilentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Enviando SOS...")
        .setContentText("Obteniendo ubicación…")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SosSendService"
        private const val EXTRA_ALIAS = "extra_alias"
        private const val EXTRA_EMAIL = "extra_email"
        private const val CHANNEL_ID = "sos_silent_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, alias: String?, email: String?) {
            val intent = Intent(context, SosSendService::class.java).apply {
                putExtra(EXTRA_ALIAS, alias)
                putExtra(EXTRA_EMAIL, email)
            }
            context.startForegroundService(intent)
        }
    }
}
