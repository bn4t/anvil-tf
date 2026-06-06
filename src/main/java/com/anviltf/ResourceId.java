package com.anviltf;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ResourceId {
    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern VANILLA = Pattern.compile("[a-z0-9_./-]+");

    private ResourceId() {
    }

    public static String normalize(String id) {
        Objects.requireNonNull(id, "id");
        if (NAMESPACED.matcher(id).matches()) {
            return id;
        }
        if (VANILLA.matcher(id).matches()) {
            return "minecraft:" + id;
        }
        throw new IllegalArgumentException("Invalid resource id: " + id);
    }
}
