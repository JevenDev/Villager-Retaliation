package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SettlementFieldPlot {
    private final UUID id;
    private final UUID area;
    private final BlockPos position;
    private BlockState baselineSoil;
    private BlockState baselineCrop;
    private BlockState soil;
    private BlockState crop;
    private BlockState baselineTop = Blocks.AIR.defaultBlockState();
    private BlockState top = Blocks.AIR.defaultBlockState();
    private boolean topKnown;
    private final List<BlockState> nearbySoil = new ArrayList<>();
    private final List<BlockState> nearbyCrops = new ArrayList<>();
    private boolean environmentKnown;
    private final List<BlockPos> waterSources = new ArrayList<>();
    private boolean rainExposed;
    private int skyLight;
    private int blockLight;
    private long checkedAt;
    private long version;
    private double growth;
    private double drying;
    private boolean pending;
    private boolean conflict;
    private SettlementCropPlan plantingPlan;
    private SettlementPlanting.Decision planting;

    SettlementPlanting.Decision planting(SettlementCropPlan plan) {
        return this.plantingPlan != null && plan.percentages().equals(this.plantingPlan.percentages())
                && plan.demand() == this.plantingPlan.demand() ? this.planting : null;
    }

    void planting(SettlementCropPlan plan, SettlementPlanting.Decision decision) {
        this.plantingPlan = plan;
        this.planting = decision;
    }

    void clearPlanting() {
        this.plantingPlan = null;
        this.planting = null;
    }


    private SettlementFieldPlot(UUID id, UUID area, BlockPos position) {
        this.id = id;
        this.area = area;
        this.position = position.immutable();
    }

    public UUID area() {
        return this.area;
    }

    public BlockPos position() {
        return this.position;
    }

    public boolean pending() {
        return this.pending;
    }

    public boolean conflict() {
        return this.conflict;
    }

    BlockState soil() {
        return this.soil;
    }

    BlockState crop() {
        return this.crop;
    }

    BlockState observedState(BlockPos position) {
        if (this.position.equals(position)) return this.soil;
        if (this.position.above().equals(position)) return this.crop;
        return this.topKnown && this.position.above(2).equals(position) ? this.top : null;
    }

    BlockState changedFrom(BlockPos position) {
        if (!this.pending) return null;
        if (this.position.equals(position)) return this.soil != this.baselineSoil ? this.baselineSoil : null;
        if (this.position.above().equals(position)) return this.crop != this.baselineCrop ? this.baselineCrop : null;
        return this.topKnown && this.position.above(2).equals(position) && this.top != this.baselineTop ? this.baselineTop : null;
    }

    static boolean supportedCrop(BlockState state) {
        return SettlementFarmPlants.supported(state);
    }

    static boolean supportedSoil(BlockState state) {
        return state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
    }

    static boolean supportedSite(BlockState soil, BlockState crop) {
        return supportedCrop(crop) || crop.isAir() && (supportedSoil(soil) || soil.is(BlockTags.DIRT));
    }

    static boolean supportedPasture(BlockState soil, BlockState crop) {
        return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || crop.is(Blocks.SHORT_GRASS);
    }

    int brightness(int skyDarken) {
        return Math.max(this.blockLight, this.skyLight - skyDarken);
    }

    int grassTicks(double elapsed) {
        if (!this.soil.is(Blocks.GRASS_BLOCK)) {
            this.growth = 0.0D;
            return 0;
        }
        this.growth = Math.min(4.0D, this.growth + elapsed);
        int ticks = (int) this.growth;
        this.growth -= ticks;
        return ticks;
    }

    BlockState top() {
        return this.top;
    }

    boolean harvestable() {
        return SettlementFarmPlants.harvestable(this.crop)
                && (!this.crop.is(Blocks.COCOA) || cocoaSupported())
                && (!this.crop.is(Blocks.PITCHER_CROP) || this.topKnown && this.top.is(Blocks.PITCHER_CROP)
                        && this.top.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER
                        && this.top.getValue(PitcherCropBlock.AGE).equals(this.crop.getValue(PitcherCropBlock.AGE)));
    }

    static SettlementFieldPlot capture(ServerLevel level, UUID area, BlockPos position, LevelChunk chunk, long now) {
        var plot = new SettlementFieldPlot(SettlementChunkReceipts.identity(chunk, position), area, position);
        plot.version = Math.max(0L, SettlementChunkReceipts.version(chunk, position, plot.id));
        plot.observe(level, chunk, now);
        return plot;
    }

    boolean reconcile(LevelChunk chunk) {
        long applied = SettlementChunkReceipts.version(chunk, this.position, this.id);
        if (applied < 0L) {
            this.conflict = this.pending;
            return false;
        }
        if (!this.pending) {
            return true;
        }
        if (applied < this.version) {
            if (chunk.getBlockState(this.position) != this.baselineSoil || chunk.getBlockState(this.position.above()) != this.baselineCrop
                    || this.topKnown && this.top != this.baselineTop && chunk.getBlockState(this.position.above(2)) != this.baselineTop) {
                this.conflict = true;
                return false;
            }
            chunk.setBlockState(this.position, this.soil, false);
            chunk.setBlockState(this.position.above(), this.crop, false);
            if (this.topKnown && this.top != this.baselineTop) chunk.setBlockState(this.position.above(2), this.top, false);
            SettlementChunkReceipts.applied(chunk, this.position, this.id, this.version);
        }
        this.pending = false;
        this.conflict = false;
        this.version = Math.max(this.version, applied);
        refresh(chunk);
        return true;
    }

    void refresh(LevelChunk chunk) {
        if (this.pending) {
            throw new IllegalStateException("Pending field work must be reconciled before observation");
        }
        BlockState previous = this.crop;
        this.baselineSoil = chunk.getBlockState(this.position);
        this.baselineCrop = chunk.getBlockState(this.position.above());
        this.soil = this.baselineSoil;
        this.crop = this.baselineCrop;
        this.topKnown = this.position.getY() + 2 < chunk.getMaxBuildHeight();
        if (this.topKnown) {
            this.baselineTop = chunk.getBlockState(this.position.above(2));
            this.top = this.baselineTop;
        }
        if (this.crop != previous) {
            this.growth = 0.0D;
            if (!this.crop.isAir() && (previous == null || previous.isAir() || !this.crop.is(previous.getBlock()))) clearPlanting();
        }
        this.checkedAt = Math.max(this.checkedAt, SettlementChunkReceipts.lastTick(chunk));
    }

    void observe(ServerLevel level, LevelChunk chunk, long now) {
        refresh(chunk);
        this.checkedAt = Math.max(this.checkedAt, now);
        observeEnvironment(level, chunk);
    }

    void observeEnvironment(ServerLevel level, LevelChunk chunk) {
        if (this.nearbySoil.isEmpty()) {
            for (int index = 0; index < 9; index++) {
                this.nearbySoil.add(Blocks.AIR.defaultBlockState());
                this.nearbyCrops.add(Blocks.AIR.defaultBlockState());
            }
        }
        int minChunkX = (this.position.getX() - 4) >> 4;
        int minChunkZ = (this.position.getZ() - 4) >> 4;
        int width = ((this.position.getX() + 4) >> 4) - minChunkX + 1;
        int depth = ((this.position.getZ() + 4) >> 4) - minChunkZ + 1;
        var neighbors = new LevelChunk[width * depth];
        boolean known = true;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int chunkX = minChunkX + x;
                int chunkZ = minChunkZ + z;
                var neighbor = chunk != null && chunk.getPos().x == chunkX && chunk.getPos().z == chunkZ
                        ? chunk : level.getChunkSource().getChunkNow(chunkX, chunkZ);
                neighbors[x * depth + z] = neighbor;
                known &= neighbor != null;
            }
        }
        int index = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var sample = this.position.offset(x, 0, z);
                var neighbor = neighbors[((sample.getX() >> 4) - minChunkX) * depth + (sample.getZ() >> 4) - minChunkZ];
                if (neighbor != null) {
                    this.nearbySoil.set(index, neighbor.getBlockState(sample));
                    this.nearbyCrops.set(index, neighbor.getBlockState(sample.above()));
                }
                index++;
            }
        }
        this.environmentKnown |= known;
        this.waterSources.removeIf(sample -> chunk(level, chunk, sample) != null);
        for (BlockPos sample : BlockPos.betweenClosed(this.position.offset(-4, 0, -4), this.position.offset(4, 1, 4))) {
            var neighbor = neighbors[((sample.getX() >> 4) - minChunkX) * depth + (sample.getZ() >> 4) - minChunkZ];
            if (neighbor != null && neighbor.getFluidState(sample).is(net.minecraft.tags.FluidTags.WATER)) {
                this.waterSources.add(sample.immutable());
            }
        }
        var ownChunk = neighbors[((this.position.getX() >> 4) - minChunkX) * depth + (this.position.getZ() >> 4) - minChunkZ];
        if (ownChunk == null) return;
        this.skyLight = level.getBrightness(LightLayer.SKY, this.position.above());
        this.blockLight = level.getBrightness(LightLayer.BLOCK, this.position.above());
        this.rainExposed = this.skyLight == 15
                && ownChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        this.position.getX(), this.position.getZ()) < this.position.above().getY()
                && ownChunk.getNoiseBiome(this.position.getX() >> 2,
                this.position.above().getY() >> 2, this.position.getZ() >> 2).value().getPrecipitationAt(this.position.above())
                == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
    }

    void advance(ServerLevel level, SettlementRecord town, long now) {
        int elapsed = SettlementSimulationClock.elapsed(this.checkedAt, now);
        this.checkedAt = Math.max(this.checkedAt, now);
        if (elapsed == 0 || this.conflict || !this.environmentKnown
                || town.areas().values().stream().noneMatch(area -> area.enabled() && SettlementFieldService.serves(area, this))) {
            return;
        }
        // use the expected random ticks per block, bounded to running server time
        double randomTicks = elapsed * (double) level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) / 4096.0D;
        if (randomTicks <= 0.0D) {
            return;
        }
        if (this.soil.is(Blocks.GRASS_BLOCK) && !this.crop.is(Blocks.COCOA)) {
            SettlementPasture.advance(level, town, this, randomTicks, level.random);
            return;
        }
        BlockState updatedSoil = this.soil;
        BlockState updatedCrop = this.crop;
        this.drying += this.soil.is(Blocks.FARMLAND) ? randomTicks : 0.0D;
        if (this.soil.is(Blocks.FARMLAND) && this.drying >= 1.0D) {
            int moisture = this.soil.getValue(FarmBlock.MOISTURE);
            if (!this.waterSources.isEmpty() || this.rainExposed && level.isRaining()) {
                updatedSoil = this.soil.setValue(FarmBlock.MOISTURE, 7);
            } else {
                updatedSoil = this.soil.setValue(FarmBlock.MOISTURE, Math.max(0, moisture - (int) Math.min(7, this.drying)));
                if (Math.floor(this.drying) > moisture && !this.crop.is(BlockTags.MAINTAINS_FARMLAND)) {
                    updatedSoil = Blocks.DIRT.defaultBlockState();
                }
            }
            this.drying %= 1.0D;
        }
        BlockState updatedTop = this.top;
        if (this.crop.is(Blocks.COCOA) && cocoaSupported()) {
            this.growth += randomTicks / 5.0D;
            updatedCrop = this.crop.setValue(CocoaBlock.AGE,
                    Math.min(CocoaBlock.MAX_AGE, SettlementFarmPlants.age(this.crop) + (int) Math.min(2.0D, this.growth)));
            this.growth %= 1.0D;
        } else if (this.soil.is(Blocks.FARMLAND)) {
            double rate = 1.0D / ((int) (25.0F / growthSpeed(town)) + 1);
            if (supportedCrop(this.crop) && this.crop.getBlock() instanceof CropBlock plant
                    && Math.max(this.blockLight, this.skyLight) >= 9) {
                if (this.crop.is(Blocks.BEETROOTS) || this.crop.is(Blocks.TORCHFLOWER_CROP)) rate *= 2.0D / 3.0D;
                this.growth += randomTicks * rate;
                int age = Math.min(plant.getMaxAge(), plant.getAge(this.crop) + (int) Math.min(plant.getMaxAge(), this.growth));
                updatedCrop = plant.getStateForAge(age);
                this.growth %= 1.0D;
            } else if (this.crop.is(Blocks.PITCHER_CROP) && Math.max(this.blockLight, this.skyLight) >= 8) {
                this.growth += randomTicks * rate;
                int maximum = this.topKnown && (this.top.isAir() || this.top.is(Blocks.PITCHER_CROP)) ? PitcherCropBlock.MAX_AGE : 2;
                int age = Math.min(maximum, SettlementFarmPlants.age(this.crop) + (int) Math.min(4.0D, this.growth));
                if (age >= SettlementFarmPlants.age(this.crop)) {
                    updatedCrop = this.crop.setValue(PitcherCropBlock.AGE, age);
                    if (age >= 3) updatedTop = updatedCrop.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER);
                }
                this.growth %= 1.0D;
            } else if (SettlementFarmPlants.stem(this.crop) && Math.max(this.blockLight, this.skyLight) >= 9) {
                if (SettlementFarmPlants.age(this.crop) < StemBlock.MAX_AGE) {
                    this.growth += randomTicks * rate;
                    updatedCrop = this.crop.setValue(StemBlock.AGE,
                            Math.min(StemBlock.MAX_AGE, SettlementFarmPlants.age(this.crop) + (int) Math.min(7.0D, this.growth)));
                } else {
                    var directions = fruitDirections(level, town);
                    this.growth += randomTicks * rate * directions.size() / 4.0D;
                    if (this.growth >= 1.0D && !directions.isEmpty()) {
                        var direction = directions.get(level.getRandom().nextInt(directions.size()));
                        var fruit = town.fields().get(this.position.relative(direction));
                        fruit.change(fruit.soil, SettlementFarmPlants.fruitFor(this.crop));
                        SettlementFieldService.applyLoaded(level, fruit);
                        updatedCrop = SettlementFarmPlants.attachedFor(this.crop).setValue(AttachedStemBlock.FACING, direction);
                    }
                }
                this.growth %= 1.0D;
            } else if (SettlementFarmPlants.attachedStem(this.crop)) {
                var direction = this.crop.getValue(AttachedStemBlock.FACING);
                var fruit = town.fields().get(this.position.relative(direction));
                if (fruit != null && !fruit.conflict() && !fruit.crop.is(SettlementFarmPlants.fruitFor(this.crop).getBlock())) {
                    updatedCrop = SettlementFarmPlants.detachedFor(this.crop);
                }
            }
        }
        change(updatedSoil, updatedCrop, updatedTop);
    }

    private boolean cocoaSupported() {
        if (!this.environmentKnown || this.nearbyCrops.size() != 9) return false;
        var direction = this.crop.getValue(CocoaBlock.FACING);
        int index = (direction.getStepX() + 1) * 3 + direction.getStepZ() + 1;
        return this.nearbyCrops.get(index).is(BlockTags.JUNGLE_LOGS);
    }

    private List<Direction> fruitDirections(ServerLevel level, SettlementRecord town) {
        List<Direction> result = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var fruit = town.fields().get(this.position.relative(direction));
            if (fruit != null && town.areas().values().stream().anyMatch(area -> area.enabled()
                    && area.type() == SettlementAreaType.FARM && SettlementFieldService.serves(area, this)
                    && SettlementFieldService.serves(area, fruit))
                    && SettlementFieldService.available(level, fruit) && fruit.crop.isAir()
                    && (fruit.soil.is(Blocks.FARMLAND) || fruit.soil.is(BlockTags.DIRT))) result.add(direction);
        }
        return result;
    }

    private float growthSpeed(SettlementRecord town) {
        float speed = 1.0F;
        boolean[] same = new boolean[9];
        int index = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var plot = town.fields().get(this.position.offset(x, 0, z));
                BlockState neighborSoil = plot == null ? this.nearbySoil.get(index) : plot.soil;
                BlockState neighborCrop = plot == null ? this.nearbyCrops.get(index) : plot.crop;
                float contribution = neighborSoil.is(Blocks.FARMLAND) ? neighborSoil.getValue(FarmBlock.MOISTURE) > 0 ? 3.0F : 1.0F : 0.0F;
                speed += x == 0 && z == 0 ? contribution : contribution / 4.0F;
                same[index++] = neighborCrop.is(this.crop.getBlock());
            }
        }
        if ((same[1] || same[7]) && (same[3] || same[5]) || same[0] || same[2] || same[6] || same[8]) {
            speed /= 2.0F;
        }
        return speed;
    }

    void change(BlockState soil, BlockState crop) {
        BlockState top = this.top;
        if (this.crop.is(Blocks.PITCHER_CROP) && !crop.is(Blocks.PITCHER_CROP) && this.top.is(Blocks.PITCHER_CROP)) {
            top = Blocks.AIR.defaultBlockState();
        }
        change(soil, crop, top);
    }

    private void change(BlockState soil, BlockState crop, BlockState top) {
        if (this.soil != soil || this.crop != crop || this.top != top) {
            if (this.soil.is(Blocks.GRASS_BLOCK) != soil.is(Blocks.GRASS_BLOCK)) this.growth = 0.0D;
            if (this.crop != crop && (!supportedCrop(crop) || !crop.is(this.crop.getBlock())
                    || SettlementFarmPlants.age(crop) < SettlementFarmPlants.age(this.crop))) {
                this.growth = 0.0D;
            }
            this.soil = soil;
            this.crop = crop;
            this.top = top;
            this.pending = true;
            this.version++;
        }
    }

    boolean canPlant() {
        return this.crop.isAir() && canReplant();
    }

    boolean canReplant() {
        return this.soil.is(Blocks.FARMLAND) && Math.max(this.blockLight, this.skyLight) >= 8;
    }

    void pause(long now) {
        this.checkedAt = Math.max(this.checkedAt, now);
    }

    private static LevelChunk chunk(ServerLevel level, LevelChunk supplied, BlockPos position) {
        return supplied != null && supplied.getPos().x == Math.floorDiv(position.getX(), 16) && supplied.getPos().z == Math.floorDiv(position.getZ(), 16)
                ? supplied : level.getChunkSource().getChunkNow(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putLong("Position", this.position.asLong());
        tag.put("BaselineSoil", NbtUtils.writeBlockState(this.baselineSoil));
        tag.put("BaselineCrop", NbtUtils.writeBlockState(this.baselineCrop));
        tag.put("Soil", NbtUtils.writeBlockState(this.soil));
        tag.put("Crop", NbtUtils.writeBlockState(this.crop));
        if (this.topKnown) {
            tag.put("BaselineTop", NbtUtils.writeBlockState(this.baselineTop));
            tag.put("Top", NbtUtils.writeBlockState(this.top));
        }
        ListTag environment = new ListTag();
        for (int index = 0; index < this.nearbySoil.size(); index++) {
            var entry = new CompoundTag();
            entry.put("Soil", NbtUtils.writeBlockState(this.nearbySoil.get(index)));
            entry.put("Crop", NbtUtils.writeBlockState(this.nearbyCrops.get(index)));
            environment.add(entry);
        }
        tag.put("Environment", environment);
        tag.putBoolean("EnvironmentKnown", this.environmentKnown);
        tag.putLongArray("WaterSources", this.waterSources.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putBoolean("RainExposed", this.rainExposed);
        tag.putInt("SkyLight", this.skyLight);
        tag.putInt("BlockLight", this.blockLight);
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putDouble("Growth", this.growth);
        tag.putDouble("Drying", this.drying);
        tag.putBoolean("Pending", this.pending);
        tag.putBoolean("Conflict", this.conflict);
        if (this.planting != null) {
            tag.put("PlantingPlan", this.plantingPlan.save());
            tag.put("Planting", this.planting.save());
        }
        return tag;
    }

    static SettlementFieldPlot load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.contains("Position", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Field identity, area or position is invalid");
        }
        var plot = new SettlementFieldPlot(tag.getUUID("Id"), tag.getUUID("Area"), BlockPos.of(tag.getLong("Position")));
        plot.baselineSoil = state(tag, "BaselineSoil");
        plot.baselineCrop = state(tag, "BaselineCrop");
        plot.soil = state(tag, "Soil");
        plot.crop = state(tag, "Crop");
        plot.topKnown = tag.contains("Top", Tag.TAG_COMPOUND);
        if (plot.topKnown) {
            plot.baselineTop = state(tag, "BaselineTop");
            plot.top = state(tag, "Top");
        }
        for (Tag raw : tag.getList("Environment", Tag.TAG_COMPOUND)) {
            plot.nearbySoil.add(state((CompoundTag) raw, "Soil"));
            plot.nearbyCrops.add(state((CompoundTag) raw, "Crop"));
        }
        plot.environmentKnown = tag.getBoolean("EnvironmentKnown");
        if (plot.nearbySoil.size() != 0 && plot.nearbySoil.size() != 9 || plot.environmentKnown && plot.nearbySoil.size() != 9) {
            throw new IllegalArgumentException("Field environment is incomplete");
        }
        for (long packed : tag.getLongArray("WaterSources")) {
            BlockPos water = BlockPos.of(packed);
            if (Math.abs(water.getX() - plot.position.getX()) > 4 || Math.abs(water.getZ() - plot.position.getZ()) > 4
                    || water.getY() < plot.position.getY() || water.getY() > plot.position.getY() + 1 || plot.waterSources.contains(water)) {
                throw new IllegalArgumentException("Field water source is outside its observed neighborhood or duplicated");
            }
            plot.waterSources.add(water);
        }
        plot.rainExposed = tag.getBoolean("RainExposed");
        plot.skyLight = Math.clamp(tag.getInt("SkyLight"), 0, 15);
        plot.blockLight = Math.clamp(tag.getInt("BlockLight"), 0, 15);
        plot.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        plot.version = Math.max(0L, tag.getLong("Version"));
        plot.growth = fraction(tag.getDouble("Growth"));
        plot.drying = fraction(tag.getDouble("Drying"));
        plot.pending = tag.getBoolean("Pending");
        plot.conflict = tag.getBoolean("Conflict");
        if (tag.contains("Planting") || tag.contains("PlantingPlan")) {
            if (!tag.contains("Planting", Tag.TAG_COMPOUND) || !tag.contains("PlantingPlan", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Incomplete planting decision");
            plot.plantingPlan = SettlementCropPlan.load(tag.getCompound("PlantingPlan"));
            plot.planting = SettlementPlanting.Decision.load(tag.getCompound("Planting"));
        }
        return plot;
    }

    private static BlockState state(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Field block state is missing");
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(key));
    }

    private static double fraction(double value) {
        if (!Double.isFinite(value) || value < 0.0D || value >= 1.0D) {
            throw new IllegalArgumentException("Field growth progress is invalid");
        }
        return value;
    }
}
