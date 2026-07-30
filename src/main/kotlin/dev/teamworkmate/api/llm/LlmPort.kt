package dev.teamworkmate.api.llm

/** Port so tests can swap the LLM backend, mirroring CalcPort. */
interface LlmPort {
    /** False when no API key is configured — the phrase layer silently skips. */
    val enabled: Boolean

    /**
     * Single-shot completion returning the raw text (JSON when [schemaJson] is given).
     * Implementations must throw on transport/API errors; callers own retry/fallback.
     */
    fun complete(model: String, prompt: String, schemaJson: String): String
}
