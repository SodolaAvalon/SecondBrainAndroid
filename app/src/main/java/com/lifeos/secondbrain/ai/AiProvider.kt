package com.lifeos.secondbrain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface AiProvider {
    suspend fun organize(rawText: String): AiOrganizeResult
}

@Serializable
data class AiOrganizeResult(val items: List<AiItem>)

@Serializable
data class AiItem(
    val type: String,
    val title: String,
    val status: String? = null,
    val due: String? = null,
    val priority: String? = null,
    val project: String? = null,
    val body: String = "",
    val tags: List<String> = emptyList()
)

object AiResultValidator {
    private val allowed = setOf("task", "idea", "plan", "writing", "learning", "journal", "decision", "reference")
    fun validate(result: AiOrganizeResult): AiOrganizeResult {
        require(result.items.size <= 30) { "AI 一次返回项目过多" }
        result.items.forEach { item ->
            require(item.type.lowercase() in allowed) { "AI 返回未知类型 ${item.type}" }
            require(item.title.isNotBlank() && item.title.length <= 200) { "AI 标题无效" }
            require(item.body.length <= 50_000) { "AI 正文过长" }
            if (item.due != null) require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(item.due)) { "AI due 日期格式无效" }
            require(item.tags.size <= 50 && item.tags.all { it.length <= 100 }) { "AI 标签无效" }
        }
        return result
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat
)
@Serializable private data class ChatMessage(val role: String, val content: String)
@Serializable private data class ResponseFormat(val type: String = "json_object")
@Serializable private data class ChatResponse(val choices: List<Choice>)
@Serializable private data class Choice(val message: ChatMessage)

class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : AiProvider {
    override suspend fun organize(rawText: String): AiOrganizeResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "AI API Key 未配置" }
        require(model.isNotBlank()) { "AI Model 未配置" }
        val system = """
            你是个人知识整理器。只能把用户明确表达的内容拆成 task/idea/plan/writing/learning/journal/decision/reference。
            不得猜测不存在的日期，不得把普通情绪制造成任务，不得修改或省略用户原意。
            due 只有在用户明确表达可推导的日期时才填写，格式 YYYY-MM-DD，否则为 null。
            只返回 JSON：{"items":[{"type":...,"title":...,"status":...,"due":...,"priority":...,"project":...,"body":...,"tags":[]}]}
        """.trimIndent()
        val payload = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model, listOf(ChatMessage("system", system), ChatMessage("user", rawText)), ResponseFormat())
        )
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) error("AI 请求失败 (${response.code})")
            val content = json.decodeFromString<ChatResponse>(body).choices.firstOrNull()?.message?.content
                ?: error("AI 返回为空")
            AiResultValidator.validate(json.decodeFromString<AiOrganizeResult>(content))
        }
    }
}
