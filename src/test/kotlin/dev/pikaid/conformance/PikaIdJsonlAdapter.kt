package dev.pikaid.conformance

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.pikaid.EntropySource
import dev.pikaid.PikaId
import dev.pikaid.PikaIdFormatException
import dev.pikaid.PikaIdGenerationException
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Test-only JSON-Lines bridge for the official PikaID conformance suite.
 *
 * This bridge is not part of the published library. It translates the suite's wire
 * protocol into calls on the real public API (plus the internal deterministic-entropy
 * seam used solely to inject a CSPRNG failure). It never computes an alternate PikaID,
 * never coerces invalid input, and never maps an unexpected failure to input validation.
 *
 * Contract: https://github.com/pikaid/pikaid-specs conformance `docs/JSONL_ADAPTER_PROTOCOL.md`.
 */
internal object PikaIdJsonlAdapter {
    private const val PROTOCOL_VERSION = 1
    private const val INJECTION_FAULT = "csprng_failure"

    private val gson = Gson()

    private val capabilities: List<String> =
        listOf(
            "generate_string",
            "text_to_binary",
            "binary_to_text",
            "boolean_validation",
            "format_only_validation",
            "parse_components",
            "parse_datetime",
            "generate_with_timestamp",
            "generate_binary",
            "generate_binary_with_timestamp",
            "public_compare",
            "csprng_failure_injection",
        )

    @Volatile
    private var csprngFailureActive: Boolean = false

