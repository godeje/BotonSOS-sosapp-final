package com.sos.app.data.model

sealed class AuthResult<out T> {
  data class Success<T>(val data: T) : AuthResult<T>()
  data class Error(val message: String) : AuthResult<Nothing>()
}



