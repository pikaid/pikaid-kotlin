# Maven Central release setup (Kotlin)

Git tags trigger `.github/workflows/publish-central.yml`, which validates
the complete Kotlin build and uploads **dev.pikaid:pikaid-kotlin** to Maven
Central, including the Kotlin library, sources, Dokka Javadoc and signatures.
The Central Portal release is automatic after validation.

## One-time setup

1. Create a Central Portal account, verify the `dev.pikaid` namespace and
   generate a publishing user token at https://central.sonatype.com/.
2. Add GitHub Actions secrets `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`,
   using the generated token username and password (not your login).
3. Publish your public GPG key to a keyserver. Add the ASCII-armored private
   key as `SIGNING_KEY` and its passphrase as `SIGNING_PASSWORD`.
   These are only used by the publishing workflow; normal builds and
   `publishToMavenLocal` remain unsigned.
4. Create the GitHub environment named `maven-central`.
5. Protect tags and the GitHub environment. Central's verified namespace
   must correspond to the Gradle `group = "dev.pikaid"`. If you cannot
   prove this namespace, migrate both Java/Kotlin coordinates together.

## Every release

1. Update `version = "X.Y.Z"` in `build.gradle.kts`.
2. Ensure ordinary CI passes, then create GitHub tag `vX.Y.Z` pointing
   to that source commit and publish the release.
3. GitHub validates formatting, tests, coverage, archive contents and
   the tag/version match, then signs and automatically publishes the bundle.

The existing `mavenJava` publication and Dokka Javadoc JAR are preserved.
The added Vanniktech **base** plugin uploads this existing publication through
the modern Central Portal protocol. No manual Sonatype staging approval is
needed once the configuration is complete.

Reference: https://vanniktech.github.io/gradle-maven-publish-plugin/central/
