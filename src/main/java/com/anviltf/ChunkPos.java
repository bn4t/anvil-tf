package com.anviltf;

public record ChunkPos(int x, int z) implements Comparable<ChunkPos> {
    @Override
    public int compareTo(ChunkPos other) {
        int zCompare = Integer.compare(z, other.z);
        return zCompare != 0 ? zCompare : Integer.compare(x, other.x);
    }
}
