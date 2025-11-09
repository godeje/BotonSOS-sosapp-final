package com.sos.app.data.remote

import com.sos.app.data.model.ForgotPasswordRequest
import com.sos.app.data.model.LoginRequest
import com.sos.app.data.model.LoginResponse
import com.sos.app.data.model.RegisterRequest
import com.sos.app.data.model.RegisterResponse
import com.sos.app.data.model.ApiResponse  // ✅ añadido
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.DELETE               // ✅ añadido

interface AuthApi {

  @POST("/auth/login")
  suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

  @POST("/auth/register")
  suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

  @POST("/auth/forgot")
  suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Unit>

  // ✅ NUEVO: eliminar cuenta en servidor
  @POST("/auth/delete")
  suspend fun deleteAccount(@Body request: Map<String, String>): Response<ApiResponse>
}
