package com.maejung.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * 말하기는 Pleos TTS의 차량 내장 엔진(ON_DEVICE). 에뮬레이터에 안드로이드 기본 TTS 엔진이 없다(docs/DESIGN.md 6절).
 * 듣기(SpeechRecognizer)는 실제 발화 테스트 뒤 A6·A8에서 붙인다. 지금은 null을 돌려줘 버튼으로만 답하게 한다.
 */
class PleosVoiceIO(context: Context) : VoiceIO {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                synchronized(pending) {
                    ready = true
                    while (pending.isNotEmpty()) tts.speak(pending.removeFirst())
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS 초기화 실패: $errorType")
            }
        })
    }

    override fun speak(text: String) {
        synchronized(pending) {
            if (ready) tts.speak(text) else pending.addLast(text)
        }
    }

    override suspend fun listen(): String? = null

    override fun release() = tts.release()

    private companion object {
        const val TAG = "PleosVoiceIO"
    }
}
