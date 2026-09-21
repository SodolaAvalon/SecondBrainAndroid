package com.lifeos.secondbrain.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Vault frontmatter is hand-written, so date-ish fields arrive in several shapes: full ISO instants,
 * offset timestamps, local datetimes and bare dates. All of them mean "when did this happen", so they
 * share one tolerant parser instead of each screen re-deriving `Instant.parse` fallbacks.
 */
fun parseFlexibleTime(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
}

private val friendlyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val friendlyDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Renders a frontmatter time for humans: absolute date when it is old, relative wording when it is
 * recent, because "3 天前" is what a person actually wants to know about their own note.
 */
fun formatWhen(raw: String?): String? {
    val instant = parseFlexibleTime(raw) ?: return raw?.takeIf { it.isNotBlank() }
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
