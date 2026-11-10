package dev.pikaid

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Generation tests use the internal entropy-source seam so that entropy can be supplied
 * deterministically and failures can be injected. The public `generate` overloads always
 * use the platform CSPRNG.
 */
class PikaIdGenerationTest {
    private val zeroEntropy = EntropySource { size -> ByteArray(size) }

    @Test
    fun `generate uses the current time and valid randomness`() {
        val before = Instant.now().epochSecond
        val id = PikaId.generate()
        val after = Instant.now().epochSecond

        assertTrue(id.timestamp in before..after, "timestamp ${id.timestamp} outside $before..$after")
        assertEquals(26, id.toString().length)
        assertEquals(id, PikaId.parse(id.toString()))
        assertEquals(PikaId.RANDOMNESS_BYTES, id.randomness.size)
        assertTrue(PikaId.isValid(id.toString()))
    }

    @Test
    fun `generate with an explicit timestamp uses exactly that second`() {
        val id = PikaId.generate(1_700_000_000L)

        assertEquals(1_700_000_000L, id.timestamp)
        assertTrue(id.toString().startsWith("0s44we8"), "unexpected prefix: $id")
        assertEquals(PikaId.RANDOMNESS_BYTES, id.randomness.size)
        assertEquals(id, PikaId.parse(id.toString()))
    }

    @Test
    fun `generateBinary returns parseable 17-byte identifiers`() {
        val binary = PikaId.generateBinary()
        assertEquals(PikaId.BINARY_LENGTH, binary.size)

        val id = PikaId.fromBinary(binary)
        assertTrue(id.timestamp <= Instant.now().epochSecond)
        assertContentEquals(binary, id.toByteArray())
    }

    @Test
    fun `generateBinary with an explicit timestamp preserves it`() {
        val binary = PikaId.generateBinary(1_234_567_890L)
        assertEquals(PikaId.BINARY_LENGTH, binary.size)
        assertEquals(1_234_567_890L, PikaId.fromBinary(binary).timestamp)
    }

    @Test
    fun `generated identifiers are not repeated`() {
        val ids = List(32) { PikaId.generate(1_700_000_000L).toString() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `generation rejects timestamps outside the unsigned Base36 range`() {
        val invalid = listOf(-1L, PikaId.MAX_TIMESTAMP + 1, Long.MIN_VALUE, Long.MAX_VALUE)

        for (timestamp in invalid) {
            val textFailure = assertFailsWith<IllegalArgumentException> { PikaId.generate(timestamp) }
            assertTrue(textFailure.message.orEmpty().contains("timestamp must be between"))
            val binaryFailure = assertFailsWith<IllegalArgumentException> { PikaId.generateBinary(timestamp) }
            assertTrue(binaryFailure.message.orEmpty().contains("timestamp must be between"))
        }
    }

    @Test
    fun `boundary timestamps are accepted`() {
        assertEquals(0L, PikaId.generate(0L).timestamp)
        assertEquals(PikaId.MAX_TIMESTAMP, PikaId.generate(PikaId.MAX_TIMESTAMP).timestamp)
    }

    @Test
    fun `injected entropy is encoded verbatim`() {
        val id =
            PikaId.generateAt(1_700_000_000L) { size ->
                check(size == PikaId.RANDOMNESS_BYTES)
                "00112233445566778899aabb".hexToBytes()
            }

        assertEquals("0s44we8002loz3po6mcf6vsn23", id.toString())
        assertEquals("00112233445566778899aabb", id.randomnessHex)
    }

    @Test
    fun `entropy source is asked for exactly twelve bytes`() {
        var requested = -1
        PikaId.generateAt(0L) { size ->
            requested = size
            ByteArray(size)
        }
        assertEquals(PikaId.RANDOMNESS_BYTES, requested)
    }

    @Test
    fun `entropy failure fails closed`() {
        val failure =
            assertFailsWith<PikaIdGenerationException> {
                PikaId.generateAt(0L) { throw IllegalStateException("csp rng unavailable") }
            }

        assertTrue(failure.message.orEmpty().contains("secure random generation failed"))
        assertTrue(failure.cause is IllegalStateException)
    }

    @Test
    fun `short entropy fails closed`() {
        val failure =
            assertFailsWith<PikaIdGenerationException> {
                PikaId.generateAt(0L) { ByteArray(PikaId.RANDOMNESS_BYTES - 1) }
            }
        assertTrue(failure.message.orEmpty().contains("11 bytes"))
    }

    @Test
    fun `clock outside the representable range fails closed`() {
        for (timestamp in listOf(-1L, PikaId.MAX_TIMESTAMP + 1)) {
            val failure = assertFailsWith<PikaIdGenerationException> { PikaId.generateAt(zeroEntropy) { timestamp } }
            assertTrue(failure.message.orEmpty().contains("system clock"))
        }
    }

    @Test
    fun `same-second generation follows entropy not call order`() {
        val values =
            ArrayDeque(listOf(ByteArray(PikaId.RANDOMNESS_BYTES) { 0xFF.toByte() }, ByteArray(PikaId.RANDOMNESS_BYTES)))
        val first = PikaId.generateAt(42L) { values.removeFirst() }
        val second = PikaId.generateAt(42L) { values.removeFirst() }

        assertEquals(first.timestamp, second.timestamp)
        assertTrue(second < first, "randomness 0 must sort before randomness max")
    }
}
