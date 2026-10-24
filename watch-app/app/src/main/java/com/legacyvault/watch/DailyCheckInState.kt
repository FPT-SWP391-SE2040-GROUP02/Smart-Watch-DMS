package com.legacyvault.watch

import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Ngày và quyền điểm danh lấy từ giờ server, không dùng đồng hồ của thiết bị. */
data class DailyCheckInState(val checkedInToday: Boolean, val canCheckIn: Boolean, val refreshAfterMs: Long) {
    companion object {
        fun from(serverTime: String, lastCheckInAt: String?, planStatus: String): DailyCheckInState {
            val vietnam = ZoneOffset.ofHours(7)
            val now = OffsetDateTime.parse(serverTime).withOffsetSameInstant(vietnam)
            val last = lastCheckInAt?.let { OffsetDateTime.parse(it).withOffsetSameInstant(vietnam) }
            val checked = last != null && !last.toLocalDate().isBefore(now.toLocalDate())
            val running = planStatus in listOf("ACTIVE", "CHECKIN_OVERDUE", "AWAITING_CHECK")
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay().atOffset(vietnam)
            val untilMidnight = java.time.Duration.between(now, midnight).toMillis()
            return DailyCheckInState(checked, running && !checked, untilMidnight.coerceIn(1000, 30_000))
        }
    }
}
