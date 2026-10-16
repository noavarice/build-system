# Blocked Spring Security modules

Backlog of Spring Security modules that are not yet added to
`BuildSpringSecurity` because they need build-system capabilities the project does not
have. Each entry records the module, the build features it needs, the evidence gathered
while trying to build it, and what would unblock it.

Currently built (for reference): `crypto`, `core`, `acl`, `data`, `web`, `messaging`,
`access`, `aspects`, `cas`, `oauth2-core`, `taglibs`, `kerberos-core`, `kerberos-test`.

Verification environment: run the self-hosted build with a full JDK 21. With JDK 25 the
`web` tests fail inside Mockito's inline mock maker (`Java 25 (69) is not supported by the
current version of Byte Buddy`) and JaCoCo 0.8.9 cannot read class file major version 69.
This is unrelated to the modules below.

## Summary

| Module | Blocking feature |
| --- | --- |
| `config` | Kotlin compilation, generated XSD sources, source-set cycle with `test` |
| `saml2-service-provider` | Build-time generated sources, extra source sets |
| `oauth2-jose` | Classifier-qualified runtime artifacts (reactor-netty) |
| `oauth2-client` | Classifier-qualified runtime artifacts (reactor-netty) |
| `oauth2-resource-server` | Classifier-qualified runtime artifacts (reactor-netty) |
| `webauthn` | Gradle-style highest-version conflict resolution (tools.jackson) |
| `ldap` | Integration-test source set; depends on `test`/`config` |
| `test` | Depends on `config` (main + test source sets) |
| `oauth2-authorization-server` | Depends on `test` and `oauth2-jose` tests |
| `rsocket` | Depends on `oauth2-resource-server` |
| `kerberos-web` | Tests depend on `config` |
| `kerberos-client` | Tests depend on `config` and `test` |
| `itest/context`, `itest/web` | Integration-test projects; depend on `config`/`test` |

## Feature gaps to implement

### 1. Classifier-qualified dependency artifacts

`MavenArtifactResolverDependencyService#resolveCompileClasspath` and
`#resolveRuntimeClasspath` both collect results into a
`Map<GroupArtifactVersion, Path>` keyed only by group/artifact/version. `GroupArtifactVersion`
deliberately drops the classifier, so two artifacts that differ only by classifier collapse
into one key.

`reactor-netty` pulls `netty-codec-http3`, which declares runtime-scoped dependencies on
`io.netty:netty-codec-native-quic` for several classifiers (`linux-x86_64`, `linux-aarch_64`,
`osx-x86_64`, `osx-aarch_64`, `windows-x86_64`, ...). Resolving the test runtime then throws:

```
java.lang.IllegalStateException: Duplicate key io.netty:netty-codec-native-quic:4.2.17.Final
(attempted merging values .../netty-codec-native-quic-4.2.17.Final-linux-x86_64.jar and
.../netty-codec-native-quic-4.2.17.Final-linux-aarch_64.jar)
    at MavenArtifactResolverDependencyService.resolveRuntimeClasspath(...)
    at TestService.getTestRuntime(...)
```

Unblock options: model the classifier in the artifact key, or change the classpath API to a
collection of paths instead of a GAV-keyed map. Affects `oauth2-jose`, `oauth2-client`,
`oauth2-resource-server` (and `config`, which also uses `reactor-netty` in tests).

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

### 4. Kotlin compilation

`config` has Kotlin main and test sources (`src/main/kotlin`, `src/test/kotlin`) and applies the
`security-kotlin` plugin. The build only compiles `.java` (javac) and AspectJ. No Kotlin
compiler support exists.

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
- Blockers: (3), (4), (5 via opensaml5 config), (6), (7).
- Extra Gradle tasks: `rncToXsd`, `versionlessXsd`, `processResources`/`sourcesJar` wiring.

### saml2-service-provider

- Path: `spring-security/saml2/saml2-service-provider`, build file
  `spring-security-saml2-service-provider.gradle`.
- Source sets: `src/main/java`, `src/test/java`, `src/opensaml5Main/java`, `src/opensaml5Test/java`.
- API: `web`, `org.opensaml:opensaml-saml-api`, `org.opensaml:opensaml-saml-impl` (5.1.6, in
  platform).
- Blockers: (3), (5).

### oauth2-jose

