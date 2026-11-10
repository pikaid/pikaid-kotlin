package dev.pikaid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Known-answer cases whose expected values were computed independently with Python's
 * integer and `int.to_bytes` operations, not with this Kotlin implementation. Three of
 * the cases also appear in the official fixture; the rest exercise integer boundaries,
 * zero padding, and the 96-bit randomness limit.
 */
class PikaIdKnownAnswerTest {
    private data class KnownAnswer(
        val name: String,
        val timestamp: Long,
        val entropyHex: String,
        val text: String,
        val binaryHex: String,
    )

    private val answers =
        listOf(
            KnownAnswer(
                name = "all-zero",
                timestamp = 0,
                entropyHex = "000000000000000000000000",
                text = "00000000000000000000000000",
                binaryHex = "0000000000000000000000000000000000",
            ),
            KnownAnswer(
                name = "one-one",
                timestamp = 1,
                entropyHex = "000000000000000000000001",
                text = "00000010000000000000000001",
                binaryHex = "0000000001000000000000000000000001",
            ),
            KnownAnswer(
                name = "last-single-digit-values",
                timestamp = 35,
                entropyHex = "000000000000000000000023",
                text = "000000z000000000000000000z",
                binaryHex = "0000000023000000000000000000000023",
            ),
            KnownAnswer(
                name = "first-two-digit-values",
                timestamp = 36,
                entropyHex = "000000000000000000000024",
                text = "00000100000000000000000010",
                binaryHex = "0000000024000000000000000000000024",
            ),
            KnownAnswer(
                name = "timestamp-sixty-power",
                timestamp = 2_176_782_336,
                entropyHex = "000000000000000000000000",
                text = "10000000000000000000000000",
                binaryHex = "0081bf1000000000000000000000000000",
            ),
            KnownAnswer(
                name = "sample-second-with-one",
                timestamp = 1_700_000_000,
                entropyHex = "000000000000000000000001",
                text = "0s44we80000000000000000001",
                binaryHex = "006553f100000000000000000000000001",
            ),
            KnownAnswer(
                name = "sample-second-mixed-entropy",
                timestamp = 1_700_000_000,
                entropyHex = "00112233445566778899aabb",
                text = "0s44we8002loz3po6mcf6vsn23",
                binaryHex = "006553f10000112233445566778899aabb",
            ),
            KnownAnswer(
                name = "maximum-timestamp-zero-entropy",
                timestamp = 78_364_164_095L,
                entropyHex = "000000000000000000000000",
                text = "zzzzzzz0000000000000000000",
                binaryHex = "123ede3fff000000000000000000000000",
            ),
            KnownAnswer(
                name = "maximum-all-fields",
                timestamp = 78_364_164_095L,
                entropyHex = "ffffffffffffffffffffffff",
                text = "zzzzzzz7oiylpimjg5u2ca1ypr",
                binaryHex = "123ede3fffffffffffffffffffffffffff",
            ),
            KnownAnswer(
                name = "high-bit-of-96-bit-randomness",
                timestamp = 0,
                entropyHex = "800000000000000000000000",
                text = "00000003u9haurb9q2x1650zcw",
                binaryHex = "0000000000800000000000000000000000",
            ),
            KnownAnswer(
                name = "maximum-randomness",
                timestamp = 0,
                entropyHex = "ffffffffffffffffffffffff",
                text = "00000007oiylpimjg5u2ca1ypr",
                binaryHex = "0000000000ffffffffffffffffffffffff",
            ),
        )

    @Test
    fun `known answers parse, extract fields, and convert to binary`() {
        for (answer in answers) {
            val id = PikaId.parse(answer.text)
            assertEquals(answer.timestamp, id.timestamp, answer.name)
            assertEquals(answer.entropyHex, id.randomnessHex, answer.name)
            assertEquals(answer.binaryHex, id.toByteArray().toHex(), answer.name)
            assertEquals(answer.text, id.toString(), answer.name)
            assertTrue(PikaId.isValid(answer.text), answer.name)
        }
    }

    @Test
    fun `known answers decode from binary without loss`() {
        for (answer in answers) {
            val id = PikaId.fromBinary(answer.binaryHex.hexToBytes())
            assertEquals(answer.text, id.toString(), answer.name)
            assertEquals(answer.timestamp, id.timestamp, answer.name)
            assertEquals(answer.entropyHex, id.randomnessHex, answer.name)
            assertEquals(answer.binaryHex, id.toByteArray().toHex(), answer.name)
        }
    }

    @Test
    fun `timestamp maximum is the documented inclusive boundary`() {
        assertEquals(78_364_164_095L, PikaId.MAX_TIMESTAMP)
        assertEquals(PikaId.MAX_TIMESTAMP, PikaId.parse("zzzzzzz0000000000000000000").timestamp)
        assertEquals(
            PikaId.MAX_TIMESTAMP,
            PikaId.generate(PikaId.MAX_TIMESTAMP).timestamp,
        )
    }

    @Test
    fun `randomness maximum plus one is rejected`() {
        val maximum = "00000007oiylpimjg5u2ca1ypr"
        val aboveMaximum = "00000007oiylpimjg5u2ca1yps"

        assertTrue(PikaId.isValid(maximum))
        assertTrue(PikaId.isWellFormed(aboveMaximum))
        assertTrue(PikaId.parse(maximum).randomness.all { it == 0xFF.toByte() })
        assertFailsWith<PikaIdFormatException> { PikaId.parse(aboveMaximum) }
    }

    @Test
    fun `binary timestamp maximum and maximum plus one differ`() {
        val atMaximum = "123ede3fffffffffffffffffffffffffff".hexToBytes()
        val aboveMaximum = "123ede4000000000000000000000000000".hexToBytes()

        assertEquals(PikaId.MAX_TIMESTAMP, PikaId.fromBinary(atMaximum).timestamp)
        val failure = assertFailsWith<PikaIdFormatException> { PikaId.fromBinary(aboveMaximum) }
        assertTrue(failure.message.orEmpty().contains("exceeds the maximum"))
    }
}
