package com.maejung.store

import kotlin.test.Test
import kotlin.test.assertEquals

class FamilyTest {
    private val minji1 = FamilyMember("r_1", "민지")
    private val minji2 = FamilyMember("r_2", "민지")
    private val junyoung = FamilyMember("r_3", "준영")

    @Test
    fun `같은 이름으로 다시 연결하면 예전 항목을 바꾼다`() {
        assertEquals(listOf(junyoung, minji2), listOf(minji1, junyoung).withMember(minji2))
    }

    @Test
    fun `같은 riderId면 이름이 바뀌어도 하나만 남긴다`() {
        val renamed = FamilyMember("r_1", "민지야")
        assertEquals(listOf(renamed), listOf(minji1).withMember(renamed))
    }

    @Test
    fun `다른 사람은 그대로 추가한다`() {
        assertEquals(listOf(minji1, junyoung), listOf(minji1).withMember(junyoung))
    }

    @Test
    fun `저장된 중복 목록은 마지막 연결만 남긴다`() {
        assertEquals(listOf(junyoung, minji2), listOf(minji1, junyoung, minji1, minji2).distinctByName())
    }
}
