package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.facts.CalcHttp
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Renders the share card PNG via the calc service (satori lives in JS land). */
@Component
class CardClient(@Value("\${calc.base-url}") baseUrl: String) {
    // Runs on the thread serving card.png, which a link-preview crawler is waiting
    // on. Longer read than saju facts: satori + resvg on a cold calc is slower.
    private val client = CalcHttp.client(baseUrl, Duration.ofSeconds(30))

    fun render(report: ReportView): ByteArray {
        val body = mapOf(
            "teamName" to report.teamName,
            "archetype" to report.archetype,
            "harmonyScore" to report.harmonyScore,
            "roles" to report.roles.map { mapOf("nickname" to it.nickname, "roleKo" to it.roleKo) },
            "bestPair" to report.bestPair?.let { mapOf("a" to it.a, "b" to it.b, "total" to it.total) },
        )
        return client.post().uri("/card").body(body).retrieve().body(ByteArray::class.java)
            ?: error("empty card response")
    }
}
