# Blocked Spring Security modules

Backlog of Spring Security modules that are not yet added to
`BuildSpringSecurity` because they need build-system capabilities the project does not
have. Each entry records the module, the build features it needs, the evidence gathered
while trying to build it, and what would unblock it.

Currently built (for reference): `crypto`, `core`, `acl`, `data`, `web`, `messaging`,
`access`, `aspects`, `cas`, `oauth2-core`, `oauth2-jose`, `oauth2-client`,
`oauth2-resource-server`, `oauth2-authorization-server`, `rsocket`, `taglibs`,
`kerberos-core` and `kerberos-test` with their full main and test source sets. The remaining
modules (`config`, `test`, `ldap`, `saml2-service-provider`, `webauthn`) build their **main
source sets only** (their tests are deferred, see below).

Verification environment: run the self-hosted build with a full JDK 21. With JDK 25 the
`web` tests fail inside Mockito's inline mock maker (`Java 25 (69) is not supported by the
current version of Byte Buddy`) and JaCoCo 0.8.9 cannot read class file major version 69.
This is unrelated to the modules below.

## Summary

All modules below build their **main** source sets; only their test source sets (and the modules
that are not registered at all) are blocked.

| Module | Blocking feature (test source set unless stated) |
| --- | --- |
| `config` | (3), (5), (6), (7) |
| `test` | (7); depends on the `config` test source set and oauth2 test classes |
| `saml2-service-provider` | (5): extra `opensaml5Test` source set |
| `webauthn` | (2) Gradle-style highest-version conflict resolution (tools.jackson) |
| `ldap` | (6) integration-test source set; tests depend on `test`/`config` |
| `kerberos-web` | Not registered yet; tests depend on `config` |
| `kerberos-client` | Not registered yet; tests depend on `config` and `test` |
| `itest/context`, `itest/web` | Not registered; integration-test projects depend on `config`/`test` |

## Feature gaps

### 1. Classifier-qualified dependency artifacts (implemented)

`GroupArtifact` and `GroupArtifactVersion` now carry an optional `classifier`, encoded in the
colon-separated form `groupId:artifactId[:extension[:classifier]]:version`. The Maven resolver
propagates the classifier through artifact construction and uses it as part of the
`Map<GroupArtifactVersion, Path>` key, so two artifacts that differ only by classifier no
longer collapse into one key.

`reactor-netty` pulls `netty-codec-http3`, which declares runtime-scoped dependencies on
`io.netty:netty-codec-native-quic` for several classifiers (`linux-x86_64`, `linux-aarch_64`,
`osx-x86_64`, `osx-aarch_64`, `windows-x86_64`, ...). Before the fix, resolving the test
runtime threw:

```
java.lang.IllegalStateException: Duplicate key io.netty:netty-codec-native-quic:4.2.17.Final
(attempted merging values .../netty-codec-native-quic-4.2.17.Final-linux-x86_64.jar and
.../netty-codec-native-quic-4.2.17.Final-linux-aarch_64.jar)
    at MavenArtifactResolverDependencyService.resolveRuntimeClasspath(...)
    at TestService.getTestRuntime(...)
