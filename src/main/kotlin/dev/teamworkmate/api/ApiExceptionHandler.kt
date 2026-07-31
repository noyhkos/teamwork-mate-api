package dev.teamworkmate.api

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
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

    /**
     * A rejected body arrives as one of two types and neither is a
     * ResponseStatusException, so both used to come back as a bare 400.
     * Validation failures are one; the other is Jackson giving up before
     * validation even runs, which is what a missing field does to a
     * non-nullable Kotlin property.
     *
     * The per-field messages are Hibernate's English defaults and are no use
     * to a reader — and the browser already marks the field it rejected — so
     * one sentence covers both.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun onUnusableBody(): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        body.detail = "입력한 내용을 다시 확인해 주세요."
        return ResponseEntity.badRequest().body(body)
    }
}
