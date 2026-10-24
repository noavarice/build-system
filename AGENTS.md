# AGENTS.md

This repo is a from-scratch Java build system (Maven/Gradle-like) that builds itself.
Maven builds this repo's own code; the real product is the `lib` API plus the `build-itself` /
`build-spring-security` mains, which use that API to rebuild other source trees from scratch.

## Modules

- `lib` — the build system core API: project/source-set model, compile, jar, and test services, plus the dependency-resolution API (`DependencyService`, `Dependency`, `DependencyConstraints`, `GroupArtifact(V|Version)`, `Pom`, `RemoteRepository`, ...). This is the code you will almost always be editing. Implementation classes live in the two resolver modules below.
- `dependency-resolver-aether` — `DependencyService` (and workspace reader) implementation over Maven Artifact Resolver (Aether), package `com.github.build.deps.aether`. The default resolver used by the build mains.
- `dependency-resolver-native` — hand-rolled `DependencyService`/`RemoteRepository` implementation, package `com.github.build.deps.nativeimpl` (`native` is a Java keyword, hence `nativeimpl`). Far from ready; not used by the consumers yet.
- `test-utils` — internal test-only helpers (e.g. `FsUtils`). Used by `lib` and the resolver tests.
- `junit-integration` — JUnit platform launcher integration used by the build system's test runner.
- `repository-proxy` — minimal Spring Boot (servlet) Maven repository proxy used as a development-time Nexus replacement. Speaks the Nexus `/repository/{id}` URL layout, stores proxied artifacts in a Maven local repository, and is startable as a `main` or programmatically. See "Dependency resolution".
- `build-itself` — main `com.github.build.BuildItself` that rebuilds `test-utils` and `lib` from their source trees and runs their tests.
- `build-spring-security` — main `com.github.build.BuildSpringSecurity` that rebuilds the Spring Security tree from the `spring-security/` git submodule (init with `git submodule update --init`). It builds the main source sets of ~23 modules (crypto, core, acl, data, web, messaging, access, aspects, cas, oauth2-*, taglibs, kerberos-*, ldap, saml2-service-provider, rsocket, webauthn, config, test); test source sets are built for some only. Test source sets that other modules consume (`core`, `web`, `oauth2-core`, `oauth2-jose`) are built as standalone `spring-security-<module>-tests` projects (their test sources are the project's main source set, with a distinct `build-system-tests` output root), because depending on another project's `SourceSet` is no longer allowed. `ldap`'s test sources are built the same way, and its `src/integration-test` sources as the standalone `spring-security-ldap-integration-tests` project (distinct `build-system-integration-tests` output root). `KotlinCompileService` handles the mixed Kotlin/Java modules. Blocked modules and the feature gaps they need are tracked in `build-spring-security/BLOCKED_MODULES.md`.

## Build

- No `mvnw` wrapper; use the system Maven (here 3.9.12). Build with a full JDK 21 (here Temurin 21); the repo's own code targets `release 21`.
- Full build including integration tests: `mvn clean install` (root `pom.xml` aggregates all modules). Equivalent short build: `mvn clean package`. This matches the checked-in `.run` configs (`build`, `package`).
- Unit tests (`*Test`) run via surefire, integration tests (`*IT`) via failsafe. ITs only run on `verify`/`install`, not `test`/`package`. Run one: `mvn -pl lib -am test` or `mvn -pl dependency-resolver-native -am verify -Dit.test=RemoteRepositoryIT`.

## JDK 21 for `build-spring-security`

Use a full JDK 21 (Temurin 21) to run `BuildSpringSecurity` and the tests it spawns. The checked-in
run config is `.run/build Spring Security.run.xml` (main class `com.github.build.BuildSpringSecurity`,
program arg `spring-security`, plus `-DbuildRuntimePath=junit-integration/...jar,lib/...jar`), and it
still needs a reachable repository proxy. With JDK 25 the Spring Security `web` tests fail inside Mockito's
inline mock maker (`Java 25 (69) is not supported by the current version of Byte Buddy`) and JaCoCo
0.8.9 cannot read class file major version 69. `BuildSpringSecurity` still emits `release 17`
bytecode for the Spring Security modules; only the JVM running it (and the test processes it spawns)
must be 21. See `build-spring-security/BLOCKED_MODULES.md`.

## Two output layouts (critical gotcha)

Projects built have two independent output directories; do not confuse them:

- Maven writes to `{module}/target/` (compiled classes, `lib-0.1.0.jar`, jaxb output under `lib/target/generated-sources`).
- The self-hosted builds write to `{module}/build/` (default `ArtifactLayout` root); `BuildItself` puts its XJC output under `lib/build/generated-sources/xjc`. `build-spring-security` uses `build-system/` as its `ArtifactLayout` root instead, so its output lands in `spring-security/{module}/build-system/`.
- `.gitignore` intentionally un-ignores `**/src/main/**/target/` and similar; do not "fix" it.

## Dependency resolution needs a network/repository proxy

- `dependency-resolver-aether` (used by the resolver tests and both build mains) resolves artifacts from `http://${NEXUS_HOST:-localhost}:8081/repository/maven-central` (see `mavenArtifactResolver()` in `BuildItself`/`BuildSpringSecurity`); the native `RemoteRepositoryImpl` (`dependency-resolver-native`) uses the same layout. When launched from the `.run` configs / plain `java` they fall back to a fresh temp local repo each run, so those still need a live repository proxy. When run as the compose services (`build-itself`, `build-spring-security`, `maven`) they share the named `maven-local-repo` volume via `-Dmaven.repo.local=/m2/repository`, so the aether/Maven caches persist across runs and container rebuilds.
- `repository-proxy` is a minimal Spring Boot (servlet) Maven repository proxy used as a development-time Nexus replacement. It serves the Nexus `/repository/{id}` URL layout, stores proxied artifacts in a Maven local repository (default `~/.m2/repository`, configured with `repository-proxy.local-repository`) and exposes upstream repositories via `repository-proxy.repositories.<id>` (default only `maven-central`; the port defaults to 8080). It is startable as a `main`, programmatically via `SpringApplication`, or with `mvn -pl repository-proxy spring-boot:run`. It needs no authentication, no EULA acceptance and no non-Maven package formats. Every local-repository hit and every outgoing upstream request (with a running counter) is logged at INFO, so upstream request counts are directly visible.
- The `shibboleth` upstream (`build.shibboleth.net/maven/releases`) is configured in `compose-local.yaml` and provides OpenSAML 5.x, which `spring-security-saml2-service-provider` needs and Maven Central does not host. The old `.../nexus/content/repositories/releases` URL 302-redirects and makes the proxy return 500, so do not revert it.
- `compose-local.yaml` runs the proxy on port 8081 under the `nexus` network alias (so the existing `NEXUS_HOST=nexus` references and the settings.xml mirror URL keep working) plus a Maven container (`mvn clean install`) + `build-itself` + `build-spring-security`. Use it (under Podman or Docker) to run self-hosted builds that resolve dependencies.
- The `build-spring-security` service builds `build-spring-security/Dockerfile` (JDK 21 + Node 20), which embeds the module `target/` jars; run `mvn clean install` (e.g. the `maven` service) before bringing it up. `BuildSpringSecurity` gets `junit-integration`/`lib` on the in-process test classpath via `-DbuildRuntimePath` (see `build Spring Security.run.xml`). Verify the `spring-security` submodule is checked out (`git submodule update --init spring-security`).
- Bring everything up under Podman: `podman compose up --build`. The proxy image is built from `repository-proxy/Dockerfile`; it is a multi-stage layered Spring Boot image (no fat jar) running as a non-root `repositoryproxy` user. All image references are fully qualified (`docker.io/...`) because no unqualified-search registries are configured.
- Network-dependent ITs (`MavenArtifactResolverDependencyServiceIT`, `RemoteRepositoryIT`, `DependencyServiceIT`, `BuildServiceIT`) start the repository proxy in-process on an ephemeral port via `test-utils`' `RepositoryProxySupport`, so `mvn verify` needs no external proxy; they fall back to `NEXUS_HOST` when the proxy classes are absent (e.g. under `BuildItself`). `RepositoryProxyIT` is hermetic (stub upstream, temporary local repository).

## Test runtime quirk

`lib`'s surefire and failsafe set the `buildRuntimePath` system property to the built `lib` jar. `BuildItself` and `BuildSpringSecurity` read this same `-DbuildRuntimePath` when they run a project's tests in-process (they expect it when launched with `-jar`/plain `java`, otherwise the property is empty). Keep the property name and comma-separated `Path` list contract intact.

## Version sync (hard-coded, no central property)

`0.1.0` is duplicated across: root and every module `pom.xml`, every inter-module dependency version, the `createProject*` GAVs in `BuildItself` (e.g. tests use `build-system-test-utils`/`build-system-lib`), Dockerfile jar names (including `repository-proxy/Dockerfile`), the `.run/build Spring Security.run.xml` `buildRuntimePath`, and some test fixtures. Bumping the version means touching all of these consistently.

## Conventions

- Nullness is enforced via JSpecify; every package is `@NullMarked` (`package-info.java`). New code must carry JSpecify annotations.
- Style is palantir-java-format (4-space indent, 120 cols), enforced by the Spotless Maven plugin: `spotless:check` is bound to `verify`, so `mvn clean install` (and `verify`) fails on unformatted Java, and `mvn spotless:apply` rewrites files in place. `.editorconfig` still describes the older IntelliJ 2-space settings, which are no longer what the Maven build enforces.
- Fine-grained unit tests for non-critical code are forbidden for now; the project is in an early stage and such tests slow development. Integration tests that verify meaningful behavior are acceptable.
- Put temporary/scratch files in the repo-local, gitignored `tmp/` directory, not the global `/tmp`.
- There is no README and no CI; `AGENTS.md` and the `.run` configs are the only workflow documentation.