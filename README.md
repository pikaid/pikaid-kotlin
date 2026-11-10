<p align="center">
  <img src="https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/logo.png" alt="pikaid" width="300" />
</p>

<p align="center">
  <strong>Small · Sortable · Secure ID generator</strong>
</p>

# pikaid — Official Kotlin Implementation

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg?logo=kotlin&logoColor=white)](#requirements)
[![JVM](https://img.shields.io/badge/JVM-17%2B-important.svg)](#requirements)
[![CI](https://github.com/pikaid/pikaid-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/pikaid/pikaid-kotlin/actions/workflows/ci.yml)

**pikaid** is a compact, chronologically sortable, collision-resistant identifier for
databases, APIs, and distributed systems. This repository is the **official Kotlin/JVM
implementation** of the immutable, unversioned [pikaid format](https://github.com/pikaid/pikaid-specs),
documented in specification revision **1.0.4**.

> **Status: stable 1.0.0.** This is the first stable release of the Kotlin implementation.
> The `1.0.0` artifacts are prepared and verified locally but have **not** been published to
> Maven Central or any other registry yet; installation from source is shown below.

## Format at a glance

| Property | Value |
| --- | --- |
| Text form | exactly **26 lowercase ASCII Base36** characters |
| Binary form | exactly **17 bytes** |
| Timestamp | 7 Base36 characters / 5 unsigned big-endian bytes, whole Unix seconds |
| Timestamp range | `0 .. 78,364,164,095` (`36^7 - 1`) |
| Randomness | 19 Base36 characters / 12 bytes, exactly 96 bits |
| Ordering | ASCII-compatible lexicographic, which equals unsigned binary order |

A pikaid is not monotonic within a single second: two IDs generated in the same second
sort by their random suffix, not by generation order. The format is immutable and
unversioned; specification revisions clarify it without changing generated values.

## Requirements

- **Java 17+** at runtime (the published bytecode targets JVM 17).
- **JDK 21** to build by default; the Gradle build uses a Java 21 toolchain and compiles
  Kotlin 2.2. A JDK 17 toolchain can be selected with `-PjvmToolchain=17` (the CI matrix
  builds with both 17 and 21).
- Gradle is provided by the wrapper; no local Gradle installation is needed.

Kotlin Multiplatform and JavaScript/Native targets are **not** supported or claimed.

## Installation

No registry release has been published yet. To consume a local build:

```bash
./gradlew publishToMavenLocal
```

Then add the dependency (the same coordinates are reserved for the registry release):

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.pikaid:pikaid-kotlin:1.0.0")
}
```

## Quick start (Kotlin)

```kotlin
import dev.pikaid.PikaId
import dev.pikaid.PikaIdFormatException

// Generate from the current time and 96 bits from the platform CSPRNG.
val id = PikaId.generate()
println(id)                 // e.g. 0s44we8002loz3po6mcf6vsn23
println(id.timestamp)       // whole Unix seconds
println(id.randomnessHex)   // exactly 24 lowercase hex characters

// Strict parsing: no trimming, case folding, or other coercion.
val parsed = PikaId.parse(id.toString())
check(parsed == id)

// Lossless 17-byte round trip.
val binary: ByteArray = id.toByteArray()
check(PikaId.fromBinary(binary) == id)

// Generate for an explicit timestamp.
val fixed = PikaId.generate(1_700_000_000L)
check(fixed.timestamp == 1_700_000_000L)

// Non-throwing variants return null for invalid input.
check(PikaId.parseOrNull("not-a-pikaid") == null)
check(PikaId.fromBinaryOrNull(ByteArray(16)) == null)

try {
    PikaId.parse("000000A0000000000000000000")
} catch (failure: PikaIdFormatException) {
    println(failure.message) // invalid character at index 6: U+0041; expected [0-9a-z]
}
```

## Java usage

Kotlin companion members are reached through `PikaId.Companion`:

```java
import dev.pikaid.PikaId;

PikaId id = PikaId.Companion.generate();
long timestamp = id.getTimestamp();
byte[] entropy = id.getRandomness();      // defensive copy, 12 bytes
byte[] binary = id.toByteArray();         // defensive copy, 17 bytes

PikaId restored = PikaId.Companion.fromBinary(binary);
boolean valid = PikaId.Companion.isValid("0s44we8002loz3po6mcf6vsn23");
```

## Public API

| Member | Purpose |
| --- | --- |
| `PikaId.generate()` | Generate from the current UTC second and CSPRNG entropy. |
| `PikaId.generate(timestamp: Long)` | Generate for an explicit whole-second timestamp. |
| `PikaId.generateBinary()` | Generate and return the 17-byte binary form. |
| `PikaId.generateBinary(timestamp: Long)` | Generate for an explicit timestamp, returned as binary. |
| `PikaId.parse(text: String)` | Strictly parse and validate the 26-character text form. |
| `PikaId.parseOrNull(text: String)` | Like `parse`, returning `null` instead of throwing. |
| `PikaId.fromBinary(bytes: ByteArray)` | Strictly decode the 17-byte binary form. |
| `PikaId.fromBinaryOrNull(bytes: ByteArray)` | Like `fromBinary`, returning `null` instead of throwing. |
| `PikaId.isValid(text: String)` | `true` only when full semantic validation succeeds. |
| `PikaId.isWellFormed(text: String)` | `true` when length and alphabet are correct; does **not** check the 96-bit bound. |
| `id.timestamp` | Unsigned Unix seconds, `0 .. 78,364,164,095`. |
| `id.randomness` | Defensive copy of the 12 entropy bytes. |
| `id.randomnessHex` | The same entropy as 24 lowercase hex characters. |
| `id.toByteArray()` | Fresh 17-byte representation. |
| `id.compareTo(other)` | ASCII-compatible lexicographic order (equals unsigned binary order). |
| `id.toString()` | Canonical 26-character form. |
| `PikaId.TEXT_LENGTH`, `BINARY_LENGTH`, `RANDOMNESS_BYTES`, `MAX_TIMESTAMP` | Format constants. |

Invalid text or binary input throws `PikaIdFormatException` (an
`IllegalArgumentException`). A failed generation prerequisite throws
`PikaIdGenerationException` (an `IllegalStateException`); no partial or fallback ID is
returned.

## Validation rules

The Kotlin implementation rejects, without coercion:

- any text that is not exactly 26 ASCII characters;
- uppercase letters and every character outside `[0-9a-z]`, including whitespace,
  punctuation, and non-ASCII code points;
- randomness characters that decode above `2^96 - 1`;
- binary input that is not exactly 17 bytes;
- binary timestamps above `78,364,164,095`, including full 40-bit overflow;
- explicit generation timestamps outside `0 .. 78,364,164,095`.

`isWellFormed` is deliberately only a shape check: `0000000zzzzzzzzzzzzzzzzzzz` is well
formed but **not** valid, because its randomness overflows 96 bits. Use `isValid` or
`parse` for semantic validation.

## Ordering

Ordering uses the canonical text and is identical to unsigned bytewise ordering of the
17-byte form:

- IDs with different timestamps sort chronologically;
- IDs with the same timestamp sort by randomness, which is **not** generation order;
- sorting is **not** monotonic within one second, and there is no sub-second precision.

## Security notes

- Randomness comes from the platform `java.security.SecureRandom` (12 independent bytes);
  generation fails closed if the source throws or returns the wrong number of bytes.
- A pikaid is **not** an authentication token, password, or secret. Do not use it as one.
- Uniqueness and secrecy are **not** guaranteed. The 96-bit random suffix makes collisions
  very unlikely, but applications that require uniqueness must still enforce it (for
  example with a database constraint).
- The timestamp reveals creation time to one-second precision.

## Building and testing

```bash
./gradlew build              # compile, test, ktlint, Kover, and archive hygiene
./gradlew test               # JUnit 5 tests
./gradlew ktlintCheck        # style check
./gradlew koverHtmlReport    # build/reports/kover/html/index.html
./gradlew dokkaJavadocJar    # build/libs/*-javadoc.jar from the KDoc
```

The build defaults to a JDK 21 toolchain. Set `JAVA_HOME` if your default JDK differs, or
select the JDK 17 toolchain explicitly with `./gradlew build -PjvmToolchain=17`. Tests run
on the JVM only.

## Releasing

The `1.0.0` release produces three archives: the library JAR, a sources JAR, and a Javadoc
JAR generated from the Kotlin KDoc. Archive entry timestamps and ordering are fixed, so the
library and sources JARs are byte-for-byte identical when rebuilt with either the JDK 17 or
the JDK 21 toolchain. The Javadoc JAR is reproducible for a given toolchain but not across
toolchains: Dokka embeds links to that JDK's generated API documentation, so JDK 17 and JDK
21 produce the same content with different `docs.oracle.com` version links. `build` fails if
any archive contains a local-only `.ai/` entry.

Publishing to Maven Central is a manual, credentialed step and is **not** performed by CI.
Before publishing, the owner must confirm:

- the `dev.pikaid` namespace is verified for the target registry account;
- a non-snapshot version (`1.0.0`) and the `v1.0.0` Git tag are approved;
- a PGP signing key and registry credentials are available.

With `SIGNING_KEY` and `SIGNING_PASSWORD` set, every publication is signed; without them,
ordinary builds and `publishToMavenLocal` run unsigned.

```bash
./gradlew clean build dokkaJavadocJar   # tests, lint, coverage, and archive hygiene
./gradlew publishToMavenLocal           # install 1.0.0 into the local Maven cache
```

No registry repository is configured in this build, so `./gradlew publish` uploads nothing.
Adding the registry repository, its credentials, and the signed release bundle is a separate
owner-approved step.

## Conformance

The authoritative conformance evidence is the shared official suite: **pikaid-specs
conformance revision `1.0.0`** (`status=OFFICIAL`, spec revision `1.0.4`), pinned to commit
[`059780cc0e5b113443812720d95c630649bf3aa3`](https://github.com/pikaid/pikaid-specs/tree/059780cc0e5b113443812720d95c630649bf3aa3).
It contributes 1,100 fixture entries: 596 valid, 258 invalid-string, 33 invalid-binary,
186 ordering, 12 generation, and 15 property cases.

The suite talks to this library through a test-only JSON-Lines bridge,
`src/test/kotlin/dev/pikaid/conformance/PikaIdJsonlAdapter.kt`. The bridge calls the real
public API (`generate`, `parse`, `fromBinary`, `isValid`, `isWellFormed`, `compareTo`, and
the binary conversions) plus the existing internal `generateAt` entropy seam used only to
inject a CSPRNG failure. It is never published. Advertised optional capabilities are
`boolean_validation`, `format_only_validation`, `parse_components`, `parse_datetime`,
`generate_with_timestamp`, `generate_binary`, `generate_binary_with_timestamp`,
`public_compare`, and `csprng_failure_injection`.

```bash
# 1. Build the test-only adapter (main library, adapter, and runtime dependencies).
./gradlew conformanceAdapterJar
# -> build/conformance/pikaid-conformance-adapter.jar

# 2. Check out the pinned suite outside this repository and install its tooling.
git clone https://github.com/pikaid/pikaid-specs "$HOME/pikaid-specs"
git -C "$HOME/pikaid-specs" checkout 059780cc0e5b113443812720d95c630649bf3aa3
python3 -m pip install -r "$HOME/pikaid-specs/conformance/tools/requirements.txt"

# 3. Fail closed on checksums, schema, revisions, fixture counts, and the independent oracle.
cd "$HOME/pikaid-specs/conformance"
python3 tools/verify_vectors.py \
  --expect-status OFFICIAL --expect-package-revision 1.0.0 --expect-spec-revision 1.0.4
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/tests -v
node tools/verify_oracle_node.mjs

# 4. Run every automated case through the real library.
python3 tools/run_adapter.py --automated-only \
  --report-json "$OLDPWD/build/conformance/official-report.json" \
  -- java -jar "$OLDPWD/build/conformance/pikaid-conformance-adapter.jar"
```

Exit code `0` means the automated cases passed; `1` means an automated failure; `2` means
the adapter broke the protocol; `3` means automated cases passed but the manual security
audit is outstanding. **An automated-only pass is not a conformance certification** and
does not prove CSPRNG quality. The suite still requires human review of the production
generation path and its failure behavior (`P-GENERATE-RNG-SOURCE`,
`P-GENERATE-RNG-FAILURE`). The full suite without `--automated-only` therefore exits `3`
unless a reviewed security-evidence record is supplied.

CI runs the same pinned suite on every push and pull request, together with the native JVM
matrix and external Java and Kotlin consumers, in
[`.github/workflows/ci.yml`](.github/workflows/ci.yml).

The vendored [pikaid-php v1.2.0](https://github.com/pikaid/pikaid-php/tree/v1.2.0) fixture
under `src/test/resources/vectors/` is retained as an auxiliary, digest-pinned regression
fixture. It is not the authoritative conformance corpus; its valid and invalid values are a
subset of the shared suite, and the one extra malformed case (`_`) is covered by the
independent 65-case matrix.

## Specification and license

- Canonical specification: [pikaid-specs revision 1.0.4](https://github.com/pikaid/pikaid-specs/tree/v1.0.4),
  pinned at commit `17014d76f568c3ac9f9ce2927c1a018fdbc36d1d`.
- This implementation is released under the [MIT License](LICENSE).
