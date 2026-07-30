package dev.teamworkmate.api.queue

import java.util.UUID

/**
 * Hand-off point between the request thread and the analysis worker.
 * Implementations must return promptly — the caller is answering an HTTP request.
 * The SQS implementation slots in here without touching callers.
 */
interface QueuePort {
    fun submit(teamId: UUID)
}
