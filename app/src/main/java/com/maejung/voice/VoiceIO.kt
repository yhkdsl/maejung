package com.maejung.voice

/** 음성 안내와 인식. 말하기는 Pleos TTS(ON_DEVICE), 듣기는 SpeechRecognizer(docs/CAR_APP.md 2절) */
interface VoiceIO {
    fun speak(text: String)

    /** 한 번 듣고 인식한 문장을 돌려준다. 실패하거나 지원되지 않으면 null */
    suspend fun listen(): String?

    fun release()
}
