package com.anviltf;

import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class WorldSpec {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    private String name = "anvil-tf world";
    private int dataVersion = 3465;
    private String versionName = null;
    private int spawnX = 0;
    private int spawnY = 80;
    private int spawnZ = 0;
    private long time = 0L;
    private long dayTime = 1000L;
    private int gameType = 1;
    private int difficulty = 2;
    private boolean hardcore = false;
    private boolean allowCommands = true;
    private boolean raining = false;
    private int rainTime = 0;
    private boolean thundering = false;
    private int thunderTime = 0;
    private int clearWeatherTime = 0;
    private WorldBorder worldBorder = WorldBorder.defaults();
    private final Map<String, String> gameRules = new TreeMap<>();
    private final Map<String, DimensionSpec> dimensions = new TreeMap<>();
    private final List<PassthroughPath> passthroughPaths = new ArrayList<>();

    public WorldSpec() {
        dimensions.put(OVERWORLD, new DimensionSpec(OVERWORLD, -64, 384, "minecraft:plains"));
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public void dataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String versionName() {
        return versionName;
    }

    public void versionName(String versionName) {
        this.versionName = versionName;
    }

    public int minY() {
        return overworld().minY();
    }

    public void minY(int minY) {
        overworld().minY(minY);
    }

    public int height() {
        return overworld().height();
    }

    public void height(int height) {
        overworld().height(height);
    }

    public int maxYExclusive() {
        return overworld().maxYExclusive();
    }

    public int minSectionY() {
        return overworld().minSectionY();
    }

    public int sectionCount() {
        return overworld().sectionCount();
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnY() {
        return spawnY;
    }

    public int spawnZ() {
        return spawnZ;
    }

    public void spawn(int x, int y, int z) {
        overworld().checkY(y);
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }

    public long time() {
        return time;
    }

    public void time(long time) {
        this.time = time;
    }

    public long dayTime() {
        return dayTime;
    }

    public void dayTime(long dayTime) {
        this.dayTime = dayTime;
    }

    public int gameType() {
        return gameType;
    }

    public void gameType(int gameType) {
        if (gameType < 0 || gameType > 3) {
            throw new IllegalArgumentException("game_mode must be survival, creative, adventure, spectator, or 0..3");
        }
        this.gameType = gameType;
    }

    public int difficulty() {
        return difficulty;
    }

    public void difficulty(int difficulty) {
        if (difficulty < 0 || difficulty > 3) {
            throw new IllegalArgumentException("difficulty must be peaceful, easy, normal, hard, or 0..3");
        }
        this.difficulty = difficulty;
    }

    public boolean hardcore() {
        return hardcore;
    }

    public void hardcore(boolean hardcore) {
        this.hardcore = hardcore;
    }

    public boolean allowCommands() {
        return allowCommands;
    }

    public void allowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
    }

    public String defaultBiome() {
        return overworld().defaultBiome();
    }

    public void defaultBiome(String defaultBiome) {
        overworld().defaultBiome(defaultBiome);
    }

    public boolean raining() {
        return raining;
    }

    public void raining(boolean raining) {
        this.raining = raining;
    }

    public int rainTime() {
        return rainTime;
    }

    public void rainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    public boolean thundering() {
        return thundering;
    }

    public void thundering(boolean thundering) {
        this.thundering = thundering;
    }

    public int thunderTime() {
        return thunderTime;
    }

    public void thunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    public int clearWeatherTime() {
        return clearWeatherTime;
    }

    public void clearWeatherTime(int clearWeatherTime) {
        this.clearWeatherTime = clearWeatherTime;
    }

    public WorldBorder worldBorder() {
        return worldBorder;
    }

    public void worldBorder(WorldBorder worldBorder) {
        this.worldBorder = worldBorder;
    }

    public Map<String, String> gameRules() {
        return Collections.unmodifiableMap(gameRules);
    }

    public void gameRule(String name, String value) {
        gameRules.put(name, value);
    }

    public Map<ChunkPos, MutableWorld.ChunkBlocks> chunks() {
        return overworld().chunks();
    }

    public Map<ChunkPos, MutableWorld.ChunkBiomes> biomes() {
        return overworld().biomes();
    }

    public Map<ChunkPos, Map<BlockPos, BlockEntity>> blockEntities() {
        return overworld().blockEntities();
    }

    public Map<ChunkPos, List<Entity>> entities() {
        return overworld().entities();
    }

    public Map<String, DimensionSpec> dimensions() {
        return Collections.unmodifiableMap(dimensions);
    }

    public DimensionSpec overworld() {
        return dimension(OVERWORLD);
    }

    public DimensionSpec dimension(String id) {
        String normalized = ResourceId.normalize(id);
        return dimensions.computeIfAbsent(normalized, key -> defaultDimension(key));
    }

    private DimensionSpec defaultDimension(String id) {
        if (NETHER.equals(id)) {
            return new DimensionSpec(id, 0, 256, "minecraft:nether_wastes");
        }
        if (END.equals(id)) {
            return new DimensionSpec(id, 0, 256, "minecraft:the_end");
        }
        return new DimensionSpec(id, -64, 384, "minecraft:plains");
    }

    public List<PassthroughPath> passthroughPaths() {
        return Collections.unmodifiableList(passthroughPaths);
    }

    public void passthroughPath(String target, Path source) {
        Path targetPath = Path.of(target).normalize();
        if (target.isBlank() || targetPath.isAbsolute() || targetPath.startsWith("..")) {
            throw new IllegalArgumentException("passthrough target must be a relative save path without '..': " + target);
        }
        passthroughPaths.add(new PassthroughPath(targetPath.toString(), source.toAbsolutePath().normalize()));
    }

    MutableWorld.ChunkBlocks chunk(ChunkPos pos) {
        return overworld().chunk(pos);
    }

    MutableWorld.ChunkBlocks chunk(String dimension, ChunkPos pos) {
        return dimension(dimension).chunk(pos);
    }

    MutableWorld.ChunkBiomes biomeChunk(ChunkPos pos) {
        return overworld().biomeChunk(pos);
    }

    MutableWorld.ChunkBiomes biomeChunk(String dimension, ChunkPos pos) {
        return dimension(dimension).biomeChunk(pos);
    }

    void blockEntity(BlockEntity entity) {
        overworld().blockEntity(entity);
    }

    void blockEntity(String dimension, BlockEntity entity) {
        dimension(dimension).blockEntity(entity);
    }

    void removeBlockEntity(int x, int y, int z) {
        overworld().removeBlockEntity(x, y, z);
    }

    void removeBlockEntity(String dimension, int x, int y, int z) {
        dimension(dimension).removeBlockEntity(x, y, z);
    }

    void entity(Entity entity) {
        overworld().entity(entity);
    }

    void entity(String dimension, Entity entity) {
        dimension(dimension).entity(entity);
    }

    public void checkY(int y) {
        overworld().checkY(y);
    }

    public record BlockPos(int x, int y, int z) implements Comparable<BlockPos> {
        @Override
        public int compareTo(BlockPos other) {
            int yCompare = Integer.compare(y, other.y);
            if (yCompare != 0) {
                return yCompare;
            }
            int zCompare = Integer.compare(z, other.z);
            return zCompare != 0 ? zCompare : Integer.compare(x, other.x);
        }
    }

    public record PassthroughPath(String target, Path source) {
    }

    public record WorldBorder(double centerX, double centerZ, double size, double sizeLerpTarget,
                              long sizeLerpTime, int warningBlocks, int warningTime,
                              double damageBuffer, double damagePerBlock) {
        public static WorldBorder defaults() {
            return new WorldBorder(0.0d, 0.0d, 5.9999968E7d, 5.9999968E7d,
                    0L, 5, 15, 5.0d, 0.2d);
        }
    }

    public static final class DimensionSpec {
        private final String id;
        private int minY;
        private int height;
        private String defaultBiome;
        private final Map<ChunkPos, MutableWorld.ChunkBlocks> chunks = new TreeMap<>();
        private final Map<ChunkPos, MutableWorld.ChunkBiomes> biomes = new TreeMap<>();
        private final Map<ChunkPos, Map<BlockPos, BlockEntity>> blockEntities = new TreeMap<>();
        private final Map<ChunkPos, List<Entity>> entities = new TreeMap<>();

        DimensionSpec(String id, int minY, int height, String defaultBiome) {
            this.id = id;
            minY(minY);
            height(height);
            defaultBiome(defaultBiome);
        }

        public String id() {
            return id;
        }

        public int minY() {
            return minY;
        }

        public void minY(int minY) {
            if (minY % 16 != 0) {
                throw new IllegalArgumentException("min_y must be a multiple of 16");
            }
            this.minY = minY;
        }

        public int height() {
            return height;
        }

        public void height(int height) {
            if (height <= 0 || height % 16 != 0) {
                throw new IllegalArgumentException("height must be positive and a multiple of 16");
            }
            this.height = height;
        }

        public int maxYExclusive() {
            return minY + height;
        }

        public int minSectionY() {
            return Math.floorDiv(minY, 16);
        }

        public int sectionCount() {
            return height / 16;
        }

        public String defaultBiome() {
            return defaultBiome;
        }

        public void defaultBiome(String defaultBiome) {
            this.defaultBiome = ResourceId.normalize(defaultBiome);
        }

        public Map<ChunkPos, MutableWorld.ChunkBlocks> chunks() {
            return Collections.unmodifiableMap(chunks);
        }

        public Map<ChunkPos, MutableWorld.ChunkBiomes> biomes() {
            return Collections.unmodifiableMap(biomes);
        }

        public Map<ChunkPos, Map<BlockPos, BlockEntity>> blockEntities() {
            return Collections.unmodifiableMap(blockEntities);
        }

        public Map<ChunkPos, List<Entity>> entities() {
            return Collections.unmodifiableMap(entities);
        }

        MutableWorld.ChunkBlocks chunk(ChunkPos pos) {
            return chunks.computeIfAbsent(pos, ignored -> new MutableWorld.ChunkBlocks(minY));
        }

        MutableWorld.ChunkBiomes biomeChunk(ChunkPos pos) {
            chunk(pos);
            return biomes.computeIfAbsent(pos, ignored -> new MutableWorld.ChunkBiomes(minY, defaultBiome));
        }

        void blockEntity(BlockEntity entity) {
            checkY(entity.y());
            int chunkX = Math.floorDiv(entity.x(), 16);
            int chunkZ = Math.floorDiv(entity.z(), 16);
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            chunk(chunk);
            blockEntities.computeIfAbsent(chunk, ignored -> new TreeMap<>())
                    .put(new BlockPos(entity.x(), entity.y(), entity.z()), entity);
        }

        void removeBlockEntity(int x, int y, int z) {
            checkY(y);
            int chunkX = Math.floorDiv(x, 16);
            int chunkZ = Math.floorDiv(z, 16);
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            Map<BlockPos, BlockEntity> chunkEntities = blockEntities.get(chunk);
            if (chunkEntities == null) {
                return;
            }
            chunkEntities.remove(new BlockPos(x, y, z));
            if (chunkEntities.isEmpty()) {
                blockEntities.remove(chunk);
            }
        }

        void entity(Entity entity) {
            checkY(entity.blockY());
            int chunkX = Math.floorDiv(entity.blockX(), 16);
            int chunkZ = Math.floorDiv(entity.blockZ(), 16);
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            chunk(chunk);
            entities.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(entity);
        }

        public void checkY(int y) {
            if (y < minY || y >= maxYExclusive()) {
                throw new IllegalArgumentException("Y " + y + " outside world range ["
                        + minY + ", " + maxYExclusive() + ") in " + id);
            }
        }
    }
}
