package com.anviltf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public final class MutableWorld {
    private final WorldSpec spec;
    private final String dimension;

    public MutableWorld() {
        this(new WorldSpec(), WorldSpec.OVERWORLD);
    }

    private MutableWorld(WorldSpec spec, String dimension) {
        this.spec = spec;
        this.dimension = ResourceId.normalize(dimension);
        this.spec.dimension(this.dimension);
    }

    public MutableWorld inDimension(String dimension) {
        return new MutableWorld(spec, dimension);
    }

    public WorldSpec.DimensionSpec dimensionSpec() {
        return spec.dimension(dimension);
    }

    public WorldSpec spec() {
        return spec;
    }

    public void setBlock(int x, int y, int z, BlockState block) {
        dimensionSpec().checkY(y);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        spec.chunk(dimension, new ChunkPos(chunkX, chunkZ)).set(localX, y, localZ, block);
    }

    public BlockState getBlock(int x, int y, int z) {
        dimensionSpec().checkY(y);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        ChunkBlocks chunk = dimensionSpec().chunks().get(new ChunkPos(chunkX, chunkZ));
        if (chunk == null) {
            return BlockState.AIR;
        }
        return chunk.get(localX, y, localZ);
    }

    public void setBlockEntity(int x, int y, int z, String id, Map<String, NbtValue> data) {
        spec.blockEntity(dimension, new BlockEntity(x, y, z, id, data));
    }

    public void mergeBlockEntityData(int x, int y, int z, String id, Map<String, NbtValue> data) {
        BlockEntity existing = getBlockEntity(x, y, z);
        Map<String, NbtValue> merged = new HashMap<>();
        if (existing != null) {
            merged.putAll(existing.data());
            id = existing.id();
        }
        for (Map.Entry<String, NbtValue> entry : data.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), MutableWorld::mergeNbtValue);
        }
        setBlockEntity(x, y, z, id, merged);
    }

    private static NbtValue mergeNbtValue(NbtValue existing, NbtValue incoming) {
        if (existing instanceof NbtValue.CompoundValue existingCompound
                && incoming instanceof NbtValue.CompoundValue incomingCompound) {
            Map<String, NbtValue> values = new HashMap<>(existingCompound.values());
            for (Map.Entry<String, NbtValue> entry : incomingCompound.values().entrySet()) {
                values.merge(entry.getKey(), entry.getValue(), MutableWorld::mergeNbtValue);
            }
            return NbtValue.compoundValue(values);
        }
        if (existing instanceof NbtValue.ListValue existingList
                && incoming instanceof NbtValue.ListValue incomingList
                && existingList.elementType() == incomingList.elementType()) {
            List<NbtValue> values = new ArrayList<>(existingList.values());
            values.addAll(incomingList.values());
            return NbtValue.listValue(existingList.elementType(), values);
        }
        return incoming;
    }

    public BlockEntity getBlockEntity(int x, int y, int z) {
        dimensionSpec().checkY(y);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        Map<WorldSpec.BlockPos, BlockEntity> chunkEntities =
                dimensionSpec().blockEntities().get(new ChunkPos(chunkX, chunkZ));
        if (chunkEntities == null) {
            return null;
        }
        return chunkEntities.get(new WorldSpec.BlockPos(x, y, z));
    }

    public void addEntity(double x, double y, double z, String id, Map<String, NbtValue> data) {
        spec.entity(dimension, new Entity(x, y, z, id, data));
    }

    public void setBiome(int x, int y, int z, String biome) {
        dimensionSpec().checkY(y);
        biome = ResourceId.normalize(biome);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int localQuartX = Math.floorMod(x, 16) / 4;
        int localQuartZ = Math.floorMod(z, 16) / 4;
        spec.biomeChunk(dimension, new ChunkPos(chunkX, chunkZ)).set(localQuartX, y, localQuartZ, biome);
    }

    public String getBiome(int x, int y, int z) {
        dimensionSpec().checkY(y);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        int localQuartX = Math.floorMod(x, 16) / 4;
        int localQuartZ = Math.floorMod(z, 16) / 4;
        ChunkBiomes chunk = dimensionSpec().biomes().get(new ChunkPos(chunkX, chunkZ));
        if (chunk == null) {
            return dimensionSpec().defaultBiome();
        }
        return chunk.get(localQuartX, y, localQuartZ);
    }

    public void fillBiome(int x1, int y1, int z1, int x2, int y2, int z2, String biome) {
        Bounds bounds = Bounds.of(x1, y1, z1, x2, y2, z2);
        for (int y = Math.floorDiv(bounds.minY, 4) * 4; y <= bounds.maxY; y += 4) {
            for (int z = Math.floorDiv(bounds.minZ, 4) * 4; z <= bounds.maxZ; z += 4) {
                for (int x = Math.floorDiv(bounds.minX, 4) * 4; x <= bounds.maxX; x += 4) {
                    setBiome(x, y, z, biome);
                }
            }
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState block) {
        Bounds bounds = Bounds.of(x1, y1, z1, x2, y2, z2);
        dimensionSpec().checkY(bounds.minY);
        dimensionSpec().checkY(bounds.maxY);
        int minChunkX = Math.floorDiv(bounds.minX, 16);
        int maxChunkX = Math.floorDiv(bounds.maxX, 16);
        int minChunkZ = Math.floorDiv(bounds.minZ, 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ, 16);

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int chunkWorldX = chunkX * 16;
                int chunkWorldZ = chunkZ * 16;
                int localMinX = Math.max(0, bounds.minX - chunkWorldX);
                int localMaxX = Math.min(15, bounds.maxX - chunkWorldX);
                int localMinZ = Math.max(0, bounds.minZ - chunkWorldZ);
                int localMaxZ = Math.min(15, bounds.maxZ - chunkWorldZ);
                ChunkBlocks chunk = spec.chunk(dimension, new ChunkPos(chunkX, chunkZ));

                for (int y = bounds.minY; y <= bounds.maxY; y++) {
                    if (localMinX == 0 && localMaxX == 15 && localMinZ == 0 && localMaxZ == 15) {
                        chunk.setLayer(y, block);
                        continue;
                    }
                    for (int z = localMinZ; z <= localMaxZ; z++) {
                        for (int x = localMinX; x <= localMaxX; x++) {
                            chunk.set(x, y, z, block);
                        }
                    }
                }
            }
        }
    }

    public void replace(int x1, int y1, int z1, int x2, int y2, int z2,
                        BlockState target, BlockState replacement) {
        Bounds bounds = Bounds.of(x1, y1, z1, x2, y2, z2);
        dimensionSpec().checkY(bounds.minY);
        dimensionSpec().checkY(bounds.maxY);
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                for (int x = bounds.minX; x <= bounds.maxX; x++) {
                    if (target.equals(getBlock(x, y, z))) {
                        setBlock(x, y, z, replacement);
                    }
                }
            }
        }
    }

    public void line(int x1, int y1, int z1, int x2, int y2, int z2, BlockState block) {
        dimensionSpec().checkY(y1);
        dimensionSpec().checkY(y2);
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) {
            setBlock(x1, y1, z1, block);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round(dx * (i / (float) steps));
            int y = y1 + Math.round(dy * (i / (float) steps));
            int z = z1 + Math.round(dz * (i / (float) steps));
            setBlock(x, y, z, block);
        }
    }

    public void copy(int x1, int y1, int z1, int x2, int y2, int z2,
                     int toX, int toY, int toZ, int quarterTurns, boolean skipAir) {
        copy(x1, y1, z1, x2, y2, z2, toX, toY, toZ, quarterTurns, skipAir, false, false, false, false);
    }

    public void copy(int x1, int y1, int z1, int x2, int y2, int z2,
                     int toX, int toY, int toZ, int quarterTurns, boolean skipAir, boolean includeEntities) {
        copy(x1, y1, z1, x2, y2, z2, toX, toY, toZ, quarterTurns, skipAir, includeEntities, false, false, false);
    }

    public void copy(int x1, int y1, int z1, int x2, int y2, int z2,
                     int toX, int toY, int toZ, int quarterTurns,
                     boolean skipAir, boolean includeEntities, boolean includeBiomes) {
        copy(x1, y1, z1, x2, y2, z2, toX, toY, toZ, quarterTurns,
                skipAir, includeEntities, includeBiomes, false, false);
    }

    public void copy(int x1, int y1, int z1, int x2, int y2, int z2,
                     int toX, int toY, int toZ, int quarterTurns,
                     boolean skipAir, boolean includeEntities, boolean includeBiomes,
                     boolean mirrorX, boolean mirrorZ) {
        Bounds source = Bounds.of(x1, y1, z1, x2, y2, z2);
        dimensionSpec().checkY(source.minY);
        dimensionSpec().checkY(source.maxY);
        dimensionSpec().checkY(toY);
        dimensionSpec().checkY(toY + source.maxY - source.minY);
        int widthX = source.maxX - source.minX;
        int widthZ = source.maxZ - source.minZ;

        List<CopiedBlock> blocks = new ArrayList<>();
        for (int y = source.minY; y <= source.maxY; y++) {
            for (int z = source.minZ; z <= source.maxZ; z++) {
                for (int x = source.minX; x <= source.maxX; x++) {
                    BlockState block = getBlock(x, y, z);
                    if (!skipAir || !BlockState.AIR.equals(block)) {
                        blocks.add(new CopiedBlock(x - source.minX, y - source.minY,
                                z - source.minZ, block));
                    }
                }
            }
        }

        List<CopiedBlockEntity> blockEntities = new ArrayList<>();
        for (Map<WorldSpec.BlockPos, BlockEntity> chunkEntities : dimensionSpec().blockEntities().values()) {
            for (BlockEntity entity : chunkEntities.values()) {
                if (source.contains(entity.x(), entity.y(), entity.z())
                        && (!skipAir || !BlockState.AIR.equals(getBlock(entity.x(), entity.y(), entity.z())))) {
                    blockEntities.add(new CopiedBlockEntity(entity.x() - source.minX,
                            entity.y() - source.minY, entity.z() - source.minZ,
                            entity.id(), entity.data()));
                }
            }
        }

        List<CopiedEntity> entities = new ArrayList<>();
        if (includeEntities) {
            for (List<Entity> chunkEntities : dimensionSpec().entities().values()) {
                for (Entity entity : chunkEntities) {
                    if (source.contains(entity.blockX(), entity.blockY(), entity.blockZ())) {
                        entities.add(new CopiedEntity(entity.x() - source.minX,
                                entity.y() - source.minY, entity.z() - source.minZ,
                                entity.id(), copiedEntityData(entity.data(), quarterTurns, mirrorX, mirrorZ)));
                    }
                }
            }
        }

        List<CopiedBiome> biomes = new ArrayList<>();
        if (includeBiomes) {
            for (int y = source.minY; y <= source.maxY; y += 4) {
                for (int z = source.minZ; z <= source.maxZ; z += 4) {
                    for (int x = source.minX; x <= source.maxX; x += 4) {
                        biomes.add(new CopiedBiome(x - source.minX, y - source.minY,
                                z - source.minZ, getBiome(x, y, z)));
                    }
                }
            }
        }

        for (CopiedBlock copied : blocks) {
            Point mirrored = mirror(copied.x(), copied.z(), widthX, widthZ, mirrorX, mirrorZ);
            Point offset = rotate(mirrored.x(), mirrored.z(), quarterTurns);
            int x = toX + offset.x;
            int y = toY + copied.y();
            int z = toZ + offset.z;
            spec.removeBlockEntity(dimension, x, y, z);
            setBlock(x, y, z, transform(copied.state(), quarterTurns, mirrorX, mirrorZ));
        }
        for (CopiedBlockEntity copied : blockEntities) {
            Point mirrored = mirror(copied.x(), copied.z(), widthX, widthZ, mirrorX, mirrorZ);
            Point offset = rotate(mirrored.x(), mirrored.z(), quarterTurns);
            setBlockEntity(toX + offset.x, toY + copied.y(), toZ + offset.z,
                    copied.id(), copied.data());
        }
        for (CopiedEntity copied : entities) {
            DoublePoint mirrored = mirror(copied.x(), copied.z(), widthX, widthZ, mirrorX, mirrorZ);
            DoublePoint offset = rotate(mirrored.x(), mirrored.z(), quarterTurns);
            addEntity(toX + offset.x(), toY + copied.y(), toZ + offset.z(), copied.id(), copied.data());
        }
        for (CopiedBiome copied : biomes) {
            Point mirrored = mirror(copied.x(), copied.z(), widthX, widthZ, mirrorX, mirrorZ);
            Point offset = rotate(mirrored.x(), mirrored.z(), quarterTurns);
            setBiome(toX + offset.x, toY + copied.y(), toZ + offset.z, copied.biome());
        }
    }

    public void hollowBox(int x1, int y1, int z1, int x2, int y2, int z2, BlockState shell, BlockState inside) {
        Bounds bounds = Bounds.of(x1, y1, z1, x2, y2, z2);
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                for (int x = bounds.minX; x <= bounds.maxX; x++) {
                    boolean boundary = x == bounds.minX || x == bounds.maxX
                            || y == bounds.minY || y == bounds.maxY
                            || z == bounds.minZ || z == bounds.maxZ;
                    setBlock(x, y, z, boundary ? shell : inside);
                }
            }
        }
    }

    public void sphere(int centerX, int centerY, int centerZ, int radius, BlockState block) {
        if (radius < 0) {
            throw new IllegalArgumentException("sphere radius must be non-negative");
        }
        int radiusSquared = radius * radius;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            dimensionSpec().checkY(y);
            int dy = y - centerY;
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    int dx = x - centerX;
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        setBlock(x, y, z, block);
                    }
                }
            }
        }
    }

    public void hollowSphere(int centerX, int centerY, int centerZ, int radius, BlockState shell, BlockState inside) {
        if (radius < 0) {
            throw new IllegalArgumentException("hollow_sphere radius must be non-negative");
        }
        int outer = radius * radius;
        int innerRadius = radius - 1;
        int inner = innerRadius < 0 ? -1 : innerRadius * innerRadius;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            dimensionSpec().checkY(y);
            int dy = y - centerY;
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    int dx = x - centerX;
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= outer) {
                        setBlock(x, y, z, distance <= inner ? inside : shell);
                    }
                }
            }
        }
    }

    public void cylinder(int centerX, int y1, int centerZ, int radius, int y2, BlockState block) {
        if (radius < 0) {
            throw new IllegalArgumentException("cylinder radius must be non-negative");
        }
        Bounds bounds = Bounds.of(centerX - radius, y1, centerZ - radius,
                centerX + radius, y2, centerZ + radius);
        int radiusSquared = radius * radius;
        dimensionSpec().checkY(bounds.minY);
        dimensionSpec().checkY(bounds.maxY);
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                int remaining = radiusSquared - dz * dz;
                if (remaining < 0) {
                    continue;
                }
                int span = floorSqrt(remaining);
                fill(centerX - span, y, z, centerX + span, y, z, block);
            }
        }
    }

    public void hollowCylinder(int centerX, int y1, int centerZ, int radius, int y2,
                               BlockState shell, BlockState inside) {
        if (radius < 0) {
            throw new IllegalArgumentException("hollow_cylinder radius must be non-negative");
        }
        Bounds bounds = Bounds.of(centerX - radius, y1, centerZ - radius,
                centerX + radius, y2, centerZ + radius);
        int outer = radius * radius;
        int innerRadius = radius - 1;
        int inner = innerRadius < 0 ? -1 : innerRadius * innerRadius;
        dimensionSpec().checkY(bounds.minY);
        dimensionSpec().checkY(bounds.maxY);
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    int dx = x - centerX;
                    int distance = dx * dx + dz * dz;
                    if (distance <= outer) {
                        boolean cap = y == bounds.minY || y == bounds.maxY;
                        setBlock(x, y, z, !cap && distance <= inner ? inside : shell);
                    }
                }
            }
        }
    }

    private static int floorSqrt(int value) {
        return (int) Math.floor(Math.sqrt(value));
    }

    private static Point rotate(int x, int z, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new Point(x, z);
            case 1 -> new Point(-z, x);
            case 2 -> new Point(-x, -z);
            case 3 -> new Point(z, -x);
            default -> throw new IllegalStateException("unreachable");
        };
    }

    private static Point mirror(int x, int z, int widthX, int widthZ, boolean mirrorX, boolean mirrorZ) {
        return new Point(mirrorX ? widthX - x : x, mirrorZ ? widthZ - z : z);
    }

    private static DoublePoint rotate(double x, double z, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new DoublePoint(x, z);
            case 1 -> new DoublePoint(-z, x);
            case 2 -> new DoublePoint(-x, -z);
            case 3 -> new DoublePoint(z, -x);
            default -> throw new IllegalStateException("unreachable");
        };
    }

    private static DoublePoint mirror(double x, double z, int widthX, int widthZ, boolean mirrorX, boolean mirrorZ) {
        return new DoublePoint(mirrorX ? widthX + 1.0 - x : x, mirrorZ ? widthZ + 1.0 - z : z);
    }

    private static BlockState transform(BlockState state, int quarterTurns, boolean mirrorX, boolean mirrorZ) {
        BlockState transformed = state;
        if (mirrorX) {
            transformed = transformed.mirroredX();
        }
        if (mirrorZ) {
            transformed = transformed.mirroredZ();
        }
        return transformed.rotatedY(quarterTurns);
    }

    private static Map<String, NbtValue> copiedEntityData(Map<String, NbtValue> source, int quarterTurns,
                                                         boolean mirrorX, boolean mirrorZ) {
        Map<String, NbtValue> data = new HashMap<>(source);
        data.remove("Pos");
        data.remove("UUID");
        data.compute("Rotation", (key, value) -> transformedEntityRotation(value, quarterTurns, mirrorX, mirrorZ));
        return data;
    }

    private static NbtValue transformedEntityRotation(NbtValue value, int quarterTurns,
                                                      boolean mirrorX, boolean mirrorZ) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0 && !mirrorX && !mirrorZ) {
            return value;
        }
        float yaw = 0.0f;
        float pitch = 0.0f;
        if (value instanceof NbtValue.ListValue list
                && list.elementType() == com.anviltf.anvil.NbtWriter.TAG_FLOAT
                && list.values().size() >= 2
                && list.values().get(0) instanceof NbtValue.FloatValue sourceYaw
                && list.values().get(1) instanceof NbtValue.FloatValue sourcePitch) {
            yaw = sourceYaw.value();
            pitch = sourcePitch.value();
        }
        if (mirrorX) {
            yaw = 180.0f - yaw;
        }
        if (mirrorZ) {
            yaw = -yaw;
        }
        yaw += turns * 90.0f;
        return NbtValue.listValue(com.anviltf.anvil.NbtWriter.TAG_FLOAT,
                List.of(NbtValue.floatValue(yaw), NbtValue.floatValue(pitch)));
    }

    public static final class ChunkBlocks {
        private final int minY;
        private final Map<Integer, BlockState> blocks = new TreeMap<>();
        private final Map<Integer, BlockState> layers = new TreeMap<>();

        ChunkBlocks(int minY) {
            this.minY = minY;
        }

        public void set(int localX, int y, int localZ, BlockState block) {
            int key = key(localX, y, localZ);
            BlockState layer = layers.get(y);
            if (BlockState.AIR.equals(block) && layer == null) {
                blocks.remove(key);
            } else if (block.equals(layer)) {
                blocks.remove(key);
            } else {
                blocks.put(key, block);
            }
        }

        public void setLayer(int y, BlockState block) {
            if (BlockState.AIR.equals(block)) {
                layers.remove(y);
            } else {
                layers.put(y, block);
            }
            int firstKey = key(0, y, 0);
            int lastKey = key(15, y, 15);
            blocks.entrySet().removeIf(entry -> entry.getKey() >= firstKey
                    && entry.getKey() <= lastKey
                    && (BlockState.AIR.equals(block) || entry.getValue().equals(block)));
        }

        public BlockState get(int localX, int y, int localZ) {
            BlockState block = blocks.get(key(localX, y, localZ));
            if (block != null) {
                return block;
            }
            return layers.getOrDefault(y, BlockState.AIR);
        }

        public boolean isEmpty() {
            return blocks.isEmpty() && layers.isEmpty();
        }

        public int explicitBlockCount() {
            return blocks.size();
        }

        public int layerCount() {
            return layers.size();
        }

        public int nonAirBlockCount() {
            int count = layers.size() * 16 * 16;
            for (Map.Entry<Integer, BlockState> entry : blocks.entrySet()) {
                int y = yFromKey(entry.getKey());
                boolean hasLayer = layers.containsKey(y);
                boolean isAir = BlockState.AIR.equals(entry.getValue());
                if (hasLayer && isAir) {
                    count--;
                } else if (!hasLayer && !isAir) {
                    count++;
                }
            }
            return count;
        }

        public void collectBlockStates(java.util.Set<BlockState> target) {
            layers.values().stream()
                    .filter(state -> !BlockState.AIR.equals(state))
                    .forEach(target::add);
            blocks.values().stream()
                    .filter(state -> !BlockState.AIR.equals(state))
                    .forEach(target::add);
        }

        public void forEachLayer(BiConsumer<Integer, BlockState> consumer) {
            layers.forEach(consumer);
        }

        public void forEachExplicitBlock(BlockConsumer consumer) {
            for (Map.Entry<Integer, BlockState> entry : blocks.entrySet()) {
                int key = entry.getKey();
                consumer.accept(key & 15, yFromKey(key), (key >> 4) & 15, entry.getValue());
            }
        }

        private int key(int localX, int y, int localZ) {
            return ((y - minY) << 8) | (localZ << 4) | localX;
        }

        private int yFromKey(int key) {
            return (key >> 8) + minY;
        }
    }

    public static final class ChunkBiomes {
        private final int minY;
        private final String defaultBiome;
        private final Map<Integer, String> biomes = new TreeMap<>();

        ChunkBiomes(int minY, String defaultBiome) {
            this.minY = minY;
            this.defaultBiome = defaultBiome;
        }

        public void set(int localQuartX, int y, int localQuartZ, String biome) {
            biomes.put(key(localQuartX, y, localQuartZ), biome);
        }

        public String get(int localQuartX, int y, int localQuartZ) {
            return biomes.getOrDefault(key(localQuartX, y, localQuartZ), defaultBiome);
        }

        public int explicitBiomeCount() {
            return biomes.size();
        }

        public void forEachExplicitBiome(BiomeConsumer consumer) {
            for (Map.Entry<Integer, String> entry : biomes.entrySet()) {
                int key = entry.getKey();
                int quartY = key >> 4;
                consumer.accept(key & 3, minY + quartY * 4, (key >> 2) & 3, entry.getValue());
            }
        }

        private int key(int localQuartX, int y, int localQuartZ) {
            int quartY = Math.floorDiv(y - minY, 4);
            return (quartY << 4) | (localQuartZ << 2) | localQuartX;
        }
    }

    @FunctionalInterface
    public interface BlockConsumer {
        void accept(int localX, int y, int localZ, BlockState state);
    }

    @FunctionalInterface
    public interface BiomeConsumer {
        void accept(int localQuartX, int y, int localQuartZ, String biome);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Bounds(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    private record Point(int x, int z) {
    }

    private record DoublePoint(double x, double z) {
    }

    private record CopiedBlock(int x, int y, int z, BlockState state) {
    }

    private record CopiedBlockEntity(int x, int y, int z, String id, Map<String, NbtValue> data) {
    }

    private record CopiedEntity(double x, double y, double z, String id, Map<String, NbtValue> data) {
    }

    private record CopiedBiome(int x, int y, int z, String biome) {
    }
}
