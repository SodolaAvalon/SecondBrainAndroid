package com.lifeos.secondbrain.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Vault frontmatter is hand-written, so date-ish fields arrive in several shapes: full ISO instants,
 * offset timestamps, local datetimes and bare dates. All of them mean "when did this happen", so they
 * share one tolerant parser instead of each screen re-deriving `Instant.parse` fallbacks.
 *
 * This lives in `domain` rather than `ui` because task visibility rules depend on it, and domain
 * logic must not reach backwards into the UI layer.
 */
object TimeParse {
    fun instant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
    }

    /** The calendar date a frontmatter value falls on, in the device zone. */
    fun date(raw: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        instant(raw)?.atZone(zone)?.toLocalDate()
}
