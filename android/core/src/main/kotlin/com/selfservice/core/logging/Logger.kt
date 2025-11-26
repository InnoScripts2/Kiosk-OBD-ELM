package com.selfservice.core.logging

/**
 * Минимальный интерфейс структурированного логгера для фич.
 * Поддерживает уровни DEBUG/INFO/WARN/ERROR с произвольным контекстом.
 */
interface Logger {
    fun debug(tag: String, message: String, metadata: Map<String, Any?> = emptyMap())
    fun info(tag: String, message: String, metadata: Map<String, Any?> = emptyMap())
    fun warn(tag: String, message: String, metadata: Map<String, Any?> = emptyMap())
    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any?> = emptyMap()
    )
}

/**
 * Адаптер поверх DiagnosticsLogger для совместимости с новым интерфейсом Logger.
 */
class DiagnosticsLoggerAdapter(
    private val diagnosticsLogger: DiagnosticsLogger
) : Logger {

    override fun debug(tag: String, message: String, metadata: Map<String, Any?>) {
        record("DEBUG", tag, message, metadata)
    }

    override fun info(tag: String, message: String, metadata: Map<String, Any?>) {
        record("INFO", tag, message, metadata)
    }

    override fun warn(tag: String, message: String, metadata: Map<String, Any?>) {
        record("WARN", tag, message, metadata)
    }

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
        metadata: Map<String, Any?>
    ) {
        val meta = if (throwable != null) {
            metadata + mapOf(
                "error" to (throwable.message ?: throwable::class.simpleName.orEmpty()),
                "stacktrace" to throwable.stackTraceToString()
            )
        } else {
            metadata
        }
        record("ERROR", tag, message, meta)
    }

    private fun record(level: String, tag: String, message: String, metadata: Map<String, Any?>) {
        diagnosticsLogger.record(
            category = tag,
            message = "[$level] $message",
            metadata = metadata + mapOf("level" to level)
        )
    }
}

/**
 * Заглушка для тестов и DEV окружений.
 */
object NoOpLogger : Logger {
    override fun debug(tag: String, message: String, metadata: Map<String, Any?>) {}
    override fun info(tag: String, message: String, metadata: Map<String, Any?>) {}
    override fun warn(tag: String, message: String, metadata: Map<String, Any?>) {}
    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
        metadata: Map<String, Any?>
    ) {}
}
