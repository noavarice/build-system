package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileService;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Build service test")
class BuildServiceTest {

  private final CompileService compileService = new CompileService();

  private final BuildService service = new BuildService(compileService);

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
}
