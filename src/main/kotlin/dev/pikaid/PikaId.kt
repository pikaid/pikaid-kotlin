package dev.pikaid

import java.time.Instant

/**
 * An immutable, fully validated pikaid value.
 *
 * A pikaid is exactly 26 lowercase Base36 ASCII characters: a seven-character unsigned
 * Unix timestamp in whole seconds followed by nineteen characters encoding 96 bits of
 * cryptographic randomness. Its binary form is exactly 17 bytes: a five-byte unsigned
 * big-endian timestamp followed by the twelve original randomness bytes.
 *
 * Instances can only be created through the validating factory methods on [PikaId].
 * Text and [toByteArray] conversions are lossless, and [compareTo] uses the canonical
 * ASCII-compatible lexicographic ordering. Values with different timestamps sort
 * chronologically; values with the same timestamp sort by randomness, not by generation
 * order.
 *
 * A pikaid is neither a secret nor an authentication token, and uniqueness is not
 * guaranteed. Enforce uniqueness where required, for example with a database constraint.
 */
public class PikaId private constructor(
    private val text: String,
    private val timestampSeconds: Long,
    randomness: ByteArray,
) : Comparable<PikaId> {
    private val randomnessBytes: ByteArray = randomness.copyOf()

    /**
     * The unsigned Unix timestamp in whole seconds since `1970-01-01T00:00:00Z`.
     *
     * The value is always in `0..78,364,164,095`.
     */
    public val timestamp: Long
        get() = timestampSeconds

    /**
     * A copy of the twelve randomness bytes.
     *
     * The returned array is a defensive copy; mutating it does not change this value.
     */
    public val randomness: ByteArray
        get() = randomnessBytes.copyOf()

    /**
     * The randomness as exactly 24 lowercase hexadecimal characters.
     */
    public val randomnessHex: String = toHex(randomnessBytes)

    /**
     * Returns the 17-byte binary representation.
     *
     * The first five bytes are the unsigned big-endian timestamp, and the remaining
     * twelve bytes are the randomness unchanged, including leading zero bytes.
     * The returned array is a new copy.
     */
    public fun toByteArray(): ByteArray {
        val binary = ByteArray(BINARY_LENGTH)
        val seconds = timestampSeconds
        binary[0] = (seconds ushr 32).toByte()
        binary[1] = (seconds ushr 24).toByte()
        binary[2] = (seconds ushr 16).toByte()
        binary[3] = (seconds ushr 8).toByte()
        binary[4] = seconds.toByte()
        randomnessBytes.copyInto(destination = binary, destinationOffset = 5)
        return binary
    }

    /**
     * Compares this value with [other] using canonical ASCII-compatible lexicographic order.
     *
     * The result matches unsigned bytewise comparison of [toByteArray] values. Values with
     * different timestamps sort chronologically. Values with the same timestamp sort by
     * randomness, which is not generation order.
     */
    public override fun compareTo(other: PikaId): Int = text.compareTo(other.text)

    /**
     * Returns `true` when [other] is a pikaid with the same 26-character text form.
     */
    public override fun equals(other: Any?): Boolean = this === other || (other is PikaId && text == other.text)

    /**
     * Returns the hash code of the canonical text form.
     */
    public override fun hashCode(): Int = text.hashCode()

    /**
     * Returns the canonical 26-character text form.
     */
    public override fun toString(): String = text

    /**
     * Factories, validators, and constants for pikaid values.
     */
    public companion object {
        /** The exact text length: 26 lowercase Base36 characters. */
        public const val TEXT_LENGTH: Int = 26

        /** The exact binary length: 17 bytes. */
        public const val BINARY_LENGTH: Int = 17

        /** The exact randomness width: 12 bytes (96 bits). */
        public const val RANDOMNESS_BYTES: Int = 12

        /** The largest valid timestamp: `36^7 - 1`, or 78,364,164,095 seconds. */
        public const val MAX_TIMESTAMP: Long = 78_364_164_095L

        /**
         * Generates a pikaid from the current UTC Unix time and 12 bytes from the
         * platform cryptographically secure random number generator.
         *
         * @throws PikaIdGenerationException if the system clock is outside the valid
         * timestamp range, or if secure randomness cannot be obtained.
         */
        public fun generate(): PikaId = generateAt(SecureRandomEntropySource) { Instant.now().epochSecond }

        /**
         * Generates a pikaid with [timestamp] and 12 bytes from the platform
         * cryptographically secure random number generator.
         *
         * @param timestamp whole Unix seconds in `0..78,364,164,095`.
         * @throws IllegalArgumentException if [timestamp] is outside the valid range.
         * @throws PikaIdGenerationException if secure randomness cannot be obtained.
         */
        public fun generate(timestamp: Long): PikaId = generateAt(timestamp, SecureRandomEntropySource)

        /**
         * Generates a pikaid from the current UTC Unix time and returns its 17-byte
         * binary representation.
         *
         * @throws PikaIdGenerationException if the system clock is outside the valid
         * timestamp range, or if secure randomness cannot be obtained.
         */
        public fun generateBinary(): ByteArray = generate().toByteArray()

        /**
         * Generates a pikaid with [timestamp] and returns its 17-byte binary representation.
         *
         * @param timestamp whole Unix seconds in `0..78,364,164,095`.
         * @throws IllegalArgumentException if [timestamp] is outside the valid range.
         * @throws PikaIdGenerationException if secure randomness cannot be obtained.
         */
        public fun generateBinary(timestamp: Long): ByteArray = generate(timestamp).toByteArray()

        /**
         * Strictly parses and validates a 26-character pikaid string.
         *
         * The input must be exactly 26 ASCII characters from `[0-9a-z]`, and the
         * nineteen randomness characters must decode to a value in `0..2^96 - 1`.
         * No trimming, case folding, padding, or other coercion is performed.
         *
         * @throws PikaIdFormatException if [text] is not a valid pikaid.
         */
        public fun parse(text: String): PikaId {
            validateText(text)
            return PikaId(text, decodeTimestamp(text), decodeRandomness(text))
        }

        /**
         * Parses [text] like [parse], returning `null` instead of throwing for invalid input.
         */
        public fun parseOrNull(text: String): PikaId? =
            try {
                parse(text)
            } catch (_: PikaIdFormatException) {
                null
            }

        /**
         * Strictly decodes a 17-byte binary pikaid.
         *
         * The buffer must contain a five-byte unsigned big-endian timestamp no greater
         * than [MAX_TIMESTAMP], followed by exactly twelve randomness bytes.
         *
         * @throws PikaIdFormatException if [bytes] is not a valid binary pikaid.
         */
        public fun fromBinary(bytes: ByteArray): PikaId {
            if (bytes.size != BINARY_LENGTH) {
                throw PikaIdFormatException(
                    "binary pikaid must be exactly $BINARY_LENGTH bytes, got ${bytes.size}",
                )
            }
            val timestamp =
                ((bytes[0].toLong() and 0xFF) shl 32) or
                    ((bytes[1].toLong() and 0xFF) shl 24) or
                    ((bytes[2].toLong() and 0xFF) shl 16) or
                    ((bytes[3].toLong() and 0xFF) shl 8) or
                    (bytes[4].toLong() and 0xFF)
            if (timestamp > MAX_TIMESTAMP) {
                throw PikaIdFormatException(
                    "binary timestamp $timestamp exceeds the maximum $MAX_TIMESTAMP",
                )
            }
            return of(timestamp, bytes.copyOfRange(5, BINARY_LENGTH))
        }

        /**
         * Decodes [bytes] like [fromBinary], returning `null` instead of throwing for invalid input.
         */
        public fun fromBinaryOrNull(bytes: ByteArray): PikaId? =
            try {
                fromBinary(bytes)
            } catch (_: PikaIdFormatException) {
                null
            }

        /**
         * Returns `true` only when [text] passes full semantic validation.
         *
         * This is equivalent to a successful [parse] and checks the 96-bit randomness bound.
         */
        public fun isValid(text: String): Boolean = parseOrNull(text) != null

        /**
         * Returns `true` when [text] has the required length and alphabet.
         *
         * This is a shape check only. It does not verify that the randomness fits in
         * 96 bits, so a `true` result is not full validity. Use [isValid] for that.
         */
        public fun isWellFormed(text: String): Boolean {
            if (text.length != TEXT_LENGTH) return false
            for (index in 0 until TEXT_LENGTH) {
                if (digitOf(text[index]) < 0) return false
            }
            return true
        }

        @JvmSynthetic
        internal fun generateAt(
            timestamp: Long,
            entropySource: EntropySource,
        ): PikaId {
            requireGenerationTimestamp(timestamp)
            return of(timestamp, drawEntropy(entropySource))
        }

        @JvmSynthetic
        internal fun generateAt(
            entropySource: EntropySource,
            epochSecond: () -> Long,
        ): PikaId {
            val timestamp = epochSecond()
            if (timestamp !in 0..MAX_TIMESTAMP) {
                throw PikaIdGenerationException(
                    "system clock returned timestamp $timestamp outside 0..$MAX_TIMESTAMP",
                )
            }
            return generateAt(timestamp, entropySource)
        }

        private fun of(
            timestamp: Long,
            randomness: ByteArray,
        ): PikaId =
            PikaId(
                text = encodeTimestamp(timestamp) + encodeRandomness(randomness),
                timestampSeconds = timestamp,
                randomness = randomness,
            )
    }
}

