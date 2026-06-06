package com.anviltf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record BlockState(String name, Map<String, String> properties) {
    private static final Pattern PROPERTY = Pattern.compile("[a-z0-9_]+");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9_./+-]+");
    public static final BlockState AIR = new BlockState("minecraft:air", Map.of());

    public BlockState {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(properties, "properties");
        name = ResourceId.normalize(name);
        properties = Map.copyOf(properties);
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!PROPERTY.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Invalid block property: " + entry.getKey());
            }
            if (!VALUE.matcher(entry.getValue()).matches()) {
                throw new IllegalArgumentException("Invalid block property value: " + entry.getValue());
            }
        }
    }

    public static BlockState parse(String text) {
        int bracket = text.indexOf('[');
        if (bracket < 0) {
            return new BlockState(text, Map.of());
        }
        if (!text.endsWith("]")) {
            throw new IllegalArgumentException("Unclosed block state properties: " + text);
        }
        String name = text.substring(0, bracket);
        String body = text.substring(bracket + 1, text.length() - 1);
        Map<String, String> properties = new LinkedHashMap<>();
        if (!body.isBlank()) {
            for (String part : body.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) {
                    throw new IllegalArgumentException("Invalid block property pair: " + part);
                }
                properties.put(pair[0].trim(), pair[1].trim());
            }
        }
        return new BlockState(name, properties);
    }

    public BlockState rotatedY(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0 || properties.isEmpty()) {
            return this;
        }

        Map<String, String> rotated = new LinkedHashMap<>(properties);
        rotateDirectionValue(rotated, "facing", turns);
        rotateDirectionValue(rotated, "horizontal_facing", turns);
        rotateAxis(rotated, turns);
        rotateNumericRotation(rotated, turns);
        rotateDirectionalKeys(rotated, turns);
        return new BlockState(name, rotated);
    }

    public BlockState mirroredX() {
        if (properties.isEmpty()) {
            return this;
        }
        Map<String, String> mirrored = new LinkedHashMap<>(properties);
        mirrorDirectionValue(mirrored, "facing", "east", "west");
        mirrorDirectionValue(mirrored, "horizontal_facing", "east", "west");
        mirrorDirectionalKeys(mirrored, "east", "west");
        mirrorNumericRotation(mirrored, true);
        mirrorLeftRight(mirrored, "hinge");
        mirrorShapeLeftRight(mirrored);
        return new BlockState(name, mirrored);
    }

    public BlockState mirroredZ() {
        if (properties.isEmpty()) {
            return this;
        }
        Map<String, String> mirrored = new LinkedHashMap<>(properties);
        mirrorDirectionValue(mirrored, "facing", "north", "south");
        mirrorDirectionValue(mirrored, "horizontal_facing", "north", "south");
        mirrorDirectionalKeys(mirrored, "north", "south");
        mirrorNumericRotation(mirrored, false);
        mirrorLeftRight(mirrored, "hinge");
        mirrorShapeLeftRight(mirrored);
        return new BlockState(name, mirrored);
    }

    private static void rotateDirectionValue(Map<String, String> properties, String key, int turns) {
        String value = properties.get(key);
        if (value == null) {
            return;
        }
        properties.put(key, rotateDirection(value, turns));
    }

    private static void rotateAxis(Map<String, String> properties, int turns) {
        if (turns % 2 == 0) {
            return;
        }
        String axis = properties.get("axis");
        if ("x".equals(axis)) {
            properties.put("axis", "z");
        } else if ("z".equals(axis)) {
            properties.put("axis", "x");
        }
    }

    private static void rotateNumericRotation(Map<String, String> properties, int turns) {
        String rotation = properties.get("rotation");
        if (rotation == null) {
            return;
        }
        try {
            int value = Integer.parseInt(rotation);
            properties.put("rotation", Integer.toString(Math.floorMod(value + turns * 4, 16)));
        } catch (NumberFormatException ignored) {
            // Non-vanilla or symbolic values are left untouched.
        }
    }

    private static void rotateDirectionalKeys(Map<String, String> properties, int turns) {
        if (!properties.keySet().containsAll(List.of("north", "east", "south", "west"))) {
            return;
        }
        Map<String, String> original = Map.copyOf(properties);
        for (String direction : List.of("north", "east", "south", "west")) {
            properties.put(rotateDirection(direction, turns), original.get(direction));
        }
    }

    private static String rotateDirection(String direction, int turns) {
        List<String> directions = List.of("north", "east", "south", "west");
        int index = directions.indexOf(direction);
        if (index < 0) {
            return direction;
        }
        return directions.get(Math.floorMod(index + turns, 4));
    }

    private static void mirrorDirectionValue(Map<String, String> properties, String key, String a, String b) {
        String value = properties.get(key);
        if (a.equals(value)) {
            properties.put(key, b);
        } else if (b.equals(value)) {
            properties.put(key, a);
        }
    }

    private static void mirrorDirectionalKeys(Map<String, String> properties, String a, String b) {
        if (!properties.containsKey(a) || !properties.containsKey(b)) {
            return;
        }
        String value = properties.get(a);
        properties.put(a, properties.get(b));
        properties.put(b, value);
    }

    private static void mirrorNumericRotation(Map<String, String> properties, boolean acrossX) {
        String rotation = properties.get("rotation");
        if (rotation == null) {
            return;
        }
        try {
            int value = Integer.parseInt(rotation);
            properties.put("rotation", Integer.toString(Math.floorMod((acrossX ? 16 : 8) - value, 16)));
        } catch (NumberFormatException ignored) {
            // Non-vanilla or symbolic values are left untouched.
        }
    }

    private static void mirrorLeftRight(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if ("left".equals(value)) {
            properties.put(key, "right");
        } else if ("right".equals(value)) {
            properties.put(key, "left");
        }
    }

    private static void mirrorShapeLeftRight(Map<String, String> properties) {
        String shape = properties.get("shape");
        if (shape == null) {
            return;
        }
        if (shape.endsWith("_left")) {
            properties.put("shape", shape.substring(0, shape.length() - "_left".length()) + "_right");
        } else if (shape.endsWith("_right")) {
            properties.put("shape", shape.substring(0, shape.length() - "_right".length()) + "_left");
        }
    }
}
