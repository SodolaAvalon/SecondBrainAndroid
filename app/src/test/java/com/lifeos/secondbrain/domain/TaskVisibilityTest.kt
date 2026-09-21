package com.lifeos.secondbrain.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Recurrence rules and completion semantics.
 *
 * These are the acceptance criteria for recurring tasks expressed as tests, including the
 * "next day it comes back" case, which is simulated by moving [LocalDate] rather than by waiting.
 */
class TaskVisibilityTest {

    private val start = LocalDate.of(2026, 9, 21)
    private val day1 = LocalDate.of(2026, 9, 21)
    private val day2 = LocalDate.of(2026, 9, 22)

    /** Mirrors the fixture from the task description: a daily task starting 2026-09-21. */
    private fun dailyTask(
        status: String? = "active",
        startDate: String? = start.toString(),
        lastCompleted: String? = null,
        due: String? = null
    ) = LifeNote(
        fileId = "daily-1",
        name = "每日规则测试.md",
        path = "Tasks/每日规则测试.md",
        type = NoteType.TASK,
        status = status,
        title = "每日规则测试",
        summary = null,
        body = "# 每日规则测试",
        created = null,
        updated = null,
        due = due,
        project = null,
        priority = null,
        source = null,
        processed = null,
        tags = emptyList(),
        modifiedTime = null,
        md5Checksum = null,
        start = startDate,
        repeat = "daily",
        lastCompleted = lastCompleted
    )

    private fun oneOffTask(status: String? = "active", due: String? = null) = LifeNote(
        fileId = "one-off",
        name = "一次性.md",
        path = null,
        type = NoteType.TASK,
        status = status,
        title = "一次性任务",
        summary = null,
        body = "",
        created = null,
        updated = null,
        due = due,
        project = null,
        priority = null,
        source = null,
        processed = null,
        tags = emptyList(),
        modifiedTime = null,
        md5Checksum = null
    )

    // ---- recognition -------------------------------------------------------

    @Test
    fun `daily repeat is recognised`() {
        assertTrue(TaskVisibility.isRecurring(dailyTask()))
        assertEquals("daily", TaskVisibility.repeatCadence(dailyTask()))
    }

    @Test
    fun `unknown or missing cadence is not recurring`() {
        assertFalse(TaskVisibility.isRecurring(oneOffTask()))
        assertFalse(TaskVisibility.isRecurring(dailyTask().copy(repeat = "weekly")))
        assertFalse(TaskVisibility.isRecurring(dailyTask().copy(repeat = "  ")))
        assertNull(TaskVisibility.repeatCadence(oneOffTask()))
    }

    // ---- day 1: appears ----------------------------------------------------

    @Test
    fun `daily task appears on its start date`() {
        assertTrue(TaskVisibility.isTodayTask(dailyTask(), day1))
    }

    @Test
    fun `daily task stays hidden before its start date`() {
        assertFalse(TaskVisibility.isTodayTask(dailyTask(), start.minusDays(1)))
    }

    @Test
    fun `daily task without a start date is available immediately`() {
        assertTrue(TaskVisibility.isTodayTask(dailyTask(startDate = null), day1))
    }

    // ---- after completion --------------------------------------------------

    @Test
    fun `daily task disappears for the rest of the day once completed`() {
        val completed = dailyTask(lastCompleted = day1.toString())

        assertTrue(TaskVisibility.isCompletedOn(completed, day1))
        assertFalse(TaskVisibility.isTodayTask(completed, day1))
    }

    @Test
    fun `daily task returns the next day`() {
        val completedYesterday = dailyTask(lastCompleted = day1.toString())

        // Same Markdown file, no new file, no status change — only the date moved.
        assertFalse(TaskVisibility.isCompletedOn(completedYesterday, day2))
        assertTrue(TaskVisibility.isTodayTask(completedYesterday, day2))
    }

    @Test
    fun `daily task never counts as a permanently finished task`() {
        assertFalse(TaskVisibility.isDoneTask(dailyTask(status = "active")))
        // Even a stray status: done must not push a habit into the completed list.
        assertFalse(TaskVisibility.isDoneTask(dailyTask(status = "done")))
    }

    @Test
    fun `daily task with status done is not surfaced as a today task`() {
        assertFalse(TaskVisibility.isTodayTask(dailyTask(status = "done"), day1))
    }

