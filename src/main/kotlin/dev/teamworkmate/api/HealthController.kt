package dev.teamworkmate.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Readiness probe — the Lambda Web Adapter waits on this before forwarding traffic. */
@RestController
class HealthController {

    @GetMapping("/healthz")
    fun healthz(): Map<String, Boolean> = mapOf("ok" to true)
}
