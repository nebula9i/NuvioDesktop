package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStallDetectorTest {

    @Test
    fun `three stalls in a minute trigger once`() {
        val detector = readyDetector()

        assertFalse(stall(detector, startMs = 1_000, endMs = 2_500))
        assertFalse(stall(detector, startMs = 10_000, endMs = 11_500))
        assertTrue(stall(detector, startMs = 20_000, endMs = 21_500))
        assertFalse(stall(detector, startMs = 30_000, endMs = 31_500))
    }

    @Test
    fun `two stalls are not enough`() {
        val detector = readyDetector()

        assertFalse(stall(detector, startMs = 1_000, endMs = 2_500))
        assertFalse(stall(detector, startMs = 10_000, endMs = 11_500))
    }

    @Test
    fun `short rebuffers are ignored`() {
        val detector = readyDetector()

        assertFalse(stall(detector, startMs = 1_000, endMs = 1_500))
        assertFalse(stall(detector, startMs = 10_000, endMs = 10_500))
        assertFalse(stall(detector, startMs = 20_000, endMs = 20_500))
    }

    @Test
    fun `stalls outside the window do not accumulate`() {
        val detector = readyDetector()

        assertFalse(stall(detector, startMs = 1_000, endMs = 2_500))
        assertFalse(stall(detector, startMs = 40_000, endMs = 41_500))
        assertFalse(stall(detector, startMs = 80_000, endMs = 81_500))
    }

    @Test
    fun `seeks do not count as stalls`() {
        val detector = readyDetector()
        detector.onSeek(3_000)

        assertFalse(stall(detector, startMs = 3_100, endMs = 4_500))
        assertFalse(stall(detector, startMs = 10_000, endMs = 11_500))
        assertFalse(stall(detector, startMs = 20_000, endMs = 21_500))
    }

    @Test
    fun `initial load is not counted`() {
        val detector = PlayerStallDetector()

        assertFalse(detector.onLoadingChanged(true, 0))
        assertFalse(detector.onLoadingChanged(false, 1_500))
        detector.onReady()

        assertFalse(stall(detector, startMs = 10_000, endMs = 11_500))
        assertFalse(stall(detector, startMs = 20_000, endMs = 21_500))
        assertTrue(stall(detector, startMs = 30_000, endMs = 31_500))
    }

    @Test
    fun `reset clears the window`() {
        val detector = readyDetector()

        assertFalse(stall(detector, startMs = 1_000, endMs = 2_500))
        assertFalse(stall(detector, startMs = 10_000, endMs = 11_500))
        detector.reset()
        detector.onReady()

        assertFalse(stall(detector, startMs = 20_000, endMs = 21_500))
        assertFalse(stall(detector, startMs = 30_000, endMs = 31_500))
        assertTrue(stall(detector, startMs = 40_000, endMs = 41_500))
    }

    @Test
    fun `the next quality step never goes up`() {
        assertEquals(1440, nextLowerQualityHeight(2160))
        assertEquals(1080, nextLowerQualityHeight(1440))
        assertEquals(720, nextLowerQualityHeight(1080))
        assertEquals(480, nextLowerQualityHeight(720))
        assertEquals(360, nextLowerQualityHeight(480))
        assertNull(nextLowerQualityHeight(360))
    }

    private fun readyDetector(): PlayerStallDetector =
        PlayerStallDetector().also { it.onReady() }

    private fun stall(detector: PlayerStallDetector, startMs: Long, endMs: Long): Boolean {
        detector.onLoadingChanged(isLoading = true, nowMs = startMs)
        return detector.onLoadingChanged(isLoading = false, nowMs = endMs)
    }
}
