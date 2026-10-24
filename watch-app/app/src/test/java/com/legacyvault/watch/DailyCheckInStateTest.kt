package com.legacyvault.watch

import org.junit.Assert.*
import org.junit.Test

class DailyCheckInStateTest {
    @Test fun sameVietnamDayStaysLockedAcrossUtcMidnight() {
        val state = DailyCheckInState.from("2026-10-24T08:00:00Z", "2026-10-23T18:00:00Z", "ACTIVE")
        assertTrue(state.checkedInToday)
        assertFalse(state.canCheckIn)
    }

    @Test fun vietnamMidnightUnlocksWithinOneSecond() {
        val state = DailyCheckInState.from("2026-10-24T17:00:00Z", "2026-10-24T16:59:59Z", "ACTIVE")
        assertFalse(state.checkedInToday)
        assertTrue(state.canCheckIn)
    }

    @Test fun pausedPlanNeverAllowsCheckIn() {
        assertFalse(DailyCheckInState.from("2026-10-24T08:00:00Z", null, "DEATH_CASE_OPEN").canCheckIn)
    }

    @Test fun refreshAtMidnightInsteadOfWaitingThirtySeconds() {
        val state = DailyCheckInState.from("2026-10-24T16:59:55Z", null, "ACTIVE")
        assertEquals(5000L, state.refreshAfterMs)
    }
}
