package com.github.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileArgs;
import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyService;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
import com.github.build.test.Test;
import com.github.build.test.TestResults;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Tests for JUnit test integration")
class JUnitTest {

  private final CompileService compileService = new CompileService();

  private final BuildService buildService;

  JUnitTest(@TempDir final Path localRepositoryBasePath) {
    final var remoteRepository = new RemoteRepository(
        // TODO: externalize
        URI.create("http://localhost:8081/repository/maven-central"),
        HttpClient.newHttpClient()
    );
    final var localRepository = new LocalRepository(
        localRepositoryBasePath,
        Map.of("sha256", "SHA-256")
    );
    final var dependencyService = new DependencyService(List.of(remoteRepository), localRepository);
    buildService = new BuildService(compileService, dependencyService);
  }

  @DisplayName("Check running tests work")
  @TestFactory
  DynamicTest[] testRunningWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);

    final var main = SourceSetArgs
        .builder()
        .build();
    final var test = SourceSetArgs
        .builder()
        .withType(SourceSetArgs.Type.TEST)
        .build();
    final var project = Project
        .withId("calculator")
        .withPath(Path.of("calculator"))
        .withMainSourceSet(main)
        .withTestSourceSet(test)
        .build();
    final var projectRoot = tempDir.resolve(project.id().value());

    // compile main classes
    buildService.compileMain(tempDir, project);

    // compile test classes
    {
      final var source = projectRoot.resolve("src/test/java/org/example/CalculatorTest.java");
      // TODO: express dependency on other source set
      final Path mainClassesDir = projectRoot
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().classesDir())
          .resolve("main");
      final Path classesDir = projectRoot
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().classesDir())
          .resolve("test");
      final var jupiterApi = tempDir.resolve("junit-jupiter-api.jar");
      final var apiguardianApi = tempDir.resolve("apiguardian-api.jar");
      final var args = new CompileArgs(
          Set.of(source),
          classesDir,
          Set.of(mainClassesDir, jupiterApi, apiguardianApi)
      );
      assertTrue(compileService.compile(args));
    }

    final TestResults result = Test.withJUnit(tempDir, project);
    return new DynamicTest[]{
        dynamicTest("Check succeeded count", () -> assertEquals(3, result.testsSucceededCount())),
        dynamicTest("Check failed count", () -> assertEquals(0, result.testsFailedCount())),
        dynamicTest("Check skipped count", () -> assertEquals(0, result.testsSkippedCount())),
    };
  }
}
