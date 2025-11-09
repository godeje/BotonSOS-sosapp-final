package com.sos.app.data.model

data class LoginRequest(
  val email: String,
  val password: String
)

data class LoginResponse(
  val ok: Boolean,
  val token: String? = null,
  val alias: String? = null,
  val email: String? = null,
  val message: String? = null
)

data class RegisterRequest(
  val alias: String,
  val email: String,
  val password: String,
  val plan: String = "individual"
)

data class RegisterResponse(
  val token: String?,
  val alias: String? = null,
  val email: String? = null
)

data class ForgotPasswordRequest(
  val email: String
)

data class ApiError(
  val message: String?
)

data class AuthSession(
  val token: String,
  val alias: String?,
  val email: String?
)