    @JvmStatic
    public fun main(args: Array<String>) {
        val input = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        val output = BufferedWriter(OutputStreamWriter(System.out, Charsets.UTF_8))
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue
            val response =
                try {
                    handleLine(line)
                } catch (failure: ProtocolViolation) {
                    System.err.println("protocol violation: ${failure.message}")
                    exitProcess(2)
                }
            output.write(response)
            output.newLine()
            output.flush()
        }
    }

    /**
     * Handles one protocol request line and returns exactly one response object as JSON.
     *
     * @throws ProtocolViolation when the request envelope itself is unusable, in which case
     *   no trustworthy `request_id` exists to echo.
     */
    internal fun handleLine(line: String): String {
        val request =
            try {
                JsonParser.parseString(line)
            } catch (failure: RuntimeException) {
                throw ProtocolViolation("request is not valid JSON: ${failure.message}")
            }
        if (!request.isJsonObject) throw ProtocolViolation("request must be a JSON object")
        val envelope = request.asJsonObject
        val requestId = integerField(envelope, "request_id")
        val operation = stringField(envelope, "operation")
        val arguments =
            when (val raw = envelope.get("arguments")) {
                null, is com.google.gson.JsonNull -> JsonObject()
                else ->
                    if (raw.isJsonObject) {
                        raw.asJsonObject
                    } else {
                        return rejection(requestId, "invalid_input", "arguments must be a JSON object")
                    }
            }

        return try {
            success(requestId, dispatch(operation, arguments))
        } catch (failure: InvalidInputException) {
            rejection(requestId, "invalid_input", messageOf(failure))
        } catch (failure: PikaIdFormatException) {
            rejection(requestId, "invalid_input", messageOf(failure))
        } catch (failure: PikaIdGenerationException) {
            rejection(requestId, "entropy_unavailable", messageOf(failure))
        } catch (failure: IllegalArgumentException) {
            rejection(requestId, "invalid_input", messageOf(failure))
        } catch (failure: UnsupportedRequestException) {
            rejection(requestId, "unsupported_operation", messageOf(failure))
        } catch (failure: Throwable) {
            rejection(requestId, "internal_error", messageOf(failure))
        }
    }

    private fun dispatch(
        operation: String,
        arguments: JsonObject,
    ): JsonElement =
        when (operation) {
            "hello" -> hello()
            "generate_string" -> JsonPrimitive(generateId(timestamp = null).toString())
            "generate_with_timestamp" ->
                JsonPrimitive(generateId(timestamp = timestampField(arguments)).toString())
            "generate_binary" -> JsonPrimitive(generateBinary(timestamp = null).toLowerHex())
            "generate_binary_with_timestamp" ->
                JsonPrimitive(generateBinary(timestamp = timestampField(arguments)).toLowerHex())
            "text_to_binary" ->
                JsonPrimitive(PikaId.parse(stringField(arguments, "text")).toByteArray().toLowerHex())
            "binary_to_text" ->
                JsonPrimitive(PikaId.fromBinary(parseBinaryHex(stringField(arguments, "binary_hex"))).toString())
            "boolean_validation" -> JsonPrimitive(PikaId.isValid(stringField(arguments, "text")))
            "format_only_validation" -> JsonPrimitive(PikaId.isWellFormed(stringField(arguments, "text")))
            "parse_components" -> parseComponents(stringField(arguments, "text"))
            "public_compare" -> {
                val a = PikaId.parse(stringField(arguments, "a"))
                val b = PikaId.parse(stringField(arguments, "b"))
                JsonPrimitive(a.compareTo(b))
            }
            "inject_fault" -> injectFault(arguments)
            else -> throw UnsupportedRequestException("unsupported operation: $operation")
        }

    private fun hello(): JsonObject =
        JsonObject().apply {
            add("protocol_version", JsonPrimitive(PROTOCOL_VERSION))
            add(
                "capabilities",
                JsonArray().apply { capabilities.forEach { add(JsonPrimitive(it)) } },
            )
        }

    private fun parseComponents(text: String): JsonObject {
        val id = PikaId.parse(text)
        return JsonObject().apply {
            add("timestamp_seconds", JsonPrimitive(id.timestamp))
            add("randomness_hex", JsonPrimitive(id.randomnessHex))
            add("datetime_utc", JsonPrimitive(Instant.ofEpochSecond(id.timestamp).toString()))
        }
    }

    private fun injectFault(arguments: JsonObject): JsonObject {
        val fault = stringField(arguments, "fault")
        if (fault != INJECTION_FAULT) {
            throw InvalidInputException("unsupported fault: $fault")
        }
        val active = booleanField(arguments, "active")
        csprngFailureActive = active
        return JsonObject().apply {
            add("fault", JsonPrimitive(fault))
            add("active", JsonPrimitive(active))
        }
    }

    private fun generateId(timestamp: Long?): PikaId =
        if (csprngFailureActive) {
            if (timestamp == null) {
                PikaId.generateAt(failingEntropySource()) { Instant.now().epochSecond }
            } else {
                PikaId.generateAt(timestamp, failingEntropySource())
            }
        } else {
            if (timestamp == null) PikaId.generate() else PikaId.generate(timestamp)
        }

    private fun generateBinary(timestamp: Long?): ByteArray =
        if (csprngFailureActive) {
            generateId(timestamp).toByteArray()
        } else {
            if (timestamp == null) PikaId.generateBinary() else PikaId.generateBinary(timestamp)
        }

    private fun failingEntropySource(): EntropySource =
        EntropySource { throw IllegalStateException("injected CSPRNG failure") }

    private fun parseBinaryHex(value: String): ByteArray {
        if (!LOWER_HEX.matches(value)) {
            throw InvalidInputException("binary_hex must contain only lowercase hexadecimal digits")
        }
        if (value.length % 2 != 0) {
            throw InvalidInputException("binary_hex must contain an even number of digits")
        }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toLowerHex(): String =
        buildString(size * 2) {
            for (byte in this@toLowerHex) {
                val value = byte.toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }

    private fun integerField(
        objectValue: JsonObject,
        name: String,
    ): Long {
        val element = objectValue.get(name) ?: throw ProtocolViolation("missing $name")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw ProtocolViolation("$name must be an integer")
        }
        val raw = element.asString
        if (!INTEGER_TEXT.matches(raw)) throw ProtocolViolation("$name must be an integer")
        return raw.toLongOrNull() ?: throw ProtocolViolation("$name is not a representable integer")
    }

    private fun stringField(
        objectValue: JsonObject,
        name: String,
    ): String {
        val element = objectValue.get(name) ?: throw InvalidInputException("missing $name argument")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw InvalidInputException("$name must be a string")
        }
        return element.asString
    }

    private fun timestampField(arguments: JsonObject): Long {
        val element = arguments.get("timestamp_seconds") ?: throw InvalidInputException("missing timestamp_seconds")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            throw InvalidInputException("timestamp_seconds must be an integer")
        }
        val raw = element.asString
        if (!INTEGER_TEXT.matches(raw)) throw InvalidInputException("timestamp_seconds must be an integer")
        return raw.toLongOrNull() ?: throw InvalidInputException("timestamp_seconds is out of range")
    }

    private fun booleanField(
        objectValue: JsonObject,
        name: String,
    ): Boolean {
        val element = objectValue.get(name) ?: throw InvalidInputException("missing $name argument")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            throw InvalidInputException("$name must be a boolean")
        }
        return element.asBoolean
    }

    private fun success(
        requestId: Long,
        value: JsonElement,
    ): String =
        gson.toJson(
            JsonObject().apply {
                add("request_id", JsonPrimitive(requestId))
                add("ok", JsonPrimitive(true))
                add("value", value)
            },
        )

    private fun rejection(
        requestId: Long,
        category: String,
        message: String,
    ): String =
        gson.toJson(
            JsonObject().apply {
                add("request_id", JsonPrimitive(requestId))
                add("ok", JsonPrimitive(false))
                add(
                    "error",
                    JsonObject().apply {
                        add("category", JsonPrimitive(category))
                        add("message", JsonPrimitive(message.ifEmpty { category }))
                    },
                )
            },
        )

    private fun messageOf(failure: Throwable): String =
        failure.message?.takeIf { it.isNotEmpty() } ?: failure.javaClass.name

    private val LOWER_HEX = Regex("[0-9a-f]*")
    private val INTEGER_TEXT = Regex("-?[0-9]+")
    private val HEX_DIGITS = "0123456789abcdef"

    private class InvalidInputException(
        message: String,
    ) : RuntimeException(message)

    private class UnsupportedRequestException(
        message: String,
    ) : RuntimeException(message)

    private class ProtocolViolation(
        message: String,
    ) : RuntimeException(message)
}
