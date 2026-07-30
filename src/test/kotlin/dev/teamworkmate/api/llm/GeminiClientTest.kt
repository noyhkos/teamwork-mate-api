package dev.teamworkmate.api.llm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the Gemini Interactions API envelope contract — the only production LlmPort,
 * otherwise bypassed entirely by the scripted fake.
 */
class GeminiClientTest {

    private val om = JsonMapper.builder().build()

    private fun withServer(responseBody: String, block: (GeminiClient, () -> String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var captured = ""
        var capturedKey = ""
        server.createContext("/v1beta/interactions") { exchange: HttpExchange ->
            captured = exchange.requestBody.readBytes().decodeToString()
            capturedKey = exchange.requestHeaders.getFirst("x-goog-api-key") ?: ""
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = GeminiClient("test-key", "http://127.0.0.1:${server.address.port}", om)
            block(client) { "$capturedKey|$captured" }
        } finally {
            server.stop(0)
        }
    }

    private val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""

    @Test
    fun `picks the last model_output step and concatenates only text parts`() {
        val body = """
            {"id":"v1_x","status":"completed","steps":[
              {"type":"model_output","content":[{"type":"text","text":"stale"}]},
              {"type":"tool_use","content":[{"type":"text","text":"ignored"}]},
              {"type":"model_output","content":[
                {"type":"thinking"},
                {"type":"text","text":"{\"text\":\"hi"},
                {"type":"text","text":"\"}"}
              ]}
            ]}
        """.trimIndent()
        withServer(body) { client, _ ->
            assertEquals("""{"text":"hi"}""", client.complete("gemini-3.6-flash", "p", schema))
        }
    }

    @Test
    fun `sends the auth header and a structured response_format`() {
        val body = """{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"ok"}]}]}"""
        withServer(body) { client, captured ->
            client.complete("gemini-3.6-flash", "prompt-body", schema)
            val (key, request) = captured().split("|", limit = 2)
            assertEquals("test-key", key)
            val sent = om.readTree(request)
            assertEquals("gemini-3.6-flash", sent.path("model").stringValue())
            assertEquals("prompt-body", sent.path("input").stringValue())
            assertEquals("application/json", sent.path("response_format").path("mime_type").stringValue())
            // schema must travel as a JSON object, not a string
            assertTrue(sent.path("response_format").path("schema").isObject)
        }
    }

    @Test
    fun `no model_output step fails loudly`() {
        val body = """{"status":"in_progress","steps":[{"type":"tool_use","content":[]}]}"""
        withServer(body) { client, _ ->
            val e = assertFailsWith<IllegalStateException> { client.complete("m", "p", schema) }
            assertTrue(e.message!!.contains("in_progress"))
        }
    }

    @Test
    fun `entries without a type field are skipped rather than throwing`() {
        val body = """{"status":"completed","steps":[{"content":[]},{"type":"model_output","content":[{"text":"x"},{"type":"text","text":"ok"}]}]}"""
        withServer(body) { client, _ ->
            assertEquals("ok", client.complete("m", "p", schema))
        }
    }

    @Test
    fun `blank output text fails loudly`() {
        val body = """{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":""}]}]}"""
        withServer(body) { client, _ ->
            assertFailsWith<IllegalStateException> { client.complete("m", "p", schema) }
        }
    }

    @Test
    fun `blank api key disables the port`() {
        val client = GeminiClient("", "http://127.0.0.1:1", om)
        assertTrue(!client.enabled)
        assertFailsWith<IllegalStateException> { client.complete("m", "p", schema) }
    }
}
