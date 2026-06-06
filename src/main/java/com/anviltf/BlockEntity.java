package com.anviltf;

import java.util.Map;
import java.util.Objects;

public record BlockEntity(int x, int y, int z, String id, Map<String, NbtValue> data) {
    public BlockEntity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
        id = ResourceId.normalize(id);
        data = Map.copyOf(data);
    }
}
