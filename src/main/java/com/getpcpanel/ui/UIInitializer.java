package com.getpcpanel.ui;

import javax.annotation.Nonnull;

public interface UIInitializer<T> {
    default void initUI(@Nonnull T args) {
    }

    record SingleParamInitializer<TT>(TT param) {
    }
}
