package com.anviltf.anvil;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestNbtReader {
    private final DataInputStream in;

    TestNbtReader(byte[] data) {
        this.in = new DataInputStream(new ByteArrayInputStream(data));
    }

    Map<String, Object> readRoot() throws IOException {
        int type = in.readUnsignedByte();
        if (type != NbtWriter.TAG_COMPOUND) {
            throw new IOException("Root is not a compound");
        }
        in.readUTF();
        return compound();
    }

    private Map<String, Object> compound() throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == NbtWriter.TAG_END) {
                return values;
            }
            String name = in.readUTF();
            values.put(name, payload(type));
        }
    }

    private Object payload(int type) throws IOException {
        return switch (type) {
            case NbtWriter.TAG_BYTE -> in.readByte();
            case NbtWriter.TAG_INT -> in.readInt();
            case NbtWriter.TAG_LONG -> in.readLong();
            case NbtWriter.TAG_FLOAT -> in.readFloat();
            case NbtWriter.TAG_DOUBLE -> in.readDouble();
            case NbtWriter.TAG_STRING -> in.readUTF();
            case NbtWriter.TAG_LIST -> list();
            case NbtWriter.TAG_COMPOUND -> compound();
            case NbtWriter.TAG_LONG_ARRAY -> longArray();
            default -> throw new IOException("Unsupported tag type in test reader: " + type);
        };
    }

    private List<Object> list() throws IOException {
        int elementType = in.readUnsignedByte();
        int size = in.readInt();
        List<Object> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(payload(elementType));
        }
        return values;
    }

    private long[] longArray() throws IOException {
        int size = in.readInt();
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = in.readLong();
        }
        return values;
    }
}
