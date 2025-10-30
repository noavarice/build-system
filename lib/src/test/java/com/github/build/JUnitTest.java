package com.github.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Tests for JUnit test integration")
class JUnitTest {

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
    final var compileService = new CompileService();
    final var dependencyService = new DependencyService(List.of(remoteRepository), localRepository);
    buildService = new BuildService(compileService, dependencyService);
  }

  @DisplayName("Check running tests work")
  @TestFactory
  DynamicTest[] testRunningWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);

    final var main = SourceSet
        .withMainDefaults()
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .compileWith(main)
        .compileWithLocalJar(tempDir.resolve("junit-jupiter-api.jar"))
        .compileWithLocalJar(tempDir.resolve("apiguardian-api.jar"))
        .build();
    final var project = Project
        .withId("calculator")
        .withPath(Path.of("calculator"))
        .withSourceSet(main)
        .withSourceSet(test)
        .build();

    // compile main and test source sets
    assertTrue(buildService.compileMain(tempDir, project));
    assertTrue(buildService.compileTest(tempDir, project));

    final TestResults result = Test.withJUnit(tempDir, project);
    return new DynamicTest[]{
        dynamicTest("Check succeeded count", () -> assertEquals(3, result.testsSucceededCount())),
        dynamicTest("Check failed count", () -> assertEquals(0, result.testsFailedCount())),
        dynamicTest("Check skipped count", () -> assertEquals(0, result.testsSkippedCount())),
    };
  }
}
