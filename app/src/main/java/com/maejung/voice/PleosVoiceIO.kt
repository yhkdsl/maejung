package com.maejung.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 말하기: Pleos TTS의 차량 내장 엔진(ON_DEVICE). 에뮬레이터에 안드로이드 기본 TTS 엔진이 없다(docs/DESIGN.md 6절).
 * 듣기: SpeechRecognizer. 말하는 중에 들으면 자기 목소리를 인식하므로 말이 끝난 뒤 듣는다.
 * 실제 음성 인식은 사람이 말해야 확인할 수 있어 아직 검증 전이다(TASKS.md A8). 실패하면 null이고, 버튼으로 답하면 된다.
 */
class PleosVoiceIO(context: Context) : VoiceIO {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, Mode.ON_DEVICE)
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()
    private val speaking = MutableStateFlow(false)

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.addEventListener(object : EventListener {
                    // onReady는 발화가 끝날 때마다 다시 오므로 여기서 speak하면 무한 반복된다
                    override fun onReady() = Unit
                    override fun onStart() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() {
                        speaking.value = false
                    }
                    override fun onStop() {
                        speaking.value = false
                    }
                    override fun onError(errorType: ErrorType) {
                        Log.w(TAG, "TTS 오류: $errorType")
                        speaking.value = false
                    }
                })
                synchronized(pending) {
                    ready = true
                    while (pending.isNotEmpty()) say(pending.removeFirst())
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS 초기화 실패: $errorType")
            }
        })
    }

    override fun speak(text: String) {
        synchronized(pending) {
            if (ready) say(text) else pending.addLast(text)
        }
    }

    private fun say(text: String) {
        speaking.value = true
        tts.speak(text)
    }

    override suspend fun listen(): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return null
        // 안내 음성이 끝날 때까지 기다린다(TTS가 멈춰 콜백이 안 와도 최대 8초)
        withTimeoutOrNull(SPEAK_WAIT_MS) { speaking.first { !it } }
        delay(300)
        return withContext(Dispatchers.Main) { withTimeoutOrNull(LISTEN_TIMEOUT_MS) { recognizeOnce() } }
    }

    private suspend fun recognizeOnce(): String? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                Log.i(TAG, "인식 결과: $text")
                recognizer.destroy()
                if (cont.isActive) cont.resume(text)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "음성 인식 실패: $error")
                recognizer.destroy()
                if (cont.isActive) cont.resume(null)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        cont.invokeOnCancellation { recognizer.destroy() }
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"),
        )
    }

    override fun release() = tts.release()

    private companion object {
        const val TAG = "PleosVoiceIO"
        const val SPEAK_WAIT_MS = 8_000L
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