private const val TIMESTAMP_CHARS: Int = 7
private const val RANDOMNESS_CHARS: Int = 19
private const val ALPHABET: String = "0123456789abcdefghijklmnopqrstuvwxyz"
private const val HEX_DIGITS: String = "0123456789abcdef"

private fun digitOf(character: Char): Int =
    when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'z' -> character - 'a' + 10
        else -> -1
    }

private fun validateText(text: String) {
    if (text.length != PikaId.TEXT_LENGTH) {
        throw PikaIdFormatException(
            "pikaid must be exactly ${PikaId.TEXT_LENGTH} ASCII characters, got ${text.length}",
        )
    }
    for (index in text.indices) {
        if (digitOf(text[index]) < 0) {
            throw PikaIdFormatException(
                "invalid character at index $index: U+${text[index].code.toString(16).padStart(4, '0')}; " +
                    "expected [0-9a-z]",
            )
        }
    }
}

private fun decodeTimestamp(text: String): Long {
    var value = 0L
    for (index in 0 until TIMESTAMP_CHARS) {
        value = value * 36 + digitOf(text[index])
    }
    return value
}

private fun decodeRandomness(text: String): ByteArray {
    val randomness = ByteArray(PikaId.RANDOMNESS_BYTES)
    for (index in 0 until RANDOMNESS_CHARS) {
        var carry = digitOf(text[TIMESTAMP_CHARS + index])
        for (byteIndex in randomness.lastIndex downTo 0) {
            val value = (randomness[byteIndex].toInt() and 0xFF) * 36 + carry
            randomness[byteIndex] = value.toByte()
            carry = value ushr 8
        }
        if (carry != 0) {
            throw PikaIdFormatException("randomness exceeds 96 bits")
        }
    }
    return randomness
}

