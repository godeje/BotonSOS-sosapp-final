package com.sos.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStorage(context: Context) {

  private val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  private val prefs = EncryptedSharedPreferences.create(
    context,
    PREFS_FILE_NAME,
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )

  fun saveCredentials(email: String, password: String) {
    prefs.edit()
      .putString(KEY_EMAIL, email)
      .putString(KEY_PASSWORD, password)
      .apply()
  }

  fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

  fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

  fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val PREFS_FILE_NAME = "credentials"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
  }
}



