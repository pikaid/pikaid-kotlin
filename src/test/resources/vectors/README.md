# Vendored conformance fixtures

These files are copied verbatim from the official PHP implementation so that the Kotlin
implementation can be checked against an independent, specification-derived fixture set.

| File | Source | SHA-256 (of this copy) |
| --- | --- | --- |
| `pikaid-v1.json` | `pikaid/pikaid-php` `tests/vectors/pikaid-v1.json` at commit `e3f843edeb5157a5d2d25196cac0d69580810a95` (tag `v1.2.0`) | `b0c9dfdd063613424414fef5c9065fbd2f81d645d0ab42b34e27cde715613500` |
| `pikaid-v1.schema.json` | `pikaid/pikaid-php` `tests/vectors/pikaid-v1.schema.json` at the same commit | `4441d4dbaa6c5450ae9cbfb2438c53bc0421bba2b7fcc9ef9b158e5d1de0759e` |

The `pikaid-v1` filename is historical. The identifier format is immutable and
unversioned; the fixture itself records specification revision `1.0.4`.

The canonical specification is pinned to `pikaid/pikaid-specs` tag `v1.0.4`, commit
`17014d76f568c3ac9f9ce2927c1a018fdbc36d1d`. In any conflict, the specification wins.
