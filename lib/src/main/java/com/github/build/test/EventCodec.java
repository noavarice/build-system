package com.github.build.test;

/**
 * @author noavarice
 * @since 1.0.0
 */
public interface EventCodec<T> {

  byte[] toBytes(T value);

  T toValue(byte[] bytes);
}
