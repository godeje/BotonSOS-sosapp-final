package com.sos.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sos.app.data.local.SessionManager
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SosReceiverService : Service() {

    private var webSocket: WebSocket? = null
    private val sessionManager by lazy { SessionManager(this) }

    override fun onCreate() {
        super.onCreate()
        Log.i("SOS_WS", "🚀 SosReceiverService onCreate ejecutado")
        startForeground(1002, buildSilentNotification())
        connectWebSocket()
    }

    private fun buildSilentNotification(): Notification {
        val channelId = "sos_receiver_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Receiver Channel",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SOS activo")
            .setContentText("Escuchando alertas…")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setSilent(true)
            .build()
    }

    private fun connectWebSocket() {
        val wsUrl = "wss://relay.jegode.com"
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val email = sessionManager.getEmail() ?: "anon@example.com"
                val register = JSONObject().apply {
                    put("tipo", "register")
                    put("email", email)
                }
                ws.send(register.toString())
                Log.i("SOS_WS", "✅ Registrado en canal WS: $email")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.i("SOS_WS", "📩 Mensaje recibido: $text")
                val data = JSONObject(text)
                if (data.optString("estado") == "SOS") {
                    // 🚨 Envía broadcast a la UI
                    sendBroadcast(Intent("SOS_EVENT").putExtra("estado", "SOS"))
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("SOS_WS", "❌ Error WS", t)
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
