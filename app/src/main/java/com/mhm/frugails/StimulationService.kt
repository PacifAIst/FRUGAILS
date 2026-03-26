package com.mhm.frugails

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StimulationService : Service() {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var backgroundPlayer: MediaPlayer? = null
    private var dingPlayer: MediaPlayer? = null
    private var flickerThread: Thread? = null
    @Volatile private var isFlickering = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    companion object {
        private val _remainingTime = MutableStateFlow(0)
        val remainingTime = _remainingTime.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_MINUTES = "EXTRA_MINUTES"
        const val EXTRA_AUDIO_ONLY = "EXTRA_AUDIO_ONLY"
        const val EXTRA_FLASHLIGHT_ONLY = "EXTRA_FLASHLIGHT_ONLY"
        const val EXTRA_PLAY_DING = "EXTRA_PLAY_DING"
        const val EXTRA_SLOWER_FLICKER = "EXTRA_SLOWER_FLICKER"
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) { e.printStackTrace() }
        setupMediaPlayers()
    }

    private fun setupMediaPlayers() {
        try {
            val bgFd = assets.openFd("55.mp3")
            backgroundPlayer = MediaPlayer().apply {
                setDataSource(bgFd.fileDescriptor, bgFd.startOffset, bgFd.length)
                isLooping = true
                prepare()
            }
            bgFd.close()
            val dingFd = assets.openFd("ding.mp3")
            dingPlayer = MediaPlayer().apply {
                setDataSource(dingFd.fileDescriptor, dingFd.startOffset, dingFd.length)
                prepare()
            }
            dingFd.close()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 55)
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
                val flashOnly = intent.getBooleanExtra(EXTRA_FLASHLIGHT_ONLY, false)
                val playDing = intent.getBooleanExtra(EXTRA_PLAY_DING, true)
                val slowerFlicker = intent.getBooleanExtra(EXTRA_SLOWER_FLICKER, false)
                startStimulation(minutes, audioOnly, flashOnly, playDing, slowerFlicker)
            }
            ACTION_STOP -> stopStimulation()
        }
        return START_NOT_STICKY
    }

    private fun startStimulation(minutes: Int, audioOnly: Boolean, flashOnly: Boolean, playDing: Boolean, slowerFlicker: Boolean) {
        if (_isRunning.value) return
        _isRunning.value = true
        _remainingTime.value = minutes * 60

        startForegroundServiceNotification()

        if (!flashOnly) backgroundPlayer?.start()
        if (!audioOnly) startFlicker(slowerFlicker)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_remainingTime.value > 0) {
                delay(1000)
                _remainingTime.value -= 1
            }
            if (playDing) {
                dingPlayer?.start()
                delay(1000)
            }
            stopStimulation()
        }
    }

    private fun startFlicker(slowerFlicker: Boolean) {
        if (cameraId == null) return
        isFlickering = true

        flickerThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var isOn = false
            // 40Hz = 12.5ms. 20% slower = 15ms.
            val halfCycleNs = if (slowerFlicker) 15_000_000L else 12_500_000L
            var nextStepTime = System.nanoTime() + halfCycleNs

            while (isFlickering) {
                isOn = !isOn
                try { cameraManager.setTorchMode(cameraId!!, isOn) } catch (e: Exception) {}
                while (System.nanoTime() < nextStepTime && isFlickering) {}
                nextStepTime += halfCycleNs
            }
            try { cameraManager.setTorchMode(cameraId!!, false) } catch (e: Exception) {}
        }.apply { start() }
    }

    private fun stopStimulation() {
        _isRunning.value = false
        isFlickering = false
        flickerThread?.join(500)
        timerJob?.cancel()
        backgroundPlayer?.pause()
        backgroundPlayer?.seekTo(0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "GammaStimChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Stimulation Active", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FRUGAILS is Running")
            .setContentText("Stimulation is active.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        stopStimulation()
        backgroundPlayer?.release()
        dingPlayer?.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}