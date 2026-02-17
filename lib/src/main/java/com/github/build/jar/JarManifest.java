package com.github.build.jar;

import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class JarManifest {

  public static Builder builder() {
    return new Builder();
  }

  @Nullable
  private final String version;

  @Nullable
  private final String implementationTitle;

  @Nullable
  private final String implementationVersion;

  @Nullable
  private final String createdBy;

  private JarManifest(
      @Nullable final String version,
      @Nullable final String implementationTitle,
      @Nullable final String implementationVersion,
      @Nullable final String createdBy
  ) {
    this.version = version;
    this.implementationTitle = implementationTitle;
    this.implementationVersion = implementationVersion;
    this.createdBy = createdBy;
  }

  @Override
  public String toString() {
    boolean empty = true;
    final var sb = new StringBuilder();

    if (version != null) {
      sb.append("Manifest-Version: ").append(version);
      empty = false;
    }

    if (createdBy != null) {
      if (!empty) {
        sb.append(System.lineSeparator());
      }
      sb.append("Created-By: ").append(createdBy);
      empty = false;
    }

    if (implementationTitle != null) {
      if (!empty) {
        sb.append(System.lineSeparator());
      }
      sb.append("Implementation-Title: ").append(implementationTitle);
      empty = false;
    }

    if (implementationVersion != null) {
      if (!empty) {
        sb.append(System.lineSeparator());
      }
      sb.append("Implementation-Version: ").append(implementationVersion);
    }

    if (!empty) {
      sb.append(System.lineSeparator());
    }
    return sb.toString();
  }

  public Manifest toManifest() {
    final var result = new Manifest();
    final var attributes = result.getMainAttributes();
    if (version != null) {
      attributes.put(Attributes.Name.MANIFEST_VERSION, version);
    }
    if (createdBy != null) {
      attributes.putValue("Created-By", createdBy);
    }
    if (implementationTitle != null) {
      attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, implementationTitle);
    }
    if (implementationVersion != null) {
      attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
    }
    return result;
  }

  public static final class Builder {

    @Nullable
    private String version;

    @Nullable
    private String implementationTitle;

    @Nullable
    private String implementationVersion;

    @Nullable
    private String createdBy;

    private Builder() {
    }

    public Builder setVersion(@Nullable final String version) {
      if (version != null && version.isBlank()) {
        throw new IllegalArgumentException();
      }

      this.version = version;
      return this;
    }

    public Builder setImplementationTitle(@Nullable final String implementationTitle) {
      if (implementationTitle != null && implementationTitle.isBlank()) {
        throw new IllegalArgumentException();
      }

      this.implementationTitle = implementationTitle;
      return this;
    }

    public Builder setImplementationVersion(@Nullable final String implementationVersion) {
      if (implementationVersion != null && implementationVersion.isBlank()) {
        throw new IllegalArgumentException();
      }

      this.implementationVersion = implementationVersion;
      return this;
    }

    public Builder setCreatedBy(@Nullable final String createdBy) {
      if (createdBy != null && createdBy.isBlank()) {
        throw new IllegalArgumentException();
      }

      this.createdBy = createdBy;
      return this;
    }

    public JarManifest build() {
      return new JarManifest(version, implementationTitle, implementationVersion, createdBy);
    }
  }
}
