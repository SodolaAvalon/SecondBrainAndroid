import com.lifeos.secondbrain.data.markdown.FrontmatterParser
import com.lifeos.secondbrain.data.markdown.MarkdownPatcher

fun main() {
    val raw = """---
type: task
status: active
custom: untouched
tags:
  - android
  - life
---

# Finish app
Keep [[Obsidian Link]] exactly.
"""
    val doc = FrontmatterParser.parse(raw)
    check(doc.scalar("type") == "task")
    check(doc.list("tags") == listOf("android", "life"))
    val patched = MarkdownPatcher.patch(raw, mapOf("status" to "done", "updated" to "2026-09-19T09:45:00Z"))
    check("custom: untouched" in patched)
    check("Keep [[Obsidian Link]] exactly." in patched)
    check("status: done" in patched)
    println("Parser smoke test passed")
}
