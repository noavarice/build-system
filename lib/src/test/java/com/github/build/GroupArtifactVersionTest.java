package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Coordinates DTO tests")
class GroupArtifactVersionTest {

    @DisplayName("Check GroupArtifactVersion parsing")
    @ParameterizedTest(name = "Check parsing {0}")
    @MethodSource("gavCoordinates")
    void testGavParsing(final String value, final GroupArtifactVersion expected) {
        assertThat(GroupArtifactVersion.parse(value)).isEqualTo(expected);
    }

    static Stream<Arguments> gavCoordinates() {
        return Stream.of(
                // group:artifact:version
                Arguments.of(
                        "org.springframework.boot:spring-boot-starter-web:3.2.5",
                        new GroupArtifactVersion("org.springframework.boot", "spring-boot-starter-web", "3.2.5")),
                // group:artifact:extension:version
                Arguments.of(
                        "org.springframework.boot:spring-boot-starter-web:jar:3.2.5",
                        new GroupArtifactVersion("org.springframework.boot", "spring-boot-starter-web", "3.2.5")),
                // group:artifact:extension:classifier:version
                Arguments.of(
                        "io.netty:netty-codec-native-quic:jar:linux-x86_64:4.2.17.Final",
                        new GroupArtifactVersion(
                                "io.netty", "netty-codec-native-quic", "linux-x86_64", "4.2.17.Final")));
    }

    @DisplayName("Check GroupArtifact parsing")
    @ParameterizedTest(name = "Check parsing {0}")
    @MethodSource("gaCoordinates")
    void testGaParsing(final String value, final GroupArtifact expected) {
        assertThat(GroupArtifact.parse(value)).isEqualTo(expected);
    }

    static Stream<Arguments> gaCoordinates() {
        return Stream.of(
                Arguments.of(
                        "org.springframework:spring-core", new GroupArtifact("org.springframework", "spring-core")),
                Arguments.of(
                        "io.netty:netty-codec-native-quic:linux-x86_64",
                        new GroupArtifact("io.netty", "netty-codec-native-quic", "linux-x86_64")),
                Arguments.of(
                        "io.netty:netty-codec-native-quic:jar:linux-x86_64",
                        new GroupArtifact("io.netty", "netty-codec-native-quic", "linux-x86_64")));
    }
}
