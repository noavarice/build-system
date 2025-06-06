package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.Dependencies;
import com.github.build.deps.Dependency;
import com.github.build.deps.ResolvedRemoteDependency;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for dependency resolution process.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for dependency resolution process")
class DependencyResolutionTest {

  @DisplayName("Check resolution works")
  @TestFactory
  DynamicTest[] testResolutionWorks(@TempDir final Path tempDir) {
    final var dependency = new Dependency.Remote(
        "org.slf4j",
        "slf4j-api",
        "2.0.0",
        Dependency.Scope.COMPILE
    );
    final var project = getProject(dependency);

    final var localRepoPath = tempDir.resolve("local-repo");
    final var localRepo = new LocalRepository(localRepoPath.toFile());
    final var cache = new ConcurrentHashMap<Dependency.Remote, ResolvedRemoteDependency>();

    final var jarPath = localRepoPath.resolve("org/slf4j/slf4j-api/2.0.0/slf4j-api-2.0.0.jar");
    assumeThat(jarPath).doesNotExist();

    return new DynamicTest[]{
        dynamicTest("Check method works without exceptions",
            () -> assertThatCode(
                () -> Dependencies.resolve(project, Dependencies.DEFAULT_REPOS, localRepo, cache)
            ).doesNotThrowAnyException()
        ),
        dynamicTest("Check cache contains new entry",
            () -> assertThat(cache)
                .containsKey(dependency)
                .containsValue(new ResolvedRemoteDependency(jarPath))
        ),
        dynamicTest("Check JAR actually downloaded", () -> assertThat(jarPath).isNotEmptyFile()),
    };
  }

