package com.lifeos.secondbrain.data.markdown

object FrontmatterParser {
    fun parse(markdown: String): MarkdownDocument {
        val normalized = markdown.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n") && normalized != "---") {
            return MarkdownDocument(markdown, linkedMapOf(), markdown, false)
        }

        val lines = normalized.split('\n')
        var closeIndex = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == "---") {
                closeIndex = i
                break
            }
        }
        if (closeIndex < 0) return MarkdownDocument(markdown, linkedMapOf(), markdown, false)

        val map = linkedMapOf<String, FrontmatterValue>()
        var i = 1
        while (i < closeIndex) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith('#')) {
                i++
                continue
            }
            if (line.startsWith(' ') || line.startsWith('\t')) {
                i++
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) {
                i++
                continue
            }
            val key = line.substring(0, colon).trim()
            val rawValue = line.substring(colon + 1).trim()
            if (rawValue.isEmpty()) {
                val items = mutableListOf<String>()
                var j = i + 1
                while (j < closeIndex) {
                    val child = lines[j]
                    val trimmed = child.trim()
                    if (!child.startsWith(' ') && !child.startsWith('\t')) break
                    if (trimmed.startsWith("- ")) {
                        items += unquote(trimmed.removePrefix("- ").trim())
                    }
                    j++
                }
                map[key] = if (items.isNotEmpty()) FrontmatterValue.ListValue(items) else FrontmatterValue.Scalar(null)
                i = if (j > i + 1) j else i + 1
                continue
            }
            map[key] = when {
                rawValue.startsWith('[') && rawValue.endsWith(']') -> {
                    val inside = rawValue.substring(1, rawValue.length - 1)
                    val values = inside.split(',').map { unquote(it.trim()) }.filter { it.isNotBlank() }
                    FrontmatterValue.ListValue(values)
                }
                rawValue == "null" || rawValue == "~" -> FrontmatterValue.Scalar(null)
                else -> FrontmatterValue.Scalar(unquote(rawValue))
            }
            i++
        }

        val body = lines.drop(closeIndex + 1).joinToString("\n").removePrefix("\n")
        return MarkdownDocument(markdown, map, body, true)
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }
}
