package dev.teamworkmate.api.facts

import java.net.http.HttpClient
import java.time.Duration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Shared builder for the two calc clients.
 *
 * Both run on a thread a user is waiting on — saju facts on the analysis job,
 * the card render on the HTTP request serving `card.png` — so an unbounded read
 * would pin that thread until the platform kills it. [GeminiClient] already
 * carried explicit timeouts for the same reason; these two did not.
 *
 * A Lambda Function URL carries a trailing slash, so the base URL is trimmed
 * here rather than at each call site — otherwise paths come out as "//saju".
 */
object CalcHttp {

    val CONNECT: Duration = Duration.ofSeconds(5)

    fun client(baseUrl: String, readTimeout: Duration): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl.trimEnd('/'))
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(CONNECT).build(),
                ).apply { setReadTimeout(readTimeout) },
            )
            .build()
}
