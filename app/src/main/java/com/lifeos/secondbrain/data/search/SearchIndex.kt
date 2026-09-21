package com.lifeos.secondbrain.data.search

import java.util.Locale

/**
 * Produces FTS-friendly tokens without depending on a locale-specific dictionary.
 *
 * CJK text is indexed as overlapping bigrams so a search such as "老师" can match a longer
 * sentence that contains those characters. Latin/digit runs stay as normal lower-cased words.
 */
object SearchIndex {
    private val runs = Regex("[\\p{L}\\p{N}_]+")

    fun tokens(vararg values: String): String = values
        .asSequence()
        .flatMap { value -> tokenize(value).asSequence() }
        .distinct()
        .joinToString(" ")

    fun matchQuery(query: String): String {
        val terms = tokenize(query.trim()).distinct()
        if (terms.isEmpty()) return ""
        return terms.joinToString(" AND ") { term ->
            val escaped = term.replace("\"", "\"\"")
            if (term.all { it.isLetterOrDigit() || it == '_' } && !term.any(::isCjk)) {
                "\"$escaped\"*"
            } else {
                "\"$escaped\""
            }
        }
    }

    private fun tokenize(value: String): List<String> {
        val out = ArrayList<String>()
        runs.findAll(value.lowercase(Locale.ROOT)).forEach { match ->
            val run = match.value
            if (run.any(::isCjk)) {
                val units = splitMixedRun(run)
                units.forEach { part ->
                    if (part.all(::isCjk)) {
                        when (part.length) {
                            0 -> Unit
                            1 -> out += part
                            else -> {
                                part.forEach { out += it.toString() }
                                for (i in 0 until part.length - 1) out += part.substring(i, i + 2)
                            }
                        }
                    } else if (part.isNotBlank()) {
                        out += part
                    }
                }
            } else {
                out += run
            }
        }
        return out
    }

    private fun splitMixedRun(run: String): List<String> {
        if (run.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var cjk = isCjk(run.first())
        run.forEach { ch ->
            val next = isCjk(ch)
            if (buffer.isNotEmpty() && next != cjk) {
                result += buffer.toString()
                buffer.clear()
            }
            buffer.append(ch)
            cjk = next
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }

    private fun isCjk(ch: Char): Boolean = when (Character.UnicodeScript.of(ch.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> true
        else -> false
    }
}
