package com.github.build.compile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aspectj.ajdt.ajc.AjdtCommand;
import org.aspectj.bridge.AbortException;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles source sets with the AspectJ compiler (ajc).
 *
 * <p>AspectJ sources ({@code .aj}) and plain Java sources in the AspectJ source roots are compiled
 * together, so the AspectJ compiler can weave aspects into the Java classes it compiles.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class AspectJCompileService {

  private static final Logger log = LoggerFactory.getLogger(AspectJCompileService.class);

  private static final String JAVA_VERSION = "17";

  /**
   * Performs AspectJ compilation with specified arguments.
   *
   * @param args Compilation arguments (source roots, classes directory, etc.)
   * @return True if compilation succeeds, false otherwise
   */
  public boolean compile(final AspectJCompileArgs args) {
    Objects.requireNonNull(args);

    final var compilerArgs = new ArrayList<String>();
    // AspectJ compiler uses -source/-target instead of javac's --release
    compilerArgs.add("-source");
    compilerArgs.add(JAVA_VERSION);
    compilerArgs.add("-target");
    compilerArgs.add(JAVA_VERSION);
    compilerArgs.add("-parameters");
    compilerArgs.add("-classpath");
    compilerArgs.add(join(args.classpath()));
    if (!args.aspectPath().isEmpty()) {
      compilerArgs.add("-aspectpath");
      compilerArgs.add(join(args.aspectPath()));
    }
    for (final Path sourceRoot : args.sourceRoots()) {
      compilerArgs.add("-sourceroots");
      compilerArgs.add(sourceRoot.toString());
    }
    compilerArgs.add("-d");
    compilerArgs.add(args.classesDir().toString());
    if (args.outXmlFile() != null) {
      compilerArgs.add("-outxmlfile");
      compilerArgs.add(args.outXmlFile().toString());
    }

    return run(compilerArgs);
  }

  private boolean run(final List<String> args) {
    log.info("[ajc] Compiling with AspectJ compiler: {}", String.join(" ", args));
    final var command = new AjdtCommand();
    final var messageHandler = new MessageHandler();
    final boolean success;
    try {
      success = command.runCommand(args.toArray(String[]::new), messageHandler);
    } catch (final AbortException e) {
      log.error("[ajc] Compilation aborted", e);
      return false;
    }
    for (final IMessage message : messageHandler.getMessages(IMessage.ERROR, true)) {
      log.error("[ajc] {}", message);
    }
    for (final IMessage message : messageHandler.getMessages(IMessage.WARNING, true)) {
      log.warn("[ajc] {}", message);
    }
    if (!success) {
      log.error("[ajc] Compilation failed");
    }
    return success;
  }

  private static String join(final Iterable<Path> paths) {
    final var result = new StringBuilder();
    for (final Path path : paths) {
      if (!result.isEmpty()) {
        result.append(':');
      }
      result.append(path);
    }
    return result.toString();
  }
}
