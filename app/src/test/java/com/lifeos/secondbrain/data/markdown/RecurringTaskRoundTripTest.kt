package com.lifeos.secondbrain.data.markdown

import com.lifeos.secondbrain.domain.TaskVisibility
import com.lifeos.secondbrain.domain.planTaskCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-end round trip for a recurring task, using the exact fixture from the acceptance criteria.
 *
 * This covers the part unit-testing the rules alone cannot: that the plan a completion produces is
 * actually written into the Markdown in a shape the parser reads back. A rule can be right while the
 * file format is wrong, and only this test would notice.
 */
class RecurringTaskRoundTripTest {

    private val fixture = """---
type: task
status: active
start: 2026-09-21
repeat: daily
---
# 每日规则测试
"""

    private val day1 = LocalDate.of(2026, 9, 21)
    private val day2 = LocalDate.of(2026, 9, 22)

    private fun read(markdown: String) = NoteMapper.fromMarkdown(
        fileId = "drive-daily",
        name = "每日规则测试.md",
        path = "Tasks/每日规则测试.md",
        markdown = markdown,
        modifiedTime = "2026-09-21T07:00:00Z",
        md5Checksum = null
    )

    @Test
    fun `fixture is parsed as a recurring task`() {
        val note = read(fixture)

        assertEquals("daily", note.repeat)
        assertEquals("2026-09-21", note.start)
        assertTrue(TaskVisibility.isRecurring(note))
        assertTrue("must appear on its start date", TaskVisibility.isTodayTask(note, day1))
    }

    @Test
    fun `completing writes last_completed and keeps the file active`() {
        val note = read(fixture)
        val plan = planTaskCompletion(note, day1, "2026-09-21T10:00:00Z")

        val patched = MarkdownPatcher.patch(fixture, plan.changes)

        assertTrue(patched.contains("last_completed: 2026-09-21"))
        assertTrue("status must stay active", patched.contains("status: active"))
        assertFalse("status: done must never be written", patched.contains("status: done"))
        // The original frontmatter is preserved, not replaced.
        assertTrue(patched.contains("repeat: daily"))
        assertTrue(patched.contains("start: 2026-09-21"))
        assertTrue(patched.contains("# 每日规则测试"))
    }

    @Test
    fun `patched file hides the task for the rest of that day`() {
        val plan = planTaskCompletion(read(fixture), day1, "2026-09-21T10:00:00Z")
        val patched = MarkdownPatcher.patch(fixture, plan.changes)

        val after = read(patched)

        assertTrue(TaskVisibility.isCompletedOn(after, day1))
        assertFalse("already done today", TaskVisibility.isTodayTask(after, day1))
        assertFalse("a habit must never join the completed list", TaskVisibility.isDoneTask(after))
    }

    @Test
    fun `the same single file returns as an open task the next day`() {
        val plan = planTaskCompletion(read(fixture), day1, "2026-09-21T10:00:00Z")
        val patched = MarkdownPatcher.patch(fixture, plan.changes)

        // Same Markdown, only the date changed — no second file is ever created.
        val nextDay = read(patched)

        assertFalse(TaskVisibility.isCompletedOn(nextDay, day2))
        assertTrue(TaskVisibility.isTodayTask(nextDay, day2))
        assertEquals("2026-09-21", nextDay.lastCompleted)
    }

    @Test
    fun `completing twice on different days leaves one last_completed key`() {
        val day1Patch = MarkdownPatcher.patch(
            fixture,
            planTaskCompletion(read(fixture), day1, "2026-09-21T10:00:00Z").changes
        )
        val day2Patch = MarkdownPatcher.patch(
            day1Patch,
            planTaskCompletion(read(day1Patch), day2, "2026-09-22T10:00:00Z").changes
        )

        // A duplicate key would make the frontmatter ambiguous for the next parse.
        assertEquals(1, Regex("last_completed:").findAll(day2Patch).count())
        assertEquals("2026-09-22", read(day2Patch).lastCompleted)
        assertTrue(read(day2Patch).body.contains("每日规则测试"))
    }

    @Test
    fun `a one-off task still becomes done and is untouched by recurrence`() {
        val plain = """---
type: task
status: active
due: 2026-09-21
---
# 一次性作业
"""
        val note = read(plain)
        assertFalse(TaskVisibility.isRecurring(note))

        val patched = MarkdownPatcher.patch(plain, planTaskCompletion(note, day1, "2026-09-21T10:00:00Z").changes)

        assertTrue(patched.contains("status: done"))
        assertFalse(patched.contains("last_completed"))
        val after = read(patched)
        assertTrue(TaskVisibility.isDoneTask(after))
        assertFalse(TaskVisibility.isTodayTask(after, day1))
    }
}
