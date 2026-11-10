<p align="center">
  <a href="https://github.com/pikaid/pikaid-specs">
    <img src="https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/logo.png" alt="pikaid" width="300">
  </a>
</p>

<p align="center">
  <strong>Small · Sortable · Secure ID generator</strong>
</p>

<div align="center">

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF.svg)](https://github.com/pikaid/pikaid-kotlin)
[![CI](https://github.com/pikaid/pikaid-kotlin/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/pikaid/pikaid-kotlin/actions/workflows/ci.yml)
[![Official implementation](https://img.shields.io/badge/official-implementation-2e7d32.svg)](https://github.com/pikaid/pikaid-specs)
[![Specification 1.0.4](https://img.shields.io/badge/spec-1.0.4-4b56a6.svg)](https://github.com/pikaid/pikaid-specs/tree/v1.0.4)

</div>

**pikaid** is the **official Kotlin implementation** of a compact, chronologically sortable, collision-resistant identifier format for databases, APIs, and distributed systems.

This is the immutable, unversioned [pikaid format](https://github.com/pikaid/pikaid-specs/tree/v1.0.4), documented in specification revision **1.0.4**.

## ✨ Why pikaid?

- **Short:** Compact, readable IDs for URLs, logs, and API resources.
- **Sortable:** Lexicographically ordered by timestamp, with **one-second precision**.
- **Collision-resistant:** 96 bits of cryptographic randomness make collisions extremely unlikely, never impossible.
- **Storage-friendly:** Use `BINARY(17)` or readable `CHAR(26)`.
- **Distributed-friendly:** No central sequence generator or coordination required.

## 🧬 Identifier format

A pikaid contains **26 lowercase Base36 characters**: a **7-character Unix-seconds timestamp** followed by **19 characters encoding 96 bits of cryptographic randomness**. Its binary form is **17 bytes**: 5 unsigned big-endian timestamp bytes followed by 12 randomness bytes.

![Identifier structure](https://raw.githubusercontent.com/pikaid/pikaid-specs/v1.0.4/structure.svg)

> **Ordering:** Different seconds sort chronologically. Within the same second, randomness—not issuance order—decides the order.

> **Security:** These are identifiers, **not secrets**, session credentials, authentication tokens, or API keys.

## ⚙️ Requirements

- **Java 17+** at runtime (the published bytecode targets JVM 17).
- **JDK 21** to build by default; the Gradle build uses a Java 21 toolchain and compiles
  Kotlin 2.2. A JDK 17 toolchain can be selected with `-PjvmToolchain=17` (the CI matrix
  builds with both 17 and 21).
- Gradle is provided by the wrapper; no local Gradle installation is needed.

Kotlin Multiplatform and JavaScript/Native targets are **not** supported or claimed.

## 📦 Installation

From the public source repository:

```sh
git clone https://github.com/pikaid/pikaid-kotlin.git
cd pikaid-kotlin
```

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

## 🚀 Basic Usage

Put this example in a Kotlin `main.kt` in a consumer project with the dependency configured above:

```kotlin
import dev.pikaid.PikaId
import dev.pikaid.PikaIdFormatException

fun main() {
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
}
```

## 💡 Example Use Case

Use pikaid to identify **users, orders, invoices, uploads, events, and API resources**. Independent services can generate compact IDs without a shared counter, while timestamp-based ordering helps with indexing, sorting, and logs.

## 📚 Canonical specification

- [Canonical specification](https://github.com/pikaid/pikaid-specs)
- [Revision 1.0.4](https://github.com/pikaid/pikaid-specs/tree/v1.0.4)

Specification revisions describe the format; they do not change the encoded identifiers.

## 🧩 API Reference

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

## 🔄 Order Guarantee

Ordering uses the canonical text and is identical to unsigned bytewise ordering of the
17-byte form:

- IDs with different timestamps sort chronologically;
- IDs with the same timestamp sort by randomness, which is **not** generation order;
- sorting is **not** monotonic within one second, and there is no sub-second precision.

## 🛢 Storage Recommendations

| Representation | Size | Recommended for |
| --- | ---: | --- |
| **BINARY(17)** | 17 bytes | Compact primary keys and indexes |
| **CHAR(26)** | 26 characters | Readable SQL, debugging, URLs, and logs |

Use an **ASCII bytewise/binary collation** for text columns (for example, `ascii_bin` on MariaDB/MySQL). Case-insensitive or locale-aware sorting is not a substitute for canonical bytewise comparison.

## ✅ Verification and release status

The [CI workflow](https://github.com/pikaid/pikaid-kotlin/actions/workflows/ci.yml) runs native tests, pinned automated conformance checks, and language-specific quality gates. To run the native checks locally from a checkout, use `./gradlew build`.

**Automated passing tests are not a security certification.** The independent CSPRNG source and failure-path audits (`P-GENERATE-RNG-SOURCE` and `P-GENERATE-RNG-FAILURE`) still require separate human evidence.

<details>
<summary>Platform, tooling, and conformance details</summary>

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

</details>

Registry publication is not implied by a green CI: use the installation method documented above.

## 🤝 Contributing

Contributions are always welcome! 💙

Whether you're fixing a typo, improving documentation, adding tests, or making pikaid even better, every contribution is appreciated.

**Want to help? Here's how:**

- 🐛 **Found a bug?** [Open an issue](https://github.com/pikaid/pikaid-kotlin/issues) with clear reproduction steps.
- 💡 **Have an idea?** Start with an issue before making major changes.
- 🛠️ **Ready to contribute?** Open a pull request explaining your changes.
- 🧪 **Keep it tested.** Add meaningful regression tests for behavior changes and follow the existing conventions.
- ✅ **Make sure CI passes.** All required checks must be green before a pull request is merged. No shortcuts!

**One important rule:** pikaid's identifier format is immutable. Contributions must preserve the [canonical specification (revision 1.0.4)](https://github.com/pikaid/pikaid-specs/tree/v1.0.4).

🔐 **Found a security vulnerability?** Report it privately using GitHub's security reporting (when available), or [contact the maintainer](mailto:larananas.dev@proton.me)—not through a public issue.

**Every improvement matters, big or small. Thanks for helping make pikaid better!** 🐇

## 📄 License

The implementation is available under the [MIT License](LICENSE).

## ✉️ Contact

For licensing or project-related questions: [larananas.dev@proton.me](mailto:larananas.dev@proton.me).

