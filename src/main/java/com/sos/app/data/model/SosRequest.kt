package com.sos.app.data.model

/**
 * Modelo para envío de alerta SOS
 * Coincide con el endpoint POST /sos
 */


data class SosRequest(
  val alias: String,
  val email: String,
  val contacto: String,   // ✅ Campo obligatorio que faltaba
  val deviceId: String = "android",
  val lat: Double,
  val lon: Double,
  val estado: String = "SOS",
  val ts: Long = System.currentTimeMillis() / 1000
)



