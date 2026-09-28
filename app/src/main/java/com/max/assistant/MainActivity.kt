package com.max.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val model = "openai/gpt-oss-120b"
    private val systemPrompt =
        "Tum Max ho, user ka assistant max hu. Jawab bahut chhote rakho, 1 se 3 vaakya. " +
        "Agar user Hindi mein bole to Devanagari script mein jawab likho taaki bolkar sunaya ja sake. " +
        "Agar English mein bole to English mein jawab do. Emoji ya formatting symbols mat use karo."

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusView: TextView
    private lateinit var chatView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var keyInput: EditText
    private val history = JSONArray()

    private fun prefs() = getSharedPreferences("max", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0F1115"))
        root.setPadding(40, 90, 40, 40)

        statusView = TextView(this)
        statusView.text = "Max tayyar hai"
        statusView.textSize = 18f
        statusView.setTextColor(Color.WHITE)
        root.addView(statusView)

        keyInput = EditText(this)
        keyInput.hint = "Groq API key yahan paste karo"
        keyInput.setTextColor(Color.WHITE)
        keyInput.setHintTextColor(Color.GRAY)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs().getString("key", ""))
        root.addView(keyInput)

        val saveBtn = Button(this)
        saveBtn.text = "Key save karo"
        saveBtn.setOnClickListener {
            prefs().edit().putString("key", keyInput.text.toString().trim()).apply()
            setStatus("Key save ho gayi")
        }
        root.addView(saveBtn)

        scroll = ScrollView(this)
        chatView = TextView(this)
        chatView.textSize = 16f
        chatView.setTextColor(Color.WHITE)
        chatView.setPadding(0, 30, 0, 30)
        scroll.addView(chatView)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val micBtn = Button(this)
        micBtn.text = "Max se bolo"
        micBtn.textSize = 20f
        micBtn.setOnClickListener { startListening() }
        root.addView(
            micBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun setStatus(msg: String) {
        statusView.text = msg
    }

    private fun appendChat(who: String, msg: String) {
        chatView.append("$who: $msg\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Is phone mein speech recognition nahi mila")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { setStatus("Sun raha hu...") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { setStatus("Sunai nahi diya, dobara dabao") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    setStatus("Sunai nahi diya, dobara dabao")
                } else {
                    askMax(text)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        recognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            setStatus("Mic ki permission chahiye")
        }
    }

    private fun askMax(userText: String) {
        val key = prefs().getString("key", "") ?: ""
        if (key.isBlank()) {
            setStatus("Pehle API key save karo")
            return
        }
        appendChat("Tum", userText)
        history.put(JSONObject().put("role", "user").put("content", userText))
        setStatus("Max soch raha hai...")

        thread {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                val start = maxOf(0, history.length() - 10)
                for (i in start until history.length()) {
                    messages.put(history.get(i))
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("reasoning_effort", "low")

                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    throw Exception("HTTP $code: " + text.take(200))
                }
                val reply = JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                history.put(JSONObject().put("role", "assistant").put("content", reply))
                runOnUiThread {
                    appendChat("Max", reply)
                    setStatus("Max tayyar hai")
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "max")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Error: " + e.message) }
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
