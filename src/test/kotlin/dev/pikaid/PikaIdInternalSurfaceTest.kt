package dev.pikaid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the internal entropy seam.
 *
 * The seam must stay callable from Kotlin tests but must not be reachable from Java
 * source, otherwise production Java callers could substitute weak entropy.
 * `@JvmSynthetic` marks the methods as synthetic in the class file, which hides them
 * from `javac` while leaving Kotlin callers unaffected.
 */
class PikaIdInternalSurfaceTest {
    @Test
    fun `entropy seam methods are synthetic and hidden from Java`() {
        val seamMethods =
            PikaId.Companion::class.java.declaredMethods
                .filter { it.name.startsWith("generateAt") }
                .sortedBy { it.name + it.parameterCount }

        assertEquals(2, seamMethods.size, "expected both internal generateAt overloads")
        for (method in seamMethods) {
            assertTrue(
                method.isSynthetic,
                "${method.name} must be @JvmSynthetic so Java callers cannot inject entropy",
            )
        }
    }
}
