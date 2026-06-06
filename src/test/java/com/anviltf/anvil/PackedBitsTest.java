package com.anviltf.anvil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PackedBitsTest {
    @Test
    void packsValuesAcrossLongBoundaries() {
        int[] values = new int[20];
        for (int i = 0; i < values.length; i++) {
            values[i] = i & 15;
        }

        long[] packed = PackedBits.pack(values, 4);

        assertEquals(2, packed.length);
        assertEquals(0xfedcba9876543210L, packed[0]);
        assertEquals(0x0000000000003210L, packed[1]);
    }

    @Test
    void zeroBitsProducesEmptyArray() {
        assertArrayEquals(new long[0], PackedBits.pack(new int[] {0, 0, 0}, 0));
    }

    @Test
    void paddedPackingDoesNotCrossLongBoundaries() {
        int[] values = new int[256];
        long[] packed = PackedBits.packPadded(values, 9);

        assertEquals(37, packed.length);
    }

    @Test
    void paddedHeightmapPackingUsesAnvilReferenceLengthForShortWorlds() {
        int[] values = new int[256];
        long[] packed = PackedBits.packPadded(values, Math.max(9, PackedBits.bitsNeeded(65)));

        assertEquals(37, packed.length);
    }

    @Test
    void paddedBlockStatePackingUsesOneLongPerFloorGroup() {
        int[] values = new int[4096];
        long[] packed = PackedBits.packPadded(values, 5);

        assertEquals(342, packed.length);
    }
}
