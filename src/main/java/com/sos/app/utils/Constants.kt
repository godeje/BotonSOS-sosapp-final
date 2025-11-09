package com.sos.app.utils

import android.os.Build

object Constants {

  private const val LOCAL_NETWORK_BASE_URL = "https://relay.jegode.com"
  private const val EMULATOR_BASE_URL = "https://relay.jegode.com"

  val API_BASE_URL: String
    get() = if (isProbablyEmulator()) EMULATOR_BASE_URL else LOCAL_NETWORK_BASE_URL

  val STATUS_UPDATE_URL: String
    get() = "$API_BASE_URL/status/update"

  val SOS_ALERT_URL: String
    get() = "$API_BASE_URL/sos"

  private fun isProbablyEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
      Build.FINGERPRINT.startsWith("unknown") ||
      Build.MODEL.contains("google_sdk") ||
      Build.MODEL.contains("Emulator") ||
      Build.MODEL.contains("Android SDK built for x86") ||
      Build.BOARD == "QC_Reference_Phone" ||
      Build.HOST?.startsWith("Build") == true
  }
}
