#!/usr/bin/env bash
# Run the BuildSpringSecurity self-hosted build on the host (no Docker/IntelliJ).
#
# Mirrors .run/build Spring Security.run.xml: launches
# com.github.build.BuildSpringSecurity over the spring-security submodule.
#
# Requirements:
#   - a full JDK 21 (JDK 25 fails: Mockito inline mock maker / Byte Buddy and JaCoCo
#     cannot read class file major version 69; see build-spring-security/BLOCKED_MODULES.md).
#     Pick a JDK 21 with JAVA_HOME (e.g. /usr/lib/jvm/java-21-openjdk-amd64).
#   - a reachable repository proxy at $NEXUS_HOST:8081 (default localhost), e.g. the
#     `repository-proxy` service from compose-local.yaml.
#   - the `spring-security` submodule checked out (git submodule update --init spring-security).
#
# Resolution cache: BuildSpringSecurity's aether local repository defaults to ~/.m2/repository
# (matching RepositoryProxySupport), so resolved Spring Security artifacts persist across runs
# (faster repeat builds, easier to inspect). We pass it explicitly so MAVEN_REPO_LOCAL can
# override it; otherwise the Java default is used.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"

# Full JDK 21 required. Honor JAVA_HOME; reject everything else (esp. 25) explicitly.
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [ "$JAVA_VERSION" -ne 21 ]; then
  echo "BuildSpringSecurity requires a full JDK 21 (found $JAVA_VERSION)." >&2
  echo "Set JAVA_HOME to a JDK 21, e.g. JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64." >&2
  exit 1
fi

# The program argument is the spring-security submodule.
SS_DIR="$REPO_ROOT/spring-security"
if [ ! -d "$SS_DIR" ]; then
  echo "Missing the spring-security submodule at $SS_DIR." >&2
  echo "Check it out with: git submodule update --init spring-security" >&2
  exit 1
fi

# Repository proxy; an existing NEXUS_HOST wins, otherwise localhost:8081.
export NEXUS_HOST="${NEXUS_HOST:-localhost}"

# buildRuntimePath contract: junit-integration + lib are handed to in-process tests.
BRTP="$REPO_ROOT/junit-integration/target/junit-integration-0.1.0.jar,$REPO_ROOT/lib/target/lib-0.1.0.jar"

# MAVEN_REPO_LOCAL overrides BuildSpringSecurity's aether local repository. Default to
# ~/.m2/repository (which is also the Java-side default); the explicit -Dmaven.repo.local
# below makes this override effective.
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"

BSS_CLASSES="$REPO_ROOT/build-spring-security/target/classes/com/github/build/BuildSpringSecurity.class"
BSS_DEPS="$REPO_ROOT/build-spring-security/target/dependencies/lib-0.1.0.jar"
LIB_JAR="$REPO_ROOT/lib/target/lib-0.1.0.jar"
JUNIT_JAR="$REPO_ROOT/junit-integration/target/junit-integration-0.1.0.jar"

# Build the required module packages if any of the outputs are stale/missing.
if [ -f "$BSS_CLASSES" ] && [ -f "$BSS_DEPS" ] && [ -f "$LIB_JAR" ] && [ -f "$JUNIT_JAR" ]; then
  echo "Existing target/ outputs are up to date; skipping package."
else
  echo "Required build outputs missing; packaging the reactor (build-spring-security + -am) ..."
  (cd "$REPO_ROOT" && mvn -q -pl build-spring-security -am package -DskipTests)
fi

# Always verify the artifacts we are about to launch against exist.
missing=0
for f in "$BSS_CLASSES" "$BSS_DEPS" "$LIB_JAR" "$JUNIT_JAR"; do
  if [ ! -f "$f" ]; then
    echo "Missing required artifact: $f (run mvn clean install)." >&2
    missing=1
  fi
done
[ "$missing" -eq 0 ] || exit 1

# build-spring-security/target/dependencies is the copy-dependencies output (runtime scope),
# which already contains lib and dependency-resolver-aether plus the aether resolver jars.
CP="$REPO_ROOT/build-spring-security/target/classes:$REPO_ROOT/build-spring-security/target/dependencies/*"

MAIN_CLASS=com.github.build.BuildSpringSecurity

exec "$JAVA_BIN" \
  "-DbuildRuntimePath=$BRTP" \
  "-Dmaven.repo.local=$MAVEN_REPO_LOCAL" \
  -cp "$CP" \
  "$MAIN_CLASS" "$SS_DIR"