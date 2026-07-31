package dev.teamworkmate.api.analysis

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** Renders the share card PNG via the calc service (satori lives in JS land). */
@Component
class CardClient(@Value("\${calc.base-url}") baseUrl: String) {
    // A Lambda Function URL carries a trailing slash; trimming avoids "//saju" paths.
    private val client = RestClient.builder().baseUrl(baseUrl.trimEnd('/')).build()

    fun render(report: ReportView): ByteArray {
        val body = mapOf(
            "teamName" to report.teamName,
            "archetype" to report.archetype,
            "harmonyScore" to report.harmonyScore,
            "roles" to report.roles.map { mapOf("nickname" to it.nickname, "roleKo" to it.roleKo) },
            "bestPair" to report.bestPair?.let { mapOf("a" to it.a, "b" to it.b, "total" to it.total) },
            "riskNote" to report.riskNote,
        )
        return client.post().uri("/card").body(body).retrieve().body(ByteArray::class.java)
            ?: error("empty card response")
    }
}
