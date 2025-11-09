package com.sos.app.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.*
import com.sos.app.R

object AlertUtils {

    // Reutilizamos recursos para evitar fugas
    private var mediaPlayer: MediaPlayer? = null
    private var flashJob: Job? = null
    private var torchCameraId: String? = null

    /** Dispara sonido + vibración + flash para receptor */
    fun startReceiverSignals(ctx: Context) {
        stopAll(ctx)                       // limpieza previa
        playSound(ctx)                     // 1) sonido corto
        vibrate(ctx, 2000L)                // 2) vibración 2s
        flashTorch(ctx, times = 3, onMs = 200L, offMs = 200L) // 3) flash x3
    }

    /** Detiene todas las señales (CLEAR o silencio local) */
    fun stopAll(ctx: Context) {
        // Sonido
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null

        // Flash
        flashJob?.cancel()
        flashJob = null
        setTorch(ctx, false)
    }

    // ------------------- Internos -------------------

    private fun playSound(ctx: Context) {
        try {
            mediaPlayer = MediaPlayer.create(ctx.applicationContext, R.raw.alert_sound).apply {
                isLooping = false
                setOnCompletionListener {
                    try { it.release() } catch (_: Exception) {}
                    if (mediaPlayer === it) mediaPlayer = null
                }
                start()
            }
        } catch (_: Exception) {
            // Silencioso: si algo falla, no rompemos el flujo
        }
    }

    private fun vibrate(ctx: Context, durationMs: Long) {
        val vib = ctx.getSystemService(Vibrator::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) { }
    }

    private fun flashTorch(ctx: Context, times: Int, onMs: Long, offMs: Long) {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        val camId = torchCameraId ?: findFlashCameraId(cm).also { torchCameraId = it } ?: return

        flashJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            repeat(times) {
                setTorch(cm, camId, true)
                delay(onMs)
                setTorch(cm, camId, false)
                if (it < times - 1) delay(offMs)
            }
        }
    }

    private fun setTorch(ctx: Context, enabled: Boolean) {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        val camId = torchCameraId ?: findFlashCameraId(cm).also { torchCameraId = it } ?: return
        setTorch(cm, camId, enabled)
    }

    private fun setTorch(cm: CameraManager, camId: String, enabled: Boolean) {
        try { cm.setTorchMode(camId, enabled) } catch (_: Exception) { /* sin crash */ }
    }

    private fun findFlashCameraId(cm: CameraManager): String? {
        return try {
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                // Preferimos cámara trasera si existe
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }
}
