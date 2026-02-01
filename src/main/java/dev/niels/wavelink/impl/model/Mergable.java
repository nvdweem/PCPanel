package dev.niels.wavelink.impl.model;

import javax.annotation.Nullable;

public interface Mergable<T> {
    T merge(@Nullable T other);
}
