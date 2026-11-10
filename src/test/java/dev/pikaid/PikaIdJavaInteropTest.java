package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exercises the published API from Java source, which the Kotlin test suite cannot cover.
 *
 * This is the compatibility surface a Java consumer compiles against after adding the
 * published jar to the classpath.
 */
class PikaIdJavaInteropTest {
    @Test
    void generatesParsesAndRoundTrips() {
        PikaId generated = PikaId.Companion.generate();

        assertEquals(26, generated.toString().length());
        assertEquals(generated, PikaId.Companion.parse(generated.toString()));
        assertArrayEquals(
                generated.toByteArray(),
                PikaId.Companion.fromBinary(generated.toByteArray()).toByteArray());

        PikaId fixed = PikaId.Companion.generate(0L);
        assertEquals(0L, fixed.getTimestamp());
        assertEquals(12, fixed.getRandomness().length);
        assertEquals(24, fixed.getRandomnessHex().length());
        assertEquals(17, fixed.toByteArray().length);
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(PikaIdFormatException.class, () -> PikaId.Companion.parse("not-a-pikaid"));
        assertThrows(PikaIdFormatException.class, () -> PikaId.Companion.parse("0".repeat(25)));
        assertThrows(IllegalArgumentException.class, () -> PikaId.Companion.generate(-1L));
        assertFalse(PikaId.Companion.isValid("0".repeat(27)));
    }

    @Test
    void ordersAndCompares() {
        PikaId earlier = PikaId.Companion.parse("00000000000000000000000000");
        PikaId later = PikaId.Companion.parse("00000010000000000000000000");

        assertTrue(earlier.compareTo(later) < 0);
        assertNotEquals(earlier, later);
        assertEquals(earlier, PikaId.Companion.parse(earlier.toString()));
    }

    @Test
    void randomnessIsDefensivelyCopied() {
        PikaId id = PikaId.Companion.generate(0L);
        byte[] first = id.getRandomness();
        byte original = first[0];
        first[0] = (byte) (original ^ 0xFF);

        assertEquals(original, id.getRandomness()[0]);
    }
}
