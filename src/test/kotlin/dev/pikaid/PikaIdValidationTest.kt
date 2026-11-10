package dev.pikaid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PikaIdValidationTest {
    @Test
    fun `empty and wrong-length strings are rejected`() {
        for (text in listOf("", "0".repeat(25), "0".repeat(27), "0".repeat(1000))) {
            val failure = assertFailsWith<PikaIdFormatException>(text.take(30)) { PikaId.parse(text) }
            assertTrue(failure.message.orEmpty().contains("exactly 26"))
            assertFalse(PikaId.isValid(text))
            assertFalse(PikaId.isWellFormed(text))
            assertNull(PikaId.parseOrNull(text))
        }
    }

    @Test
    fun `uppercase and non-alphabet characters are rejected`() {
        val malformed =
            listOf(
                "000000A",
                "000000-",
                "000000_",
                "0000000 ",
                "0000000\t",
                "0000000\n",
                "0000000\u0000",
                "0000000/",
                "0000000:",
                "0000000+",
                "0000000@",
                "0000000~",
            ).map { prefix -> prefix + "0".repeat(26 - prefix.length) }

        for (text in malformed) {
            assertEquals(26, text.length, "test case must be 26 code units: $text")
            assertFailsWith<PikaIdFormatException> { PikaId.parse(text) }
            assertFalse(PikaId.isValid(text))
            assertFalse(PikaId.isWellFormed(text))
            assertNull(PikaId.parseOrNull(text))
        }
    }

    @Test
    fun `non-ASCII characters are rejected without normalization`() {
        val accented = "0000000\u00e9" + "0".repeat(18)
        val supplementary = "0000000\uD83D\uDE00" + "0".repeat(17)

        assertEquals(26, accented.length)
        assertEquals(26, supplementary.length)

        for (text in listOf(accented, supplementary)) {
            assertFailsWith<PikaIdFormatException> { PikaId.parse(text) }
            assertFalse(PikaId.isValid(text))
            assertFalse(PikaId.isWellFormed(text))
        }
    }

    @Test
    fun `invalid character error identifies the index and code point`() {
        val failure =
            assertFailsWith<PikaIdFormatException> {
                PikaId.parse("000000A0000000000000000000")
            }
        assertTrue(failure.message.orEmpty().contains("index 6"))
        assertTrue(failure.message.orEmpty().contains("U+0041"))
    }

    @Test
    fun `randomness above 96 bits is rejected`() {
        val cases =
            listOf(
                "0000000zzzzzzzzzzzzzzzzzzz",
                "00000007oiylpimjg5u2ca1yps",
                "0000000zzzzzzzzzzzzzzzzzzy",
            )

        for (text in cases) {
            assertEquals(26, text.length)
            assertTrue(PikaId.isWellFormed(text), "shape is well formed: $text")
            assertFalse(PikaId.isValid(text), "semantic validation must fail: $text")
            val failure = assertFailsWith<PikaIdFormatException> { PikaId.parse(text) }
            assertTrue(failure.message.orEmpty().contains("96 bits"))
        }
    }

    @Test
    fun `maximum randomness is accepted`() {
        val text = "zzzzzzz7oiylpimjg5u2ca1ypr"
        val id = PikaId.parse(text)
        assertTrue(PikaId.isWellFormed(text))
        assertTrue(PikaId.isValid(text))
        assertTrue(id.randomness.all { it == 0xFF.toByte() })
    }

    @Test
    fun `text timestamp digits always stay within the representable range`() {
        val id = PikaId.parse("zzzzzzz" + "0".repeat(19))
        assertEquals(PikaId.MAX_TIMESTAMP, id.timestamp)
        assertTrue(PikaId.isValid(id.toString()))
    }

    @Test
    fun `binary input must be exactly 17 bytes`() {
        for (size in listOf(0, 1, 5, 16, 18, 32)) {
            val bytes = ByteArray(size)
            val failure = assertFailsWith<PikaIdFormatException> { PikaId.fromBinary(bytes) }
            assertTrue(failure.message.orEmpty().contains("exactly 17 bytes"))
            assertNull(PikaId.fromBinaryOrNull(bytes))
            assertFalse(PikaId.isValid(bytes.toHex()))
        }
    }

    @Test
    fun `all-zero binary input is valid and canonical`() {
        val id = PikaId.fromBinary(ByteArray(PikaId.BINARY_LENGTH))
        assertEquals("00000000000000000000000000", id.toString())
        assertEquals(0L, id.timestamp)
        assertTrue(id.randomness.all { it == 0.toByte() })
    }

    @Test
    fun `binary timestamp above the Base36 maximum is rejected`() {
        val overflow = binaryOf(timestamp = PikaId.MAX_TIMESTAMP + 1, entropy = ByteArray(12))
        assertEquals(17, overflow.size)

        val failure = assertFailsWith<PikaIdFormatException> { PikaId.fromBinary(overflow) }
        assertTrue(failure.message.orEmpty().contains("exceeds the maximum"))
        assertNull(PikaId.fromBinaryOrNull(overflow))

        val fortyBitMaximum = ByteArray(17) { index -> if (index < 5) 0xFF.toByte() else 0 }
        assertFailsWith<PikaIdFormatException> { PikaId.fromBinary(fortyBitMaximum) }
    }

    @Test
    fun `binary timestamp bytes are read as unsigned`() {
        val twoToThirtyFirst = binaryOf(timestamp = 2_147_483_648L, entropy = ByteArray(12))
        assertEquals(2_147_483_648L, PikaId.fromBinary(twoToThirtyFirst).timestamp)

        val oneHundredTwentyEight = binaryOf(timestamp = 128L, entropy = ByteArray(12))
        assertEquals(128L, PikaId.fromBinary(oneHundredTwentyEight).timestamp)
    }

    @Test
    fun `randomness copies are defensive`() {
        val id = PikaId.parse("0s44we8002loz3po6mcf6vsn23")
        val expectedHex = id.randomnessHex

        val randomness = id.randomness
        randomness[0] = 0x7F
        assertEquals(expectedHex, id.randomnessHex)
        assertNotEquals(randomness[0], id.randomness[0])

        val binary = id.toByteArray()
        binary[5] = 0x7F
        assertEquals(expectedHex, id.randomnessHex)

        val input = PikaId.parse("0s44we8002loz3po6mcf6vsn23").toByteArray()
        val fromBinary = PikaId.fromBinary(input)
        input[16] = 0x7F
        assertEquals("0s44we8002loz3po6mcf6vsn23", fromBinary.toString())
    }

    @Test
    fun `equality and hash code follow canonical text`() {
        val first = PikaId.parse("00000010000000000000000001")
        val second = PikaId.parse("00000010000000000000000001")
        val otherEntropy = PikaId.parse("00000010000000000000000002")
        val otherTimestamp = PikaId.parse("00000020000000000000000001")

        assertEquals(first, second)
        assertTrue(first.equals(first))
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, otherEntropy)
        assertNotEquals(first, otherTimestamp)
        assertNotEquals<Any>(first, "00000010000000000000000001")
    }

    @Test
    fun `same-second ordering follows randomness not generation order`() {
        val maximumRandomness = PikaId.parse("00000017oiylpimjg5u2ca1ypr")
        val zeroRandomness = PikaId.parse("00000010000000000000000000")

        assertTrue(maximumRandomness > zeroRandomness)
        assertEquals(0, maximumRandomness.compareTo(maximumRandomness))
    }

    @Test
    fun `different-second ordering follows timestamp prefix`() {
        val earlierMaximum = PikaId.parse("0000001" + "7oiylpimjg5u2ca1ypr")
        val laterMinimum = PikaId.parse("0000002" + "0".repeat(19))

        assertTrue(earlierMaximum < laterMinimum)
        assertTrue(earlierMaximum.toByteArray().toUnsignedHex() < laterMinimum.toByteArray().toUnsignedHex())
    }
}

internal fun binaryOf(
    timestamp: Long,
    entropy: ByteArray,
): ByteArray {
    require(entropy.size == PikaId.RANDOMNESS_BYTES)
    val binary = ByteArray(PikaId.BINARY_LENGTH)
    binary[0] = (timestamp ushr 32).toByte()
    binary[1] = (timestamp ushr 24).toByte()
    binary[2] = (timestamp ushr 16).toByte()
    binary[3] = (timestamp ushr 8).toByte()
    binary[4] = timestamp.toByte()
    entropy.copyInto(binary, destinationOffset = 5)
    return binary
}

internal fun ByteArray.toUnsignedHex(): String = toHex()
