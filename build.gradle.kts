import org.gradle.api.JavaVersion

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  // ✅ Añadido: plugin de Google Services para Firebase
  id("com.google.gms.google-services")
}

android {
  namespace = "com.sos.app"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.sos.app"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    viewBinding = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation(libs.material)

  implementation(libs.retrofit2)
  implementation(libs.retrofit2.converter.gson)
  implementation(libs.okhttp3.logging.interceptor)
  implementation(libs.gson)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

  implementation("androidx.security:security-crypto:1.1.0-alpha06")

  // ✅ Añadido: dependencias de Firebase (mensajería + analítica)
  implementation("com.google.firebase:firebase-messaging:24.0.2")
  implementation("com.google.firebase:firebase-analytics:21.5.0")

  // ✅ Añadido: dependencia para obtener ubicación (necesaria para SosSendService)
  implementation("com.google.android.gms:play-services-location:21.3.0")
}
