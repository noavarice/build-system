package com.github.build.compile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * @author noavarice
 */
public final class CompileService {

  private static final Logger log = LoggerFactory.getLogger(CompileService.class);

  /**
   * Performs compilation with specified arguments.
   *
   * @param args Compilation arguments (sources, classes directory, etc.)
   * @return True if compilation succeeds, false otherwise
   */
  public boolean compile(final CompileArgs args) {
    Objects.requireNonNull(args);
    log.debug("Compiling {} files: {}", args.sources().size(), args.sources());

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
      final Level level = switch (diagnostic.getKind()) {
        case ERROR -> Level.ERROR;
        case WARNING, MANDATORY_WARNING -> Level.WARN;
        case NOTE -> Level.INFO;
        case OTHER -> Level.DEBUG;
      };
      log.atLevel(level).log("{}", diagnostic);
    };

    final var fileManager = compiler.getStandardFileManager(
        diagnosticListener,
        Locale.US,
        StandardCharsets.UTF_8
    );

    final var classPathFiles = args.classpath()
        .stream()
        .map(Path::toFile)
        .toList();
    try {
      fileManager.setLocation(StandardLocation.CLASS_PATH, classPathFiles);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Files.createDirectories(args.classesDir());
      fileManager.setLocation(
          StandardLocation.CLASS_OUTPUT,
          List.of(args.classesDir().toFile())
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var compUnits = fileManager.getJavaFileObjectsFromPaths(args.sources());
    final var task = compiler.getTask(
        new LogWriter(),
        fileManager,
        diagnosticListener,
        null,
        null,
        compUnits
    );
    task.setLocale(Locale.US);
    final boolean result = task.call();
    if (result) {
      log.info("Compilation succeeded");
    } else {
      log.error("Compilation failed");
    }

    return result;
  }

  /**
   * Writes compiler output to {@link Logger}.
   */
  private static final class LogWriter extends Writer {

    @Override
    public void write(final char[] cbuf, final int off, final int len) {
      final var compilerMessage = new String(cbuf);
      log.debug("[compiler] {}", compilerMessage);
    }

    @Override
    public void flush() {
      // do nothing
    }

    @Override
    public void close() {
      // do nothing
    }
  }
}
