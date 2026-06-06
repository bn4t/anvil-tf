package com.anviltf;

import com.anviltf.anvil.NbtWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public sealed interface NbtValue permits NbtValue.ByteValue, NbtValue.ShortValue, NbtValue.IntValue,
        NbtValue.LongValue, NbtValue.FloatValue, NbtValue.DoubleValue, NbtValue.ByteArrayValue,
        NbtValue.StringValue, NbtValue.ListValue, NbtValue.CompoundValue, NbtValue.IntArrayValue,
        NbtValue.LongArrayValue {
    void writeNamed(NbtWriter writer, String name) throws IOException;

    void writePayload(NbtWriter writer) throws IOException;

    int type();

    static NbtValue byteValue(int value) {
        return new ByteValue(value);
    }

    static NbtValue shortValue(int value) {
        return new ShortValue(value);
    }

    static NbtValue intValue(int value) {
        return new IntValue(value);
    }

    static NbtValue longValue(long value) {
        return new LongValue(value);
    }

    static NbtValue floatValue(float value) {
        return new FloatValue(value);
    }

    static NbtValue doubleValue(double value) {
        return new DoubleValue(value);
    }

    static NbtValue byteArrayValue(byte[] value) {
        return new ByteArrayValue(Arrays.copyOf(value, value.length));
    }

    static NbtValue stringValue(String value) {
        return new StringValue(value);
    }

    static NbtValue listValue(int elementType, List<NbtValue> values) {
        return new ListValue(elementType, List.copyOf(values));
    }

    static NbtValue compoundValue(Map<String, NbtValue> values) {
        return new CompoundValue(Map.copyOf(values));
    }

    static NbtValue intArrayValue(int[] value) {
        return new IntArrayValue(Arrays.copyOf(value, value.length));
    }

    static NbtValue longArrayValue(long[] value) {
        return new LongArrayValue(Arrays.copyOf(value, value.length));
    }

    record ByteValue(int value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.byteTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.bytePayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_BYTE;
        }
    }

    record ShortValue(int value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.shortTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.shortPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_SHORT;
        }
    }

    record IntValue(int value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.intTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.intPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_INT;
        }
    }

    record LongValue(long value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.longTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.longPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_LONG;
        }
    }

    record FloatValue(float value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.floatTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.floatPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_FLOAT;
        }
    }

    record DoubleValue(double value) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.doubleTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.doublePayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_DOUBLE;
        }
    }

    record ByteArrayValue(byte[] value) implements NbtValue {
        public ByteArrayValue {
            value = Arrays.copyOf(value, value.length);
        }

        @Override
        public byte[] value() {
            return Arrays.copyOf(value, value.length);
        }

        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.byteArray(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.byteArrayPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_BYTE_ARRAY;
        }
    }

    record StringValue(String value) implements NbtValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.stringTag(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.stringPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_STRING;
        }
    }

    record ListValue(int elementType, List<NbtValue> values) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.list(name, elementType, values.size());
            writeElements(writer);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.listPayloadHeader(elementType, values.size());
            writeElements(writer);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_LIST;
        }

        private void writeElements(NbtWriter writer) throws IOException {
            for (NbtValue value : values) {
                if (value.type() != elementType) {
                    throw new IllegalArgumentException("List contains tag type " + value.type()
                            + " but declared " + elementType);
                }
                value.writePayload(writer);
            }
        }
    }

    record IntArrayValue(int[] value) implements NbtValue {
        public IntArrayValue {
            value = Arrays.copyOf(value, value.length);
        }

        @Override
        public int[] value() {
            return Arrays.copyOf(value, value.length);
        }

        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.intArray(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.intArrayPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_INT_ARRAY;
        }
    }

    record LongArrayValue(long[] value) implements NbtValue {
        public LongArrayValue {
            value = Arrays.copyOf(value, value.length);
        }

        @Override
        public long[] value() {
            return Arrays.copyOf(value, value.length);
        }

        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.longArray(name, value);
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writer.longArrayPayload(value);
        }

        @Override
        public int type() {
            return NbtWriter.TAG_LONG_ARRAY;
        }
    }

    record CompoundValue(Map<String, NbtValue> values) implements NbtValue {
        @Override
        public void writeNamed(NbtWriter writer, String name) throws IOException {
            writer.compound(name, () -> writePayloadBody(writer));
        }

        @Override
        public void writePayload(NbtWriter writer) throws IOException {
            writePayloadBody(writer);
            writer.end();
        }

        @Override
        public int type() {
            return NbtWriter.TAG_COMPOUND;
        }

        private void writePayloadBody(NbtWriter writer) throws IOException {
            for (Map.Entry<String, NbtValue> entry : new TreeMap<>(values).entrySet()) {
                entry.getValue().writeNamed(writer, entry.getKey());
            }
        }
    }
}
