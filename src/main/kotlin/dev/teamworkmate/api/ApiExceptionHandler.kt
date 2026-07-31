package dev.teamworkmate.api

import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * Puts our own reason in the body.
 *
 * Spring drops the reason by default, so a duplicate nickname reached the
 * browser as `{"error":"Conflict"}` and the UI showed the word "Conflict" to a
 * Korean user. Turning on `include-message` globally would have leaked
 * arbitrary exception text instead; this only forwards strings we wrote.
 * Anything that is not a ResponseStatusException still falls through to the
 * default handler and stays opaque.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun onStatusException(e: ResponseStatusException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatus(e.statusCode)
        e.reason?.let { body.detail = it }
        return ResponseEntity.status(e.statusCode).body(body)
    }
}
