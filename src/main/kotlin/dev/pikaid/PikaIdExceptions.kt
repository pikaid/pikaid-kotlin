package dev.pikaid

/**
 * Thrown when a string or binary value is not a valid pikaid.
 *
 * Parsing and decoding never truncate, wrap, case-fold, or otherwise coerce invalid
 * input. The message identifies the violated rule without echoing the whole input.
 */
public class PikaIdFormatException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Thrown when a pikaid cannot be generated.
 *
 * This indicates a failure of a generation prerequisite, such as a system clock outside
 * the representable timestamp range or a cryptographically secure random source that
 * failed or returned the wrong number of bytes. No partial or fallback identifier is
 * produced.
 */
public class PikaIdGenerationException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
