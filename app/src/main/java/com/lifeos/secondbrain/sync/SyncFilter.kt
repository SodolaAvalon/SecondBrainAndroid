package com.lifeos.secondbrain.sync

object SyncFilter {
    private val templateMarkers = listOf("{{date", "{{time", "{{title", "{{content", "{{file", "{{selection")

    fun parseFolders(raw: String): Set<String> =
        raw.split(',', '，')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun isIgnoredFolderName(name: String, ignored: Set<String>): Boolean =
        name.startsWith(".") || name.lowercase() in ignored

    fun isIgnoredPath(path: String, ignored: Set<String>): Boolean =
        path.split('/').any { isIgnoredFolderName(it, ignored) }

    fun isTemplate(markdown: String): Boolean =
        templateMarkers.any { markdown.contains(it, ignoreCase = true) }
}
