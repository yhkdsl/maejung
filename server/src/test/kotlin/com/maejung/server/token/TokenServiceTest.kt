package com.maejung.server.token

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenServiceTest {
    private val service = TokenService("test-secret-0123456789-0123456789", jacksonObjectMapper())

    @Test
    fun `발급한 차 토큰과 탑승자 토큰을 검증한다`() {
        assertEquals(Principal.Vehicle("v_1"), service.verify(service.issueVehicle("v_1")))
        assertEquals(Principal.Rider("v_1", "r_1"), service.verify(service.issueRider("v_1", "r_1")))
    }

    @Test
    fun `서명을 바꾸거나 다른 비밀키로 만든 토큰은 거부한다`() {
        val token = service.issueVehicle("v_1")
        val tampered = token.dropLast(2) + if (token.endsWith("AA")) "BB" else "AA"
        assertNull(service.verify(tampered))

        val other = TokenService("other-secret-0123456789-0123456789", jacksonObjectMapper())
        assertNull(service.verify(other.issueVehicle("v_1")))
    }

    @Test
    fun `페이로드를 바꿔치기한 토큰은 거부한다`() {
        val rider = service.issueRider("v_1", "r_1")
        val vehicle = service.issueVehicle("v_2")
        val swapped = vehicle.substringBefore(".") + "." + rider.substringAfter(".")
        assertNull(service.verify(swapped))
    }

    @Test
    fun `형식이 틀린 토큰은 거부한다`() {
        assertNull(service.verify(null))
        assertNull(service.verify(""))
        assertNull(service.verify("abc"))
        assertNull(service.verify("a.b.c"))
    }

    @Test
    fun `id는 접두사와 12자다`() {
        val id = TokenService.newId("s")
        assertEquals(14, id.length)
        assertEquals("s_", id.take(2))
    }
}
