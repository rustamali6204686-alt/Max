package com.max.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class WakeService : Service() {

    companion object {
        const val CHANNEL = "max_wake"
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

        @Volatile
        var running = false

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, WakeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var stopped = false
    private var lastWake = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        createChannel()
        try {
            val n = buildNote("Max sun raha hai, max bolo")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            note("Mic ki permission chahiye")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        stopped = false
        thread { prepareModelAndListen() }
        return START_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Max sunna", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNote(text: String): Notification {
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Max")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun note(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNote(text))
    }

    private fun prepareModelAndListen() {
        val dir = File(filesDir, MODEL_NAME)
        try {
            if (!File(dir, "am").exists()) {
                note("Model download ho raha hai (40 MB), thoda ruko...")
                downloadModel()
            }
            model = Model(dir.absolutePath)
            main.post { beginListening() }
        } catch (e: Exception) {
            dir.deleteRecursively()
            note("Error: " + e.message)
        }
    }

    private fun downloadModel() {
        val zipFile = File(cacheDir, "model.zip")
        val conn = URL(MODEL_URL).openConnection()
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.getInputStream().use { input ->
            zipFile.outputStream().use { out -> input.copyTo(out) }
        }
        val base = filesDir.canonicalPath
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val f = File(filesDir, e.name)
                if (!f.canonicalPath.startsWith(base)) throw Exception("bad zip")
                if (e.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { out -> zin.copyTo(out) }
                }
                e = zin.nextEntry
            }
        }
        zipFile.delete()
    }

    private fun beginListening() {
        if (stopped) return
        val m = model ?: return
        try {
            val rec = Recognizer(m, 16000.0f, "[\"max\", \"[unk]\"]")
            val s = SpeechService(rec, 16000.0f)
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) { heard(hypothesis) }
                override fun onResult(hypothesis: String?) { heard(hypothesis) }
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) { note("Error: " + exception?.message) }
                override fun onTimeout() {}
            })
            speech = s
            note("Max sun raha hai, max bolo")
        } catch (e: Exception) {
            note("Error: " + e.message)
        }
    }

    private fun heard(h: String?) {
        if (h == null || !h.contains("max")) return
        val now = System.currentTimeMillis()
        if (now - lastWake < 20000) return
        lastWake = now
        main.post { wake() }
    }

    private fun wake() {
        stopListening()
        val i = Intent(this, MainActivity::class.java)
        i.putExtra("wake", true)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            note("Max nahi khul paya: " + e.message)
        }
        main.postDelayed({ beginListening() }, 20000)
    }

    private fun stopListening() {
        try {
            speech?.stop()
        } catch (e: Exception) {
        }
        speech = null
    }

    override fun onDestroy() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        stopListening()
        try {
            model?.close()
        } catch (e: Exception) {
        }
        model = null
        running = false
        super.onDestroy()
    }
}
