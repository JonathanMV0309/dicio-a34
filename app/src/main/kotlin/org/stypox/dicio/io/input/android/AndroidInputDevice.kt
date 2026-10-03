package org.stypox.dicio.io.input.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import java.util.Locale

/** Uses Android's configured speech provider (Google, Samsung, or WhisperIME).
 * All SpeechRecognizer operations must run on the Android main thread.
 */
class AndroidInputDevice(
    private val context: Context,
    private val localeManager: LocaleManager,
) : SttInputDevice {
    private val handler = Handler(Looper.getMainLooper())
    private val state = MutableStateFlow<SttState>(SttState.Loaded)
    override val uiState: StateFlow<SttState> = state
    private var recognizer: SpeechRecognizer? = null
    private var callback: ((InputEvent) -> Unit)? = null
    private var generation = 0L
    @Volatile private var destroyed = false

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        if (destroyed) return false
        thenStartListeningEventListener?.let { start(it) }
        return true
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) = start(eventListener)

    private fun finish(event: InputEvent) {
        val listener = callback
        callback = null
        generation++
        recognizer?.destroy()
        recognizer = null
        state.value = SttState.Loaded
        listener?.invoke(event)
    }

    private fun start(listener: (InputEvent) -> Unit) {
        handler.post {
            if (destroyed) return@post
            if (callback != null) finish(InputEvent.None)
            callback = listener
            val token = ++generation
            try {
                check(SpeechRecognizer.isRecognitionAvailable(context)) {
                    "No speech recognition provider is installed"
                }
                val selected = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
                    ?.let { ComponentName.unflattenFromString(it) }
                check(selected?.packageName != context.packageName) {
                    "Select Google, Samsung or Whisper as the Android speech provider, not Dicio itself"
                }
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer!!.setRecognitionListener(object : RecognitionListener {
                    private fun current() = token == generation && !destroyed
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (current()) state.value = SttState.Listening
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        if (current()) state.value = SttState.WaitingForResult
                    }
                    override fun onError(error: Int) {
                        if (!current()) return
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            finish(InputEvent.None)
                        } else {
                            finish(InputEvent.Error(IllegalStateException("Android speech error: $error")))
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        if (!current()) return
                        val alternatives = RecognitionResults.prepare(
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(),
                            results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
                        )
                        finish(if (alternatives.isEmpty()) InputEvent.None else InputEvent.Final(alternatives))
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        if (current()) partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.let { callback?.invoke(InputEvent.Partial(it)) }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val language = localeManager.locale.value
                // Preserve the phone's Spanish region rather than selecting an arbitrary country.
                val system = Locale.getDefault()
                val tag = if (language.country.isEmpty() && language.language == system.language)
                    system.toLanguageTag() else language.toLanguageTag()
                state.value = SttState.WaitingForResult
                recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                })
            } catch (e: Exception) {
                finish(InputEvent.Error(e))
            }
        }
    }

    override fun stopListening() {
        handler.post {
            if (callback != null) {
                recognizer?.cancel()
                finish(InputEvent.None)
            }
        }
    }

    override suspend fun destroy() {
        destroyed = true
        handler.post {
            if (callback != null) finish(InputEvent.None)
            else { recognizer?.destroy(); recognizer = null }
        }
    }
}