- Path: `spring-security/oauth2/oauth2-jose`, build file `spring-security-oauth2-jose.gradle`.
- API: `core`, `oauth2-core`, spring-core, `com.nimbusds:nimbus-jose-jwt`.
- Optional: reactor-core, spring-webflux.
- Test: bouncycastle bcpkix/bcprov, jakarta.servlet-api, mockwebserver, `reactor-netty`,
  tools.jackson jackson-databind, assertj, junit, mockito, spring-test.
- Blocker: (1) via `reactor-netty`.

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
- Blockers: (1) via `reactor-netty` (also depends on `oauth2-jose`).

### oauth2-resource-server

- Path: `spring-security/oauth2/oauth2-resource-server`, build file
  `spring-security-oauth2-resource-server.gradle`.
- API: `core`, `oauth2-core`, `web`, spring-core.
- Optional: `oauth2-jose`, oauth2-oidc-sdk, reactor-core, spring-webflux; provided
  jakarta.servlet-api.
- Test: `core` tests, `oauth2-jose` tests, mockwebserver, tools.jackson jackson-databind,
  `reactor-netty`, reactor-test, assertj, junit, mockito, spring-test; runtime launcher.
- Blocker: (1) via `reactor-netty` (also depends on `oauth2-jose`).

### webauthn

- Path: `spring-security/webauthn`, build file `spring-security-webauthn.gradle`.
- API: `core`, `web`, spring-core/beans/web, `com.webauthn4j:webauthn4j-core:0.31.6.RELEASE`.
- Optional: spring-jdbc, spring-tx, tools.jackson jackson-databind, jackson-databind.
- Provided: jakarta.servlet-api.
- Test: `core` tests, `com.fasterxml.jackson.dataformat:jackson-dataformat-cbor`, reactor-test,
  jakarta.xml.bind-api, jakarta.websocket-api/client-api, hamcrest, mockito, jsonassert,
  spring-webflux, nio-multipart-parser, assertj, junit, mockito-junit, spring-test, spring-webmvc,
  mockwebserver; runtime hsqldb, launcher.
- Blocker: (2).

### ldap

- Path: `spring-security/ldap`, build file `spring-security-ldap.gradle`.
- API: `core`, spring-beans/context/core/tx, `org.springframework.ldap:spring-ldap-core`
  (with excludes).
- Optional: jackson-databind, `ldapsdk:ldapsdk`, `com.unboundid:unboundid-ldapsdk`,
  tools.jackson jackson-databind.
- Test: `core` tests, `spring-security-test`, slf4j-api, assertj, junit, mockito, spring-test,
  jsonassert.
- Source sets: `main`, `test`, `integration-test`.
- Blockers: (6); plus needs `test` (7).

### test

- Path: `spring-security/test`, build file `spring-security-test.gradle`.
- API: `core`, `web`, spring-core, spring-test.
- Optional: `config`, `oauth2-client`, `oauth2-jose`, `oauth2-resource-server`, reactor-core,
  spring-webmvc, spring-webflux; provided jakarta.servlet-api.
- Main imports `org.springframework.security.config.BeanIds` in
  `SecurityMockMvcConfigurer` and `WebTestUtils` (compile-time dependency on `config`).
- Test: `config` tests configuration, jackson-databind, jackson-datatype-jsr310, reactor-test,
  jakarta.xml.bind-api, spring-webmvc, spring-tx, assertj, junit, mockito, jsonassert.
- Blockers: (4) and (7) transitively via `config`.

### oauth2-authorization-server

- Path: `spring-security/oauth2/oauth2-authorization-server`, build file
  `spring-security-oauth2-authorization-server.gradle`.
- API: `web`, `oauth2-core`, `oauth2-jose`, `oauth2-resource-server`, spring-core,
  `com.nimbusds:nimbus-jose-jwt`, tools.jackson jackson-databind.
- Optional: jackson-databind, jackson-datatype-jsr310, spring-jdbc; provided jakarta.servlet-api.
- Test: `spring-security-test`, `oauth2-jose` tests, spring-webmvc, bouncycastle, junit,
  assertj, mockito, json-path, mockwebserver; runtime hsqldb, launcher.
- Blockers: depends on `test` and `oauth2-jose`.

### rsocket

- Path: `spring-security/rsocket`, build file `spring-security-rsocket.gradle`.
- API: `core`, `io.rsocket:rsocket-core` (rsocket-bom).
- Optional: `oauth2-resource-server`, spring-messaging.
- Main imports `org.springframework.security.oauth2.server.resource.*`
  (`BearerPayloadExchangeConverter`, `AuthenticationPayloadExchangeConverter`).
- Blocker: `oauth2-resource-server` (1).

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
