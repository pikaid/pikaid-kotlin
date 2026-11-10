import dev.pikaid.PikaId;
import dev.pikaid.PikaIdFormatException;
import java.util.HexFormat;

/**
 * External Java 17 consumer. Compiled and run only against the published artifact, never
 * against this repository's source set.
 */
public final class Main {
    private Main() {}

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        PikaId generated = PikaId.Companion.generate();
        check(generated.toString().length() == 26, "generated text length");
        check(PikaId.Companion.isValid(generated.toString()), "generated validity");
        check(generated.toByteArray().length == 17, "generated binary length");

        PikaId zero = PikaId.Companion.parse("00000000000000000000000000");
        check(zero.getTimestamp() == 0L, "zero timestamp");
        check(zero.getRandomness().length == 12, "randomness length");
        check(zero.getRandomnessHex().equals("000000000000000000000000"), "randomness hex");
        check(
                HexFormat.of().formatHex(zero.toByteArray())
                        .equals("0000000000000000000000000000000000"),
                "zero binary hex");

        PikaId fromBinary =
                PikaId.Companion.fromBinary(
                        HexFormat.of().parseHex("000000000000112233445566778899aabb"));
        check(fromBinary.toString().equals("0000000002loz3po6mcf6vsn23"), "binary to text");
        check(fromBinary.equals(PikaId.Companion.parse("0000000002loz3po6mcf6vsn23")), "equal values");

        PikaId later = PikaId.Companion.parse("00000010000000000000000001");
        check(zero.compareTo(later) < 0, "ordering");

        boolean rejected = false;
        try {
            PikaId.Companion.parse("not-a-pikaid");
        } catch (PikaIdFormatException expected) {
            rejected = true;
        }
        check(rejected, "invalid text must throw");

        boolean lengthRejected = false;
        try {
            PikaId.Companion.fromBinary(new byte[16]);
        } catch (PikaIdFormatException expected) {
            lengthRejected = true;
        }
        check(lengthRejected, "invalid binary must throw");

        System.out.println(
                "JAVA_CONSUMER_OK java="
                        + System.getProperty("java.version")
                        + " generated="
                        + generated);
    }
}
