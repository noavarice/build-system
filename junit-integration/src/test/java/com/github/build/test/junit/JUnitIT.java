package com.github.build.test.junit;

import com.github.build.BuildService;
import com.github.build.FsUtils;
import com.github.build.Project;
import com.github.build.SourceSet;
import com.github.build.compile.CompileService;
import com.github.build.compile.CompilerOptions;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.MavenArtifactResolverDependencyService;
import com.github.build.jar.JarService;
import com.github.build.test.JUnitTestArgs;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Integration tests for JUnit test integration")
@Disabled("Fix classpath issues")
class JUnitIT {

  private final DependencyService dependencyService;

  private final TestService testService;

  private final BuildService buildService;

  JUnitIT(@TempDir final Path localRepositoryBasePath) {
    final RepositorySystem repoSystem = new RepositorySystemSupplier().get();
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    session.setSystemProperty("java.version", "21");
    final var localRepo = new org.eclipse.aether.repository.LocalRepository(
        localRepositoryBasePath.toFile()
    );
    final var manager = repoSystem.newLocalRepositoryManager(session, localRepo);
    session.setLocalRepositoryManager(manager);

    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );
    final List<org.eclipse.aether.repository.RemoteRepository> repositories = List.of(
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("nexus", "default", "http://" + nexusHost + ":8081/repository/maven-central")
            .build()
    );
    dependencyService = new MavenArtifactResolverDependencyService(
        repoSystem,
        session,
        repositories
    );
    testService = new TestService(dependencyService);
    buildService = new BuildService(new CompileService(), dependencyService, new JarService());
  }

  @DisplayName("Check running tests work")
  @TestFactory
  DynamicTest[] testRunningWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);

    final DependencyConstraints junitBom = dependencyService.getConstraints(
        GroupArtifactVersion.parse("org.junit:junit-bom:6.0.1")
    );
    final var main = SourceSet
        .withMainDefaults()
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .withDependencyConstraints(junitBom)
        .compileAndRunWith(main)
        .compileAndRunWith("org.junit.jupiter:junit-jupiter-api")
        .runWith(
            "org.junit.jupiter:junit-jupiter-engine",
            "ch.qos.logback:logback-classic:1.5.19"
        )
        .build();
    final var project = Project
        .withId("calculator")
        .withPath(Path.of("calculator"))
        .withSourceSet(main)
        .withSourceSet(test)
        .build();

    // compile main and test source sets
    Assertions.assertTrue(buildService.compileMain(tempDir, project, CompilerOptions.EMPTY));
    buildService.copyResources(tempDir, project, SourceSet.Id.MAIN);

    Assertions.assertTrue(buildService.compileTest(tempDir, project, CompilerOptions.EMPTY));
    buildService.copyResources(tempDir, project, SourceSet.Id.TEST);

    final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
    final Path buildRuntimePath = Path.of(buildRuntimePathStr);
    final var testArgs = new JUnitTestArgs(
        List.of(buildRuntimePath),
        ClassLoader.getPlatformClassLoader()
    );
    final TestResults result = testService.withJUnit(tempDir, project, testArgs);
    return new DynamicTest[]{
        DynamicTest.dynamicTest("Check succeeded count",
            () -> Assertions.assertEquals(3, result.testsSucceededCount())),
        DynamicTest.dynamicTest("Check failed count",
            () -> Assertions.assertEquals(0, result.testsFailedCount())),
        DynamicTest.dynamicTest("Check skipped count",
            () -> Assertions.assertEquals(0, result.testsSkippedCount())),
    };
  }
}
