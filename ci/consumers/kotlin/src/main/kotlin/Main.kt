import dev.pikaid.PikaId
import dev.pikaid.PikaIdFormatException

/**
 * External Kotlin consumer. Resolved from `mavenLocal()` after `publishToMavenLocal`, so it
 * proves the published artifact, its metadata, and its Kotlin stdlib dependency graph.
 */
fun main() {
    val generated = PikaId.generate()
    check(generated.toString().length == PikaId.TEXT_LENGTH) { "text length" }
    check(PikaId.isValid(generated.toString())) { "validity" }
    check(generated.toByteArray().size == PikaId.BINARY_LENGTH) { "binary length" }

    val zero = PikaId.parse("00000000000000000000000000")
    check(zero.timestamp == 0L) { "timestamp" }
    check(zero.randomnessHex == "000000000000000000000000") { "randomness hex" }
    check(PikaId.fromBinary(zero.toByteArray()) == zero) { "binary round trip" }

    val later = PikaId.parse("00000010000000000000000001")
    check(zero < later) { "ordering" }

    val rejected =
        runCatching { PikaId.parse("not-a-pikaid") }
            .exceptionOrNull()
    check(rejected is PikaIdFormatException) { "invalid text must throw, got $rejected" }

    println(
        "KOTLIN_CONSUMER_OK generated=$generated " +
            "java=${System.getProperty("java.version")} " +
            "stdlib=${kotlin.Unit::class.java.`package`?.implementationVersion}",
    )
}
