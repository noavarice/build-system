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
    final var slf4jApi = new Dependency.Remote(
        "org.slf4j",
        "slf4j-api",
        "2.0.0",
        Dependency.Scope.COMPILE
    );
    final var project = new Project(
        new Project.Id("test-project"),
        Path.of(""),
        Set.of(new SourceSet(
            new SourceSet.Id("main"),
            List.of(Path.of("src/main/java")),
            List.of(Path.of("src/main/resources")),
            SourceSet.Type.PROD,
            Set.of(slf4jApi)
        )),
        Project.ArtifactLayout.DEFAULT
    );

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
                .containsKey(slf4jApi)
                .containsValue(new ResolvedRemoteDependency(jarPath))
        ),
        dynamicTest("Check JAR actually downloaded", () -> assertThat(jarPath).isNotEmptyFile()),
    };
  }
}
