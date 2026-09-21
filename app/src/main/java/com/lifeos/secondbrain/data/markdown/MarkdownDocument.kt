package com.lifeos.secondbrain.data.markdown

data class MarkdownDocument(
    val raw: String,
    val frontmatter: LinkedHashMap<String, FrontmatterValue>,
    val body: String,
    val hasFrontmatter: Boolean
) {
    fun scalar(key: String): String? = when (val value = frontmatter[key]) {
        is FrontmatterValue.Scalar -> value.value
        is FrontmatterValue.ListValue -> value.values.firstOrNull()
        null -> null
    }

    fun list(key: String): List<String> = when (val value = frontmatter[key]) {
        is FrontmatterValue.ListValue -> value.values
        is FrontmatterValue.Scalar -> value.value
            ?.removePrefix("[")?.removeSuffix("]")
            ?.split(',')?.map { it.trim().trim('"', '\'') }?.filter { it.isNotEmpty() }.orEmpty()
        null -> emptyList()
    }
}

sealed interface FrontmatterValue {
    data class Scalar(val value: String?) : FrontmatterValue
    data class ListValue(val values: List<String>) : FrontmatterValue
}
