package com.maejung.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener

/**
 * 말하기·듣기 모두 Pleos Speech SDK의 차량 내장 엔진(ON_DEVICE, 앱 등록 불필요)을 쓴다(docs/DESIGN.md 6절).
 * - 에뮬레이터에는 안드로이드 기본 TTS 엔진이 없다.
 * - 안드로이드 SpeechRecognizer는 인식 서비스가 응답하지 않았다(준비 콜백조차 없이 10초 시간 초과, 11/07).
 * 말하는 중에 들으면 자기 목소리를 인식하므로 안내가 끝난 뒤 듣는다. 실패하면 null이고, 버튼으로 답하면 된다.
 */
class PleosVoiceIO(context: Context) : VoiceIO {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, Mode.ON_DEVICE)
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()
    private val speaking = MutableStateFlow(false)

    private val stt = SpeechToText(appContext, SttMode.ON_DEVICE)
    private val sttReady = CompletableDeferred<Boolean>()
    @Volatile private var current: CompletableDeferred<String?>? = null

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

        stt.initialize(object : SttInitializeListener {
            override fun onSuccess() {
                stt.setLocale(Locale.KOREA)
                stt.addListener(sttListener)
                sttReady.complete(true)
            }

            override fun onFailure(errorType: SttErrorType) {
                Log.w(TAG, "STT 초기화 실패: $errorType")
                sttReady.complete(false)
            }
        })
    }

    private val sttListener = object : SttResultListener {
        override fun onReady() = Log.i(TAG, "말할 준비 됨").let { }
        override fun onStartedRecognition() = Log.i(TAG, "인식 시작").let { }
        override fun onUpdated(stt: String, completed: Boolean) {
            if (!completed) return
            Log.i(TAG, "인식 결과: $stt")
            current?.complete(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            Log.i(TAG, "인식 종료")
            current?.complete(null)
        }
        override fun onError(errorType: SttErrorType) {
            Log.w(TAG, "음성 인식 실패: $errorType")
            current?.complete(null)
        }
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
        if (withTimeoutOrNull(STT_INIT_WAIT_MS) { sttReady.await() } != true) {
            Log.i(TAG, "STT 사용 불가")
            return null
        }
        // 안내 음성이 끝날 때까지 기다린다(TTS 콜백이 안 와도 최대 8초)
        if (withTimeoutOrNull(SPEAK_WAIT_MS) { speaking.first { !it } } == null) {
            Log.w(TAG, "안내 음성이 8초 안에 끝나지 않아 그냥 듣기 시작")
        }
        delay(300)
        val result = CompletableDeferred<String?>()
        current = result
        Log.i(TAG, "듣기 시작")
        stt.request()
        val text = withTimeoutOrNull(LISTEN_TIMEOUT_MS) { result.await() }
        if (text == null) {
            Log.i(TAG, "듣기 끝: 결과 없음")
            stt.stop()
        }
        current = null
        return text
    }

    override fun release() {
        tts.release()
        stt.removeListener(sttListener)
        stt.release()
    }

    private companion object {
        const val TAG = "PleosVoiceIO"
        const val STT_INIT_WAIT_MS = 5_000L
        const val SPEAK_WAIT_MS = 8_000L
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
