package dev.pikaid

import java.util.Arrays
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seeded randomized round trips and cross-representation ordering checks.
 *
 * The seed is fixed so failures are reproducible. Expected canonical text is derived from
 * the binary input, the timestamp and the entropy bytes, not from a second encoder in this
 * implementation.
 */
class PikaIdPropertyTest {
    private val random = Random(0x5EED_1234)

    private fun randomTimestamp(): Long =
        when (random.nextInt(4)) {
            0 -> 0L
            1 -> PikaId.MAX_TIMESTAMP
            2 -> random.nextLong(0, PikaId.MAX_TIMESTAMP + 1)
            else -> random.nextLong(0, 2_000_000_000L)
        }

    private fun randomEntropy(): ByteArray = ByteArray(PikaId.RANDOMNESS_BYTES) { random.nextInt(256).toByte() }

    private fun sample(size: Int): List<PikaId> =
        List(size) {
            PikaId.fromBinary(binaryOf(randomTimestamp(), randomEntropy()))
        }

    @Test
    fun `binary to text to parse round trips are lossless`() {
        repeat(2_000) {
            val timestamp = randomTimestamp()
            val entropy = randomEntropy()
            val binary = binaryOf(timestamp, entropy)

            val id = PikaId.fromBinary(binary)
            val text = id.toString()

            assertEquals(PikaId.TEXT_LENGTH, text.length)
            assertTrue(text.all { character -> character in '0'..'9' || character in 'a'..'z' })
            assertEquals(timestamp, id.timestamp)
            assertContentEquals(entropy, id.randomness)
            assertContentEquals(binary, id.toByteArray())
            assertEquals(id, PikaId.parse(text))
            assertContentEquals(binary, PikaId.parse(text).toByteArray())
            assertEquals(id.randomnessHex, entropy.toHex())
        }
    }

    @Test
    fun `text ordering matches unsigned binary ordering`() {
        val ids = sample(250)
        for (left in ids) {
            for (right in ids) {
                assertEquals(
                    Integer.signum(Arrays.compareUnsigned(left.toByteArray(), right.toByteArray())),
                    Integer.signum(left.compareTo(right)),
                    "$left vs $right",
                )
                assertEquals(
                    Integer.signum(left.toString().compareTo(right.toString())),
                    Integer.signum(left.compareTo(right)),
                    "$left vs $right",
                )
            }
        }
    }

    @Test
    fun `sorting text and sort order agree`() {
        val ids = sample(500)
        val byNaturalOrder = ids.sorted().map(PikaId::toString)
        val byBinaryOrder =
            ids
                .sortedWith { left, right -> Arrays.compareUnsigned(left.toByteArray(), right.toByteArray()) }
                .map(PikaId::toString)
        val byTextOrder = ids.map(PikaId::toString).sorted()

        assertEquals(byTextOrder, byNaturalOrder)
        assertEquals(byNaturalOrder, byBinaryOrder)
    }

    @Test
    fun `timestamp prefix always separates different seconds`() {
        repeat(500) {
            val earlierTimestamp = random.nextLong(0, PikaId.MAX_TIMESTAMP)
            val earlier = PikaId.fromBinary(binaryOf(earlierTimestamp, randomEntropy()))
            val later = PikaId.fromBinary(binaryOf(earlierTimestamp + 1, randomEntropy()))

            assertTrue(earlier < later, "$earlier should sort before $later")
            assertTrue(
                Arrays.compareUnsigned(earlier.toByteArray(), later.toByteArray()) < 0,
                "unsigned binary order must agree",
            )
        }
    }
}
