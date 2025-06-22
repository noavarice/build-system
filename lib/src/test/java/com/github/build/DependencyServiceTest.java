package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyService;
import com.github.build.deps.Pom;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DependencyService} tests.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for dependency service")
@Disabled("Because implementation is not ready yet")
class DependencyServiceTest {

  private final Dependency.Remote.Exact logbackClassic = new Dependency.Remote.Exact(
      "ch.qos.logback",
      "logback-classic",
      "1.5.18"
  );

  private final RemoteRepository mavenCentral = new RemoteRepository(
      URI.create("https://repo.maven.apache.org/maven2"),
      HttpClient.newHttpClient()
  );

  private final DependencyService service = new DependencyService(List.of(mavenCentral));

  @DisplayName("Check resolving transitive dependencies for a single dependency")
  @Test
  void testResolvingTransitiveDependencies() {
    final var ref = new AtomicReference<List<Pom.Dependency>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(logbackClassic))
    ).doesNotThrowAnyException();

    final List<Pom.Dependency> actual = ref.get();
    final List<Pom.Dependency> expected = List.of(
        new Pom.Dependency(
            "ch.qos.logback",
            "logback-core",
            "1.5.18",
            Pom.Dependency.Scope.COMPILE,
            false
        ),
        new Pom.Dependency(
            "jakarta.activation",
            "jakarta.activation-api",
            "2.1.0",
            Pom.Dependency.Scope.COMPILE,
            true
        )
    );

    assertThat(actual).isEqualTo(expected);
  }
}
