package com.sos.app.data.network

import com.sos.app.data.model.ApiResponse
import com.sos.app.data.model.LoginRequest
import com.sos.app.data.model.LoginResponse
import com.sos.app.data.model.SosRequest
import com.sos.app.data.model.User
import com.sos.app.data.model.DeleteRequest // ✅ añadido
import retrofit2.Call // ✅ añadido
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

  /**
   * Registra un nuevo usuario
   * Body: { "alias": "Ana", "email": "ana@example.com", "password": "12345678", "plan": "Individual" }
   * Response: { "ok": true, "userId": 123 }
   */
  @POST("/auth/register")
  suspend fun register(@Body user: User): Response<ApiResponse>

  /**
   * Inicia sesión
   * Body: { "email": "ana@example.com", "password": "12345678" }
   * Response: { "ok": true, "token": "JWT..." }
   */
  @POST("/auth/login")
  suspend fun login(@Body credentials: LoginRequest): Response<LoginResponse>

  /**
   * Actualiza estado online/offline
   * Body: { "email": "ana@example.com", "online": true }
   */
  @POST("/status/update")
  suspend fun updateStatus(@Body status: Map<String, Any>): Response<ApiResponse>

  /**
   * Envía alerta SOS
   * Body: { "alias": "Ana", "email": "ana@example.com", "deviceId": "android1", "lat": 20.67, "lon": -103.34, "estado": "SOS", "ts": 1731000000 }
   * Response: { "ok": true, "id": 456 }
   */
  @POST("/sos")
  suspend fun sendSos(@Body sos: SosRequest): Response<ApiResponse>

  /**
   * Elimina cuenta de usuario del servidor
   * Body: { "email": "ana@example.com" }
   * Response: { "ok": true, "msg": "User deleted from database" }
   */
  // ✅ Eliminar cuenta
  @POST("/auth/delete")
  fun deleteAccount(@Body request: DeleteRequest): Call<ApiResponse>
}
