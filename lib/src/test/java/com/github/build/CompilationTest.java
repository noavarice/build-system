package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compilation tests.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Compilation tests")
class CompilationTest {

  @DisplayName("Compiling hello world project works")
  @TestFactory
  DynamicTest[] compilingHelloWorldWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
    final Path source = tempDir.resolve("org/example/HelloWorld.java");

    final var args = new CompileArgs(Set.of(source), tempDir.resolve("classes"), Set.of());
    args.sources().forEach(path -> assumeThat(path).isRegularFile());
    assumeThat(args.classesDir()).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Check compilation succeeds",
            () -> assertTrue(Build.compile(args))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertThat(
                args.classesDir().resolve("org/example/HelloWorld.class")
            ).isNotEmptyFile()
        ),
    };
  }

  @DisplayName("Compiling without required classpath dependency fails")
  @TestFactory
  DynamicTest[] compilingWithoutRequiredDependencyFails(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final Path source = tempDir.resolve("org/example/Slf4jExample.java");

    final var args = new CompileArgs(Set.of(source), tempDir.resolve("classes"), Set.of());
    args.sources().forEach(path -> assumeThat(path).isRegularFile());
    assumeThat(args.classesDir()).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Check compilation fails",
            () -> assertFalse(Build.compile(args))
        ),
        dynamicTest(
            "Check class file is not generated",
            () -> assertThat(
                args.classesDir().resolve("org/example/Slf4jExample.class")
            ).doesNotExist()
        ),
    };
  }

  @DisplayName("Compiling with dependency works")
  @TestFactory
  DynamicTest[] compilingWithDependencyWorks(@TempDir final Path tempDir) {
    FsUtils.setupFromYaml("/projects/slf4j.yaml", tempDir);
    final Path source = tempDir.resolve("org/example/Slf4jExample.java");
    final Path jar = tempDir.resolve("slf4j-api.jar");
    final var args = new CompileArgs(Set.of(source), tempDir.resolve("classes"), Set.of(jar));

    assumeThat(source).isRegularFile();
    assumeThat(jar).isRegularFile();
    assumeThat(args.classesDir()).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Check build succeeds",
            () -> assertTrue(Build.compile(args))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertThat(
                args.classesDir().resolve("org/example/Slf4jExample.class")
            ).isNotEmptyFile()
        ),
    };
  }
}
