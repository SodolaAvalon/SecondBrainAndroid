package com.lifeos.secondbrain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFilterTest {
    private val ignored = SyncFilter.parseFolders("Templates, 模板, 99 System, _System")

    @Test fun parsesChineseAndAsciiSeparators() {
        assertEquals(setOf("templates", "模板", "99 system", "_system"), ignored)
        assertEquals(setOf("a", "b"), SyncFilter.parseFolders(" a , b ，"))
    }

    @Test fun detectsTemplatePlaceholders() {
        assertTrue(SyncFilter.isTemplate("---\ncreated: {{date:YYYY-MM-DD}} {{time:HH:mm:ss}}\n---\n{{content}}"))
        assertFalse(SyncFilter.isTemplate("---\ntype: task\nstatus: active\n---\nreal note"))
    }

    @Test fun ignoresConfiguredAndHiddenFolders() {
        assertTrue(SyncFilter.isIgnoredPath("99 System/Templates/Task.md", ignored))
        assertTrue(SyncFilter.isIgnoredPath("_System/Daily.md", ignored))
        assertTrue(SyncFilter.isIgnoredPath(".obsidian/plugins/foo.md", ignored))
        assertFalse(SyncFilter.isIgnoredPath("00 Inbox/note.md", ignored))
    }
}
