package com.lifeos.secondbrain.data.markdown

object MarkdownPatcher {
    /**
     * Patches top-level scalar frontmatter keys while preserving body text, unknown keys,
     * field order, and the original line-ending convention. A null value removes a key.
     */
    fun patch(markdown: String, changes: Map<String, String?>): String {
        if (changes.isEmpty()) return markdown
        val eol = if (markdown.contains("\r\n")) "\r\n" else "\n"
        val normalized = markdown.replace("\r\n", "\n")
        val lines = normalized.split('\n').toMutableList()

        val start = if (lines.firstOrNull() == "---") 0 else -1
        val close = if (start == 0) (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: -1 else -1

        if (start != 0 || close < 0) {
            val header = buildList {
                add("---")
                changes.forEach { (key, value) -> if (value != null) add("$key: ${encode(value)}") }
                add("---")
            }.joinToString("\n")
            return (header + "\n" + normalized).replace("\n", eol)
        }

        var closingIndex = close
        changes.forEach { (key, value) ->
            val keyPrefix = "$key:"
            val index = (1 until closingIndex).firstOrNull { idx ->
                val line = lines[idx]
                !line.startsWith(' ') && !line.startsWith('\t') && line.startsWith(keyPrefix)
            }
            if (index != null) {
                var end = index + 1
                while (end < closingIndex && (lines[end].startsWith(' ') || lines[end].startsWith('\t'))) end++
                repeat(end - index) { lines.removeAt(index) }
                closingIndex -= (end - index)
                if (value != null) {
                    lines.add(index, "$key: ${encode(value)}")
                    closingIndex++
                }
            } else if (value != null) {
                lines.add(closingIndex, "$key: ${encode(value)}")
                closingIndex++
            }
        }
        return lines.joinToString("\n").replace("\n", eol)
    }

    private fun encode(value: String): String {
        val needsQuotes = value.isBlank() || value.startsWith(' ') || value.endsWith(' ') ||
            value.contains(": ") || value.startsWith('#') || value.contains('\n')
        return if (needsQuotes) "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else value
    }
}