private fun encodeTimestamp(timestamp: Long): String {
    val characters = CharArray(TIMESTAMP_CHARS)
    var remaining = timestamp
    for (index in characters.lastIndex downTo 0) {
        characters[index] = ALPHABET[(remaining % 36).toInt()]
        remaining /= 36
    }
    check(remaining == 0L) { "timestamp $timestamp does not fit in $TIMESTAMP_CHARS Base36 characters" }
    return String(characters)
}

private fun encodeRandomness(randomness: ByteArray): String {
    val buffer = randomness.copyOf()
    val characters = CharArray(RANDOMNESS_CHARS)
    for (position in characters.lastIndex downTo 0) {
        var remainder = 0
        for (index in buffer.indices) {
            val value = (remainder shl 8) or (buffer[index].toInt() and 0xFF)
            buffer[index] = (value / 36).toByte()
            remainder = value % 36
        }
        characters[position] = ALPHABET[remainder]
    }
    check(buffer.all { it == 0.toByte() }) { "randomness does not fit in $RANDOMNESS_CHARS Base36 characters" }
    return String(characters)
}

private fun toHex(bytes: ByteArray): String {
    val characters = CharArray(bytes.size * 2)
    for (index in bytes.indices) {
        val value = bytes[index].toInt() and 0xFF
        characters[index * 2] = HEX_DIGITS[value ushr 4]
        characters[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(characters)
}

private fun requireGenerationTimestamp(timestamp: Long) {
    if (timestamp !in 0..PikaId.MAX_TIMESTAMP) {
        throw IllegalArgumentException(
            "timestamp must be between 0 and ${PikaId.MAX_TIMESTAMP}, got $timestamp",
        )
    }
}

private fun drawEntropy(source: EntropySource): ByteArray {
    val randomness =
        try {
            source.nextBytes(PikaId.RANDOMNESS_BYTES)
        } catch (failure: RuntimeException) {
            throw PikaIdGenerationException("secure random generation failed", failure)
        }
    if (randomness.size != PikaId.RANDOMNESS_BYTES) {
        throw PikaIdGenerationException(
            "secure random source returned ${randomness.size} bytes; expected ${PikaId.RANDOMNESS_BYTES}",
        )
    }
    return randomness
}
