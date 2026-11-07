package com.maejung.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KoreanTest {
    @Test
    fun `받침에 따라 조사를 고른다`() {
        assertEquals("민지가", "민지".toSubject())
        assertEquals("준영이가", "준영".toSubject())
        assertEquals("민지에게", "민지".toObject())
        assertEquals("준영이에게", "준영".toObject())
        assertEquals("Mina가", "Mina".toSubject())
        assertEquals("민지와", "민지".toWith())
        assertEquals("준영과", "준영".toWith())
    }

    @Test
    fun `음성 답을 승인·거절로 바꾼다`() {
        assertEquals(VoiceAnswer.ACCEPT, VoiceAnswers.parse("응"))
        assertEquals(VoiceAnswer.ACCEPT, VoiceAnswers.parse("그래 갈게"))
        assertEquals(VoiceAnswer.REJECT, VoiceAnswers.parse("아니"))
        assertEquals(VoiceAnswer.REJECT, VoiceAnswers.parse("지금은 안 갈게"))
        assertEquals(VoiceAnswer.REJECT, VoiceAnswers.parse("못 가"))
    }

    @Test
    fun `알 수 없는 말이나 실패는 무시한다`() {
        assertNull(VoiceAnswers.parse("어디야"))
        assertNull(VoiceAnswers.parse(""))
        assertNull(VoiceAnswers.parse(null))
    }
}
