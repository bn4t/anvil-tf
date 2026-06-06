package com.anviltf.anvil;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class NbtWriter implements Closeable {
    public static final int TAG_END = 0;
    public static final int TAG_BYTE = 1;
    public static final int TAG_SHORT = 2;
    public static final int TAG_INT = 3;
    public static final int TAG_LONG = 4;
    public static final int TAG_FLOAT = 5;
    public static final int TAG_DOUBLE = 6;
    public static final int TAG_BYTE_ARRAY = 7;
    public static final int TAG_STRING = 8;
    public static final int TAG_LIST = 9;
    public static final int TAG_COMPOUND = 10;
    public static final int TAG_INT_ARRAY = 11;
    public static final int TAG_LONG_ARRAY = 12;

    private final DataOutputStream out;

    public NbtWriter(OutputStream out) {
        this.out = new DataOutputStream(out);
    }

    public void namedRoot(String name, ThrowingRunnable body) throws IOException {
        out.writeByte(TAG_COMPOUND);
        name(name);
        body.run();
        end();
    }

    public void compound(String name, ThrowingRunnable body) throws IOException {
        tag(TAG_COMPOUND, name);
        body.run();
        end();
    }

    public void compoundPayload(ThrowingRunnable body) throws IOException {
        body.run();
        end();
    }

    public void byteTag(String name, int value) throws IOException {
        tag(TAG_BYTE, name);
        bytePayload(value);
    }

    public void shortTag(String name, int value) throws IOException {
        tag(TAG_SHORT, name);
        shortPayload(value);
    }

    public void intTag(String name, int value) throws IOException {
        tag(TAG_INT, name);
        intPayload(value);
    }

    public void longTag(String name, long value) throws IOException {
        tag(TAG_LONG, name);
        longPayload(value);
    }

    public void floatTag(String name, float value) throws IOException {
        tag(TAG_FLOAT, name);
        floatPayload(value);
    }

    public void doubleTag(String name, double value) throws IOException {
        tag(TAG_DOUBLE, name);
        doublePayload(value);
    }

    public void stringTag(String name, String value) throws IOException {
        tag(TAG_STRING, name);
        stringPayload(value);
    }

    public void list(String name, int elementType, int size) throws IOException {
        tag(TAG_LIST, name);
        listPayloadHeader(elementType, size);
    }

    public void listPayloadHeader(int elementType, int size) throws IOException {
        out.writeByte(elementType);
        out.writeInt(size);
    }

    public void longArray(String name, long[] values) throws IOException {
        tag(TAG_LONG_ARRAY, name);
        longArrayPayload(values);
    }

    public void byteArray(String name, byte[] values) throws IOException {
        tag(TAG_BYTE_ARRAY, name);
        byteArrayPayload(values);
    }

    public void intArray(String name, int[] values) throws IOException {
        tag(TAG_INT_ARRAY, name);
        intArrayPayload(values);
    }

    public void byteArrayPayload(byte[] values) throws IOException {
        out.writeInt(values.length);
        out.write(values);
    }

    public void intArrayPayload(int[] values) throws IOException {
        out.writeInt(values.length);
        for (int value : values) {
            out.writeInt(value);
        }
    }

    public void longArrayPayload(long[] values) throws IOException {
        out.writeInt(values.length);
        for (long value : values) {
            out.writeLong(value);
        }
    }

    public void stringPayload(String value) throws IOException {
        out.writeUTF(value);
    }

    public void bytePayload(int value) throws IOException {
        out.writeByte(value);
    }

    public void shortPayload(int value) throws IOException {
        out.writeShort(value);
    }

    public void intPayload(int value) throws IOException {
        out.writeInt(value);
    }

    public void longPayload(long value) throws IOException {
        out.writeLong(value);
    }

    public void floatPayload(float value) throws IOException {
        out.writeFloat(value);
    }

    public void doublePayload(double value) throws IOException {
        out.writeDouble(value);
    }

    public void end() throws IOException {
        out.writeByte(TAG_END);
    }

    private void tag(int type, String name) throws IOException {
        out.writeByte(type);
        name(name);
    }

    private void name(String name) throws IOException {
        out.writeUTF(name);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws IOException;
    }
}
