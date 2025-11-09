package com.sos.app

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sos.app.databinding.ActivityAlertBinding
import kotlinx.coroutines.*

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var vibrator: Vibrator
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var flashingJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isBannerRed = true
    private val bannerBlinkRunnable = object : Runnable {
        override fun run() {
            toggleBannerColor()
            handler.postDelayed(this, 600L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)!!
        cameraManager = ContextCompat.getSystemService(this, CameraManager::class.java)!!
        cameraId = cameraManager.cameraIdList.firstOrNull()

        val alias = intent.getStringExtra(EXTRA_ALIAS) ?: "Contacto"
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
        val estado = intent.getStringExtra(EXTRA_ESTADO) ?: "SOS"

        if (estado == "CLEAR") {
            stopAlertEffects()
            finish()
            return
        }

        setupBanner(alias)
        setupMapButton(lat, lon)
        setupSilenceButton()
        startAlertEffects()

        // Tocar el banner también silencia
        binding.alertBanner.setOnClickListener { silenceAlert() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlertEffects()
    }

    private fun setupBanner(alias: String) {
        binding.alertTitle.text = "⚠️ ${alias.uppercase()} ESTÁ EN PELIGRO"
        handler.post(bannerBlinkRunnable)
    }

    private fun setupMapButton(lat: Double, lon: Double) {
        binding.openMapsButton.setOnClickListener {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun setupSilenceButton() {
        binding.silenceButton.setOnClickListener { silenceAlert() }
    }

    private fun startAlertEffects() {
        // 🔊 Sonido (usa tu archivo existente)
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound).apply {
            isLooping = true
            start()
        }

        // 📳 Vibración
        val pattern = longArrayOf(0, 400, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }

        // 🔦 Linterna
        flashingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                toggleFlash(true); delay(250)
                toggleFlash(false); delay(250)
            }
        }
    }

    private fun silenceAlert() {
        stopAlertEffects()
        binding.alertBanner.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )
        binding.alertTitle.text = "✅ Alerta atendida localmente"
    }

    private fun stopAlertEffects() {
        handler.removeCallbacks(bannerBlinkRunnable)
        if (this::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        if (this::vibrator.isInitialized) vibrator.cancel()
        flashingJob?.cancel()
        toggleFlash(false)
    }

    private fun toggleBannerColor() {
        val red = ContextCompat.getColor(this, android.R.color.holo_red_dark)
        val dark = ContextCompat.getColor(this, android.R.color.black)
        binding.alertBanner.setBackgroundColor(if (isBannerRed) red else dark)
        isBannerRed = !isBannerRed
    }

    private fun toggleFlash(isOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraId?.let {
                try {
                    cameraManager.setTorchMode(it, isOn)
                } catch (_: Exception) { }
            }
        }
    }

    companion object {
        const val EXTRA_ALIAS = "extra_alias"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_ESTADO = "extra_estado"

        fun open(context: Context, alias: String, lat: Double, lon: Double, estado: String) {
            val intent = Intent(context, AlertActivity::class.java).apply {
                putExtra(EXTRA_ALIAS, alias)
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LON, lon)
                putExtra(EXTRA_ESTADO, estado)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}
