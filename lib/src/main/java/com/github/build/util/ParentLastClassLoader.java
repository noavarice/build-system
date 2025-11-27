package com.github.build.util;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Class loader that first searches inside {@link URLClassLoader} content and then delegates to
 * parents.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class ParentLastClassLoader extends URLClassLoader {

  public ParentLastClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> loadClass(
      final String name,
      final boolean resolve
  ) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      final Class<?> loaded = findLoadedClass(name);
      if (loaded != null) {
        return loaded;
      }

      final Class<?> result;
      try {
        result = findClass(name);
      } catch (final ClassNotFoundException e) {
        return super.loadClass(name, resolve);
      }

      if (resolve) {
        resolveClass(result);
      }

      return result;
    }
  }

  @Override
  public URL getResource(final String name) {
    return super.getResource(name);
  }
}
