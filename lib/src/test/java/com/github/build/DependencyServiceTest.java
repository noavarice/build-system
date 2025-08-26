package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.build.deps.Coordinates;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyService;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
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

  private final Dependency.Remote.Exact springContext = new Dependency.Remote.Exact(
      "org.springframework",
      "spring-context",
      "6.0.11"
  );

  private final RemoteRepository mavenCentral = new RemoteRepository(
      URI.create("https://repo.maven.apache.org/maven2"),
      HttpClient.newHttpClient()
  );

  private final DependencyService service = new DependencyService(List.of(mavenCentral));

  @DisplayName("Check resolving transitive dependencies for a single dependency")
  @Test
  void testResolvingTransitiveDependencies() {
    final var ref = new AtomicReference<Set<Coordinates>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(logbackClassic))
    ).doesNotThrowAnyException();

    final Set<Coordinates> actual = ref.get();
    final Set<Coordinates> expected = Set.of(
        new Coordinates("ch.qos.logback", "logback-core", "1.5.18"),
        new Coordinates("org.slf4j", "slf4j-api", "2.0.17")
    );

    assertThat(actual).isEqualTo(expected);
  }

  @DisplayName("Check resolving more transitive dependencies for a single dependency")
  @Test
  void testResolvingMoreTransitiveDependencies() {
    final var ref = new AtomicReference<Set<Coordinates>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(springContext))
    ).doesNotThrowAnyException();

    final Set<Coordinates> actual = ref.get();
    final Set<Coordinates> expected = Set.of(
        new Coordinates("org.springframework", "spring-aop", "6.0.11"),
        new Coordinates("org.springframework", "spring-beans", "6.0.11"),
        new Coordinates("org.springframework", "spring-core", "6.0.11"),
        new Coordinates("org.springframework", "spring-expression", "6.0.11"),
        new Coordinates("org.springframework", "spring-jcl", "6.0.11")
    );

    assertThat(actual).isEqualTo(expected);
  }
}