```

This unblocked the test source sets of `oauth2-jose`, `oauth2-client` and
`oauth2-resource-server`. `config` also uses `reactor-netty` in tests, but stays blocked by
the features below.

### 2. Gradle-style highest-version conflict resolution

The resolver currently implements Maven nearest/platform-wins. `webauthn4j-core:0.31.6.RELEASE`
declares `tools.jackson.dataformat:jackson-dataformat-cbor:3.1.3` and
`tools.jackson.core:jackson-databind:3.1.3`, but the platform imports
`tools.jackson:jackson-bom:3.0.4`, which manages both to `3.0.4`. The resolved `3.0.4` lacks
`CBORMapper.treeToValue(JsonNode, Class)`, so 10 tests in
`Webauthn4jRelyingPartyOperationsTests` fail with:

```
java.lang.NoSuchMethodError: 'java.lang.Object
tools.jackson.dataformat.cbor.CBORMapper.treeToValue(tools.jackson.databind.JsonNode, java.lang.Class)'
```

Gradle would select the higher transitive `3.1.3`. Unblock: implement highest-version-wins (at
least for non-hard requirements), or raise the platform's tools.jackson BOM.

### 3. Build-time generated sources

- `saml2-service-provider`: its Gradle build (`spring-security-saml2-service-provider.gradle`)
  copies `src/main/java/org/springframework/security/saml2/internal` into several
  `.../saml2/provider/service/...` package directories, applying a package-rename filter
  (`.saml2.internal` -> `.saml2.provider.service.*`). Main sources import those generated types,
  e.g. `OpenSamlOperations` from `saml2.provider.service.authentication.logout`.
- `config`: runs `rncToXsd` (trang) over `src/main/resources/.../spring-security/config/*.rnc`
  and a `versionlessXsd` task, feeding the generated XSD into resources.

None of the built modules generate sources, so this capability does not exist.

### 4. Kotlin compilation (implemented)

`KotlinCompileService` (in `build-spring-security`) compiles Kotlin first with the Java sources
exposed through `-Xjava-source-roots`, then javac compiles Java with the Kotlin output on the
classpath. It is backed by `org.jetbrains.kotlin:kotlin-compiler-embeddable` (added to
`build-spring-security/pom.xml`). `config`'s main and test source sets include
`src/main/kotlin`/`src/test/kotlin`, but only the main source set is exercised so far.

### 5. Extra source sets beyond testFixtures

`crypto` demonstrates the `ExtraSourceSetArgs` mechanism (`testFixtures`), but that source set is
plain and only pulls `assertj-core`. `saml2-service-provider` needs `opensaml5Main` and
`opensaml5Test` source sets with their own `opensamlFiveMain`/`opensamlFiveTest` dependency
configurations, and adds `opensaml5Main.output` to the main jar and `opensaml5Test.output` to a
separate test task. This is beyond the current extra-source-set support.

### 6. Integration-test source sets

`ldap` (`src/integration-test/java`) and `config` (`src/integration-test/java`) use a separate
`integration-test` source set with its own dependencies and an `integrationTest` task. The build
only knows `main` + `test`.

### 7. Source-set level project cycles

`test` main depends on `config` main (`org.springframework.security.config.BeanIds`), while
`config` tests depend on `test` main. A single linear project order cannot express this; building
source sets independently (or splitting `config`) would be required.

## Per-module detail

### config

- Path: `spring-security/config`, build file `spring-security-config.gradle`.
- Source sets: `src/main/java`, `src/main/kotlin`, `src/main/resources`, `src/test/java`,
  `src/test/kotlin`, `src/integration-test/java`.
- Main API: `core`, spring-aop/beans/context/core.
- Optional projects: `access`, `data`, `ldap`, `messaging`, `saml2-service-provider`,
  `oauth2-client`, `oauth2-jose`, `oauth2-resource-server`, `oauth2-authorization-server`,
  `rsocket`, `web`, `webauthn`; optional libraries: reactor-core, aspectjweaver, spring-jdbc,
  spring-messaging, spring-tx, spring-webmvc, spring-web, spring-webflux, spring-websocket,
  kotlin-reflect, kotlin-stdlib-jdk8, jakarta.annotation-api; provided jakarta.servlet-api.
- Tests reference almost the whole tree (aspects, cas, test, plus `tests` configurations of
  access/core/ldap/oauth2-client/oauth2-resource-server/oauth2-authorization-server/saml2/web/
  webauthn).
- Main source set builds. All sibling modules are now referenced as projects (no remote
  `org.springframework.security` coordinates). OpenSAML 5.x (saml2's `api` dependency) is not on
  Maven Central, so the local repository proxy exposes it (see `compose-local.yaml`).
- Test source set blockers: (3), (5 via opensaml5 config), (6), (7), plus test classes from the
  sibling modules whose tests are not built yet.
- Extra Gradle tasks: `rncToXsd`, `versionlessXsd`, `processResources`/`sourcesJar` wiring. The
  generated XSDs are committed, so they are currently shipped via resource copying.

### saml2-service-provider

- Path: `spring-security/saml2/saml2-service-provider`, build file
  `spring-security-saml2-service-provider.gradle`.
- Source sets: `src/main/java`, `src/test/java`, `src/opensaml5Main/java`, `src/opensaml5Test/java`.
- API: `web`, `org.opensaml:opensaml-saml-api`, `org.opensaml:opensaml-saml-impl` (5.1.6, in
  platform).
- Main builds. The generated package copies under `src/main/java` are committed, and
  `BuildSpringSecurity#compileOpenSaml5Main` compiles and merges the `opensaml5Main` source set.
- Test source set blocker: (5) via the `opensaml5Test` source set.

### oauth2-jose

- Path: `spring-security/oauth2/oauth2-jose`, build file `spring-security-oauth2-jose.gradle`.
- API: `core`, `oauth2-core`, spring-core, `com.nimbusds:nimbus-jose-jwt`.
- Optional: reactor-core, spring-webflux.
- Test: bouncycastle bcpkix/bcprov, jakarta.servlet-api, mockwebserver, `reactor-netty`,
  tools.jackson jackson-databind, assertj, junit, mockito, spring-test.
- Main and test build (the classifier-qualified `reactor-netty` runtime artifacts are resolved
  correctly).

### oauth2-client

- Path: `spring-security/oauth2/oauth2-client`, build file `spring-security-oauth2-client.gradle`.
- API: `core`, `oauth2-core`, `web`, spring-core, `com.nimbusds:oauth2-oidc-sdk`.
- Optional: `oauth2-jose`, reactor-core, spring-webflux, jackson-databind,
  jackson-datatype-jsr310, spring-jdbc, spring-r2dbc, tools.jackson jackson-databind.
- Provided: jakarta.servlet-api.
- Test: `oauth2-core` tests, `oauth2-jose` tests, `web` tests, mockwebserver,
  context-propagation, `reactor-netty`, reactor-test, jsonassert, `io.r2dbc:r2dbc-h2:1.0.0.RELEASE`,
  `io.r2dbc:r2dbc-spi-test:0.9.1.RELEASE`, assertj, junit, mockito, spring-test; runtime hsqldb,
  launcher.
- Main and test build (the classifier-qualified `reactor-netty` runtime artifacts are resolved
  correctly, and it uses the `oauth2-jose` test source set).

### oauth2-resource-server

- Path: `spring-security/oauth2/oauth2-resource-server`, build file
  `spring-security-oauth2-resource-server.gradle`.
- API: `core`, `oauth2-core`, `web`, spring-core.
- Optional: `oauth2-jose`, oauth2-oidc-sdk, reactor-core, spring-webflux; provided
  jakarta.servlet-api.
- Test: `core` tests, `oauth2-jose` tests, mockwebserver, tools.jackson jackson-databind,
  `reactor-netty`, reactor-test, assertj, junit, mockito, spring-test; runtime launcher.
- Main and test build (the classifier-qualified `reactor-netty` runtime artifacts are resolved
  correctly, and it uses the `oauth2-jose` test source set).

### webauthn

- Path: `spring-security/webauthn`, build file `spring-security-webauthn.gradle`.
- API: `core`, `web`, spring-core/beans/web, `com.webauthn4j:webauthn4j-core:0.31.6.RELEASE`.
- Optional: spring-jdbc, spring-tx, tools.jackson jackson-databind, jackson-databind.
- Provided: jakarta.servlet-api.
- Test: `core` tests, `com.fasterxml.jackson.dataformat:jackson-dataformat-cbor`, reactor-test,
  jakarta.xml.bind-api, jakarta.websocket-api/client-api, hamcrest, mockito, jsonassert,
  spring-webflux, nio-multipart-parser, assertj, junit, mockito-junit, spring-test, spring-webmvc,
  mockwebserver; runtime hsqldb, launcher.
- Main builds.
- Blocker (test source set): (2).

### ldap

- Path: `spring-security/ldap`, build file `spring-security-ldap.gradle`.
- API: `core`, spring-beans/context/core/tx, `org.springframework.ldap:spring-ldap-core`
  (with excludes).
- Optional: jackson-databind, `ldapsdk:ldapsdk`, `com.unboundid:unboundid-ldapsdk`,
  tools.jackson jackson-databind.
- Test: `core` tests, `spring-security-test`, slf4j-api, assertj, junit, mockito, spring-test,
  jsonassert.
- Source sets: `main`, `test`, `integration-test`.
- Main builds.
- Blockers (test source set): (6); plus needs `test` (7).

### test

- Path: `spring-security/test`, build file `spring-security-test.gradle`.
- API: `core`, `web`, spring-core, spring-test.
- Optional: `config`, `oauth2-client`, `oauth2-jose`, `oauth2-resource-server`, reactor-core,
  spring-webmvc, spring-webflux; provided jakarta.servlet-api.
- Main imports `org.springframework.security.config.BeanIds` in
  `SecurityMockMvcConfigurer` and `WebTestUtils` (compile-time dependency on `config`).
- Test: `config` tests configuration, jackson-databind, jackson-datatype-jsr310, reactor-test,
  jakarta.xml.bind-api, spring-webmvc, spring-tx, assertj, junit, mockito, jsonassert.
- Main source set builds and depends on `config` main.
- Test source set blockers: depends on the `config` test source set and on test classes from
  `oauth2-core`/`oauth2-client`/`oauth2-jose`. See (7).

### oauth2-authorization-server

- Path: `spring-security/oauth2/oauth2-authorization-server`, build file
  `spring-security-oauth2-authorization-server.gradle`.
- API: `web`, `oauth2-core`, `oauth2-jose`, `oauth2-resource-server`, spring-core,
  `com.nimbusds:nimbus-jose-jwt`, tools.jackson jackson-databind.
- Optional: jackson-databind, jackson-datatype-jsr310, spring-jdbc; provided jakarta.servlet-api.
- Test: `oauth2-jose` tests, spring-webmvc, bouncycastle, junit, assertj, mockito, json-path,
  mockwebserver; runtime hsqldb, launcher.
- Main and test build (it uses the now-built `oauth2-jose` test source set).

### rsocket

- Path: `spring-security/rsocket`, build file `spring-security-rsocket.gradle`.
- API: `core`, `io.rsocket:rsocket-core` (rsocket-bom).
- Optional: `oauth2-resource-server`, spring-messaging.
- Main imports `org.springframework.security.oauth2.server.resource.*`
  (`BearerPayloadExchangeConverter`, `AuthenticationPayloadExchangeConverter`).
- Main and test build (the `oauth2-resource-server` main output it needs is now built).

### kerberos-web / kerberos-client

- `kerberos-web`: path `spring-security/kerberos/kerberos-web`; API `kerberos-core`, `web`,
  jakarta.servlet-api; test depends on `config`.
- `kerberos-client`: path `spring-security/kerberos/kerberos-client`; API `kerberos-core`,
  `kerberos-web`, spring-web,
  `httpclient5`; optional `ldap`; test depends on `kerberos-test`, spring-test, `config`,
  mockwebserver.
- Blockers: `config` (4/7); client also on `test`.

### itest/context, itest/web

- Paths: `spring-security/itest/context`, `spring-security/itest/web`.
- Apply `io.spring.convention.spring-test` and use `integrationTest` tasks. Depend on
  `config`, `ldap`, `test` test source sets, `web` tests; `itest/context` also uses
  `org.python:jython`.
- Blockers: (6); plus dependencies on `config`/`test`/`ldap`.

## Not applicable

- `bom` (`spring-security-bom.gradle`) and `dependencies`
  (`spring-security-dependencies.gradle`) are platform/BOM modules, represented in
  `BuildSpringSecurity#getPlatform`.
- `docs` is outside the scope of `BuildSpringSecurity`.
- `javascript` is already consumed by the `web` build (`buildJsModule`).
