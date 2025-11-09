package com.sos.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.sos.app.data.local.SessionManager
import com.sos.app.AlertActivity    // ✅ correcto
import com.sos.app.R              // ✅ necesario para ícono y strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.sos.app.ui.HomeActivity


class MyFirebaseService : FirebaseMessagingService() {

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendTokenToBackendSilently(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_MSG", "Mensaje recibido (data keys=${remoteMessage.data.keys})")

        val data = remoteMessage.data
        val alias = data["alias"]
        val estado = data["estado"]
        val lat = data["lat"]?.toDoubleOrNull()
        val lon = data["lon"]?.toDoubleOrNull()

        if (alias != null && estado != null && lat != null && lon != null) {
            if (estado == "SOS") {
                HomeActivity.updateSosButton(true)
                com.sos.app.utils.AlertUtils.startReceiverSignals(applicationContext)
            } else if (estado == "OK" || estado == "clear") {
                HomeActivity.updateSosButton(false)
                com.sos.app.utils.AlertUtils.stopAll(applicationContext)
            }

            AlertActivity.open(applicationContext, alias, lat, lon, estado)
            sendNotification(alias, estado, lat, lon)
        }

    }

    // ✅ Notificación visible (para dispositivos que no permiten abrir Activity directamente)
    private fun sendNotification(alias: String, estado: String, lat: Double, lon: Double) {
        val channelId = "sos_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal visible para alertas de emergencia"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alias", alias)
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("estado", estado)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ SOS de $alias")
            .setContentText("Toca para abrir la alerta")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun sendTokenToBackendSilently(token: String) {
        try {
            val session = SessionManager(applicationContext)
            val jwt = session.getToken()

            val payload = JSONObject().apply {
                put("token", token)
            }

            val body = payload.toString().toRequestBody(JSON)
            val reqBuilder = Request.Builder()
                .url("http://192.168.100.16:3000/fcm/register")
                .post(body)

            if (!jwt.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $jwt")
            }

            val request = reqBuilder.build()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w("FCM_TOKEN", "Registro token fallo: ${resp.code}")
                        } else {
                            Log.d("FCM_TOKEN", "Token registrado OK")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FCM_TOKEN", "Error registrando token: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("FCM_TOKEN", "sendToken error: ${e.message}")
        }
    }

    companion object {
        fun obtenerToken() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM_TOKEN", "Error al obtener token", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("FCM_TOKEN", "Token actual: $token")
            }
        }
    }
}
