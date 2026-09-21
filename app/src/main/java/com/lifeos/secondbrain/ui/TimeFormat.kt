package com.lifeos.secondbrain.ui

import com.lifeos.secondbrain.domain.TimeParse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tolerant frontmatter time parsing now lives in [TimeParse] under `domain`, because task visibility
 * rules depend on it and domain logic must not reach forwards into the UI layer. Kept as a thin
 * alias so existing call sites do not churn.
 */
fun parseFlexibleTime(raw: String?): Instant? = TimeParse.instant(raw)

private val friendlyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val friendlyDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Renders a frontmatter time for humans: absolute date when it is old, relative wording when it is
 * recent, because "3 天前" is what a person actually wants to know about their own note.
 */
fun formatWhen(raw: String?): String? {
    val instant = TimeParse.instant(raw) ?: return raw?.takeIf { it.isNotBlank() }
    val zone = ZoneId.systemDefault()
    val moment = instant.atZone(zone)
    val today = LocalDate.now(zone)
    val date = moment.toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> "今天 " + moment.format(friendlyDateTime).substringAfter(' ')
        days == 1L -> "昨天 " + moment.format(friendlyDateTime).substringAfter(' ')
        days in 2..6 -> "$days 天前"
        date.year == today.year -> moment.format(friendlyDate)
        else -> moment.format(friendlyDate)
    }
}
