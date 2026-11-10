package dev.pikaid

import java.security.SecureRandom

/**
 * Internal source of cryptographically secure random bytes.
 *
 * Kept internal so that callers cannot accidentally substitute a non-cryptographic
 * generator. Tests inject deterministic and failing implementations.
 */
internal fun interface EntropySource {
    fun nextBytes(size: Int): ByteArray
}

/**
 * The platform CSPRNG.
 *
 * [SecureRandom] without an algorithm name uses the platform's default cryptographically
 * secure provider and is thread-safe. Generation failures propagate and are handled by
 * [PikaId.generate], which fails closed.
 */
internal object SecureRandomEntropySource : EntropySource {
    private val secureRandom = SecureRandom()

    override fun nextBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)
}
