package dev.pikaid

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Arrays
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the pinned, independently generated conformance matrix.
 *
 * The matrix is produced by a Python big-integer reference algorithm (`int.to_bytes` and
 * repeated division), not by this SDK's production encoder. Each valid value is checked
 * through every applicable public entrypoint: strict parse, strict binary decode, boolean
 * validation, shape-only validation, field extraction, both conversion directions, and the
 * internal deterministic-generation seam. Expected strings and bytes are read directly
 * from the matrix, so a regression in the Kotlin encoder cannot silently update its own
 * oracle.
 */
class PikaIdConformanceMatrixTest {
    private val matrix: JsonObject by lazy {
        val stream =
            checkNotNull(javaClass.getResourceAsStream(MATRIX_RESOURCE)) {
                "missing conformance matrix $MATRIX_RESOURCE"
            }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    private data class ValidCase(
        val name: String,
        val timestamp: Long,
        val randomnessHex: String,
        val text: String,
        val binaryHex: String,
    )

    private fun validCases(): List<ValidCase> =
        matrix.getAsJsonArray("valid").map { element ->
            val value = element.asJsonObject
            ValidCase(
                name = value.get("name").asString,
                timestamp = value.get("timestamp").asLong,
                randomnessHex = value.get("randomness_hex").asString,
                text = value.get("text").asString,
                binaryHex = value.get("binary_hex").asString,
            )
        }

    @Test
    fun `matrix and official fixture digests are pinned`() {
        val meta = matrix.getAsJsonObject("meta")
        assertEquals("1.0.4", meta.get("spec_revision").asString)
        assertEquals("17014d76f568c3ac9f9ce2927c1a018fdbc36d1d", meta.get("spec_commit").asString)
        assertEquals(
            "e3f843edeb5157a5d2d25196cac0d69580810a95",
            meta.get("official_fixture_commit").asString,
        )
        assertEquals(OFFICIAL_FIXTURE_SHA256, meta.get("official_fixture_sha256").asString)
        assertTrue(
            meta.get("generated_by").asString.contains("independent"),
            "matrix must record an independent oracle",
        )

        assertEquals(MATRIX_SHA256, sha256(resourceBytes(MATRIX_RESOURCE)))
        assertEquals(OFFICIAL_FIXTURE_SHA256, sha256(resourceBytes(OFFICIAL_FIXTURE_RESOURCE)))
        assertEquals(SCHEMA_SHA256, sha256(resourceBytes(SCHEMA_RESOURCE)))
    }

    @Test
    fun `every canonical value agrees across every public entrypoint`() {
        for (case in validCases()) {
            val expectedBinary = case.binaryHex.hexToBytes()
            val expectedRandomness = case.randomnessHex.hexToBytes()

            val parsed = PikaId.parse(case.text)
            assertEquals(case.timestamp, parsed.timestamp, "${case.name}: parse timestamp")
            assertEquals(case.randomnessHex, parsed.randomnessHex, "${case.name}: parse randomness")
            assertContentEquals(expectedRandomness, parsed.randomness, "${case.name}: parse randomness bytes")
            assertEquals(case.text, parsed.toString(), "${case.name}: parse text")
            assertContentEquals(expectedBinary, parsed.toByteArray(), "${case.name}: parse binary")

            val decoded = PikaId.fromBinary(expectedBinary)
            assertEquals(case.text, decoded.toString(), "${case.name}: binary text")
            assertEquals(case.timestamp, decoded.timestamp, "${case.name}: binary timestamp")
            assertEquals(case.randomnessHex, decoded.randomnessHex, "${case.name}: binary randomness")
            assertContentEquals(expectedBinary, decoded.toByteArray(), "${case.name}: binary round trip")

            assertEquals(parsed, decoded, "${case.name}: text and binary values agree")
            assertEquals(parsed.hashCode(), decoded.hashCode(), "${case.name}: hash agrees")
            assertTrue(PikaId.isValid(case.text), "${case.name}: isValid")
            assertTrue(PikaId.isWellFormed(case.text), "${case.name}: isWellFormed")
            assertNull(PikaId.parseOrNull("${case.text}0"), "${case.name}: parseOrNull rejects extra character")
            assertNull(
                PikaId.fromBinaryOrNull(expectedBinary + byteArrayOf(0)),
                "${case.name}: fromBinaryOrNull rejects extra byte",
            )
            assertNull(
                PikaId.fromBinaryOrNull(expectedBinary.copyOf(16)),
                "${case.name}: fromBinaryOrNull rejects short input",
            )

            // The generation seam must encode the same bytes as the reference oracle.
            val generated =
                PikaId.generateAt(case.timestamp) { size ->
                    assertEquals(PikaId.RANDOMNESS_BYTES, size, "${case.name}: entropy request size")
                    expectedRandomness.copyOf()
                }
            assertEquals(case.text, generated.toString(), "${case.name}: deterministic generation text")
            assertContentEquals(
                expectedBinary,
                generated.toByteArray(),
                "${case.name}: deterministic generation binary",
            )

            // Public generation still uses the CSPRNG, so only its invariants are fixed.
            val random = PikaId.generate(case.timestamp)
            assertEquals(case.timestamp, random.timestamp, "${case.name}: public generation timestamp")
            assertEquals(PikaId.TEXT_LENGTH, random.toString().length)
            assertEquals(PikaId.RANDOMNESS_BYTES, random.randomness.size)
            assertEquals(random, PikaId.parse(random.toString()))
            assertEquals(case.timestamp, PikaId.fromBinary(PikaId.generateBinary(case.timestamp)).timestamp)
        }
    }

    @Test
    fun `text and binary round trips are lossless in both directions`() {
        for (case in validCases()) {
            val fromText = PikaId.parse(case.text)
            assertContentEquals(case.binaryHex.hexToBytes(), fromText.toByteArray())
            assertEquals(case.text, fromText.toString())

            val fromBinary = PikaId.fromBinary(case.binaryHex.hexToBytes())
            assertContentEquals(case.binaryHex.hexToBytes(), fromBinary.toByteArray())
            assertEquals(case.text, fromBinary.toString())

            assertEquals(fromText, PikaId.fromBinary(fromText.toByteArray()))
            assertEquals(fromBinary, PikaId.parse(fromBinary.toString()))
        }
    }

    @Test
    fun `shape-valid suffixes above 96 bits are not semantically valid`() {
        val invalid = matrix.getAsJsonArray("invalid_text").map { it.asJsonObject }
        assertTrue(invalid.isNotEmpty())

        for (case in invalid) {
            val text = case.get("text").asString
            val name = case.get("name").asString
            val wellFormed = case.get("well_formed").asBoolean

            assertEquals(wellFormed, PikaId.isWellFormed(text), "$name: shape-only result")
            assertFalse(PikaId.isValid(text), "$name: must not be semantically valid")
            assertNull(PikaId.parseOrNull(text), "$name: parseOrNull result")
            val failure = assertFailsWith<PikaIdFormatException>("$name: parse must reject") { PikaId.parse(text) }
            assertTrue(failure.message.orEmpty().isNotEmpty(), "$name: diagnostic")
        }
    }

    @Test
    fun `invalid binary buffers are rejected without truncation`() {
        val invalid = matrix.getAsJsonArray("invalid_binary").map { it.asJsonObject }
        assertTrue(invalid.isNotEmpty())

        for (case in invalid) {
            val bytes = case.get("binary_hex").asString.hexToBytes()
            val name = case.get("name").asString

            assertNull(PikaId.fromBinaryOrNull(bytes), "$name: fromBinaryOrNull result")
            val failure =
                assertFailsWith<PikaIdFormatException>("$name: fromBinary must reject") {
                    PikaId.fromBinary(bytes)
                }
            assertTrue(failure.message.orEmpty().isNotEmpty(), "$name: diagnostic")
        }
    }

    @Test
    fun `ordering follows timestamps in text and unsigned binary order`() {
        val ordering = matrix.getAsJsonObject("ordering")
        val cases = ordering.getAsJsonArray("cases").map { it.asJsonObject }
        assertTrue(cases.size >= 2)

        val ids = cases.map { PikaId.parse(it.get("text").asString) }
        val ascending = ids.sorted()
        val descending = ascending.reversed()

        assertEquals(ids.map { it.toString() }, ascending.map { it.toString() })
        assertEquals(ascending.map { it.toString() }.sorted(), ascending.map { it.toString() })
        assertEquals(descending.map { it.toString() }.sortedDescending(), descending.map { it.toString() })

        for (left in ids) {
            for (right in ids) {
                val textOrder = Integer.signum(left.toString().compareTo(right.toString()))
                val binaryOrder =
                    Integer.signum(Arrays.compareUnsigned(left.toByteArray(), right.toByteArray()))
                assertEquals(textOrder, Integer.signum(left.compareTo(right)), "$left vs $right")
                assertEquals(textOrder, binaryOrder, "$left vs $right")
            }
        }

        // Same-second values order by randomness, never by generation sequence.
        val first = PikaId.parse("0000001" + "0".repeat(19))
        val second = PikaId.parse("0000001" + "7oiylpimjg5u2ca1ypr")
        assertTrue(first < second)
    }

    @Test
    fun `generation validates the timestamp before consuming entropy`() {
        for (timestamp in listOf(-1L, PikaId.MAX_TIMESTAMP + 1, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var draws = 0
            val source =
                EntropySource {
                    draws++
                    ByteArray(PikaId.RANDOMNESS_BYTES)
                }

            assertFailsWith<IllegalArgumentException> { PikaId.generateAt(timestamp, source) }
            assertEquals(0, draws, "entropy must not be consumed for timestamp $timestamp")

            draws = 0
            assertFailsWith<IllegalArgumentException> { PikaId.generateBinary(timestamp) }
            assertEquals(0, draws, "binary generation must not consume entropy for $timestamp")
        }
    }

    @Test
    fun `clock failure is rejected before entropy is consumed`() {
        for (timestamp in listOf(-1L, PikaId.MAX_TIMESTAMP + 1)) {
            var draws = 0
            val failure =
                assertFailsWith<PikaIdGenerationException> {
                    PikaId.generateAt(
                        EntropySource {
                            draws++
                            ByteArray(PikaId.RANDOMNESS_BYTES)
                        },
                    ) { timestamp }
                }
            assertTrue(failure.message.orEmpty().contains("system clock"))
            assertEquals(0, draws)
        }
    }

    private fun resourceBytes(resource: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" }
        return stream.use { it.readBytes() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        const val MATRIX_RESOURCE = "/conformance/pikaid-conformance-matrix.json"
        const val OFFICIAL_FIXTURE_RESOURCE = "/vectors/pikaid-v1.json"
        const val SCHEMA_RESOURCE = "/vectors/pikaid-v1.schema.json"
        const val MATRIX_SHA256 = "ca0bf8c95dc59f297801ac46f7adef603908d01cf03d43a600d2128477d6b965"
        const val OFFICIAL_FIXTURE_SHA256 = "b0c9dfdd063613424414fef5c9065fbd2f81d645d0ab42b34e27cde715613500"
        const val SCHEMA_SHA256 = "4441d4dbaa6c5450ae9cbfb2438c53bc0421bba2b7fcc9ef9b158e5d1de0759e"
    }
}
