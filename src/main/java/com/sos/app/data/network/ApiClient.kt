package com.sos.app.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

object ApiClient {
  private const val BASE_URL = "https://relay.jegode.com/"

  private val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
  }

  // 🚨 TEMPORAL: Confiar en todos los certificados (para probar en Android)
  private val trustAllCerts = arrayOf<TrustManager>(
    object : X509TrustManager {
      override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
      override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
      override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
  )

  private val sslContext = SSLContext.getInstance("SSL").apply {
    init(null, trustAllCerts, java.security.SecureRandom())
  }

  private val client = OkHttpClient.Builder()
    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
    .hostnameVerifier { _, _ -> true }
    .addInterceptor(logging)
    .build()

  val instance: ApiService by lazy {
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
      .create(ApiService::class.java)
  }
}
