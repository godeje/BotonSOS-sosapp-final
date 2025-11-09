package com.sos.app.data.local

import android.content.Context
import android.location.Location

class SessionManager(context: Context) {

  private val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)

  fun saveSession(token: String, alias: String?, email: String?) {
    prefs.edit().apply {
      putString(KEY_TOKEN, token)
      alias?.let { putString(KEY_ALIAS, it) } ?: remove(KEY_ALIAS)
      email?.let { putString(KEY_EMAIL, it) } ?: remove(KEY_EMAIL)
    }.apply()
  }

  fun saveToken(token: String) {
    val currentAlias = getAlias()
    val currentEmail = getEmail()
    saveSession(token, currentAlias, currentEmail)
  }

  fun saveAlias(alias: String) {
    prefs.edit().putString(KEY_ALIAS, alias).apply()
  }

  fun saveEmail(email: String) {
    prefs.edit().putString(KEY_EMAIL, email).apply()
  }

  fun savePlan(plan: String) {
    prefs.edit().putString(KEY_PLAN, plan).apply()
  }

  fun saveKeyword(keyword: String) {
    prefs.edit().putString(KEY_KEYWORD, keyword).apply()
  }

  fun getKeyword(): String? = prefs.getString(KEY_KEYWORD, null)

  fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
  fun getAlias(): String? = prefs.getString(KEY_ALIAS, null)
  fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
  fun getPlan(): String? = prefs.getString(KEY_PLAN, null)

  fun clear() {
    val savedContact = getEmergencyContact() // Guarda el contacto actual
    prefs.edit().clear().apply()
    if (!savedContact.isNullOrEmpty()) {
      // Restaura el contacto de emergencia después del clear
      prefs.edit().putString(KEY_EMERGENCY_CONTACT, savedContact).apply()
    }
  }

  val isLoggedIn: Boolean
    get() = !getToken().isNullOrBlank()

  fun saveEmergencyContact(contact: String) {
    prefs.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply()
  }

  fun getEmergencyContact(): String? = prefs.getString(KEY_EMERGENCY_CONTACT, null)

  fun appendSosHistory(alias: String, location: Location) {
    val existing = prefs.getStringSet(KEY_HISTORY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    val entry = "${System.currentTimeMillis()};$alias;${location.latitude};${location.longitude}"
    existing.add(entry)
    prefs.edit().putStringSet(KEY_HISTORY, existing).apply()
  }

  fun getSosHistory(): List<String> =
    prefs.getStringSet(KEY_HISTORY, emptySet())?.toList() ?: emptyList()

  companion object {
    private const val SESSION_PREFS_NAME = "session"
    private const val KEY_TOKEN = "token"
    private const val KEY_ALIAS = "alias"
    private const val KEY_EMAIL = "email"
    private const val KEY_PLAN = "plan"
    private const val KEY_KEYWORD = "keyword"
    private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
    private const val KEY_HISTORY = "sos_history"
  }
}


