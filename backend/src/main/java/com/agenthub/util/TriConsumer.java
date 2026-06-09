package com.agenthub.util;

import java.util.Objects;

@FunctionalInterface
public interface TriConsumer<T, U, V, R> {
    void accept(T t, U u, V v, R r);

    default TriConsumer<T, U, V, R> andThen(TriConsumer<? super T, ? super U, ? super V, ? super R> after) {
        Objects.requireNonNull(after);
        return (t, u, v, r) -> {
            accept(t, u, v, r);
            after.accept(t, u, v, r);
        };
    }
}
