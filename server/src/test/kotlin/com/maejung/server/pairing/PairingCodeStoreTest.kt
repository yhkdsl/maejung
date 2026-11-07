package com.maejung.server.pairing

import com.maejung.server.MutableClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class PairingCodeStoreTest {
    private val clock = MutableClock()
    private val store = PairingCodeStore(clock)

    @Test
    fun `6자리 코드를 발급하고 한 번 쓰면 사라진다`() {
        val code = store.issue("v_1")
        assertTrue(code.code.matches(Regex("\\d{6}")))
        assertEquals("v_1", store.consume(code.code))
        assertNull(store.consume(code.code))
    }

    @Test
    fun `10분이 지나면 만료된다`() {
        val code = store.issue("v_1")
        clock.advance(Duration.ofMinutes(10))
        assertNull(store.consume(code.code))
    }

    @Test
    fun `10분 전에는 유효하다`() {
        val code = store.issue("v_1")
        clock.advance(Duration.ofMinutes(9).plusSeconds(59))
        assertEquals("v_1", store.consume(code.code))
    }

    @Test
    fun `같은 차가 새 코드를 받으면 이전 코드는 무효가 된다`() {
        val first = store.issue("v_1")
        val second = store.issue("v_1")
        if (first.code != second.code) assertNull(store.consume(first.code))
        assertEquals("v_1", store.consume(second.code))
    }

    @Test
    fun `없는 코드는 null이다`() {
        assertNull(store.consume("abcdef"))
    }
}
