package com.max.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class WakeService : Service(), TextToSpeech.OnInitListener {

    companion object {

        const val CHANNEL = "max_wake"

        const val MODEL_NAME =
            "vosk-model-small-en-us-0.15"

        const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

        @Volatile
        var running = false

        @Volatile
        private var instance: WakeService? = null

        /**
         * Starts the microphone foreground service.
         *
         * This method is intentionally NOT called from BootReceiver.
         * MainActivity calls it while the app is visible.
         */
        fun start(context: Context) {

            if (
                context.checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException(
                    "RECORD_AUDIO permission is required"
                )
            }

            val intent =
                Intent(
                    context,
                    WakeService::class.java
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                context.startForegroundService(
                    intent
                )
            } else {
                context.startService(intent)
            }
        }

        fun stop(
            context: Context
        ) {
            try {
                context.stopService(
                    Intent(
                        context,
                        WakeService::class.java
                    )
                )
            } catch (_: Exception) {
            }
        }

        fun resume(
            context: Context
        ) {
            instance?.let { service ->

                service.paused = false

                service.main.post {
                    service.beginListening()
                }

                service.note(
                    "Max is listening, say 'max'"
                )
            }
        }

        fun announceCall(
            context: Context,
            number: String
        ) {
            instance?.speak(
                "Master, incoming call from $number. Should I receive it?"
            )
        }

        fun announceSms(
            context: Context,
            sender: String,
            body: String
        ) {
            val short =
                if (body.length > 120) {
                    body.substring(0, 120) + "..."
                } else {
                    body
                }

            instance?.speak(
                "Master, message from $sender. $short"
            )
        }
    }

    private val main =
        Handler(Looper.getMainLooper())

    private var model: Model? = null

    private var speech: SpeechService? = null

    private var tts: TextToSpeech? = null

    private var ttsReady = false

    @Volatile
    private var stopped = false

    @Volatile
    private var paused = false

    @Volatile
    private var preparing = false

    private var lastWake = 0L

    private var restartAttempts = 0

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onCreate() {

        super.onCreate()

        instance = this

        stopped = false
        paused = false
        preparing = false
        restartAttempts = 0

        tts =
            TextToSpeech(
                this,
                this
            )
    }

    override fun onInit(
        status: Int
    ) {

        if (
            status ==
            TextToSpeech.SUCCESS
        ) {

            try {
                tts?.language =
                    Locale.US
            } catch (_: Exception) {
            }

            applyMaleVoice()

            ttsReady = true
        }
    }

    private fun applyMaleVoice() {

        val engine =
            tts ?: return

        try {

            val voices =
                engine.voices
                    ?: emptySet()

            val enVoices =
                voices.filter {
                    it.locale.language == "en"
                }

            val male =
                enVoices.firstOrNull {
                    it.name
                        .lowercase()
                        .contains("male") &&
                        !it.name
                            .lowercase()
                            .contains("female")
                }

            if (male != null) {

                engine.voice = male

            } else {

                val notFemale =
                    enVoices.firstOrNull {
                        !it.name
                            .lowercase()
                            .contains("female")
                    }

                if (notFemale != null) {
                    engine.voice =
                        notFemale
                }
            }

        } catch (_: Exception) {
        }

        try {
            engine.setPitch(0.5f)
            engine.setSpeechRate(0.85f)
        } catch (_: Exception) {
        }
    }

    private fun speak(
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        stopListening()

        main.removeCallbacksAndMessages(null)

        if (
            ttsReady &&
            tts != null
        ) {

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(
                        utteranceId: String?
                    ) {
                    }

                    override fun onDone(
                        utteranceId: String?
                    ) {

                        if (
                            !paused &&
                            !stopped
                        ) {

                            main.postDelayed(
                                {
                                    beginListening()
                                },
                                500
                            )
                        }
                    }

                    override fun onError(
                        utteranceId: String?
                    ) {

                        if (
                            !paused &&
                            !stopped
                        ) {

                            main.postDelayed(
                                {
                                    beginListening()
                                },
                                500
                            )
                        }
                    }
                }
            )

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "announce"
            )

        } else {

            if (
                !paused &&
                !stopped
            ) {

                main.postDelayed(
                    {
                        beginListening()
                    },
                    1200
                )
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (running) {
            return START_STICKY
        }

        /*
         * Check permission BEFORE startForeground().
         *
         * On modern Android the microphone FGS has strict runtime
         * permission requirements.
         */
        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            note(
                "Microphone permission needed"
            )

            stopSelf()

            return START_NOT_STICKY
        }

        createChannel()

        try {

            val notification =
                buildNote(
                    "Max is listening, say 'max'"
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )

            } else {

                startForeground(
                    1,
                    notification
                )
            }

        } catch (e: Exception) {

            running = false

            note(
                "Wake service failed: " +
                    (e.message ?: "unknown error")
            )

            stopSelf()

            return START_NOT_STICKY
        }

        running = true
        stopped = false
        paused = false
        restartAttempts = 0

        if (!preparing) {

            preparing = true

            thread {
                prepareModelAndListen()
            }
        }

        return START_STICKY
    }

    private fun createChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL,
                "Max listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Max voice assistant listening service"
                setShowBadge(false)
            }

        manager.createNotificationChannel(
            channel
        )
    }

    private fun buildNote(
        text: String
    ): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            Notification.Builder(
                this,
                CHANNEL
            )
                .setContentTitle("Max")
                .setContentText(text)
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Max")
                .setContentText(text)
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    private fun note(
        text: String
    ) {

        try {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.notify(
                1,
                buildNote(text)
            )

        } catch (_: Exception) {
        }
    }

    private fun prepareModelAndListen() {

        val dir =
            File(
                filesDir,
                MODEL_NAME
            )

        try {

            if (
                !File(
                    dir,
                    "am"
                ).exists()
            ) {

                note(
                    "Downloading model (40 MB), please wait..."
                )

                downloadModel()
            }

            if (stopped) {
                return
            }

            model =
                Model(
                    dir.absolutePath
                )

            restartAttempts = 0

            main.post {

                preparing = false

                if (
                    !stopped &&
                    !paused
                ) {
                    beginListening()
                }
            }

        } catch (e: Exception) {

            model = null

            dir.deleteRecursively()

            preparing = false

            running = false

            note(
                "Model error: " +
                    (e.message ?: "unknown error")
            )

            main.postDelayed(
                {
                    if (!stopped) {
                        stopSelf()
                    }
                },
                500
            )
        }
    }

    private fun downloadModel() {

        val zipFile =
            File(
                cacheDir,
                "model.zip"
            )

        try {

            val connection =
                URL(MODEL_URL)
                    .openConnection()

            connection.connectTimeout =
                20000

            connection.readTimeout =
                60000

            connection.getInputStream()
                .use { input ->

                    zipFile
                        .outputStream()
                        .use { output ->

                            input.copyTo(
                                output
                            )
                        }
                }

            val base =
                filesDir
                    .canonicalFile
                    .path +
                    File.separator

            ZipInputStream(
                zipFile.inputStream()
            ).use { zip ->

                var entry =
                    zip.nextEntry

                while (entry != null) {

                    if (stopped) {
                        return
                    }

                    val target =
                        File(
                            filesDir,
                            entry.name
                        )

                    val canonical =
                        target
                            .canonicalFile
                            .path

                    if (
                        !canonical.startsWith(
                            base
                        )
                    ) {
                        throw SecurityException(
                            "Invalid model archive path"
                        )
                    }

                    if (entry.isDirectory) {

                        target.mkdirs()

                    } else {

                        target.parentFile
                            ?.mkdirs()

                        target
                            .outputStream()
                            .use { output ->

                                zip.copyTo(
                                    output
                                )
                            }
                    }

                    zip.closeEntry()

                    entry =
                        zip.nextEntry
                }
            }

        } finally {

            try {
                zipFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun beginListening() {

        if (
            stopped ||
            paused ||
            !running
        ) {
            return
        }

        if (speech != null) {
            return
        }

        val m =
            model ?: return

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            note(
                "Microphone permission needed"
            )

            stopSelf()

            return
        }

        try {

            val recognizer =
                Recognizer(
                    m,
                    16000.0f,
                    "[\"max\", \"[unk]\"]"
                )

            val service =
                SpeechService(
                    recognizer,
                    16000.0f
                )

            service.startListening(
                object : RecognitionListener {

                    override fun onPartialResult(
                        hypothesis: String?
                    ) {
                        heard(hypothesis)
                    }

                    override fun onResult(
                        hypothesis: String?
                    ) {
                        heard(hypothesis)
                    }

                    override fun onFinalResult(
                        hypothesis: String?
                    ) {
                    }

                    override fun onError(
                        exception: Exception?
                    ) {

                        speech = null

                        if (
                            !stopped &&
                            !paused
                        ) {

                            scheduleListeningRestart(
                                exception
                            )
                        }
                    }

                    override fun onTimeout() {

                        speech = null

                        if (
                            !stopped &&
                            !paused
                        ) {
                            scheduleListeningRestart(
                                null
                            )
                        }
                    }
                }
            )

            speech = service

            restartAttempts = 0

            note(
                "Max is listening, say 'max'"
            )

        } catch (e: Exception) {

            speech = null

            scheduleListeningRestart(e)
        }
    }

    private fun scheduleListeningRestart(
        error: Exception?
    ) {

        if (
            stopped ||
            paused
        ) {
            return
        }

        restartAttempts =
            (restartAttempts + 1)
                .coerceAtMost(6)

        val delay =
            when (restartAttempts) {
                1 -> 1000L
                2 -> 2000L
                3 -> 4000L
                4 -> 8000L
                5 -> 15000L
                else -> 30000L
            }

        if (restartAttempts == 1) {

            note(
                "Voice listener restarting..."
            )
        }

        main.removeCallbacksAndMessages(
            LISTEN_RESTART_TOKEN
        )

        main.postDelayed(
            {
                if (
                    !stopped &&
                    !paused &&
                    speech == null
                ) {
                    beginListening()
                }
            },
            LISTEN_RESTART_TOKEN,
            delay
        )
    }

    private fun heard(
        hypothesis: String?
    ) {

        if (paused || stopped) {
            return
        }

        if (
            hypothesis.isNullOrBlank()
        ) {
            return
        }

        if (
            !hypothesis
                .lowercase(Locale.US)
                .contains("max")
        ) {
            return
        }

        val now =
            System.currentTimeMillis()

        if (
            now - lastWake < 5000L
        ) {
            return
        }

        lastWake = now

        main.post {
            wake()
        }
    }

    private fun wake() {

        if (
            stopped ||
            paused
        ) {
            return
        }

        paused = true

        stopListening()

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                putExtra(
                    "wake",
                    true
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }

        try {

            startActivity(intent)

        } catch (e: Exception) {

            note(
                "Max couldn't start: " +
                    (e.message ?: "unknown error")
            )

            paused = false

            main.postDelayed(
                {
                    beginListening()
                },
                1500
            )

            return
        }

        /*
         * Safety:
         * If MainActivity doesn't become active within 20 seconds,
         * resume wake-word listening.
         */
        main.postDelayed(
            {
                if (
                    paused &&
                    !MainActivity.isActive &&
                    !stopped
                ) {

                    paused = false

                    beginListening()
                }
            },
            20000L
        )
    }

    private fun stopListening() {

        try {
            speech?.stop()
        } catch (_: Exception) {
        }

        try {
            speech?.shutdown()
        } catch (_: Exception) {
        }

        speech = null
    }

    override fun onDestroy() {

        stopped = true
        running = false
        preparing = false

        main.removeCallbacksAndMessages(
            null
        )

        stopListening()

        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null
        ttsReady = false

        try {
            model?.close()
        } catch (_: Exception) {
        }

        model = null

        instance = null

        super.onDestroy()
    }

    companion object {
        private val LISTEN_RESTART_TOKEN =
            Any()
    }
}
