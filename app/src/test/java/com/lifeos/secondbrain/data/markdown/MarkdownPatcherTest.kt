package com.lifeos.secondbrain.data.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPatcherTest {
    @Test fun patchesOnlyRequestedFieldsAndPreservesUnknownData() {
        val original = """---
type: task
custom: keep-me
status: active
---

正文 [[链接]]
"""
        val patched = MarkdownPatcher.patch(original, mapOf("status" to "done", "updated" to "2026-09-19T10:00:00Z"))
        assertTrue(patched.contains("custom: keep-me"))
        assertTrue(patched.contains("status: done"))
        assertTrue(patched.contains("updated: 2026-09-19T10:00:00Z"))
        assertTrue(patched.endsWith("正文 [[链接]]\n"))
    }

    @Test fun keepsCrlf() {
        val original = "---\r\ntype: task\r\nstatus: active\r\n---\r\nbody"
        val patched = MarkdownPatcher.patch(original, mapOf("status" to "done"))
        assertEquals(false, patched.replace("\r\n", "").contains('\n'))
    }

    @Test fun indentedRuleInBodyIsNotTreatedAsFrontmatter() {
        val original = "  ---\nstatus: draft\n  ---\nbody"
        val patched = MarkdownPatcher.patch(original, mapOf("status" to "done"))
        assertTrue(patched.startsWith("---\nstatus: done\n---\n"))
        assertTrue(patched.contains("  ---\nstatus: draft\n  ---\nbody"))
    }

    @Test fun missingFrontmatterIsPrependedWithoutTouchingBody() {
        val original = "plain body\nstatus: untouched"
        val patched = MarkdownPatcher.patch(original, mapOf("status" to "done"))
        assertTrue(patched.startsWith("---\nstatus: done\n---\n"))
        assertTrue(patched.endsWith(original))
    }
}
