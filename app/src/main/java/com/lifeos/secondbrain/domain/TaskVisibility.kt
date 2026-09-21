package com.lifeos.secondbrain.domain

import java.time.LocalDate

/**
 * Everything that decides whether a task is visible today, and what "completed" means for it.
 *
 * This exists as one place on purpose. Home and Tasks previously each carried their own due-date
 * rule, which had already drifted once; adding recurrence to both would have doubled the bug
 * surface. Any new "is this a today task" call site must go through here.
 *
 * ## Recurring task model
 *
 * A recurring task is a single Markdown file that never becomes `done`. Instead each completion
 * writes `last_completed`, and the file re-qualifies as a today task the next day:
 *
 * ```yaml
 * type: task
 * status: active
 * start: 2026-09-21
 * repeat: daily
 * last_completed: 2026-09-21
 * ```
 *
 * Daily eligibility: `today >= start` and `status != done` and `last_completed != today`.
 */
object TaskVisibility {

    const val DAILY = "daily"

    /** `repeat: daily` is the supported cadence. Blank or unknown values mean "not recurring". */
    fun isRecurring(note: LifeNote): Boolean = repeatCadence(note) != null

    fun repeatCadence(note: LifeNote): String? =
        note.repeat?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it == DAILY }

    /**
     * A recurring task must not be completed until its start date arrives. When `start` is absent the
     * task is treated as having always been available — refusing to show a task because the user
     * omitted a field would be worse than showing it early.
     *
     * `due` is deliberately not consulted: for a one-off task `due` is a deadline, but for a
     * repeating one it would silently mean "the series begins here", which is not what a user writing
     * `repeat: daily` intends.
     */
    fun hasStarted(note: LifeNote, today: LocalDate): Boolean =
        TimeParse.date(note.start)?.let { !today.isBefore(it) } ?: true

    /**
     * Completed on this specific day. For a recurring task this is the only completion state there
     * is, because it never takes `status: done`.
     */
    fun isCompletedOn(note: LifeNote, today: LocalDate): Boolean = if (isRecurring(note)) {
        TimeParse.date(note.lastCompleted) == today
    } else {
        note.status.equals("done", ignoreCase = true)
    }

    /** One-off task whose deadline has arrived. Undated one-off tasks are not "today" tasks. */
    private fun oneOffIsDue(note: LifeNote, today: LocalDate): Boolean =
        TimeParse.date(note.due)?.let { !it.isAfter(today) } ?: false

    /** One-off task due within the next [days]; recurring tasks have no deadline so they never appear. */
    fun isUpcoming(note: LifeNote, today: LocalDate, days: Long = 14): Boolean {
        if (isRecurring(note)) return false
        val due = TimeParse.date(note.due) ?: return false
        return due.isAfter(today) && !due.isAfter(today.plusDays(days))
    }

    /**
     * Should this task appear under "today" right now? This is the single rule the whole app shares.
     */
    fun isTodayTask(note: LifeNote, today: LocalDate): Boolean {
        if (note.status.equals("done", ignoreCase = true)) return false
        return if (isRecurring(note)) {
            hasStarted(note, today) && !isCompletedOn(note, today)
        } else {
            oneOffIsDue(note, today)
        }
    }

    /** Still outstanding in some sense — used for the "全部" segment. */
    fun isOpenTask(note: LifeNote, today: LocalDate): Boolean {
        if (note.status.equals("done", ignoreCase = true)) return false
        return if (isRecurring(note)) hasStarted(note, today) else true
    }

    /**
     * The "完成" list. Recurring tasks are excluded on purpose: they are never done, so including
     * them would add one permanent row for every habit, every day, forever.
     */
    fun isDoneTask(note: LifeNote): Boolean =
        !isRecurring(note) && note.status.equals("done", ignoreCase = true)

    /** Whole days, for a light "已完成" hint on the task itself. */
    fun daysSinceCompletion(note: LifeNote, today: LocalDate): Long? =
        TimeParse.date(note.lastCompleted)?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
}

/**
 * What a completion should write, kept apart from the writing itself so the rule is unit-testable
 * without a database, a Drive client, or a clock.
 *
 * @param changes frontmatter keys to patch. A null value would delete the key, so every entry here
 *   is non-null by construction.
 * @param completedToday what the local optimistic update should reflect.
 */
data class TaskCompletionPlan(
    val changes: Map<String, String>,
    val isRecurring: Boolean,
    val completedToday: Boolean
)

/**
 * Builds the completion plan for a task.
 *
 * One-off tasks keep the original behaviour (`status: done`). Recurring tasks must never take
 * `status: done`, or the habit would disappear after its first completion, so they record the date
 * instead and stay `active`.
 */
fun planTaskCompletion(
    note: LifeNote,
    today: LocalDate,
    timestamp: String
): TaskCompletionPlan {
    val todayIso = today.toString()
    return if (TaskVisibility.isRecurring(note)) {
        TaskCompletionPlan(
            changes = linkedMapOf(
                "status" to "active",
                "last_completed" to todayIso,
                "updated" to timestamp
            ),
            isRecurring = true,
            completedToday = true
        )
    } else {
        TaskCompletionPlan(
            changes = linkedMapOf(
                "status" to "done",
                "updated" to timestamp
            ),
            isRecurring = false,
            completedToday = true
        )
    }
}
