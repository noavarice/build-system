# AGENTS.md

This repo is a from-scratch Java build system (Maven/Gradle-like) that builds itself.
Maven builds this repo's own code; the real product is the `lib` API plus the `build-itself` /
`build-spring-security` mains, which use that API to rebuild other source trees from scratch.

## Modules

- `lib` — the build system core: compile, jar, test, dependency resolution, graph. This is the code you will almost always be editing.
- `test-utils` — internal test-only helpers (e.g. `FsUtils`). Used by `lib` tests.
- `junit-integration` — JUnit platform launcher integration used by the build system's test runner.
- `build-itself` — main `com.github.build.BuildItself` that rebuilds `test-utils` and `lib` from their source trees and runs their tests.
- `build-spring-security` — main `com.github.build.BuildSpringSecurity` that rebuilds the Spring Security tree from the `spring-security/` git submodule (init with `git submodule update --init`). It builds the main source sets of ~23 modules (crypto, core, acl, data, web, messaging, access, aspects, cas, oauth2-*, taglibs, kerberos-*, ldap, saml2-service-provider, rsocket, webauthn, config, test); test source sets are built for some only. `KotlinCompileService` handles the mixed Kotlin/Java modules. Blocked modules and the feature gaps they need are tracked in `build-spring-security/BLOCKED_MODULES.md`.

## Build

- No `mvnw` wrapper; use the system Maven (here 3.9.12). Build with a full JDK 21 (here Temurin 21); the repo's own code targets `release 21`.
- Full build including integration tests: `mvn clean install` (root `pom.xml` aggregates all modules). Equivalent short build: `mvn clean package`. This matches the checked-in `.run` configs (`build`, `package`).
- Unit tests (`*Test`) run via surefire, integration tests (`*IT`) via failsafe. ITs only run on `verify`/`install`, not `test`/`package`. Run one: `mvn -pl lib -am test` or `mvn -pl lib -am verify -Dit.test=RemoteRepositoryIT`.

## JDK 21 for `build-spring-security`

Use a full JDK 21 (Temurin 21) to run `BuildSpringSecurity` and the tests it spawns. The checked-in
run config is `.run/build Spring Security.run.xml` (main class `com.github.build.BuildSpringSecurity`,
program arg `spring-security`, plus `-DbuildRuntimePath=junit-integration/...jar,lib/...jar`), and it
still needs a reachable Nexus. With JDK 25 the Spring Security `web` tests fail inside Mockito's
inline mock maker (`Java 25 (69) is not supported by the current version of Byte Buddy`) and JaCoCo
0.8.9 cannot read class file major version 69. `BuildSpringSecurity` still emits `release 17`
bytecode for the Spring Security modules; only the JVM running it (and the test processes it spawns)
must be 21. See `build-spring-security/BLOCKED_MODULES.md`.

## Two output layouts (critical gotcha)

Projects built have two independent output directories; do not confuse them:

- Maven writes to `{module}/target/` (compiled classes, `lib-0.1.0.jar`, jaxb output under `lib/target/generated-sources`).
- The self-hosted builds write to `{module}/build/` (default `ArtifactLayout` root); `BuildItself` puts its XJC output under `lib/build/generated-sources/xjc`. `build-spring-security` uses `build-system/` as its `ArtifactLayout` root instead, so its output lands in `spring-security/{module}/build-system/`.
- `.gitignore` intentionally un-ignores `**/src/main/**/target/` and similar; do not "fix" it.

## Dependency resolution needs a network/nexus

- `lib` and both build mains resolve artifacts from `http://${NEXUS_HOST:-localhost}:8081/repository/maven-central` (see `mavenArtifactResolver()` in `BuildItself`/`BuildSpringSecurity`, and `RemoteRepositoryImpl`). They use a fresh temp local repo each run, so `~/.m2` caches are NOT reused — offline runs fail.
- `scripts/nexus-init.sh` (run by the `nexus-init` compose service) also configures a proxy repository to `build.shibboleth.net` for OpenSAML 5.x, which `spring-security-saml2-service-provider` needs and Maven Central does not host.
- `compose-local.yaml` spins up Nexus + a Maven container (`mvn clean install`) + `build-itself`. Use it (or `docker compose up`) to run self-hosted builds that resolve dependencies.
- Network-dependent ITs: `MavenArtifactResolverDependencyServiceIT`, `RemoteRepositoryIT`, `DependencyServiceIT`, `BuildServiceIT`.

## Test runtime quirk

`lib`'s surefire and failsafe set the `buildRuntimePath` system property to the built `lib` jar. `BuildItself` and `BuildSpringSecurity` read this same `-DbuildRuntimePath` when they run a project's tests in-process (they expect it when launched with `-jar`/plain `java`, otherwise the property is empty). Keep the property name and comma-separated `Path` list contract intact.

## Version sync (hard-coded, no central property)

`0.1.0` is duplicated across: root and every module `pom.xml`, every inter-module dependency version, the `createProject*` GAVs in `BuildItself` (e.g. tests use `build-system-test-utils`/`build-system-lib`), Dockerfile jar names, the `.run/build Spring Security.run.xml` `buildRuntimePath`, and some test fixtures. Bumping the version means touching all of these consistently.

## Conventions

- Nullness is enforced via JSpecify; every package is `@NullMarked` (`package-info.java`). New code must carry JSpecify annotations.
- Style is the IntelliJ formatter via `.editorconfig`: 2-space indent, 100-col limit, `final` locals. No formatter/lint plugin runs in the Maven build — match existing style manually.
- Fine-grained unit tests for non-critical code are forbidden for now; the project is in an early stage and such tests slow development. Integration tests that verify meaningful behavior are acceptable.
- Put temporary/scratch files in the repo-local, gitignored `tmp/` directory, not the global `/tmp`.
- There is no README and no CI; `AGENTS.md` and the `.run` configs are the only workflow documentation.