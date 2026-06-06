package com.anviltf.anvil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

final class RegionWriter {
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = SECTOR_BYTES * 2;

    void write(Path path, Map<Integer, byte[]> chunkNbtByIndex) throws IOException {
        Files.createDirectories(path.getParent());
        List<Entry> entries = new ArrayList<>();
        int nextSector = 2;
        int timestamp = 0;

        for (Map.Entry<Integer, byte[]> chunk : chunkNbtByIndex.entrySet()) {
            byte[] compressed = zlib(chunk.getValue());
            int length = compressed.length + 1;
            int sectors = Math.max(1, ceilDiv(length + Integer.BYTES, SECTOR_BYTES));
            entries.add(new Entry(chunk.getKey(), nextSector, sectors, timestamp, compressed));
            nextSector += sectors;
        }

        byte[] header = new byte[HEADER_BYTES];
        for (Entry entry : entries) {
            int offset = entry.index * 4;
            header[offset] = (byte) (entry.sectorOffset >>> 16);
            header[offset + 1] = (byte) (entry.sectorOffset >>> 8);
            header[offset + 2] = (byte) entry.sectorOffset;
            header[offset + 3] = (byte) entry.sectorCount;

            int timestampOffset = SECTOR_BYTES + offset;
            header[timestampOffset] = (byte) (entry.timestamp >>> 24);
            header[timestampOffset + 1] = (byte) (entry.timestamp >>> 16);
            header[timestampOffset + 2] = (byte) (entry.timestamp >>> 8);
            header[timestampOffset + 3] = (byte) entry.timestamp;
        }

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.write(header);
            for (Entry entry : entries) {
                out.writeInt(entry.compressed.length + 1);
                out.writeByte(2);
                out.write(entry.compressed);
                int used = Integer.BYTES + 1 + entry.compressed.length;
                out.write(new byte[entry.sectorCount * SECTOR_BYTES - used]);
            }
        }
    }

    private static byte[] zlib(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
            deflater.write(data);
        }
        return bytes.toByteArray();
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private record Entry(int index, int sectorOffset, int sectorCount, int timestamp, byte[] compressed) {
    }
}
