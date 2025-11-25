package com.github.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Tests for JUnit test integration")
class JUnitTest {

  private final TestService testService;

  private final BuildService buildService;

  JUnitTest(@TempDir final Path localRepositoryBasePath) {
    final var nexusLocal = new RemoteRepository(
        // TODO: externalize
        URI.create("http://localhost:8081/repository/maven-central"),
        HttpClient.newHttpClient()
    );
    final var nexusDocker = new RemoteRepository(
        URI.create("http://nexus:8081/repository/maven-central"),
        HttpClient.newHttpClient()
    );
    final var localRepository = new LocalRepository(
        localRepositoryBasePath,
        Map.of("sha256", "SHA-256")
    );
    final var compileService = new CompileService();
    final var dependencyService = new DependencyService(
        List.of(nexusLocal, nexusDocker),
        localRepository
    );
    testService = new TestService(dependencyService);
    buildService = new BuildService(compileService, dependencyService);
  }

  @DisplayName("Check running tests work")
  @Disabled("""
      Method to be tested replaces thread context classloader
      to set test source set runtime classpath, but it does not work
      as expected when we're testing it with JUnit as JupiterTestEngine
      is already loaded by the build tool (currently by Maven). Loading
      JupiterTestEngine via ServiceLoader fails as JupiterTestEngine
      and TestEngine interface are loaded using different classloaders.
      """)
  @TestFactory
  DynamicTest[] testRunningWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);

    final var main = SourceSet
        .withMainDefaults()
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .compileWith(main)
        .compileAndRunWith(GroupArtifactVersion.parse("org.junit.jupiter:junit-jupiter-api:5.13.4"))
        .runWith(
            GroupArtifactVersion.parse("org.junit.jupiter:junit-jupiter-engine:5.13.4")
        )
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

    final TestResults result = testService.withJUnit(tempDir, project);
    return new DynamicTest[]{
        dynamicTest("Check succeeded count", () -> assertEquals(3, result.testsSucceededCount())),
        dynamicTest("Check failed count", () -> assertEquals(0, result.testsFailedCount())),
        dynamicTest("Check skipped count", () -> assertEquals(0, result.testsSkippedCount())),
    };
  }
}
