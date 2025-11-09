package com.sos.app.data.model

/**
 * Modelo para registro de usuario
 * Coincide con el endpoint POST /auth/register
 */
data class User(
  val alias: String,
  val email: String,
  val password: String,
  val plan: String = "Individual"
)






