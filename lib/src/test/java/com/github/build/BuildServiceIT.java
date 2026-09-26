package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileService;
import com.github.build.compile.CompilerOptions;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.maven.MavenArtifactResolverDependencyService;
import com.github.build.deps.maven.ProjectWorkspaceReader;
import com.github.build.jar.JarService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Build service integration tests")
class BuildServiceIT {

  private final ProjectService projectService;

  private final BuildService service;

  BuildServiceIT(@TempDir final Path localRepositoryBasePath) {
    projectService = new ProjectService();
    final DependencyService dependencyService = createDependencyService(
        localRepositoryBasePath,
        projectService,
        null // without project workspace reader, so does not suit all tests
    );
    service = new BuildService(new CompileService(), dependencyService, new JarService());
  }

  @DisplayName("Check compiling main source set")
  @Nested
  class CompileMain {

    @DisplayName("Compiling empty source set works")
    @TestFactory
    DynamicTest[] compilingEmptyWorks(@TempDir final Path tempDir) throws IOException {
      Files.createDirectories(
          tempDir
              .resolve("empty-sources")
              .resolve("src")
              .resolve("main")
              .resolve("java")
      );
      final var project = projectService.create("org.example", "empty-sources", "0.1.0",
          builder -> builder
              .withPath(Path.of("empty-sources"))
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );

      final Path classesDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().classesDir())
          .resolve("main");
      assertThat(classesDir).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
          ),
          dynamicTest(
              "Classes directory created but empty",
              () -> assertThat(classesDir).isEmptyDirectory()
          ),
      };
    }

    @DisplayName("Compiling main source set works")
    @TestFactory
    DynamicTest[] compilingMainWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
      final var project = projectService.create("org.example", "calculator", "0.1.0",
          builder -> builder
              .withPath(Path.of("calculator"))
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );

      final Path classesDir = tempDir.resolve("calculator/build/classes/main");
      assumeThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Calculator.class");
      assumeThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var main = new MainSourceSetArgs(
          SourceSet.Id.MAIN.toString(),
          Set.of(Path.of("src", "main", "java")),
          Set.of(Path.of("src", "main", "resources")),
          List.of(new LocalJar(tempDir.resolve("slf4j-api.jar"))),
          List.of(),
          DependencyConstraints.EMPTY
      );
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation fails",
              () -> assertFalse(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var main = new MainSourceSetArgs(
          SourceSet.Id.MAIN.toString(),
          Set.of(Path.of("src", "main", "java")),
          Set.of(Path.of("src", "main", "resources")),
          List.of(slf4jApi),
          List.of(),
          DependencyConstraints.EMPTY
      );
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
      );

      final DependencyService dependencyService = createDependencyService(
          tempDir.resolve("local-repository"),
          projectService,
          tempDir
      );
      final var service = new BuildService(
          new CompileService(),
          dependencyService,
          new JarService()
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var main = new MainSourceSetArgs(
          SourceSet.Id.MAIN.toString(),
          Set.of(Path.of("src", "main", "java")),
          Set.of(Path.of("src", "main", "resources")),
          List.of(slf4jApi),
          List.of(),
          DependencyConstraints.EMPTY
      );
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation fails",
              () -> assertFalse(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var main = new MainSourceSetArgs(
          SourceSet.Id.MAIN.toString(),
          Set.of(Path.of("src", "main", "java")),
          Set.of(Path.of("src", "main", "resources")),
          List.of(slf4jApi),
          List.of(),
          constraints
      );
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation fails",
              () -> assertFalse(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
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
      final var main = new MainSourceSetArgs(
          SourceSet.Id.MAIN.toString(),
          Set.of(Path.of("src", "main", "java")),
          Set.of(Path.of("src", "main", "resources")),
          List.of(slf4jApi),
          List.of(),
          constraints
      );
      final var project = projectService.create("org.example", "slf4j-example", "0.1.0",
          builder -> builder
              .withPath(Path.of("slf4j-example"))
              .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
      );

      final DependencyService dependencyService = createDependencyService(
          tempDir.resolve("local-repository"),
          projectService,
          tempDir
      );

      final BuildService service = new BuildService(
          new CompileService(),
          dependencyService,
          new JarService()
      );

      final Path classesDir = tempDir.resolve("slf4j-example/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/Slf4jExample.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY))
          ),
          dynamicTest(
              "Class file exists",
              () -> assertThat(classFile).isRegularFile()
          ),
      };
    }
  }

  @DisplayName("Check compiling main source set")
  @Nested
  class CompileMainWithProjectDependency {

    @DisplayName("Compiling main source set without compiling project dependency fails")
    @TestFactory
    DynamicTest[] compilingMainWithoutCompilingProjectDependencyFails(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
      FsUtils.setupFromYaml("/projects/calculator-consumer.yaml", tempDir);

      final Project calculatorProject;
      {
        calculatorProject = projectService.create("org.example", "calculator", "0.1.0",
            builder -> builder
                .withPath(Path.of("calculator"))
                .withSourceSets(
                    MainSourceSetArgs.withMainDefaults(),
                    TestSourceSetArgs.withTestDefaults()
                )
        );
      }

      final Project calculatorConsumerProject;
      {
        final var main = new MainSourceSetArgs(
            SourceSet.Id.MAIN.toString(),
            Set.of(Path.of("src", "main", "java")),
            Set.of(Path.of("src", "main", "resources")),
            List.of(calculatorProject),
            List.of(),
            DependencyConstraints.EMPTY
        );
        calculatorConsumerProject = projectService.create("org.example", "calculator-consumer",
            "0.1.0",
            builder -> builder
                .withPath(Path.of("calculator-consumer"))
                .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
        );
      }

      final DependencyService dependencyService = createDependencyService(
          tempDir.resolve("local-repository"),
          projectService,
          tempDir
      );
      final var service = new BuildService(
          new CompileService(),
          dependencyService,
          new JarService()
      );

      final Path classesDir = tempDir.resolve("calculator-consumer/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/CalculatorConsumer.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation fails",
              () -> assertFalse(
                  service.compileMain(tempDir, calculatorConsumerProject, CompilerOptions.EMPTY)
              )
          ),
          dynamicTest(
              "Class file does not exist",
              () -> assertThat(classFile).doesNotExist()
          ),
      };
    }

    @DisplayName("Compiling main source set with compiled project dependency works")
    @TestFactory
    DynamicTest[] compilingMainWithCompiledProjectDependencyWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
      FsUtils.setupFromYaml("/projects/calculator-consumer.yaml", tempDir);

      final Project calculatorProject;
      {
        calculatorProject = projectService.create("org.example", "calculator", "0.1.0",
            builder -> builder
                .withPath(Path.of("calculator"))
                .withSourceSets(
                    MainSourceSetArgs.withMainDefaults(),
                    TestSourceSetArgs.withTestDefaults()
                )
        );
      }

      final Project calculatorConsumerProject;
      {
        final var main = new MainSourceSetArgs(
            SourceSet.Id.MAIN.toString(),
            Set.of(Path.of("src", "main", "java")),
            Set.of(Path.of("src", "main", "resources")),
            List.of(calculatorProject),
            List.of(),
            DependencyConstraints.EMPTY
        );
        calculatorConsumerProject = projectService.create("org.example", "calculator-consumer",
            "0.1.0",
            builder -> builder
                .withPath(Path.of("calculator-consumer"))
                .withSourceSets(main, TestSourceSetArgs.withTestDefaults())
        );
      }

      final DependencyService dependencyService = createDependencyService(
          tempDir.resolve("local-repository"),
          projectService,
          tempDir
      );
      final var service = new BuildService(
          new CompileService(),
          dependencyService,
          new JarService()
      );

      assertThat(service.compileMain(tempDir, calculatorProject, CompilerOptions.EMPTY)).isTrue();
      service.createJar(tempDir, calculatorProject, Map.of(), null);

      final Path classesDir = tempDir.resolve("calculator-consumer/build/classes/main");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/CalculatorConsumer.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(
                  service.compileMain(tempDir, calculatorConsumerProject, CompilerOptions.EMPTY)
              )
          ),
          dynamicTest(
              "Class file exists",
              () -> assertThat(classFile).isRegularFile()
          ),
      };
    }
  }

  @DisplayName("Check compiling test source set")
  @Nested
  class CompileTest {

    @DisplayName("Compiling test source set without compiling main works")
    @TestFactory
    DynamicTest[] compilingTestWithoutMainFails(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
      final var main = MainSourceSetArgs.withMainDefaults();
      final var test = new TestSourceSetArgs(
          SourceSet.Id.TEST.toString(),
          Set.of(Path.of("src", "test", "java")),
          Set.of(Path.of("src", "test", "resources")),
          List.of(
              main,
              new LocalJar(tempDir.resolve("junit-jupiter-api.jar")),
              new LocalJar(tempDir.resolve("apiguardian-api.jar"))
          ),
          List.of(),
          DependencyConstraints.EMPTY
      );
      final var project = projectService.create("org.example", "calculator", "0.1.0",
          builder -> builder
              .withPath(Path.of("calculator"))
              .withSourceSets(main, test)
      );

      final Path classesDir = tempDir.resolve("calculator/build/classes/test");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/CalculatorTest.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation fails",
              () -> assertFalse(service.compileTest(tempDir, project, CompilerOptions.EMPTY))
          ),
          dynamicTest(
              "Class file does not exist",
              () -> assertThat(classFile).doesNotExist()
          ),
      };
    }

    @DisplayName("Compiling test source set works")
    @TestFactory
    DynamicTest[] compilingTestAfterMainWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/calculator.yaml", tempDir);
      final var main = MainSourceSetArgs.withMainDefaults();
      final var test = new TestSourceSetArgs(
          SourceSet.Id.TEST.toString(),
          Set.of(Path.of("src", "test", "java")),
          Set.of(Path.of("src", "test", "resources")),
          List.of(
              main,
              new LocalJar(tempDir.resolve("junit-jupiter-api.jar")),
              new LocalJar(tempDir.resolve("apiguardian-api.jar"))
          ),
          List.of(),
          DependencyConstraints.EMPTY
      );
      final var project = projectService.create("org.example", "calculator", "0.1.0",
          builder -> builder
              .withPath(Path.of("calculator"))
              .withSourceSets(main, test)
      );

      final var dependencyService =
          createDependencyService(tempDir.resolve("local-repository"), projectService, tempDir);
      final BuildService service = new BuildService(
          new CompileService(),
          dependencyService,
          new JarService()
      );
      assertTrue(service.compileMain(tempDir, project, CompilerOptions.EMPTY));
      service.createJar(tempDir, project.mainSourceSet(), Map.of(), null);

      final Path classesDir = tempDir.resolve("calculator/build/classes/test");
      assertThat(classesDir).doesNotExist();

      final Path classFile = classesDir.resolve("org/example/CalculatorTest.class");
      assertThat(classFile).doesNotExist();

      return new DynamicTest[]{
          dynamicTest(
              "Compilation succeeds",
              () -> assertTrue(service.compileTest(tempDir, project, CompilerOptions.EMPTY))
          ),
          dynamicTest(
              "Class file exists",
              () -> assertThat(classFile).isRegularFile()
          ),
      };
    }
  }

  @DisplayName("Tests for cleaning project build output")
  @Nested
  class CleanTest {

    @DisplayName("Check cleaning missing directory works")
    @Test
    void testCleanMissingDirectoryWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final Project project = projectService.create("org.example", "hello-world", "0.1.0",
          builder -> builder
              .withPath("hello-world")
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );
      final Path buildOutputDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir());
      assertThat(buildOutputDir).doesNotExist();
      assertThatCode(() -> service.clean(tempDir, project)).doesNotThrowAnyException();
    }

    @DisplayName("Check cleaning empty directory works")
    @Test
    void testCleanEmptyDirectoryWorks(@TempDir final Path tempDir) throws IOException {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final Project project = projectService.create("org.example", "hello-world", "0.1.0",
          builder -> builder
              .withPath("hello-world")
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );

      final Path buildOutputDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir());
      Files.createDirectories(buildOutputDir);
      assertThat(buildOutputDir).isEmptyDirectory();

      assertThatCode(() -> service.clean(tempDir, project)).doesNotThrowAnyException();
    }

    @DisplayName("Check cleaning non-empty directory works")
    @Test
    void testCleanNonEmptyDirectoryWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final Project project = projectService.create("org.example", "hello-world", "0.1.0",
          builder -> builder
              .withPath("hello-world")
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );
      final var java21 = CompilerOptions
          .builder()
          .release("21")
          .build();
      service.compileMain(tempDir, project, java21);

      final Path buildOutputDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir());
      assertThat(buildOutputDir).isNotEmptyDirectory();

      assertThatCode(() -> service.clean(tempDir, project)).doesNotThrowAnyException();
    }

    @DisplayName("Check cleaning build output which is regular file works silently")
    @Test
    void testCleanFile(@TempDir final Path tempDir) throws IOException {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final Project project = projectService.create("org.example", "hello-world", "0.1.0",
          builder -> builder
              .withPath("hello-world")
              .withSourceSets(
                  MainSourceSetArgs.withMainDefaults(),
                  TestSourceSetArgs.withTestDefaults()
              )
      );

      final Path buildOutputDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir());
      Files.writeString(buildOutputDir, "This should not be a file");
      assertThat(buildOutputDir).isNotEmptyFile();

      assertThatCode(() -> service.clean(tempDir, project)).doesNotThrowAnyException();
    }
  }

  /**
   * Dependency service with project workspace reader set.
   */
  private static DependencyService createDependencyService(
      final Path localRepositoryBasePath,
      final ProjectService projectService,
      @Nullable final Path workdir
  ) {
    final RepositorySystem repoSystem = new RepositorySystemSupplier().get();
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    session.setSystemProperty("java.version", "21");
    final var localRepo = new org.eclipse.aether.repository.LocalRepository(
        localRepositoryBasePath.toFile()
    );
    final var manager = repoSystem.newLocalRepositoryManager(session, localRepo);
    session.setLocalRepositoryManager(manager);

    session.setDependencySelector(new AndDependencySelector(
        new ScopeDependencySelector("test"),
        new OptionalDependencySelector(),
        new ExclusionDependencySelector()
    ));

    if (workdir != null) {
      session.setWorkspaceReader(new ProjectWorkspaceReader(
          new WorkspaceRepository("test"),
          workdir,
          projectService
      ));
    }

    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );
    final List<org.eclipse.aether.repository.RemoteRepository> repositories = List.of(
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("nexus", "default", "http://" + nexusHost + ":8081/repository/maven-central")
            .build()
    );
    return new MavenArtifactResolverDependencyService(
        repoSystem,
        session,
        repositories
    );
  }
}
