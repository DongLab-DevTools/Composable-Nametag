package com.donglab.compose.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NametagSafetyTest {

    @Before
    fun setUp() = NametagSafety.resetForTest()

    @After
    fun tearDown() = NametagSafety.resetForTest()

    @Test
    fun `guard 안의 예외는 밖으로 나가지 않는다`() {
        NametagSafety.guard("test") { throw IllegalStateException("boom") }

        assertFalse(NametagSafety.broken)
    }

    @Test
    fun `연속 실패가 한도에 닿으면 꺼지고 끄는 훅을 한 번 부른다`() {
        var turnedOff = 0
        NametagSafety.onBroken = { turnedOff++ }

        repeat(NametagSafety.MAX_FAILURES + 2) { NametagSafety.guard("test") { error("boom") } }

        assertTrue(NametagSafety.broken)
        assertEquals(1, turnedOff)
    }

    @Test
    fun `중간에 성공하면 실패 횟수를 다시 센다`() {
        repeat(NametagSafety.MAX_FAILURES - 1) { NametagSafety.guard("test") { error("boom") } }
        NametagSafety.succeeded()
        repeat(NametagSafety.MAX_FAILURES - 1) { NametagSafety.guard("test") { error("boom") } }

        assertFalse(NametagSafety.broken)
    }

    @Test
    fun `꺼진 뒤에는 아무것도 실행하지 않는다`() {
        repeat(NametagSafety.MAX_FAILURES) { NametagSafety.guard("test") { error("boom") } }
        var ran = false

        NametagSafety.guard("test") { ran = true }

        assertFalse(ran)
    }

    @Test
    fun `끄는 훅이 실패해도 밖으로 나가지 않는다`() {
        NametagSafety.onBroken = { error("hook failed") }

        repeat(NametagSafety.MAX_FAILURES) { NametagSafety.guard("test") { error("boom") } }

        assertTrue(NametagSafety.broken)
    }
}
