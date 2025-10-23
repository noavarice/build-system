package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
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
@DisplayName("Build service test")
class BuildServiceTest {

  private final BuildService service;

  BuildServiceTest(@TempDir final Path localRepositoryBasePath) {
    final var compileService = new CompileService();
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
    service = new BuildService(compileService, dependencyService);
  }

  @DisplayName("Compiling main source set works")
  @TestFactory
  DynamicTest[] compilingMainWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
    final var main = SourceSetArgs
        .builder()
        .build();
    final var project = Project
        .withId("calculator")
        .withPath(Path.of("calculator"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("calculator/build/classes/main");
    assumeThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Calculator.class");
    assumeThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation succeeds",
            () -> assertTrue(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file exists",
            () -> assertThat(classFile).isRegularFile()
        ),
    };
  }

  @DisplayName("Compiling main source with local JAR dependency works")
  @TestFactory
  DynamicTest[] compilingMainWithLocalJarDependencyWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var main = SourceSetArgs
        .builder()
        .compileWithLocalJar(tempDir.resolve("slf4j-api.jar"))
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation succeeds",
            () -> assertTrue(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file exists",
            () -> assertThat(classFile).isRegularFile()
        ),
    };
  }

  @DisplayName("Compiling main source without remote dependency fails")
  @TestFactory
  DynamicTest[] compilingMainWithoutRemoteDependencyFails(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var main = SourceSetArgs
        .builder()
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation fails",
            () -> assertFalse(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file is not generated",
            () -> assertThat(classFile).doesNotExist()
        ),
    };
  }

  @DisplayName("Compiling main source with remote dependency works")
  @TestFactory
  DynamicTest[] compilingMainWithRemoteDependencyWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var slf4jApi = GroupArtifactVersion.parse("org.slf4j:slf4j-api:2.0.17");
    final var main = SourceSetArgs
        .builder()
        .compileWith(slf4jApi)
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation succeeds",
            () -> assertTrue(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file exists",
            () -> assertThat(classFile).isRegularFile()
        ),
    };
  }

  @DisplayName("""
      Compiling main source with dependency without version
       and without dependency constraints fails
      """)
  @TestFactory
  DynamicTest[] compilingMainWithDependencyWithoutVersionAndConstraintFails(
      @TempDir final Path tempDir
  ) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var slf4jApi = new GroupArtifact("org.slf4j", "slf4j-api");
    final var main = SourceSetArgs
        .builder()
        .compileWith(slf4jApi)
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation fails",
            () -> assertFalse(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file is not generated",
            () -> assertThat(classFile).doesNotExist()
        ),
    };
  }

  @DisplayName("""
      Compiling main source with dependency without version
       and with dependency constraints but without necessary
       constraint fails
      """)
  @TestFactory
  DynamicTest[] compilingMainWithoutNecessaryConstraintFails(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var slf4jApi = new GroupArtifact("org.slf4j", "slf4j-api");
    final var constraints = DependencyConstraints
        .builder()
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-core"), "1.5.20")
        .build();
    final var main = SourceSetArgs
        .builder()
        .compileWith(slf4jApi)
        .withDependencyConstraints(constraints)
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation fails",
            () -> assertFalse(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file is not generated",
            () -> assertThat(classFile).doesNotExist()
        ),
    };
  }

  @DisplayName("""
      Compiling main source with dependency without version
       and with dependency constraints and with necessary
       constraint works
      """)
  @TestFactory
  DynamicTest[] compilingMainWithNecessaryConstraintWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final var slf4jApi = new GroupArtifact("org.slf4j", "slf4j-api");
    final var constraints = DependencyConstraints
        .builder()
        .withExactVersion(slf4jApi, "2.0.17")
        .build();
    final var main = SourceSetArgs
        .builder()
        .compileWith(slf4jApi)
        .withDependencyConstraints(constraints)
        .build();
    final var project = Project
        .withId("slf4j-example")
        .withPath(Path.of("slf4j-example"))
        .withMainSourceSet(main)
        .build();

    final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
    assertThat(classesDir).doesNotExist();

    final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
    assertThat(classFile).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Compilation succeeds",
            () -> assertTrue(service.compileMain(tempDir, project))
        ),
        dynamicTest(
            "Class file exists",
            () -> assertThat(classFile).isRegularFile()
        ),
    };
  }
}
