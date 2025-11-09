package com.sos.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sos.app.data.model.ApiError
import com.sos.app.data.model.AuthResult
import com.sos.app.data.model.ForgotPasswordRequest
import com.sos.app.data.model.LoginRequest
import com.sos.app.data.model.RegisterRequest
import java.io.IOException
import com.sos.app.data.model.AuthSession

class AuthRepository(
  private val api: AuthApi,
  private val gson: Gson = Gson()
) {

  suspend fun login(email: String, password: String): AuthResult<AuthSession> {
    return try {
      val response = api.login(LoginRequest(email, password))
      if (response.isSuccessful) {
        val body = response.body()
        val token = body?.token
        if (!token.isNullOrBlank()) {
          AuthResult.Success(
            AuthSession(
              token = token,
              alias = body?.alias,
              email = body?.email ?: email
            )
          )
        } else {
          AuthResult.Error("Missing token in response")
        }
      } else {
        AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
      }
    } catch (io: IOException) {
      AuthResult.Error("Network error: ${io.localizedMessage ?: "unknown"}")
    } catch (t: Throwable) {
      AuthResult.Error(t.localizedMessage ?: "Unexpected error")
    }
  }

  suspend fun register(alias: String, email: String, password: String): AuthResult<AuthSession> {
    return try {
      val response = api.register(RegisterRequest(alias, email, password))
      if (response.isSuccessful) {
        val body = response.body()
        val token = body?.token
        if (!token.isNullOrBlank()) {
          AuthResult.Success(
            AuthSession(
              token = token,
              alias = body?.alias ?: alias,
              email = body?.email ?: email
            )
          )
        } else {
          AuthResult.Error("Missing token in response")
        }
      } else {
        AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
      }
    } catch (io: IOException) {
      AuthResult.Error("Network error: ${io.localizedMessage ?: "unknown"}")
    } catch (t: Throwable) {
      AuthResult.Error(t.localizedMessage ?: "Unexpected error")
    }
  }

  suspend fun forgotPassword(email: String): AuthResult<Unit> {
    return try {
      val response = api.forgotPassword(ForgotPasswordRequest(email))
      if (response.isSuccessful) {
        AuthResult.Success(Unit)
      } else {
        AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
      }
    } catch (io: IOException) {
      AuthResult.Error("Network error: ${io.localizedMessage ?: "unknown"}")
    } catch (t: Throwable) {
      AuthResult.Error(t.localizedMessage ?: "Unexpected error")
    }
  }

  private fun parseErrorMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return "Request failed"
    return try {
      gson.fromJson(raw, ApiError::class.java)?.message ?: "Request failed"
    } catch (_: JsonSyntaxException) {
      raw
    }
  }

  // ✅ NUEVO: elimina la cuenta del servidor por email
  suspend fun deleteAccount(email: String): Boolean {
    return try {
      val url = java.net.URL("https://relay.jegode.com/auth/delete")
      val conn = url.openConnection() as java.net.HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      conn.doOutput = true

      val json = """{"email":"$email"}"""
      conn.outputStream.use { it.write(json.toByteArray()) }

      conn.responseCode == 200
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}