    @Test
    fun `daily task appears in the open list once started`() {
        val completedYesterday = dailyTask(lastCompleted = day1.toString())

        assertTrue(TaskVisibility.isOpenTask(completedYesterday, day2))
        assertFalse(TaskVisibility.isOpenTask(dailyTask(), start.minusDays(1)))
    }

    @Test
    fun `daily task is never an upcoming task`() {
        // It has no deadline, so it must not be pulled into the 近期 segment by a due date.
        assertFalse(TaskVisibility.isUpcoming(dailyTask(due = day2.toString()), day1))
    }

    // ---- one-off tasks keep their old behaviour ----------------------------

    @Test
    fun `one-off task is a today task once its due date arrives`() {
        assertTrue(TaskVisibility.isTodayTask(oneOffTask(due = day1.toString()), day1))
        assertTrue(TaskVisibility.isTodayTask(oneOffTask(due = day1.minusDays(3).toString()), day1))
    }

    @Test
    fun `one-off task without a due date is not a today task`() {
        assertFalse(TaskVisibility.isTodayTask(oneOffTask(), day1))
    }

    @Test
    fun `one-off task due later is upcoming, not today`() {
        val note = oneOffTask(due = day2.toString())

        assertFalse(TaskVisibility.isTodayTask(note, day1))
        assertTrue(TaskVisibility.isUpcoming(note, day1))
        assertFalse(TaskVisibility.isUpcoming(oneOffTask(due = day1.plusDays(30).toString()), day1))
    }

    @Test
    fun `completed one-off task lands in the done list`() {
        assertTrue(TaskVisibility.isDoneTask(oneOffTask(status = "done")))
        assertFalse(TaskVisibility.isTodayTask(oneOffTask(status = "done", due = day1.toString()), day1))
    }

    @Test
    fun `status comparison is case insensitive`() {
        assertTrue(TaskVisibility.isDoneTask(oneOffTask(status = "DONE")))
    }

    // ---- completion plans --------------------------------------------------

    @Test
    fun `completing a daily task records the date and keeps it active`() {
        val plan = planTaskCompletion(dailyTask(), day1, "2026-09-21T10:00:00Z")

        assertEquals("active", plan.changes["status"])
        assertEquals("2026-09-21", plan.changes["last_completed"])
        assertEquals("2026-09-21T10:00:00Z", plan.changes["updated"])
        assertTrue(plan.isRecurring)
        // The word "done" must never reach the file, or the habit would disappear for good.
        assertFalse(plan.changes.containsValue("done"))
    }

    @Test
    fun `completing a one-off task keeps the original behaviour`() {
        val plan = planTaskCompletion(oneOffTask(due = day1.toString()), day1, "2026-09-21T10:00:00Z")

        assertEquals("done", plan.changes["status"])
        assertEquals("2026-09-21T10:00:00Z", plan.changes["updated"])
        assertFalse(plan.isRecurring)
        // No last_completed on a one-off task: it must not look recurring in the Markdown.
        assertFalse(plan.changes.containsKey("last_completed"))
    }

    @Test
    fun `every planned change has a value so no key can be deleted`() {
        // MarkdownPatcher treats a null value as "remove this key", so a null would silently strip
        // frontmatter rather than write it.
        listOf(dailyTask(), oneOffTask()).forEach { note ->
            planTaskCompletion(note, day1, "ts").changes.forEach { (key, value) ->
                assertTrue("value for $key must not be blank", value.isNotBlank())
            }
        }
    }

    @Test
    fun `completion is idempotent for the same day`() {
        val first = planTaskCompletion(dailyTask(), day1, "t1")
        val again = planTaskCompletion(dailyTask(lastCompleted = "2026-09-21"), day1, "t2")

        assertEquals(first.changes["status"], again.changes["status"])
        assertEquals(first.changes["last_completed"], again.changes["last_completed"])
    }

    @Test
    fun `days since completion is reported for the task hint`() {
        val note = dailyTask(lastCompleted = day1.toString())

        assertEquals(0L, TaskVisibility.daysSinceCompletion(note, day1))
        assertEquals(1L, TaskVisibility.daysSinceCompletion(note, day2))
        assertNull(TaskVisibility.daysSinceCompletion(dailyTask(), day1))
    }
}
