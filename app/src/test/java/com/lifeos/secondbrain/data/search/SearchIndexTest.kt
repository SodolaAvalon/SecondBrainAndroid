package com.lifeos.secondbrain.data.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {
    @Test
    fun chineseTextGetsOverlappingBigrams() {
        val tokens = SearchIndex.tokens("联系老师加入课程群")
        assertTrue(tokens.split(' ').contains("老师"))
        assertTrue(tokens.split(' ').contains("课程"))
        assertTrue(tokens.split(' ').contains("老"))
    }

    @Test
    fun latinQueryUsesPrefixSearch() {
        val query = SearchIndex.matchQuery("OpenAI model")
        assertTrue(query.contains("\"openai\"*"))
        assertTrue(query.contains("\"model\"*"))
    }

    @Test
    fun chineseQueryRequiresAllBigrams() {
        val query = SearchIndex.matchQuery("人工智能")
        assertTrue(query.contains("人工"))
        assertTrue(query.contains("工智"))
        assertTrue(query.contains("智能"))
        assertTrue(query.contains("AND"))
    }
}
