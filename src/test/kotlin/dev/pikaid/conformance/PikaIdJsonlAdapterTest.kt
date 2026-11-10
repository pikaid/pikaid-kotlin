package dev.pikaid.conformance

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the test-only JSON-Lines bridge directly, including the envelopes the official
 * runner depends on. Every assertion goes through [PikaIdJsonlAdapter.handleLine], so a
 * regression in the adapter or in the public API it calls fails the native build as well.
 */
class PikaIdJsonlAdapterTest {
    @AfterTest
    fun clearInjectedFault() {
        request(
            """{"request_id":900,"operation":"inject_fault","arguments":{"fault":"csprng_failure","active":false}}""",
        )
    }

    @Test
    fun `hello advertises the protocol version and distinct capabilities`() {
        val value = successObject(1, "hello", "{}")
        assertEquals(1, value.get("protocol_version").asInt)
        val capabilities = value.getAsJsonArray("capabilities").map { it.asString }
        assertEquals(capabilities.distinct(), capabilities)
        assertTrue(capabilities.containsAll(listOf("generate_string", "text_to_binary", "binary_to_text")))
        assertTrue(capabilities.contains("csprng_failure_injection"))
    }

    @Test
    fun `generated text converts to binary and back without loss`() {
        val text = successText(1, "generate_string", "{}")
        assertEquals(26, text.length)
        val binaryHex = successText(2, "text_to_binary", """{"text":"$text"}""")
        assertTrue(binaryHex.matches(Regex("[0-9a-f]{34}")))
        assertEquals(text, successText(3, "binary_to_text", """{"binary_hex":"$binaryHex"}"""))
    }

    @Test
    fun `parse components reports timestamp, randomness, and UTC second`() {
        val value = successObject(1, "parse_components", """{"text":"00000000000000000000000000"}""")
        assertEquals(0, value.get("timestamp_seconds").asLong)
        assertEquals("000000000000000000000000", value.get("randomness_hex").asJsonPrimitive.asString)
        assertEquals("1970-01-01T00:00:00Z", value.get("datetime_utc").asJsonPrimitive.asString)
    }

    @Test
    fun `invalid input is rejected with its category and request id`() {
        val response = request("""{"request_id":41,"operation":"text_to_binary","arguments":{"text":"not-a-pikaid"}}""")
        assertEquals(41, response.get("request_id").asLong)
        assertFalse(response.get("ok").asBoolean)
        val error = response.getAsJsonObject("error")
        assertEquals("invalid_input", error.get("category").asJsonPrimitive.asString)
        assertTrue(
            error
                .get("message")
                .asJsonPrimitive.asString
                .isNotEmpty(),
        )
    }

    @Test
    fun `uppercase or oversized binary hex is rejected without normalization`() {
        for (hex in listOf("00000000000000000000000000000000AA", "00", "zz")) {
            val response =
                request("""{"request_id":7,"operation":"binary_to_text","arguments":{"binary_hex":"$hex"}}""")
            assertEquals(
                "invalid_input",
                response
                    .getAsJsonObject("error")
                    .get("category")
                    .asJsonPrimitive.asString,
                hex,
            )
        }
    }

    @Test
    fun `out-of-range timestamps are rejected`() {
        for (timestamp in listOf(-1L, 78364164096L, 1099511627776L)) {
            val response =
                request(
                    """{"request_id":8,"operation":"generate_with_timestamp","arguments":{"timestamp_seconds":$timestamp}}""",
                )
            assertEquals(
                "invalid_input",
                response
                    .getAsJsonObject("error")
                    .get("category")
                    .asJsonPrimitive.asString,
                "$timestamp",
            )
        }
    }

    @Test
    fun `injected CSPRNG failure fails closed for every generation path`() {
        request("""{"request_id":1,"operation":"inject_fault","arguments":{"fault":"csprng_failure","active":true}}""")
        for (operation in listOf("generate_string", "generate_binary")) {
            assertEquals(
                "entropy_unavailable",
                request("""{"request_id":2,"operation":"$operation","arguments":{}}""")
                    .getAsJsonObject("error")
                    .get("category")
                    .asJsonPrimitive.asString,
                operation,
            )
        }
        for (operation in listOf("generate_with_timestamp", "generate_binary_with_timestamp")) {
            assertEquals(
                "entropy_unavailable",
                request("""{"request_id":3,"operation":"$operation","arguments":{"timestamp_seconds":0}}""")
                    .getAsJsonObject("error")
                    .get("category")
                    .asJsonPrimitive.asString,
                operation,
            )
        }

        request("""{"request_id":4,"operation":"inject_fault","arguments":{"fault":"csprng_failure","active":false}}""")
        assertEquals(26, successText(5, "generate_string", "{}").length)
    }

    @Test
    fun `unknown operations are reported instead of being guessed`() {
        val response = request("""{"request_id":9,"operation":"parse_datetime","arguments":{}}""")
        assertFalse(response.get("ok").asBoolean)
        assertEquals(
            "unsupported_operation",
            response
                .getAsJsonObject("error")
                .get("category")
                .asJsonPrimitive.asString,
        )
    }

    @Test
    fun `malformed envelopes are protocol violations`() {
        for (line in listOf("not json", """{"operation":"hello","arguments":{}}""", """{"request_id":"1"}""")) {
            val failure = assertFailsWith<RuntimeException>(line) { PikaIdJsonlAdapter.handleLine(line) }
            assertTrue(failure.message.orEmpty().isNotEmpty())
        }
    }

    private fun successObject(
        requestId: Int,
        operation: String,
        arguments: String,
    ): JsonObject = successElement(requestId, operation, arguments).asJsonObject

    private fun successText(
        requestId: Int,
        operation: String,
        arguments: String,
    ): String = successElement(requestId, operation, arguments).asJsonPrimitive.asString

    private fun successElement(
        requestId: Int,
        operation: String,
        arguments: String,
    ): JsonElement {
        val response = request("""{"request_id":$requestId,"operation":"$operation","arguments":$arguments}""")
        assertTrue(response.get("ok").asBoolean, "$operation failed: $response")
        return response.get("value")
    }

    private fun request(line: String): JsonObject =
        JsonParser.parseString(PikaIdJsonlAdapter.handleLine(line)).asJsonObject
}