  @DisplayName("Check transitive dependency resolution works")
  @TestFactory
  DynamicTest[] testTransitiveDependencyResolutionWorks(@TempDir final Path tempDir) {
    final var logbackClassic = new Dependency.Remote(
        "ch.qos.logback",
        "logback-classic",
        "1.5.14",
        Dependency.Scope.COMPILE
    );
    final var logbackCore = new Dependency.Remote(
        "ch.qos.logback",
        "logback-core",
        "1.5.14",
        Dependency.Scope.COMPILE
    );
    final var slf4jApi = new Dependency.Remote(
        "org.slf4j",
        "slf4j-api",
        "2.0.15",
        Dependency.Scope.COMPILE
    );
    final var jakartaMail = new Dependency.Remote(
        "jakarta.mail",
        "jakarta.mail-api",
        "2.1.0",
        Dependency.Scope.COMPILE
    );
    final var jakartaActivation = new Dependency.Remote(
        "jakarta.activation",
        "jakarta.activation-api",
        "2.1.0",
        Dependency.Scope.COMPILE
    );
    final var janino = new Dependency.Remote(
        "org.codehaus.janino",
        "janino",
        "3.1.8",
        Dependency.Scope.COMPILE
    );
    final var janinoCommonsCompiler = new Dependency.Remote(
        "org.codehaus.janino",
        "commons-compiler",
        "3.1.8",
        Dependency.Scope.COMPILE
    );

    final var project = getProject(logbackClassic);

    final var localRepoPath = tempDir.resolve("local-repo");
    final var localRepo = new LocalRepository(localRepoPath.toFile());
    final var cache = new ConcurrentHashMap<Dependency.Remote, ResolvedRemoteDependency>();

    final var slf4jApiJarPath = localRepoPath.resolve(
        "org/slf4j/slf4j-api/2.0.15/slf4j-api-2.0.15.jar"
    );
    assumeThat(slf4jApiJarPath).doesNotExist();

    final var logbackCoreJarPath = localRepoPath.resolve(
        "ch/qos/logback/logback-core/1.5.14/logback-core-1.5.14.jar"
    );
    assumeThat(logbackCoreJarPath).doesNotExist();

    final var logbackClassicJarPath = localRepoPath.resolve(
        "ch/qos/logback/logback-classic/1.5.14/logback-classic-1.5.14.jar"
    );
    assumeThat(logbackClassicJarPath).doesNotExist();

    final var jakartaMailJarPath = localRepoPath.resolve(
        "jakarta/mail/jakarta.mail-api/2.1.0/jakarta.mail-api-2.1.0.jar"
    );
    assumeThat(jakartaMailJarPath).doesNotExist();

    final var jakartaActivationJarPath = localRepoPath.resolve(
        "jakarta/activation/jakarta.activation-api/2.1.0/jakarta.activation-api-2.1.0.jar"
    );
    assumeThat(jakartaActivationJarPath).doesNotExist();

    final var janinoJarPath = localRepoPath.resolve(
        "org/codehaus/janino/janino/3.1.8/janino-3.1.8.jar"
    );
    assumeThat(janinoJarPath).doesNotExist();

    final var janinoCommonsCompilerJarPath = localRepoPath.resolve(
        "org/codehaus/janino/commons-compiler/3.1.8/commons-compiler-3.1.8.jar"
    );
    assumeThat(janinoCommonsCompilerJarPath).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Check method works without exceptions",
            () -> assertThatCode(
                () -> Dependencies.resolve(project, Dependencies.DEFAULT_REPOS, localRepo, cache)
            ).doesNotThrowAnyException()
        ),
        dynamicTest(
            "Check cache contains slf4j API entry",
            () -> assertThat(cache).containsKey(slf4jApi)
        ),
        dynamicTest(
            "Check cache contains logback-core entry",
            () -> assertThat(cache).containsKey(logbackCore)
        ),
        dynamicTest(
            "Check cache contains logback-classic entry",
            () -> assertThat(cache).containsKey(logbackClassic)
        ),
        dynamicTest(
            "Check cache contains Jakarta Mail API entry",
            () -> assertThat(cache).containsKey(jakartaMail)
        ),
        dynamicTest(
            "Check cache contains Jakarta Activation API entry",
            () -> assertThat(cache).containsKey(jakartaActivation)
        ),
        dynamicTest(
            "Check cache contains Janino entry",
            () -> assertThat(cache).containsKey(janino)
        ),
        dynamicTest(
            "Check cache contains Janino Commons Compiler entry",
            () -> assertThat(cache).containsKey(janinoCommonsCompiler)
        ),
        dynamicTest(
            "Check slf4j jar downloaded",
            () -> assertThat(slf4jApiJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check logback-core jar downloaded",
            () -> assertThat(logbackCoreJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check logback-classic jar downloaded",
            () -> assertThat(logbackClassicJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check Jakarta Mail API jar downloaded",
            () -> assertThat(jakartaMailJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check Jakarta Activation API jar downloaded",
            () -> assertThat(jakartaActivationJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check Janino jar downloaded",
            () -> assertThat(janinoJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check Janino Commons Compiler jar downloaded",
            () -> assertThat(janinoCommonsCompilerJarPath).isNotEmptyFile()
        ),
    };
  }

  @DisplayName("Check dependency resolution with filter works")
  @TestFactory
  DynamicTest[] testDependencyResolutionWithFilterWorks(@TempDir final Path tempDir) {
    final var logbackClassic = new Dependency.Remote(
        "ch.qos.logback",
        "logback-classic",
        "1.5.14",
        Dependency.Scope.COMPILE
    );
    final var logbackCore = new Dependency.Remote(
        "ch.qos.logback",
        "logback-core",
        "1.5.14",
        Dependency.Scope.COMPILE
    );
    final var slf4jApi = new Dependency.Remote(
        "org.slf4j",
        "slf4j-api",
        "2.0.15",
        Dependency.Scope.COMPILE
    );
    final var jakartaMail = new Dependency.Remote(
        "jakarta.mail",
        "jakarta.mail-api",
        "2.1.0",
        Dependency.Scope.COMPILE
    );
    final var jakartaActivation = new Dependency.Remote(
        "jakarta.activation",
        "jakarta.activation-api",
        "2.1.0",
        Dependency.Scope.COMPILE
    );
    final var janino = new Dependency.Remote(
        "org.codehaus.janino",
        "janino",
        "3.1.8",
        Dependency.Scope.COMPILE
    );
    final var janinoCommonsCompiler = new Dependency.Remote(
        "org.codehaus.janino",
        "commons-compiler",
        "3.1.8",
        Dependency.Scope.COMPILE
    );

    final var project = getProject(logbackClassic);

    final var localRepoPath = tempDir.resolve("local-repo");
    final var localRepo = new LocalRepository(localRepoPath.toFile());
    final var cache = new ConcurrentHashMap<Dependency.Remote, ResolvedRemoteDependency>();

    final var slf4jApiJarPath = localRepoPath.resolve(
        "org/slf4j/slf4j-api/2.0.15/slf4j-api-2.0.15.jar"
    );
    assumeThat(slf4jApiJarPath).doesNotExist();

    final var logbackCoreJarPath = localRepoPath.resolve(
        "ch/qos/logback/logback-core/1.5.14/logback-core-1.5.14.jar"
    );
    assumeThat(logbackCoreJarPath).doesNotExist();

    final var logbackClassicJarPath = localRepoPath.resolve(
        "ch/qos/logback/logback-classic/1.5.14/logback-classic-1.5.14.jar"
    );
    assumeThat(logbackClassicJarPath).doesNotExist();

    final var jakartaMailJarPath = localRepoPath.resolve(
        "jakarta/mail/jakarta.mail-api/2.1.0/jakarta.mail-api-2.1.0.jar"
    );
    assumeThat(jakartaMailJarPath).doesNotExist();

    final var jakartaActivationJarPath = localRepoPath.resolve(
        "jakarta/activation/jakarta.activation-api/2.1.0/jakarta.activation-api-2.1.0.jar"
    );
    assumeThat(jakartaActivationJarPath).doesNotExist();

    final var janinoJarPath = localRepoPath.resolve(
        "org/codehaus/janino/janino/3.1.8/janino-3.1.8.jar"
    );
    assumeThat(janinoJarPath).doesNotExist();

    final var janinoCommonsCompilerJarPath = localRepoPath.resolve(
        "org/codehaus/janino/commons-compiler/3.1.8/commons-compiler-3.1.8.jar"
    );
    assumeThat(janinoCommonsCompilerJarPath).doesNotExist();

    final var filter = new DependencyFilter() {
      @Override
      public boolean accept(final DependencyNode node, final List<DependencyNode> parents) {
        return !node.getDependency().isOptional();
      }
    };

    return new DynamicTest[]{
        dynamicTest(
            "Check method works without exceptions",
            () -> assertThatCode(() ->
                Dependencies.resolve(project, Dependencies.DEFAULT_REPOS, localRepo, cache, filter)
            ).doesNotThrowAnyException()
        ),
        dynamicTest(
            "Check cache contains slf4j API entry",
            () -> assertThat(cache).containsKey(slf4jApi)
        ),
        dynamicTest(
            "Check cache contains logback-core entry",
            () -> assertThat(cache).containsKey(logbackCore)
        ),
        dynamicTest(
            "Check cache contains logback-classic entry",
            () -> assertThat(cache).containsKey(logbackClassic)
        ),
        dynamicTest(
            "Check cache does not contain Jakarta Mail API entry",
            () -> assertThat(cache).doesNotContainKey(jakartaMail)
        ),
        dynamicTest(
            "Check cache does not container Jakarta Activation API entry",
            () -> assertThat(cache).doesNotContainKey(jakartaActivation)
        ),
        dynamicTest(
            "Check cache does not contain Janino entry",
            () -> assertThat(cache).doesNotContainKey(janino)
        ),
        dynamicTest(
            "Check cache does not container Janino Commons Compiler entry",
            () -> assertThat(cache).doesNotContainKey(janinoCommonsCompiler)
        ),
        dynamicTest(
            "Check slf4j jar downloaded",
            () -> assertThat(slf4jApiJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check logback-core jar downloaded",
            () -> assertThat(logbackCoreJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check logback-classic jar downloaded",
            () -> assertThat(logbackClassicJarPath).isNotEmptyFile()
        ),
        dynamicTest(
            "Check Jakarta Mail API jar is not downloaded",
            () -> assertThat(jakartaMailJarPath).doesNotExist()
        ),
        dynamicTest(
            "Check Jakarta Activation API jar is not downloaded",
            () -> assertThat(jakartaActivationJarPath).doesNotExist()
        ),
        dynamicTest(
            "Check Janino jar is not downloaded",
            () -> assertThat(janinoJarPath).doesNotExist()
        ),
        dynamicTest(
            "Check Janino Commons Compiler jar is downloaded",
            () -> assertThat(janinoCommonsCompilerJarPath).doesNotExist()
        ),
    };
  }

  private static Project getProject(final Dependency.Remote dependency) {
    return new Project(
        new Project.Id("test-project"),
        Path.of(""),
        Set.of(new SourceSet(
            new SourceSet.Id("main"),
            List.of(Path.of("src/main/java")),
            List.of(Path.of("src/main/resources")),
            SourceSet.Type.PROD,
            Set.of(dependency)
        )),
        Project.ArtifactLayout.DEFAULT
    );
  }
}
