package com.lifeos.secondbrain.data.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterParserTest {
    @Test fun parsesScalarAndLists() {
        val doc = FrontmatterParser.parse("""---
type: task
status: active
tags:
  - school
  - chemistry
---
# 作业
正文
""")
        assertTrue(doc.hasFrontmatter)
        assertEquals("task", doc.scalar("type"))
        assertEquals(listOf("school", "chemistry"), doc.list("tags"))
        assertTrue(doc.body.contains("正文"))
    }

    @Test fun damagedFrontmatterFallsBackToBody() {
        val raw = "---\ntype: task\nmissing close"
        val doc = FrontmatterParser.parse(raw)
        assertFalse(doc.hasFrontmatter)
        assertEquals(raw, doc.body)
    }
}
