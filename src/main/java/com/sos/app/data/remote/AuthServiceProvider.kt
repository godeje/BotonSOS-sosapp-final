package com.sos.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthServiceProvider {

  private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
  }

  private val client: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .build()

  private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("${com.sos.app.utils.Constants.API_BASE_URL}/")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

  val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}
