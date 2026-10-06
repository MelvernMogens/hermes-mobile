package id.melvern.hermesmobile.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Voice input: SpeechRecognizer on-device / Google. Teks partial masuk LIVE ke draft
 * (di belakang teks yang sudah ada), hasil final menggantikan partial. Tidak auto-kirim —
 * user cek dulu lalu tekan tombol kirim.
 *
 * Bahasa: id-ID diutamakan, en-US sebagai bahasa tambahan (campur Indo-Inggris).
 */
class VoiceInput internal constructor(private val context: Context) {
    var listening by mutableStateOf(false); private set
    var level by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null)
    private var recognizer: SpeechRecognizer? = null
    private var base = ""
    private var onText: (String) -> Unit = {}

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(currentDraft: String, onText: (String) -> Unit) {
        if (listening) return
        error = null
        this.onText = onText
        base = currentDraft.trimEnd().let { if (it.isEmpty()) "" else "$it " }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                listening = false; level = 0f
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice needs internet"
                    else -> null
                }
            }
            override fun onResults(results: Bundle?) {
                best(results)?.let { onText(base + it) }
                listening = false; level = 0f
            }
            override fun onPartialResults(partial: Bundle?) { best(partial)?.let { onText(base + it) } }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        listening = true
        try { r.startListening(intent) } catch (e: Throwable) {
            listening = false; level = 0f; error = "Voice input unavailable"; return
        }
        // Review fix: recognizer bisa mati tanpa callback → jangan nyangkut "listening" selamanya
        val token = ++session
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (listening && token == session) { recognizer?.cancel(); listening = false; level = 0f }
        }, 60_000)
    }
    private var session = 0

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel(); listening = false; level = 0f
    }

    internal fun destroy() {
        recognizer?.destroy(); recognizer = null; listening = false
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { if (base.isEmpty()) it.uppercase() else it.toString() }
}

@Composable
fun rememberVoiceInput(): VoiceInput {
    val ctx = LocalContext.current
    val v = remember { VoiceInput(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { v.destroy() } }
    return v
}
