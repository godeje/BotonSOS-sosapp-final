package com.sos.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context   // ← 💥 ESTA LÍNEA ES LA QUE FALTABA
import android.os.Build

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal silencioso existente
            val silentChannel = NotificationChannel(
                "sos_silent_channel",
                "SOS Silent Channel",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Canal silencioso para envío de SOS"
                setSound(null, null)
                enableVibration(false)
            }

            // ✅ Nuevo canal de alerta visible
            val alertChannel = NotificationChannel(
                "sos_alert_channel",
                "Alertas SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal visible para alertas de emergencia"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(silentChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }
}
