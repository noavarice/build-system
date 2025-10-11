package com.github.build;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @author noavarice
 */
public final class FsUtils {

  private FsUtils() {
  }

  public static void setupFromYaml(final String yamlResourceName, final Path projectRoot) {
    final YamlProject yamlProject;
    try (final var is = FsUtils.class.getResourceAsStream(yamlResourceName)) {
      final var objectMapper = new ObjectMapper(new YAMLFactory());
      yamlProject = objectMapper.readValue(is, YamlProject.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final YamlFile file : yamlProject.files()) {
      switch (file) {
        case YamlFile.PlainText plainText -> createPlaintextFile(plainText, projectRoot);
        case YamlFile.Resource resource -> copyResource(resource, projectRoot);
      }
    }
  }

  private static void createPlaintextFile(
      final YamlFile.PlainText plainText,
      final Path projectRoot
  ) {
    final Path filePath = projectRoot.resolve(plainText.path());
    final Path directory = filePath.resolve("..");
    try {
      Files.createDirectories(directory);
      Files.writeString(filePath, plainText.text());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void copyResource(final YamlFile.Resource resource, final Path projectRoot) {
    final byte[] bytes;
    try (final var is = FsUtils.class.getResourceAsStream(resource.name())) {
      bytes = Objects.requireNonNull(is).readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final Path filePath = projectRoot.resolve(resource.path());
    final Path directory = filePath.resolve("..");
    try {
      Files.createDirectories(directory);
      Files.write(filePath, bytes);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record YamlProject(List<YamlFile> files) {

    private YamlProject {
      files = List.copyOf(files);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = YamlFile.PlainText.class, name = "plaintext"),
      @JsonSubTypes.Type(value = YamlFile.Resource.class, name = "resource")
  })
  private sealed interface YamlFile {

    Path path();

    record PlainText(Path path, String text) implements YamlFile {

      public PlainText {
        Objects.requireNonNull(path);
        Objects.requireNonNull(text);
      }
    }

    record Resource(Path path, String name) implements YamlFile {

      public Resource {
        Objects.requireNonNull(path);
        Objects.requireNonNull(name);
      }
    }
  }
}
