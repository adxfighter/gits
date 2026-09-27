package ru.gits.task.shop.t06;

import java.util.Locale;
import java.util.Objects;

/**
 * Stock keeping unit, normalized: trimmed and upper-cased.
 */
public final class Sku {

    private final String code;

    public Sku(String code) {
        Objects.requireNonNull(code, "code");
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
        this.code = normalized;
    }

    public String code() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Sku that)) {
            return false;
        }
        return code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
