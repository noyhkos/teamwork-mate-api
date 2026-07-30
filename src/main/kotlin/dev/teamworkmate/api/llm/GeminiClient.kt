package dev.teamworkmate.api.llm

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * Gemini Interactions API client (POST /v1beta/interactions).
 * Auth keys (AQ. prefix) go in the x-goog-api-key header.
 */
@Component
class GeminiClient(
    @Value("\${llm.gemini.api-key:}") private val apiKey: String,
    @Value("\${llm.gemini.base-url:https://generativelanguage.googleapis.com}") baseUrl: String,
    private val om: ObjectMapper,
) : LlmPort {

    override val enabled: Boolean get() = apiKey.isNotBlank()

    // Explicit timeouts: enrich() runs on the analyze request thread, so a stalled
    // connection would otherwise pin it forever. Timeouts surface as exceptions,
    // which PhraseService already treats as the fallback path.
    private val client = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(30)) },
        )
        .build()

    override fun complete(model: String, prompt: String, schemaJson: String): String {
        check(enabled) { "GEMINI_API_KEY is not configured" }
        val body = mapOf(
            "model" to model,
            "input" to prompt,
            "response_format" to mapOf(
                "type" to "text",
                "mime_type" to "application/json",
                "schema" to om.readValue(schemaJson, Map::class.java),
            ),
        )
        val response = client.post().uri("/v1beta/interactions")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .body(body).retrieve().body(String::class.java)
            ?: error("empty LLM response")

        // Lenient accessors throughout — the envelope is model/service controlled.
        val root = om.readTree(response)
        val output = root.path("steps").values()
            .lastOrNull { it.path("type").stringValue("") == "model_output" }
            ?: error("no model_output step (status=${root.path("status").stringValue("")})")
        return output.path("content").values()
            .filter { it.path("type").stringValue("") == "text" }
            .joinToString("") { it.path("text").stringValue("") }
            .ifBlank { error("model_output contained no text") }
    }
}
