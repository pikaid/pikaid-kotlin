package dev.pikaid

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs every vector from the official PHP v1.2.0 conformance fixture.
 *
 * The fixture is vendored verbatim; the test derives no expected value from [PikaId].
 */
class PikaIdOfficialVectorsTest {
    private val fixture: JsonObject by lazy {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/vectors/pikaid-v1.json")) {
                "missing official vector fixture"
            }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    @Test
    fun `fixture metadata matches the immutable format`() {
        val meta = fixture.getAsJsonObject("meta")
        assertEquals("pikaid", meta.get("project").asString)
        assertEquals(26, meta.get("string_length").asInt)
        assertEquals("immutable and unversioned", meta.get("identifier_format").asString)
        assertEquals("1.0.4", meta.get("spec_revision").asString)

        val timestamp = meta.getAsJsonObject("timestamp")
        assertEquals(7, timestamp.get("chars").asInt)
        assertEquals(0L, timestamp.get("min").asLong)
        assertEquals(PikaId.MAX_TIMESTAMP, timestamp.get("max").asLong)

        val entropy = meta.getAsJsonObject("entropy")
        assertEquals(12, entropy.get("bytes").asInt)
        assertEquals(19, entropy.get("chars").asInt)

        val binary = meta.getAsJsonObject("binary")
        assertEquals(17, binary.get("bytes").asInt)
        assertEquals("[5 bytes timestamp big-endian][12 bytes entropy]", binary.get("layout").asString)
    }

    @Test
    fun `fixture contains the expected number of cases`() {
        assertEquals(4, fixture.getAsJsonArray("valid").size())
        val invalid = fixture.getAsJsonObject("invalid")
        assertEquals(6, invalid.getAsJsonArray("string").size())
        assertEquals(4, invalid.getAsJsonArray("binary").size())
    }

    @Test
    fun `every valid vector round-trips text and binary losslessly`() {
        for (element in fixture.getAsJsonArray("valid")) {
            val vector = element.asJsonObject
            val name = vector.get("name").asString
            val text = vector.get("id").asString
            val binaryHex = vector.get("bin_hex").asString
            val entropyHex = vector.get("entropy_hex").asString
            val expectedTimestamp = vector.get("timestamp").asLong

            val id = PikaId.parse(text)
            assertEquals(text, id.toString(), name)
            assertEquals(expectedTimestamp, id.timestamp, name)
            assertEquals(entropyHex, id.randomnessHex, name)
            assertEquals(binaryHex, id.toByteArray().toHex(), name)

            val fromBinary = PikaId.fromBinary(binaryHex.hexToBytes())
            assertEquals(text, fromBinary.toString(), name)
            assertEquals(expectedTimestamp, fromBinary.timestamp, name)
            assertEquals(entropyHex, fromBinary.randomnessHex, name)

            assertTrue(PikaId.isValid(text), name)
            assertTrue(PikaId.isWellFormed(text), name)
            assertEquals(id, PikaId.parseOrNull(text), name)
            assertEquals(id, PikaId.fromBinaryOrNull(binaryHex.hexToBytes()), name)
        }
    }

    @Test
    fun `every invalid string vector is rejected without coercion`() {
        for (element in fixture.getAsJsonObject("invalid").getAsJsonArray("string")) {
            val vector = element.asJsonObject
            val name = vector.get("name").asString
            val text = vector.get("id").asString

            assertFailsWith<PikaIdFormatException>(name) { PikaId.parse(text) }
            assertFalse(PikaId.isValid(text), name)
            assertNull(PikaId.parseOrNull(text), name)
        }
    }

    @Test
    fun `invalid string shape is distinguished from invalid semantics`() {
        val overflow = "0000000zzzzzzzzzzzzzzzzzzz"
        assertTrue(PikaId.isWellFormed(overflow))
        assertFalse(PikaId.isValid(overflow))
        assertFailsWith<PikaIdFormatException> { PikaId.parse(overflow) }

        for (shapeInvalid in listOf("0000000000000000000000000", "000000A0000000000000000000")) {
            assertFalse(PikaId.isWellFormed(shapeInvalid))
            assertFalse(PikaId.isValid(shapeInvalid))
        }
    }

    @Test
    fun `every invalid binary vector is rejected`() {
        for (element in fixture.getAsJsonObject("invalid").getAsJsonArray("binary")) {
            val vector = element.asJsonObject
            val name = vector.get("name").asString
            val bytes = vector.get("bin_hex").asString.hexToBytes()

            assertFailsWith<PikaIdFormatException>(name) { PikaId.fromBinary(bytes) }
            assertNull(PikaId.fromBinaryOrNull(bytes), name)
        }
    }
}

internal fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex input must have an even length" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
