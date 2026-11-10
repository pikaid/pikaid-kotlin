# Changelog

All notable changes to `pikaid-kotlin` are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The pikaid identifier format itself is immutable and unversioned; version numbers here
refer only to this library.

## [Unreleased]

Nothing yet.

## [1.0.0] - 2026-11-07

First stable release, implementing pikaid specification revision 1.0.4.

### Added

- `PikaId.generate()`, `generate(timestamp)`, `generateBinary()`, and
  `generateBinary(timestamp)` using `java.security.SecureRandom`, failing closed on
  entropy or clock-range failures.
- Strict `parse`, `parseOrNull`, `fromBinary`, `fromBinaryOrNull`, `isValid`, and
  `isWellFormed` operations with no trimming, case folding, truncation, or wrapping.
- Lossless 26-character text and 17-byte binary representations, defensive copies, and
  canonical ASCII-compatible ordering.
- Official conformance vectors, independent known-answer, boundary, overflow, property,
  Java interop, and internal-surface regression tests.
- Reproducible library, sources, and KDoc-derived Javadoc archives with an archive-hygiene
  check that rejects local-only `.ai/` entries.
- Conditional PGP signing for publications; unsigned local builds remain supported.

### Notes

- Runtime requirement: Java 17 or newer. The build defaults to a JDK 21 toolchain and also
  supports `-PjvmToolchain=17`.
- The library is not published to a registry yet. `1.0.0` artifacts are prepared locally.

[1.0.0]: https://github.com/pikaid/pikaid-kotlin/releases/tag/v1.0.0
