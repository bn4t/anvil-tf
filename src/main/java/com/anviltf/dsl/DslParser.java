package com.anviltf.dsl;

import com.anviltf.BlockState;
import com.anviltf.MutableWorld;
import com.anviltf.NbtValue;
import com.anviltf.ResourceId;
import com.anviltf.WorldSpec;
import com.anviltf.WorldPostProcessor;
import com.anviltf.anvil.NbtWriter;

import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DslParser {
    public WorldSpec parse(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Set<Path> includeStack = new HashSet<>();
        includeStack.add(normalized);
        try (StreamingTokenStream tokens = new StreamingTokenStream(Files.newBufferedReader(normalized))) {
            Parser parser = new Parser(tokens,
                    new MutableWorld(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    new HashMap<>(), 0, 0, 0, Rotation.NONE, normalized.getParent(), includeStack);
            return parser.parse();
        }
    }

    public WorldSpec parse(String source) {
        Parser parser = new Parser(new ListTokenStream(tokenize(source)));
        return parser.parse();
    }

    private static List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (!quoted && c == '#') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '"') {
                if (quoted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    quoted = false;
                } else {
                    flush(tokens, current);
                    quoted = true;
                }
                continue;
            }
            if (quoted) {
                if (c == '\\') {
                    if (i + 1 >= source.length()) {
                        throw new IllegalArgumentException("Dangling escape in quoted string");
                    }
                    current.append(source.charAt(++i));
                    continue;
                }
                current.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                flush(tokens, current);
                continue;
            }
            if (c == '{' || c == '}') {
                flush(tokens, current);
                tokens.add(Character.toString(c));
                continue;
            }
            current.append(c);
        }
        if (quoted) {
            throw new IllegalArgumentException("Unclosed quoted string");
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private interface TokenStream {
        boolean isEmpty();

        String peek();

        String poll();
    }

    private static final class ListTokenStream implements TokenStream {
        private final Queue<String> tokens;

        ListTokenStream(List<String> tokens) {
            this.tokens = new ArrayDeque<>(tokens);
        }

        @Override
        public boolean isEmpty() {
            return tokens.isEmpty();
        }

        @Override
        public String peek() {
            return tokens.peek();
        }

        @Override
        public String poll() {
            return tokens.poll();
        }
    }

    private static final class StreamingTokenStream implements TokenStream, Closeable {
        private final PushbackReader reader;
        private String next;
        private boolean finished;

        StreamingTokenStream(Reader reader) {
            this.reader = new PushbackReader(reader, 1);
        }

        @Override
        public boolean isEmpty() {
            return peek() == null;
        }

        @Override
        public String peek() {
            if (next == null && !finished) {
                next = readToken();
                finished = next == null;
            }
            return next;
        }

        @Override
        public String poll() {
            String token = peek();
            next = null;
            return token;
        }

        private String readToken() {
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            try {
                while (true) {
                    int raw = reader.read();
                    if (raw < 0) {
                        if (quoted) {
                            throw new IllegalArgumentException("Unclosed quoted string");
                        }
                        return current.isEmpty() ? null : current.toString();
                    }
                    char c = (char) raw;
                    if (!quoted && c == '#') {
                        skipComment();
                        if (!current.isEmpty()) {
                            return current.toString();
                        }
                        continue;
                    }
                    if (c == '"') {
                        if (quoted) {
                            return current.toString();
                        }
                        if (!current.isEmpty()) {
                            reader.unread(c);
                            return current.toString();
                        }
                        quoted = true;
                        continue;
                    }
                    if (quoted) {
                        if (c == '\\') {
                            int escaped = reader.read();
                            if (escaped < 0) {
                                throw new IllegalArgumentException("Dangling escape in quoted string");
                            }
                            current.append((char) escaped);
                        } else {
                            current.append(c);
                        }
                        continue;
                    }
                    if (Character.isWhitespace(c)) {
                        if (!current.isEmpty()) {
                            return current.toString();
                        }
                        continue;
                    }
                    if (c == '{' || c == '}') {
                        if (!current.isEmpty()) {
                            reader.unread(c);
                            return current.toString();
                        }
                        return Character.toString(c);
                    }
                    current.append(c);
                }
            } catch (IOException error) {
                throw new IllegalArgumentException("Could not read ATF input: " + error.getMessage(), error);
            }
        }

        private void skipComment() throws IOException {
            int raw;
            while ((raw = reader.read()) >= 0 && raw != '\n') {
                // Skip comment characters.
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private static final class Parser {
        private final TokenStream tokens;
        private final MutableWorld world;
        private final Map<String, Template> templates;
        private final Map<String, List<String>> patternTemplates;
        private final Map<String, BlockState> blockAliases;
        private final Map<String, BlockPalette> blockPalettes;
        private final Map<String, String> biomeAliases;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final Rotation rotation;
        private final Path baseDir;
        private final Set<Path> includeStack;

        Parser(TokenStream tokens) {
            this(tokens, new MutableWorld(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    0, 0, 0, Rotation.NONE, Path.of("").toAbsolutePath().normalize(), new HashSet<>());
        }

        Parser(TokenStream tokens, MutableWorld world, Map<String, Template> templates,
               Map<String, List<String>> patternTemplates, Map<String, BlockState> blockAliases,
               Map<String, BlockPalette> blockPalettes,
               Map<String, String> biomeAliases,
               int originX, int originY, int originZ, Rotation rotation, Path baseDir, Set<Path> includeStack) {
            this.tokens = tokens;
            this.world = world;
            this.templates = templates;
            this.patternTemplates = patternTemplates;
            this.blockAliases = blockAliases;
            this.blockPalettes = blockPalettes;
            this.biomeAliases = biomeAliases;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.rotation = rotation;
            this.baseDir = baseDir;
            this.includeStack = includeStack;
        }

        WorldSpec parse() {
            Map<String, Integer> variables = new HashMap<>();
            if (peek("world")) {
                take("world");
                world.spec().name(take());
                take("{");
                parseStatements(variables);
                take("}");
            } else {
                parseStatements(variables);
            }
            if (!tokens.isEmpty()) {
                throw new IllegalArgumentException("Unexpected token after document end: " + take());
            }
            new WorldPostProcessor().apply(world.spec());
            return world.spec();
        }

        private void parseStatements(Map<String, Integer> variables) {
            while (!tokens.isEmpty() && !peek("}")) {
                parseStatement(variables);
            }
        }

        private void parseStatement(Map<String, Integer> variables) {
            String command = take();
            switch (command) {
                case "version" -> world.spec().dataVersion(expr(take(), variables));
                case "version_name" -> world.spec().versionName(take());
                case "min_y" -> world.dimensionSpec().minY(expr(take(), variables));
                case "height" -> world.dimensionSpec().height(expr(take(), variables));
                case "spawn" -> world.spec().spawn(expr(take(), variables), expr(take(), variables), expr(take(), variables));
                case "time" -> world.spec().time(Long.parseLong(take()));
                case "day_time" -> world.spec().dayTime(Long.parseLong(take()));
                case "game_mode" -> world.spec().gameType(gameMode(take()));
                case "difficulty" -> world.spec().difficulty(difficulty(take()));
                case "hardcore" -> world.spec().hardcore(booleanValue(take()));
                case "allow_commands" -> world.spec().allowCommands(booleanValue(take()));
                case "weather" -> weather();
                case "world_border" -> worldBorder();
                case "game_rule" -> world.spec().gameRule(variableName(take()), take());
                case "default_biome" -> world.dimensionSpec().defaultBiome(biome());
                case "passthrough" -> passthrough();
                case "dimension" -> dimension(variables);
                case "let" -> {
                    String name = variableName(take());
                    take("=");
                    variables.put(name, expr(take(), variables));
                }
                case "include" -> include(variables);
                case "block" -> blockAlias();
                case "block_palette" -> blockPalette();
                case "biome_alias" -> biomeAlias();
                case "component" -> component();
                case "template" -> template();
                case "use" -> useTemplate(variables);
                case "pattern_template" -> patternTemplate();
                case "use_pattern" -> usePattern(variables);
                case "at" -> at(variables);
                case "group" -> group(variables);
                case "bounds" -> bounds(variables);
                case "slice" -> slice(variables);
                case "pattern" -> pattern(variables);
                case "block_entity" -> blockEntity(variables);
                case "merge_block_entity" -> mergeBlockEntity(variables);
                case "sign_text" -> signText(variables);
                case "entity" -> entity(variables);
                case "biome" -> {
                    int localX = expr(take(), variables);
                    int y = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    Point point = worldPoint(localX, localZ);
                    world.setBiome(point.x, y, point.z, biome());
                }
                case "fill_biome" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    world.fillBiome(p1.x, y1, p1.z, p2.x, y2, p2.z, biome());
                }
                case "set" -> {
                    int localX = expr(take(), variables);
                    int y = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    Point point = worldPoint(localX, localZ);
                    world.setBlock(point.x, y, point.z, block());
                }
                case "place" -> placeComponent(variables);
                case "door" -> door(variables);
                case "bed" -> bed(variables);
                case "fill", "box" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    if (peek("to")) {
                        take("to");
                    }
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    world.fill(p1.x, y1, p1.z, p2.x, y2, p2.z, block());
                }
                case "wall" -> wall(variables);
                case "line" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    world.line(p1.x, y1, p1.z, p2.x, y2, p2.z, block());
                }
                case "fill_palette" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    String paletteName = variableName(take());
                    int seed = 0;
                    if (peek("seed")) {
                        take("seed");
                        seed = expr(take(), variables);
                    }
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    fillPalette(p1.x, y1, p1.z, p2.x, y2, p2.z, paletteName, seed);
                }
                case "replace" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    BlockState target = block();
                    BlockState replacement = block();
                    world.replace(p1.x, y1, p1.z, p2.x, y2, p2.z, target, replacement);
                }
                case "replace_palette" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    BlockState target = block();
                    String paletteName = variableName(take());
                    int seed = 0;
                    if (peek("seed")) {
                        take("seed");
                        seed = expr(take(), variables);
                    }
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    replacePalette(p1.x, y1, p1.z, p2.x, y2, p2.z, target, paletteName, seed);
                }
                case "copy" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    take("to");
                    int toLocalX = expr(take(), variables);
                    int toY = worldY(expr(take(), variables));
                    int toLocalZ = expr(take(), variables);
                    Rotation copyRotation = Rotation.NONE;
                    boolean skipAir = false;
                    boolean includeEntities = false;
                    boolean includeBiomes = false;
                    boolean mirrorX = false;
                    boolean mirrorZ = false;
                    while (peek("rotate") || peek("skip_air") || peek("with_entities") || peek("with_biomes")
                            || peek("mirror_x") || peek("mirror_z")) {
                        if (peek("rotate")) {
                            take("rotate");
                            copyRotation = Rotation.fromDegrees(expr(take(), variables));
                        } else if (peek("skip_air")) {
                            take("skip_air");
                            skipAir = true;
                        } else if (peek("with_entities")) {
                            take("with_entities");
                            includeEntities = true;
                        } else if (peek("with_biomes")) {
                            take("with_biomes");
                            includeBiomes = true;
                        } else if (peek("mirror_x")) {
                            take("mirror_x");
                            mirrorX = true;
                        } else {
                            take("mirror_z");
                            mirrorZ = true;
                        }
                    }
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    Point target = worldPoint(toLocalX, toLocalZ);
                    world.copy(p1.x, y1, p1.z, p2.x, y2, p2.z,
                            target.x, toY, target.z, copyRotation.quarters,
                            skipAir, includeEntities, includeBiomes, mirrorX, mirrorZ);
                }
                case "hollow_box" -> {
                    int localX1 = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ1 = expr(take(), variables);
                    int localX2 = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    int localZ2 = expr(take(), variables);
                    Point p1 = worldPoint(localX1, localZ1);
                    Point p2 = worldPoint(localX2, localZ2);
                    BlockState shell = block();
                    BlockState inside = block();
                    world.hollowBox(p1.x, y1, p1.z, p2.x, y2, p2.z, shell, inside);
                }
                case "sphere" -> {
                    int localX = expr(take(), variables);
                    int y = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    int radius = expr(take(), variables);
                    Point point = worldPoint(localX, localZ);
                    world.sphere(point.x, y, point.z, radius, block());
                }
                case "hollow_sphere" -> {
                    int localX = expr(take(), variables);
                    int y = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    int radius = expr(take(), variables);
                    Point point = worldPoint(localX, localZ);
                    BlockState shell = block();
                    BlockState inside = block();
                    world.hollowSphere(point.x, y, point.z, radius, shell, inside);
                }
                case "cylinder" -> {
                    int localX = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    int radius = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    Point point = worldPoint(localX, localZ);
                    world.cylinder(point.x, y1, point.z, radius, y2, block());
                }
                case "hollow_cylinder" -> {
                    int localX = expr(take(), variables);
                    int y1 = worldY(expr(take(), variables));
                    int localZ = expr(take(), variables);
                    int radius = expr(take(), variables);
                    int y2 = worldY(expr(take(), variables));
                    Point point = worldPoint(localX, localZ);
                    BlockState shell = block();
                    BlockState inside = block();
                    world.hollowCylinder(point.x, y1, point.z, radius, y2, shell, inside);
                }
                case "repeat" -> repeat(variables);
                case "if" -> ifStatement(variables);
                case "choose" -> choose(variables);
                case "scatter" -> scatter(variables);
                case "surface_scatter" -> surfaceScatter(variables);
                case "terrain" -> terrain(variables);
                case "smooth_terrain" -> smoothTerrain(variables);
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
        }

        private void passthrough() {
            String target = take();
            take("from");
            world.spec().passthroughPath(target, baseDir.resolve(take()).normalize());
        }

        private void weather() {
            take("{");
            List<String> body = collectBody();
            for (int i = 0; i < body.size(); i++) {
                String command = body.get(i);
                switch (command) {
                    case "raining" -> world.spec().raining(booleanValue(body.get(++i)));
                    case "rain_time" -> world.spec().rainTime(Integer.parseInt(body.get(++i)));
                    case "thundering" -> world.spec().thundering(booleanValue(body.get(++i)));
                    case "thunder_time" -> world.spec().thunderTime(Integer.parseInt(body.get(++i)));
                    case "clear_weather_time" -> world.spec().clearWeatherTime(Integer.parseInt(body.get(++i)));
                    default -> throw new IllegalArgumentException("Unknown weather command: " + command);
                }
            }
        }

        private void worldBorder() {
            take("{");
            List<String> body = collectBody();
            WorldSpec.WorldBorder current = world.spec().worldBorder();
            double centerX = current.centerX();
            double centerZ = current.centerZ();
            double size = current.size();
            double sizeLerpTarget = current.sizeLerpTarget();
            long sizeLerpTime = current.sizeLerpTime();
            int warningBlocks = current.warningBlocks();
            int warningTime = current.warningTime();
            double damageBuffer = current.damageBuffer();
            double damagePerBlock = current.damagePerBlock();
            for (int i = 0; i < body.size(); i++) {
                String command = body.get(i);
                switch (command) {
                    case "center" -> {
                        centerX = Double.parseDouble(body.get(++i));
                        centerZ = Double.parseDouble(body.get(++i));
                    }
                    case "size" -> size = Double.parseDouble(body.get(++i));
                    case "size_lerp_target" -> sizeLerpTarget = Double.parseDouble(body.get(++i));
                    case "size_lerp_time" -> sizeLerpTime = Long.parseLong(body.get(++i));
                    case "warning_blocks" -> warningBlocks = Integer.parseInt(body.get(++i));
                    case "warning_time" -> warningTime = Integer.parseInt(body.get(++i));
                    case "damage_buffer" -> damageBuffer = Double.parseDouble(body.get(++i));
                    case "damage_per_block" -> damagePerBlock = Double.parseDouble(body.get(++i));
                    default -> throw new IllegalArgumentException("Unknown world_border command: " + command);
                }
            }
            world.spec().worldBorder(new WorldSpec.WorldBorder(centerX, centerZ, size, sizeLerpTarget,
                    sizeLerpTime, warningBlocks, warningTime, damageBuffer, damagePerBlock));
        }

        private void dimension(Map<String, Integer> variables) {
            String id = take();
            take("{");
            List<String> body = collectBody();
            new Parser(new ListTokenStream(body), world.inDimension(id), templates, patternTemplates,
                    blockAliases, blockPalettes, biomeAliases,
                    0, 0, 0, Rotation.NONE, baseDir, includeStack)
                    .parseStatements(variables);
        }

        private void group(Map<String, Integer> variables) {
            take();
            take("{");
            GroupBody group = groupBody(collectBody(), variables);
            child(group.body, group.originX, group.originY, group.originZ, Rotation.NONE)
                    .parseStatements(variables);
        }

        private GroupBody groupBody(List<String> source, Map<String, Integer> variables) {
            List<String> body = new ArrayList<>();
            int groupOriginX = 0;
            int groupOriginY = 0;
            int groupOriginZ = 0;
            for (int i = 0; i < source.size(); i++) {
                String token = source.get(i);
                if ("origin".equals(token)) {
                    groupOriginX = expr(source.get(++i), variables);
                    groupOriginY = expr(source.get(++i), variables);
                    groupOriginZ = expr(source.get(++i), variables);
                } else if ("bounds".equals(token)) {
                    i += 7;
                } else {
                    body.add(token);
                }
            }
            return new GroupBody(body, groupOriginX, groupOriginY, groupOriginZ);
        }

        private void bounds(Map<String, Integer> variables) {
            expr(take(), variables);
            expr(take(), variables);
            expr(take(), variables);
            take("to");
            expr(take(), variables);
            expr(take(), variables);
            expr(take(), variables);
        }

        private void slice(Map<String, Integer> variables) {
            int y = sliceY(variables);
            take("{");
            while (!tokens.isEmpty() && !peek("}")) {
                String command = take();
                switch (command) {
                    case "fill", "box" -> {
                        int localX1 = expr(take(), variables);
                        int localZ1 = expr(take(), variables);
                        take("to");
                        int localX2 = expr(take(), variables);
                        int localZ2 = expr(take(), variables);
                        Point p1 = worldPoint(localX1, localZ1);
                        Point p2 = worldPoint(localX2, localZ2);
                        world.fill(p1.x, y, p1.z, p2.x, y, p2.z, block());
                    }
                    case "set", "place" -> {
                        int localX = expr(take(), variables);
                        int localZ = expr(take(), variables);
                        Point point = worldPoint(localX, localZ);
                        world.setBlock(point.x, y, point.z, block());
                    }
                    default -> throw new IllegalArgumentException("Unknown slice command: " + command);
                }
            }
            take("}");
        }

        private int sliceY(Map<String, Integer> variables) {
            String axis = take();
            if (axis.startsWith("y=")) {
                return worldY(expr(axis.substring(2), variables));
            }
            if (!"y".equals(axis)) {
                throw new IllegalArgumentException("Only y slices are supported: " + axis);
            }
            if (peek("=")) {
                take("=");
            }
            return worldY(expr(take(), variables));
        }

        private void pattern(Map<String, Integer> variables) {
            int localX = expr(take(), variables);
            int y = worldY(expr(take(), variables));
            int localZ = expr(take(), variables);
            take("{");
            patternBody(variables, localX, y, localZ, true);
        }

        private void wall(Map<String, Integer> variables) {
            int localX = expr(take(), variables);
            int y = expr(take(), variables);
            int localZ = expr(take(), variables);
            int width = expr(take(), variables);
            int height = expr(take(), variables);
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("wall width and height must be positive");
            }
            WallFacing facing = WallFacing.fromToken(take());
            BlockState block = block(facing.quarterTurns);
            for (int dy = 0; dy < height; dy++) {
                for (int span = 0; span < width; span++) {
                    int x = localX + (facing.spansX() ? span : 0);
                    int z = localZ + (facing.spansZ() ? span : 0);
                    Point point = worldPoint(x, z);
                    world.setBlock(point.x, worldY(y + dy), point.z, block);
                }
            }
        }

        private void door(Map<String, Integer> variables) {
            int localX = expr(take(), variables);
            int y = expr(take(), variables);
            int localZ = expr(take(), variables);
            String facing = doorFacing(take());
            String hinge = doorHinge(take());
            String open = doorOpen(take());
            String name = blockName(take());
            Point point = worldPoint(localX, localZ);
            world.setBlock(point.x, worldY(y), point.z,
                    doorBlock(name, facing, hinge, open, "lower").rotatedY(rotation.quarters));
            world.setBlock(point.x, worldY(y + 1), point.z,
                    doorBlock(name, facing, hinge, open, "upper").rotatedY(rotation.quarters));
        }

        private void bed(Map<String, Integer> variables) {
            int localX = expr(take(), variables);
            int y = expr(take(), variables);
            int localZ = expr(take(), variables);
            String facing = doorFacing(take());
            String name = blockName(take());
            Point foot = worldPoint(localX, localZ);
            Point headOffset = facingOffset(facing);
            Point head = worldPoint(localX + headOffset.x(), localZ + headOffset.z());
            world.setBlock(foot.x, worldY(y), foot.z, bedBlock(name, facing, "foot").rotatedY(rotation.quarters));
            world.setBlock(head.x, worldY(y), head.z, bedBlock(name, facing, "head").rotatedY(rotation.quarters));
        }

        private BlockState bedBlock(String name, String facing, String part) {
            return new BlockState(name, Map.of(
                    "facing", facing,
                    "occupied", "false",
                    "part", part));
        }

        private Point facingOffset(String facing) {
            return switch (facing) {
                case "north" -> new Point(0, -1);
                case "east" -> new Point(1, 0);
                case "south" -> new Point(0, 1);
                case "west" -> new Point(-1, 0);
                default -> throw new IllegalArgumentException("Unknown facing: " + facing);
            };
        }

        private BlockState doorBlock(String name, String facing, String hinge, String open, String half) {
            return new BlockState(name, Map.of(
                    "facing", facing,
                    "half", half,
                    "hinge", hinge,
                    "open", open,
                    "powered", "false"));
        }

        private String blockName(String token) {
            if (token.startsWith("$")) {
                String name = variableName(token);
                BlockState state = blockAliases.get(name);
                if (state == null) {
                    throw new IllegalArgumentException("Unknown block alias: $" + name);
                }
                return state.name();
            }
            return BlockState.parse(token).name();
        }

        private String doorFacing(String token) {
            return switch (token) {
                case "north", "east", "south", "west" -> token;
                default -> throw new IllegalArgumentException("door facing must be north, east, south, or west");
            };
        }

        private String doorHinge(String token) {
            return switch (token) {
                case "left", "right" -> token;
                default -> throw new IllegalArgumentException("door hinge must be left or right");
            };
        }

        private String doorOpen(String token) {
            return switch (token) {
                case "open", "true" -> "true";
                case "closed", "false" -> "false";
                default -> throw new IllegalArgumentException("door state must be open or closed");
            };
        }

        private void patternBody(Map<String, Integer> variables, int localX, int y, int localZ, boolean requireClose) {
            Map<Character, BlockState> palette = new HashMap<>();
            int layer = 0;
            int row = 0;
            while (!tokens.isEmpty() && !peek("}")) {
                String command = take();
                switch (command) {
                    case "block" -> {
                        char key = patternKey(take());
                        take("=");
                        palette.put(key, block());
                    }
                    case "row" -> {
                        String text = take();
                        for (int x = 0; x < text.length(); x++) {
                            char key = text.charAt(x);
                            if (key == '.' || key == ' ') {
                                continue;
                            }
                            BlockState block = palette.get(key);
                            if (block == null) {
                                throw new IllegalArgumentException("Pattern row uses unmapped character: " + key);
                            }
                            Point point = worldPoint(localX + x, localZ + row);
                            world.setBlock(point.x, y + layer, point.z, block);
                        }
                        row++;
                    }
                    case "layer" -> {
                        layer++;
                        row = 0;
                    }
                    default -> throw new IllegalArgumentException("Unknown pattern command: " + command);
                }
            }
            if (requireClose) {
                take("}");
            }
        }

        private char patternKey(String token) {
            if (token.length() != 1 || token.charAt(0) == '.' || token.charAt(0) == ' ') {
                throw new IllegalArgumentException("Pattern block key must be one non-skip character");
            }
            return token.charAt(0);
        }

        private void include(Map<String, Integer> variables) {
            Path included = baseDir.resolve(take()).toAbsolutePath().normalize();
            if (includeStack.contains(included)) {
                throw new IllegalArgumentException("Cyclic include: " + included);
            }
            try {
                Set<Path> childStack = new HashSet<>(includeStack);
                childStack.add(included);
                try (StreamingTokenStream includedTokens =
                             new StreamingTokenStream(Files.newBufferedReader(included))) {
                    new Parser(includedTokens, world, templates, patternTemplates,
                            blockAliases, blockPalettes, biomeAliases,
                            originX, originY, originZ, rotation, included.getParent(), childStack)
                            .parseStatements(variables);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not include " + included + ": " + e.getMessage(), e);
            }
        }

        private void blockAlias() {
            String name = variableName(take());
            take("=");
            blockAlias(blockAliases, name);
        }

        private void blockAlias(Map<String, BlockState> target, String name) {
            String token = take();
            if (token.startsWith("$")) {
                String alias = variableName(token);
                BlockState state = target.get(alias);
                if (state == null) {
                    throw new IllegalArgumentException("Unknown block alias: $" + alias);
                }
                target.put(name, state);
                return;
            }
            target.put(name, BlockState.parse(token));
        }

        private void blockPalette() {
            String name = variableName(take());
            take("{");
            List<WeightedBlock> blocks = new ArrayList<>();
            while (!tokens.isEmpty() && !peek("}")) {
                take("block");
                BlockState state = block();
                int weight = 1;
                if (peek("weight")) {
                    take("weight");
                    weight = Integer.parseInt(take());
                }
                if (weight <= 0) {
                    throw new IllegalArgumentException("block_palette weight must be positive");
                }
                blocks.add(new WeightedBlock(state, weight));
            }
            take("}");
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("block_palette must contain at least one block");
            }
            blockPalettes.put(name, new BlockPalette(blocks));
        }

        private void biomeAlias() {
            String name = variableName(take());
            take("=");
            biomeAlias(biomeAliases, name);
        }

        private void biomeAlias(Map<String, String> target, String name) {
            target.put(name, ResourceId.normalize(take()));
        }

        private int gameMode(String token) {
            return switch (token) {
                case "survival" -> 0;
                case "creative" -> 1;
                case "adventure" -> 2;
                case "spectator" -> 3;
                default -> Integer.parseInt(token);
            };
        }

        private int difficulty(String token) {
            return switch (token) {
                case "peaceful" -> 0;
                case "easy" -> 1;
                case "normal" -> 2;
                case "hard" -> 3;
                default -> Integer.parseInt(token);
            };
        }

        private boolean booleanValue(String token) {
            return switch (token) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> throw new IllegalArgumentException("Expected boolean but got: " + token);
            };
        }

        private void blockEntity(Map<String, Integer> variables) {
            putBlockEntity(variables, false);
        }

        private void mergeBlockEntity(Map<String, Integer> variables) {
            putBlockEntity(variables, true);
        }

        private void putBlockEntity(Map<String, Integer> variables, boolean merge) {
            int localX = expr(take(), variables);
            int y = worldY(expr(take(), variables));
            int localZ = expr(take(), variables);
            Point point = worldPoint(localX, localZ);
            String id = ResourceId.normalize(take());
            Map<String, NbtValue> data = new HashMap<>();
            if (peek("{")) {
                take("{");
                data.putAll(nbtCompoundBody(variables));
            }
            if (merge) {
                world.mergeBlockEntityData(point.x, y, point.z, id, data);
            } else {
                world.setBlockEntity(point.x, y, point.z, id, data);
            }
        }

        private void signText(Map<String, Integer> variables) {
            int localX = expr(take(), variables);
            int y = worldY(expr(take(), variables));
            int localZ = expr(take(), variables);
            Point point = worldPoint(localX, localZ);
            String side = signSide(take());
            List<NbtValue> messages = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                messages.add(NbtValue.stringValue(take()));
            }
            Map<String, NbtValue> text = new HashMap<>();
            text.put("messages", NbtValue.listValue(NbtWriter.TAG_STRING, messages));
            text.put("color", NbtValue.stringValue("black"));
            text.put("has_glowing_text", NbtValue.byteValue(0));
            world.mergeBlockEntityData(point.x, y, point.z, "minecraft:sign",
                    Map.of(side + "_text", NbtValue.compoundValue(text)));
        }

        private String signSide(String token) {
            return switch (token) {
                case "front", "back" -> token;
                default -> throw new IllegalArgumentException("sign_text side must be front or back");
            };
        }

        private void entity(Map<String, Integer> variables) {
            double localX = doubleExpr(take(), variables);
            double y = worldY(doubleExpr(take(), variables));
            double localZ = doubleExpr(take(), variables);
            DoublePoint point = worldDoublePoint(localX, localZ);
            String id = ResourceId.normalize(take());
            Map<String, NbtValue> data = new HashMap<>();
            if (peek("{")) {
                take("{");
                data.putAll(nbtCompoundBody(variables));
            }
            world.addEntity(point.x, y, point.z, id, data);
        }

        private Map<String, NbtValue> nbtCompoundBody(Map<String, Integer> variables) {
            Map<String, NbtValue> values = new HashMap<>();
            while (!tokens.isEmpty() && !peek("}")) {
                String type = take();
                if ("item".equals(type)) {
                    String name = take();
                    values.put(name, appendedItem(values.get(name), itemStack(variables, true)));
                    continue;
                }
                if ("item_stack".equals(type)) {
                    String name = take();
                    values.put(name, itemStack(variables, false));
                    continue;
                }
                String name = take();
                values.put(name, switch (type) {
                    case "byte" -> NbtValue.byteValue(expr(take(), variables));
                    case "short" -> NbtValue.shortValue(expr(take(), variables));
                    case "int" -> NbtValue.intValue(expr(take(), variables));
                    case "long" -> NbtValue.longValue(Long.parseLong(take()));
                    case "float" -> NbtValue.floatValue(Float.parseFloat(take()));
                    case "double" -> NbtValue.doubleValue(Double.parseDouble(take()));
                    case "byte_array" -> NbtValue.byteArrayValue(byteArray(variables));
                    case "int_array" -> NbtValue.intArrayValue(intArray(variables));
                    case "long_array" -> NbtValue.longArrayValue(longArray());
                    case "string" -> NbtValue.stringValue(take());
                    case "compound" -> {
                        take("{");
                        yield NbtValue.compoundValue(nbtCompoundBody(variables));
                    }
                    case "list" -> nbtList(variables);
                    default -> throw new IllegalArgumentException("Unknown NBT tag type: " + type);
                });
            }
            take("}");
            return values;
        }

        private NbtValue appendedItem(NbtValue existing, NbtValue item) {
            List<NbtValue> values = new ArrayList<>();
            if (existing != null) {
                if (!(existing instanceof NbtValue.ListValue list)
                        || list.elementType() != NbtWriter.TAG_COMPOUND) {
                    throw new IllegalArgumentException("item can only append to a compound list");
                }
                values.addAll(list.values());
            }
            values.add(item);
            return NbtValue.listValue(NbtWriter.TAG_COMPOUND, values);
        }

        private NbtValue itemStack(Map<String, Integer> variables, boolean slotted) {
            Map<String, NbtValue> values = new HashMap<>();
            if (slotted) {
                take("slot");
                values.put("Slot", NbtValue.byteValue(expr(take(), variables)));
            }
            values.put("id", NbtValue.stringValue(ResourceId.normalize(take())));
            int count = 1;
            if (peek("count")) {
                take("count");
                count = expr(take(), variables);
            }
            values.put("Count", NbtValue.byteValue(count));
            if (peek("{")) {
                take("{");
                values.put("tag", NbtValue.compoundValue(nbtCompoundBody(variables)));
            }
            return NbtValue.compoundValue(values);
        }

        private NbtValue nbtList(Map<String, Integer> variables) {
            int elementType = nbtType(take());
            take("{");
            List<NbtValue> values = new ArrayList<>();
            while (!tokens.isEmpty() && !peek("}")) {
                values.add(nbtListElement(elementType, variables));
            }
            take("}");
            return NbtValue.listValue(elementType, values);
        }

        private NbtValue nbtListElement(int elementType, Map<String, Integer> variables) {
            return switch (elementType) {
                case NbtWriter.TAG_BYTE -> NbtValue.byteValue(expr(take(), variables));
                case NbtWriter.TAG_SHORT -> NbtValue.shortValue(expr(take(), variables));
                case NbtWriter.TAG_INT -> NbtValue.intValue(expr(take(), variables));
                case NbtWriter.TAG_LONG -> NbtValue.longValue(Long.parseLong(take()));
                case NbtWriter.TAG_FLOAT -> NbtValue.floatValue(Float.parseFloat(take()));
                case NbtWriter.TAG_DOUBLE -> NbtValue.doubleValue(Double.parseDouble(take()));
                case NbtWriter.TAG_STRING -> NbtValue.stringValue(take());
                case NbtWriter.TAG_COMPOUND -> {
                    take("{");
                    yield NbtValue.compoundValue(nbtCompoundBody(variables));
                }
                default -> throw new IllegalArgumentException("Unsupported list element type: " + elementType);
            };
        }

        private int nbtType(String type) {
            return switch (type) {
                case "end" -> NbtWriter.TAG_END;
                case "byte" -> NbtWriter.TAG_BYTE;
                case "short" -> NbtWriter.TAG_SHORT;
                case "int" -> NbtWriter.TAG_INT;
                case "long" -> NbtWriter.TAG_LONG;
                case "float" -> NbtWriter.TAG_FLOAT;
                case "double" -> NbtWriter.TAG_DOUBLE;
                case "string" -> NbtWriter.TAG_STRING;
                case "compound" -> NbtWriter.TAG_COMPOUND;
                default -> throw new IllegalArgumentException("Unknown NBT list type: " + type);
            };
        }

        private byte[] byteArray(Map<String, Integer> variables) {
            take("{");
            List<Byte> values = new ArrayList<>();
            while (!tokens.isEmpty() && !peek("}")) {
                values.add((byte) expr(take(), variables));
            }
            take("}");
            byte[] result = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private int[] intArray(Map<String, Integer> variables) {
            take("{");
            List<Integer> values = new ArrayList<>();
            while (!tokens.isEmpty() && !peek("}")) {
                values.add(expr(take(), variables));
            }
            take("}");
            int[] result = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private long[] longArray() {
            take("{");
            List<Long> values = new ArrayList<>();
            while (!tokens.isEmpty() && !peek("}")) {
                values.add(Long.parseLong(take()));
            }
            take("}");
            long[] result = new long[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private void template() {
            String name = take();
            List<String> parameters = new ArrayList<>();
            if (peek("params")) {
                take("params");
                while (!peek("{")) {
                    parameters.add(variableName(take()));
                }
            }
            take("{");
            templates.put(name, new Template(parameters, collectBody()));
        }

        private void component() {
            String name = take();
            take("{");
            templates.put(name, new Template(List.of(), collectBody()));
        }

        private void patternTemplate() {
            String name = take();
            take("{");
            patternTemplates.put(name, List.copyOf(collectBody()));
        }

        private void useTemplate(Map<String, Integer> variables) {
            String name = take();
            Template template = templates.get(name);
            if (template == null) {
                throw new IllegalArgumentException("Unknown template: " + name);
            }
            take("at");
            int x = expr(take(), variables);
            int y = expr(take(), variables);
            int z = expr(take(), variables);
            Rotation childRotation = Rotation.NONE;
            List<Integer> arguments = null;
            while (peek("rotate") || peek("args")) {
                if (peek("rotate")) {
                    take("rotate");
                    childRotation = Rotation.fromDegrees(expr(take(), variables));
                } else {
                    take("args");
                    List<Integer> parsed = new ArrayList<>();
                    for (int i = 0; i < template.parameters().size(); i++) {
                        parsed.add(expr(take(), variables));
                    }
                    arguments = parsed;
                }
            }
            Scope scope = optionalScope(variables);
            Map<String, Integer> scopedVariables = new HashMap<>(scope.variables);
            bindArguments(name, template, arguments, scopedVariables);
            child(template.body(), x, y, z, childRotation, scope.blockAliases, scope.biomeAliases)
                    .parseStatements(scopedVariables);
        }

        private void placeComponent(Map<String, Integer> variables) {
            String name = take();
            Template component = templates.get(name);
            if (component == null) {
                throw new IllegalArgumentException("Unknown component: " + name);
            }
            if (!component.parameters().isEmpty()) {
                throw new IllegalArgumentException("Component " + name + " must not declare parameters");
            }
            take("at");
            int x = expr(take(), variables);
            int y = expr(take(), variables);
            int z = expr(take(), variables);
            Rotation childRotation = optionalRotation(variables);
            Scope scope = optionalScope(variables);
            child(component.body(), x, y, z, childRotation, scope.blockAliases, scope.biomeAliases)
                    .parseStatements(scope.variables);
        }

        private void usePattern(Map<String, Integer> variables) {
            String name = take();
            take("at");
            int x = expr(take(), variables);
            int y = expr(take(), variables);
            int z = expr(take(), variables);
            Rotation childRotation = optionalRotation(variables);
            Scope scope = optionalScope(variables);
            List<String> body = patternTemplates.get(name);
            if (body == null) {
                throw new IllegalArgumentException("Unknown pattern template: " + name);
            }
            child(body, x, y, z, childRotation, scope.blockAliases, scope.biomeAliases)
                    .patternBody(scope.variables, 0, originY + y, 0, false);
        }

        private void bindArguments(String name, Template template, List<Integer> arguments,
                                   Map<String, Integer> variables) {
            if (template.parameters().isEmpty()) {
                if (arguments != null) {
                    throw new IllegalArgumentException("Template " + name + " does not declare parameters");
                }
                return;
            }
            if (arguments == null) {
                throw new IllegalArgumentException("Template " + name + " expects "
                        + template.parameters().size() + " argument(s)");
            }
            if (arguments.size() != template.parameters().size()) {
                throw new IllegalArgumentException("Template " + name + " expects "
                        + template.parameters().size() + " argument(s) but got " + arguments.size());
            }
            for (int i = 0; i < template.parameters().size(); i++) {
                variables.put(template.parameters().get(i), arguments.get(i));
            }
        }

        private void at(Map<String, Integer> variables) {
            int x = expr(take(), variables);
            int y = expr(take(), variables);
            int z = expr(take(), variables);
            Rotation childRotation = optionalRotation(variables);
            Scope scope = optionalScope(variables);
            take("{");
            child(collectBody(), x, y, z, childRotation, scope.blockAliases, scope.biomeAliases)
                    .parseStatements(scope.variables);
        }

        private void repeat(Map<String, Integer> variables) {
            String name = variableName(take());
            int start = expr(take(), variables);
            int end = expr(take(), variables);
            int step = 1;
            if (peek("step")) {
                take("step");
                step = expr(take(), variables);
            }
            if (step == 0) {
                throw new IllegalArgumentException("repeat step must not be zero");
            }
            take("{");
            List<String> body = collectBody();
            Integer previous = variables.get(name);
            boolean forward = step > 0;
            for (int i = start; forward ? i <= end : i >= end; i += step) {
                variables.put(name, i);
                child(body, 0, 0, 0, Rotation.NONE).parseStatements(variables);
            }
            if (previous == null) {
                variables.remove(name);
            } else {
                variables.put(name, previous);
            }
        }

        private void ifStatement(Map<String, Integer> variables) {
            boolean condition = condition(variables);
            take("{");
            List<String> trueBody = collectBody();
            List<String> falseBody = List.of();
            if (peek("else")) {
                take("else");
                take("{");
                falseBody = collectBody();
            }
            child(condition ? trueBody : falseBody, 0, 0, 0, Rotation.NONE).parseStatements(variables);
        }

        private boolean condition(Map<String, Integer> variables) {
            int left = expr(take(), variables);
            if (peek("{")) {
                return left != 0;
            }
            String operator = take();
            int right = expr(take(), variables);
            return switch (operator) {
                case "==" -> left == right;
                case "!=" -> left != right;
                case "<" -> left < right;
                case "<=" -> left <= right;
                case ">" -> left > right;
                case ">=" -> left >= right;
                default -> throw new IllegalArgumentException("Unknown condition operator: " + operator);
            };
        }

        private void choose(Map<String, Integer> variables) {
            int seed = 0;
            if (peek("seed")) {
                take("seed");
                seed = expr(take(), variables);
            }
            take("{");
            List<WeightedBody> choices = new ArrayList<>();
            int totalWeight = 0;
            while (!tokens.isEmpty() && !peek("}")) {
                take("weight");
                int weight = expr(take(), variables);
                if (weight <= 0) {
                    throw new IllegalArgumentException("choose weight must be positive");
                }
                take("{");
                choices.add(new WeightedBody(weight, collectBody()));
                totalWeight += weight;
            }
            take("}");
            if (choices.isEmpty()) {
                throw new IllegalArgumentException("choose requires at least one weighted body");
            }
            int x = variables.getOrDefault("x", originX);
            int y = variables.getOrDefault("y", originY);
            int z = variables.getOrDefault("z", originZ);
            int slot = Math.floorMod(mix(x, y, z, seed), totalWeight);
            int cursor = 0;
            for (WeightedBody choice : choices) {
                cursor += choice.weight();
                if (slot < cursor) {
                    child(choice.body(), 0, 0, 0, Rotation.NONE).parseStatements(variables);
                    return;
                }
            }
        }

        private void scatter(Map<String, Integer> variables) {
            int x1 = expr(take(), variables);
            int y1 = expr(take(), variables);
            int z1 = expr(take(), variables);
            int x2 = expr(take(), variables);
            int y2 = expr(take(), variables);
            int z2 = expr(take(), variables);
            take("chance");
            int numerator = expr(take(), variables);
            int denominator = expr(take(), variables);
            if (denominator <= 0) {
                throw new IllegalArgumentException("scatter chance denominator must be positive");
            }
            if (numerator < 0 || numerator > denominator) {
                throw new IllegalArgumentException("scatter chance numerator must be in [0, denominator]");
            }
            int seed = 0;
            int step = 1;
            while (peek("seed") || peek("step")) {
                if (peek("seed")) {
                    take("seed");
                    seed = expr(take(), variables);
                } else {
                    take("step");
                    step = expr(take(), variables);
                    if (step <= 0) {
                        throw new IllegalArgumentException("scatter step must be positive");
                    }
                }
            }
            take("{");
            List<String> body = collectBody();
            Integer previousX = variables.get("x");
            Integer previousY = variables.get("y");
            Integer previousZ = variables.get("z");
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            for (int y = minY; y <= maxY; y += step) {
                for (int z = minZ; z <= maxZ; z += step) {
                    for (int x = minX; x <= maxX; x += step) {
                        if (Math.floorMod(mix(x, y, z, seed), denominator) < numerator) {
                            variables.put("x", x);
                            variables.put("y", y);
                            variables.put("z", z);
                            child(body, x, y, z, Rotation.NONE).parseStatements(variables);
                        }
                    }
                }
            }
            restoreVariable(variables, "x", previousX);
            restoreVariable(variables, "y", previousY);
            restoreVariable(variables, "z", previousZ);
        }

        private void surfaceScatter(Map<String, Integer> variables) {
            int localX1 = expr(take(), variables);
            int localZ1 = expr(take(), variables);
            int localX2 = expr(take(), variables);
            int localZ2 = expr(take(), variables);
            take("from");
            int fromY = expr(take(), variables);
            take("to");
            int toY = expr(take(), variables);
            take("chance");
            int numerator = expr(take(), variables);
            int denominator = expr(take(), variables);
            if (denominator <= 0) {
                throw new IllegalArgumentException("surface_scatter chance denominator must be positive");
            }
            if (numerator < 0 || numerator > denominator) {
                throw new IllegalArgumentException("surface_scatter chance numerator must be in [0, denominator]");
            }
            int seed = 0;
            int step = 1;
            while (peek("seed") || peek("step")) {
                if (peek("seed")) {
                    take("seed");
                    seed = expr(take(), variables);
                } else {
                    take("step");
                    step = expr(take(), variables);
                    if (step <= 0) {
                        throw new IllegalArgumentException("surface_scatter step must be positive");
                    }
                }
            }
            take("{");
            List<String> body = collectBody();
            Integer previousX = variables.get("x");
            Integer previousY = variables.get("y");
            Integer previousZ = variables.get("z");
            Integer previousSurfaceY = variables.get("surface_y");
            int minX = Math.min(localX1, localX2);
            int maxX = Math.max(localX1, localX2);
            int minZ = Math.min(localZ1, localZ2);
            int maxZ = Math.max(localZ1, localZ2);
            for (int localZ = minZ; localZ <= maxZ; localZ += step) {
                for (int localX = minX; localX <= maxX; localX += step) {
                    Point worldColumn = worldPoint(localX, localZ);
                    Integer surfaceY = surfaceY(worldColumn.x, worldColumn.z, worldY(fromY), worldY(toY));
                    if (surfaceY == null || surfaceY + 1 >= world.spec().maxYExclusive()) {
                        continue;
                    }
                    if (Math.floorMod(mix(worldColumn.x, surfaceY, worldColumn.z, seed), denominator) < numerator) {
                        int localSurfaceY = surfaceY - originY;
                        int localPlaceY = localSurfaceY + 1;
                        variables.put("x", localX);
                        variables.put("y", localPlaceY);
                        variables.put("z", localZ);
                        variables.put("surface_y", localSurfaceY);
                        child(body, localX, localPlaceY, localZ, Rotation.NONE).parseStatements(variables);
                    }
                }
            }
            restoreVariable(variables, "x", previousX);
            restoreVariable(variables, "y", previousY);
            restoreVariable(variables, "z", previousZ);
            restoreVariable(variables, "surface_y", previousSurfaceY);
        }

        private Integer surfaceY(int x, int z, int y1, int y2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            world.spec().checkY(minY);
            world.spec().checkY(maxY);
            for (int y = maxY; y >= minY; y--) {
                if (!BlockState.AIR.equals(world.getBlock(x, y, z))) {
                    return y;
                }
            }
            return null;
        }

        private void terrain(Map<String, Integer> variables) {
            int localX1 = expr(take(), variables);
            int localZ1 = expr(take(), variables);
            int localX2 = expr(take(), variables);
            int localZ2 = expr(take(), variables);
            take("from");
            int fromY = worldY(expr(take(), variables));
            take("base");
            int baseY = worldY(expr(take(), variables));
            take("amplitude");
            int amplitude = expr(take(), variables);
            if (amplitude < 0) {
                throw new IllegalArgumentException("terrain amplitude must be non-negative");
            }
            int seed = 0;
            if (peek("seed")) {
                take("seed");
                seed = expr(take(), variables);
            }
            BlockState top = block();
            BlockState under = block();
            Point p1 = worldPoint(localX1, localZ1);
            Point p2 = worldPoint(localX2, localZ2);
            int minX = Math.min(p1.x, p2.x);
            int maxX = Math.max(p1.x, p2.x);
            int minZ = Math.min(p1.z, p2.z);
            int maxZ = Math.max(p1.z, p2.z);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int topY = baseY + Math.floorMod(mix(x, baseY, z, seed), amplitude + 1);
                    if (fromY < topY) {
                        world.fill(x, fromY, z, x, topY - 1, z, under);
                    }
                    world.setBlock(x, topY, z, top);
                }
            }
        }

        private void smoothTerrain(Map<String, Integer> variables) {
            int localX1 = expr(take(), variables);
            int localZ1 = expr(take(), variables);
            int localX2 = expr(take(), variables);
            int localZ2 = expr(take(), variables);
            take("from");
            int fromY = worldY(expr(take(), variables));
            take("base");
            int baseY = worldY(expr(take(), variables));
            take("amplitude");
            int amplitude = expr(take(), variables);
            if (amplitude < 0) {
                throw new IllegalArgumentException("smooth_terrain amplitude must be non-negative");
            }
            int scale = 16;
            int octaves = 1;
            int seed = 0;
            while (peek("scale") || peek("octaves") || peek("seed")) {
                if (peek("scale")) {
                    take("scale");
                    scale = expr(take(), variables);
                    if (scale <= 0) {
                        throw new IllegalArgumentException("smooth_terrain scale must be positive");
                    }
                } else if (peek("octaves")) {
                    take("octaves");
                    octaves = expr(take(), variables);
                    if (octaves <= 0) {
                        throw new IllegalArgumentException("smooth_terrain octaves must be positive");
                    }
                } else {
                    take("seed");
                    seed = expr(take(), variables);
                }
            }
            BlockState top = block();
            BlockState under = block();
            Point p1 = worldPoint(localX1, localZ1);
            Point p2 = worldPoint(localX2, localZ2);
            int minX = Math.min(p1.x, p2.x);
            int maxX = Math.max(p1.x, p2.x);
            int minZ = Math.min(p1.z, p2.z);
            int maxZ = Math.max(p1.z, p2.z);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int topY = baseY + Math.round((float) (smoothNoise(x, z, seed, scale, octaves) * amplitude));
                    if (fromY < topY) {
                        world.fill(x, fromY, z, x, topY - 1, z, under);
                    }
                    world.setBlock(x, topY, z, top);
                }
            }
        }

        private void restoreVariable(Map<String, Integer> variables, String name, Integer previous) {
            if (previous == null) {
                variables.remove(name);
            } else {
                variables.put(name, previous);
            }
        }

        private List<String> collectBody() {
            List<String> body = new ArrayList<>();
            int depth = 1;
            while (!tokens.isEmpty() && depth > 0) {
                String token = take();
                if ("{".equals(token)) {
                    depth++;
                } else if ("}".equals(token)) {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                body.add(token);
            }
            if (depth != 0) {
                throw new IllegalArgumentException("Unclosed block body");
            }
            return body;
        }

        private BlockState block() {
            return block(0);
        }

        private BlockState block(int localQuarterTurns) {
            String token = take();
            BlockState state;
            if (token.startsWith("$")) {
                String name = variableName(token);
                state = blockAliases.get(name);
                if (state == null) {
                    throw new IllegalArgumentException("Unknown block alias: $" + name);
                }
            } else {
                state = BlockState.parse(token);
            }
            return state.rotatedY(rotation.quarters + localQuarterTurns);
        }

        private String biome() {
            String token = take();
            if (!token.startsWith("$")) {
                return ResourceId.normalize(token);
            }
            String name = variableName(token);
            String biome = biomeAliases.get(name);
            if (biome == null) {
                throw new IllegalArgumentException("Unknown biome alias: $" + name);
            }
            return biome;
        }

        private void fillPalette(int x1, int y1, int z1, int x2, int y2, int z2, String paletteName, int seed) {
            BlockPalette palette = blockPalettes.get(paletteName);
            if (palette == null) {
                throw new IllegalArgumentException("Unknown block palette: " + paletteName);
            }
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        world.setBlock(x, y, z, palette.pick(x, y, z, seed).rotatedY(rotation.quarters));
                    }
                }
            }
        }

        private void replacePalette(int x1, int y1, int z1, int x2, int y2, int z2,
                                    BlockState target, String paletteName, int seed) {
            BlockPalette palette = blockPalettes.get(paletteName);
            if (palette == null) {
                throw new IllegalArgumentException("Unknown block palette: " + paletteName);
            }
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        if (target.equals(world.getBlock(x, y, z))) {
                            world.setBlock(x, y, z, palette.pick(x, y, z, seed).rotatedY(rotation.quarters));
                        }
                    }
                }
            }
        }

        private Parser child(List<String> body, int offsetX, int offsetY, int offsetZ, Rotation childRotation) {
            return child(body, offsetX, offsetY, offsetZ, childRotation, blockAliases, biomeAliases);
        }

        private Parser child(List<String> body, int offsetX, int offsetY, int offsetZ, Rotation childRotation,
            Map<String, BlockState> childBlockAliases, Map<String, String> childBiomeAliases) {
            Point point = rotate(offsetX, offsetZ, rotation);
            return new Parser(new ListTokenStream(body), world, templates, patternTemplates,
                    childBlockAliases, blockPalettes, childBiomeAliases,
                    originX + point.x, originY + offsetY, originZ + point.z,
                    rotation.plus(childRotation), baseDir, includeStack);
        }

        private int worldY(int y) {
            return originY + y;
        }

        private double worldY(double y) {
            return originY + y;
        }

        private Point worldPoint(int x, int z) {
            Point point = rotate(x, z, rotation);
            return new Point(originX + point.x, originZ + point.z);
        }

        private DoublePoint worldDoublePoint(double x, double z) {
            DoublePoint point = rotate(x, z, rotation);
            return new DoublePoint(originX + point.x, originZ + point.z);
        }

        private Rotation optionalRotation(Map<String, Integer> variables) {
            if (!peek("rotate")) {
                return Rotation.NONE;
            }
            take("rotate");
            return Rotation.fromDegrees(expr(take(), variables));
        }

        private Scope optionalScope(Map<String, Integer> variables) {
            Map<String, Integer> scopedVariables = new HashMap<>(variables);
            Map<String, BlockState> scopedBlockAliases = new HashMap<>(blockAliases);
            Map<String, String> scopedBiomeAliases = new HashMap<>(biomeAliases);
            if (!peek("with")) {
                return new Scope(scopedVariables, scopedBlockAliases, scopedBiomeAliases);
            }
            take("with");
            take("{");
            while (!tokens.isEmpty() && !peek("}")) {
                String command = take();
                switch (command) {
                    case "let" -> {
                        String name = variableName(take());
                        take("=");
                        scopedVariables.put(name, expr(take(), scopedVariables));
                    }
                    case "block" -> {
                        String name = variableName(take());
                        take("=");
                        blockAlias(scopedBlockAliases, name);
                    }
                    case "biome_alias" -> {
                        String name = variableName(take());
                        take("=");
                        biomeAlias(scopedBiomeAliases, name);
                    }
                    default -> throw new IllegalArgumentException("Unsupported with override: " + command);
                }
            }
            take("}");
            return new Scope(scopedVariables, scopedBlockAliases, scopedBiomeAliases);
        }

        private int expr(String token, Map<String, Integer> variables) {
            return new Expression(token, variables).parse();
        }

        private double doubleExpr(String token, Map<String, Integer> variables) {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return expr(token, variables);
        }

        private String variableName(String token) {
            return token.startsWith("$") ? token.substring(1) : token;
        }

        private boolean peek(String expected) {
            return expected.equals(tokens.peek());
        }

        private String take() {
            String token = tokens.poll();
            if (token == null) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            return token;
        }

        private void take(String expected) {
            String actual = take();
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("Expected '" + expected + "' but found '" + actual + "'");
            }
        }
    }

    private static final class Expression {
        private final String text;
        private final Map<String, Integer> variables;
        private int index;

        Expression(String text, Map<String, Integer> variables) {
            this.text = text;
            this.variables = variables;
        }

        int parse() {
            int value = addSub();
            if (index != text.length()) {
                throw new IllegalArgumentException("Invalid expression: " + text);
            }
            return value;
        }

        private int addSub() {
            int value = mulDiv();
            while (index < text.length()) {
                char op = text.charAt(index);
                if (op != '+' && op != '-') {
                    break;
                }
                index++;
                int rhs = mulDiv();
                value = op == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private int mulDiv() {
            int value = atom();
            while (index < text.length()) {
                char op = text.charAt(index);
                if (op != '*' && op != '/' && op != '%') {
                    break;
                }
                index++;
                int rhs = atom();
                value = switch (op) {
                    case '*' -> value * rhs;
                    case '/' -> value / rhs;
                    case '%' -> value % rhs;
                    default -> throw new IllegalStateException("unreachable");
                };
            }
            return value;
        }

        private int atom() {
            if (index >= text.length()) {
                throw new IllegalArgumentException("Invalid expression: " + text);
            }
            char c = text.charAt(index);
            if (c == '(') {
                index++;
                int value = addSub();
                if (index >= text.length() || text.charAt(index) != ')') {
                    throw new IllegalArgumentException("Unclosed expression: " + text);
                }
                index++;
                return value;
            }
            if (c == '$') {
                index++;
                int start = index;
                while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
                    index++;
                }
                String name = text.substring(start, index);
                Integer value = variables.get(name);
                if (value == null) {
                    throw new IllegalArgumentException("Unknown variable: $" + name);
                }
                return value;
            }
            int sign = 1;
            if (c == '-') {
                sign = -1;
                index++;
            }
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid expression: " + text);
            }
            return sign * Integer.parseInt(text.substring(start, index));
        }
    }

    private record Point(int x, int z) {
    }

    private record DoublePoint(double x, double z) {
    }

    private record Scope(Map<String, Integer> variables, Map<String, BlockState> blockAliases,
                         Map<String, String> biomeAliases) {
    }

    private record GroupBody(List<String> body, int originX, int originY, int originZ) {
        GroupBody {
            body = List.copyOf(body);
        }
    }

    private record Template(List<String> parameters, List<String> body) {
        Template {
            parameters = List.copyOf(parameters);
            body = List.copyOf(body);
        }
    }

    private record WeightedBody(int weight, List<String> body) {
        WeightedBody {
            body = List.copyOf(body);
        }
    }

    private record WeightedBlock(BlockState state, int weight) {
    }

    private record BlockPalette(List<WeightedBlock> blocks, int totalWeight) {
        BlockPalette(List<WeightedBlock> blocks) {
            this(List.copyOf(blocks), blocks.stream().mapToInt(WeightedBlock::weight).sum());
        }

        BlockState pick(int x, int y, int z, int seed) {
            int slot = Math.floorMod(mix(x, y, z, seed), totalWeight);
            int cursor = 0;
            for (WeightedBlock block : blocks) {
                cursor += block.weight();
                if (slot < cursor) {
                    return block.state();
                }
            }
            return blocks.get(blocks.size() - 1).state();
        }

        private static int mix(int x, int y, int z, int seed) {
            return DslParser.mix(x, y, z, seed);
        }
    }

    private static int mix(int x, int y, int z, int seed) {
        int hash = seed ^ 0x7f4a7c15;
        hash = hash * 0x45d9f3b + x;
        hash = hash * 0x45d9f3b + y;
        hash = hash * 0x45d9f3b + z;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return hash;
    }

    private static double smoothNoise(int x, int z, int seed, int scale, int octaves) {
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double total = 0.0;
        double weight = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            total += valueNoise(x * frequency, z * frequency, seed + octave * 1013) * amplitude;
            weight += amplitude;
            frequency *= 2.0;
            amplitude *= 0.5;
        }
        return total / weight;
    }

    private static double valueNoise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);
        double north = lerp(latticeNoise(x0, z0, seed), latticeNoise(x0 + 1, z0, seed), tx);
        double south = lerp(latticeNoise(x0, z0 + 1, seed), latticeNoise(x0 + 1, z0 + 1, seed), tx);
        return lerp(north, south, tz);
    }

    private static double latticeNoise(int x, int z, int seed) {
        return Integer.toUnsignedLong(mix(x, 0, z, seed)) / (double) 0x1_0000_0000L;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }

    private static Point rotate(int x, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new Point(x, z);
            case CW_90 -> new Point(-z, x);
            case CW_180 -> new Point(-x, -z);
            case CW_270 -> new Point(z, -x);
        };
    }

    private static DoublePoint rotate(double x, double z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new DoublePoint(x, z);
            case CW_90 -> new DoublePoint(-z, x);
            case CW_180 -> new DoublePoint(-x, -z);
            case CW_270 -> new DoublePoint(z, -x);
        };
    }

    private enum WallFacing {
        NORTH(0, true),
        EAST(1, false),
        SOUTH(2, true),
        WEST(3, false);

        private final int quarterTurns;
        private final boolean spanX;

        WallFacing(int quarterTurns, boolean spanX) {
            this.quarterTurns = quarterTurns;
            this.spanX = spanX;
        }

        boolean spansX() {
            return spanX;
        }

        boolean spansZ() {
            return !spanX;
        }

        static WallFacing fromToken(String token) {
            return switch (token) {
                case "north" -> NORTH;
                case "east" -> EAST;
                case "south" -> SOUTH;
                case "west" -> WEST;
                default -> throw new IllegalArgumentException("wall facing must be north, east, south, or west");
            };
        }
    }

    private enum Rotation {
        NONE(0),
        CW_90(1),
        CW_180(2),
        CW_270(3);

        private final int quarters;

        Rotation(int quarters) {
            this.quarters = quarters;
        }

        Rotation plus(Rotation other) {
            return fromQuarters(quarters + other.quarters);
        }

        static Rotation fromDegrees(int degrees) {
            if (degrees % 90 != 0) {
                throw new IllegalArgumentException("rotate value must be one of 0, 90, 180, 270");
            }
            return fromQuarters(degrees / 90);
        }

        static Rotation fromQuarters(int quarters) {
            return switch (Math.floorMod(quarters, 4)) {
                case 0 -> NONE;
                case 1 -> CW_90;
                case 2 -> CW_180;
                case 3 -> CW_270;
                default -> throw new IllegalStateException("unreachable");
            };
        }
    }
}
