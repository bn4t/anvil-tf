package com.anviltf.anvil;

public final class PackedBits {
    private PackedBits() {
    }

    public static int bitsNeeded(int values) {
        if (values <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(values - 1);
    }

    public static long[] pack(int[] values, int bitsPerEntry) {
        if (bitsPerEntry <= 0) {
            return new long[0];
        }
        long mask = (1L << bitsPerEntry) - 1L;
        long[] packed = new long[(values.length * bitsPerEntry + 63) / 64];
        for (int i = 0; i < values.length; i++) {
            long value = values[i] & mask;
            int bitIndex = i * bitsPerEntry;
            int longIndex = bitIndex / 64;
            int offset = bitIndex % 64;
            packed[longIndex] |= value << offset;
            if (offset + bitsPerEntry > 64) {
                packed[longIndex + 1] |= value >>> (64 - offset);
            }
        }
        return packed;
    }

    public static long[] packPadded(int[] values, int bitsPerEntry) {
        if (bitsPerEntry <= 0) {
            return new long[0];
        }
        int valuesPerLong = 64 / bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1L;
        long[] packed = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int offset = (i % valuesPerLong) * bitsPerEntry;
            packed[longIndex] |= (values[i] & mask) << offset;
        }
        return packed;
    }
}
