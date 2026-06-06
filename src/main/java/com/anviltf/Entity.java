package com.anviltf;

import java.util.Map;
import java.util.Objects;

public record Entity(double x, double y, double z, String id, Map<String, NbtValue> data) {
    public Entity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
        id = ResourceId.normalize(id);
        data = Map.copyOf(data);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }
}
