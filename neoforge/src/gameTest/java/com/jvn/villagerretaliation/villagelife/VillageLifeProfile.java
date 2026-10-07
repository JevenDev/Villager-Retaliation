package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("village_life_profile")
public final class VillageLifeProfile {
    private static final int WARMUP = 400;
    private static final int SAMPLES = 1_200;
    private static boolean outputInitialized;

    private VillageLifeProfile() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> profiles() {
        if (!Boolean.getBoolean("villagerretaliation.villageLifeProfile")) return List.of();
        return List.of(100, 300, 500).stream().map(count -> new TestFunction(
                "village_life_profile_" + count, "village_life_profile_" + count, "village_life_profile:empty",
                3_400, 0, true, helper -> new Run(helper, count).start())).toList();
    }

    private static final class Run {
        private final GameTestHelper helper;
        private final int population;
        private final List<Villager> villagers = new ArrayList<>();
        private final List<CompoundTag> initial = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<ChunkPos> forced = new ArrayList<>();
        private final long[][] samples = {new long[SAMPLES], new long[SAMPLES]};
        private final VillageLifeService.Diagnostics[] diagnostics = new VillageLifeService.Diagnostics[2];
        private final boolean previousLife = VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get();
        private final boolean previousFamilies = VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.get();
        private final java.util.Map<VillagerRetaliationConfig.ConfigValue<Integer>, Integer> previousSettings = java.util.Map.of(
                VillagerRetaliationConfig.VILLAGE_LIFE_DECISION_INTERVAL, VillagerRetaliationConfig.VILLAGE_LIFE_DECISION_INTERVAL.get(),
                VillagerRetaliationConfig.VILLAGE_LIFE_MAX_DECISIONS, VillagerRetaliationConfig.VILLAGE_LIFE_MAX_DECISIONS.get(),
                VillagerRetaliationConfig.VILLAGE_LIFE_MAX_PATHS, VillagerRetaliationConfig.VILLAGE_LIFE_MAX_PATHS.get(),
                VillagerRetaliationConfig.VILLAGE_LIFE_BUDGET_MICROS, VillagerRetaliationConfig.VILLAGE_LIFE_BUDGET_MICROS.get(),
                VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE, VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.get());
        private final long previousDay;
        private final boolean previousDaylight;
        private final boolean previousMobs;
        private final boolean previousWeather;
        private final Consumer<ServerTickEvent.Pre> before = this::beforeTick;
        private final Consumer<ServerTickEvent.Post> after = this::afterTick;
        private long started;
        private int phase;
        private int tick;
        private boolean cleaned;
        private String invalidPopulation;

        private Run(GameTestHelper helper, int population) {
            this.helper = helper;
            this.population = population;
            var level = helper.getLevel();
            this.previousDay = level.getDayTime();
            this.previousDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            this.previousMobs = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            this.previousWeather = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        }

        private void start() {
            var level = this.helper.getLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            this.helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo test) {}
                @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            });
            var defaults = new com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel.Social();
            VillagerRetaliationConfig.VILLAGE_LIFE_DECISION_INTERVAL.set(defaults.villageLifeDecisionInterval);
            VillagerRetaliationConfig.VILLAGE_LIFE_MAX_DECISIONS.set(defaults.villageLifeMaxDecisions);
            VillagerRetaliationConfig.VILLAGE_LIFE_MAX_PATHS.set(defaults.villageLifeMaxPaths);
            VillagerRetaliationConfig.VILLAGE_LIFE_BUDGET_MICROS.set(defaults.villageLifeBudgetMicros);
            VillagerRetaliationConfig.VILLAGE_LIFE_ACTIVITY_CHANCE.set(defaults.villageLifeActivityChance);
            LogUtils.getLogger().info("Village life profile settings: interval {}, decisions {}, paths {}, budget {} us, chance {}%",
                    defaults.villageLifeDecisionInterval, defaults.villageLifeMaxDecisions, defaults.villageLifeMaxPaths,
                    defaults.villageLifeBudgetMicros, defaults.villageLifeActivityChance);
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(false);
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(false);
            VillageLifeService.clear(level.getServer());
            for (int villageIndex = 0; villageIndex < this.population / 100; villageIndex++) prepareVillage(villageIndex);
            for (var villager : this.villagers) this.initial.add(villager.saveWithoutId(new CompoundTag()));
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this.before);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this.after);
            LogUtils.getLogger().info("Village life profile started: {} residents across {} villages", this.population, this.population / 100);
        }

        private void prepareVillage(int index) {
            var level = this.helper.getLevel();
            var origin = this.helper.absolutePos(new BlockPos(24 + index * 128, 2, 24));
            for (int x = (origin.getX() - 48) >> 4; x <= (origin.getX() + 64) >> 4; x++) {
                for (int z = (origin.getZ() - 48) >> 4; z <= (origin.getZ() + 64) >> 4; z++) {
                    level.getChunk(x, z);
                    level.setChunkForced(x, z, true);
                    this.forced.add(new ChunkPos(x, z));
                }
            }
            for (var pos : BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(36, -1, 36))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            for (var pos : BlockPos.betweenClosed(origin.offset(-8, 0, -8), origin.offset(36, 1, 36))) {
                if (pos.getX() == origin.getX() - 8 || pos.getX() == origin.getX() + 36
                        || pos.getZ() == origin.getZ() - 8 || pos.getZ() == origin.getZ() + 36) {
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                }
            }
            var bell = origin.offset(14, 0, 14);
            level.setBlock(bell, Blocks.BELL.defaultBlockState(), 2);
            var sealed = origin.offset(32, 0, 14);
            for (var pos : BlockPos.betweenClosed(sealed.offset(-2, 0, -2), sealed.offset(2, 4, 2))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(sealed, Blocks.BELL.defaultBlockState(), 2);
            level.setBlock(sealed.above(), Blocks.AIR.defaultBlockState(), 2);
            var shelter = origin.offset(14, 0, 32);
            var bed = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
            level.setBlock(shelter.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
            level.setBlock(shelter, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
            for (var pos : BlockPos.betweenClosed(shelter.offset(-3, 3, -3), shelter.offset(3, 3, 3))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            var player = this.helper.makeMockServerPlayerInLevel();
            player.setPos(bell.above(6).getCenter());
            player.setInvulnerable(true);
            this.players.add(player);
            var id = VillageAllegianceRegistrySavedData.get(level).create(level.getGameTime(), level.dimension().location(), bell, "Life profile " + index);
            for (int resident = 0; resident < 100; resident++) {
                var villager = EntityType.VILLAGER.create(level);
                villager.setUUID(UUID.nameUUIDFromBytes(("life-profile:" + this.population + ":" + index + ":" + resident)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                villager.setPos(origin.offset(resident % 10 * 3, 0, resident / 10 * 3).getBottomCenter());
                villager.setOnGround(true);
                villager.setInvulnerable(true);
                if (resident % 8 == 0) villager.setAge(-24_000);
                VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
                level.addFreshEntity(villager);
                villager.getRandom().setSeed(index * 100L + resident);
                villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), resident % 5 == 0 ? sealed : bell));
                villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), shelter));
                this.villagers.add(villager);
            }
        }

        private void beforeTick(ServerTickEvent.Pre event) {
            if (this.cleaned || event.getServer() != this.helper.getLevel().getServer()) return;
            var level = this.helper.getLevel();
            level.setDayTime(10_000L + (this.tick / 400) * 24_000L);
            if (this.tick % 400 == 0) level.setWeatherParameters(0, 10_000, this.tick / 400 % 2 == 1, false);
            this.started = System.nanoTime();
        }

        private void afterTick(ServerTickEvent.Post event) {
            if (this.cleaned || this.started == 0L || event.getServer() != this.helper.getLevel().getServer()) return;
            long duration = System.nanoTime() - this.started;
            this.tick++;
            if (this.tick > WARMUP && this.tick <= WARMUP + SAMPLES) {
                this.samples[this.phase][this.tick - WARMUP - 1] = duration;
                long nearby = this.villagers.stream().filter(v -> v.isAlive() && !v.isNoAi()
                        && this.helper.getLevel().isPositionEntityTicking(v.blockPosition())
                        && this.helper.getLevel().getNearestPlayer(v, 64.0D) != null).count();
                if (nearby != this.population && this.invalidPopulation == null) {
                    this.invalidPopulation = "expected " + this.population + " nearby ticking villagers, found " + nearby;
                }
            }
            if (this.tick % 400 == 0) LogUtils.getLogger().info("Village life profile {} {} tick {}, {}", this.population,
                    this.phase == 0 ? "off" : "on", this.tick, VillageLifeService.diagnostics(event.getServer()));
            if (this.tick < WARMUP + SAMPLES) return;
            this.diagnostics[this.phase] = VillageLifeService.diagnostics(event.getServer());
            if (this.phase == 0) {
                this.phase = 1;
                this.tick = 0;
                this.villagers.forEach(Villager::discard);
                this.villagers.clear();
                VillageLifeService.clear(event.getServer());
                VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(true);
                VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(true);
                for (int index = 0; index < this.initial.size(); index++) {
                    var villager = EntityType.VILLAGER.create(this.helper.getLevel());
                    villager.load(this.initial.get(index).copy());
                    this.helper.getLevel().addFreshEntity(villager);
                    villager.getRandom().setSeed(index);
                    this.villagers.add(villager);
                }
                return;
            }
            var graph = VillagerSocialGraphSavedData.get(this.helper.getLevel()).save(new CompoundTag(), this.helper.getLevel().registryAccess());
            var residents = this.villagers.stream().map(Villager::getUUID).collect(java.util.stream.Collectors.toSet());
            long visits = graph.getList("RomanticBonds", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                    .map(tag -> (CompoundTag) tag).filter(bond -> residents.contains(bond.getUUID("First")))
                    .mapToLong(bond -> bond.getLongArray("CourtshipDays").length).sum();
            LogUtils.getLogger().info("Village life profile {} completed {} distinct-day courtship visits", this.population, visits);
            writeResults();
            cleanup();
            this.helper.runAfterDelay(1, () -> {
                this.helper.assertTrue(this.invalidPopulation == null, String.valueOf(this.invalidPopulation));
                this.helper.assertTrue(this.diagnostics[1].decisions() > 100, "profiling exercises the real ambient queue");
                this.helper.assertTrue(this.diagnostics[1].paths() > 0, "profiling exercises real path requests");
                this.helper.succeed();
            });
        }

        private void writeResults() {
            try {
                var summary = java.nio.file.Path.of("village-life-profile.csv");
                var raw = java.nio.file.Path.of("village-life-profile-ticks.csv");
                if (!outputInitialized) {
                    Files.writeString(summary, "villagers,enabled,samples,mean_ms,p95_ms,service_ms,decisions,paths,failures,budget_overruns,max_queue_delay_ticks\n");
                    Files.writeString(raw, "villagers,enabled,sample,mspt\n");
                    outputInitialized = true;
                }
                var records = new StringBuilder();
                for (int index = 0; index < 2; index++) {
                    long[] values = this.samples[index].clone();
                    Arrays.sort(values);
                    double mean = Arrays.stream(values).average().orElseThrow() / 1_000_000.0D;
                    double p95 = values[(int) Math.ceil(values.length * 0.95D) - 1] / 1_000_000.0D;
                    var metrics = this.diagnostics[index];
                    Files.writeString(summary, String.format(Locale.ROOT, "%d,%s,%d,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d%n",
                            this.population, index == 1, values.length, mean, p95, metrics.nanos() / 1_000_000.0D,
                            metrics.decisions(), metrics.paths(), metrics.failures(), metrics.budgetOverruns(), metrics.maxQueueDelayTicks()),
                            StandardOpenOption.APPEND);
                    for (int sample = 0; sample < SAMPLES; sample++) records.append(String.format(Locale.ROOT, "%d,%s,%d,%.6f%n",
                            this.population, index == 1, sample, this.samples[index][sample] / 1_000_000.0D));
                    LogUtils.getLogger().info("Village life profile result: {} residents, enabled {}, mean {} ms, p95 {} ms",
                            this.population, index == 1, mean, p95);
                }
                Files.writeString(raw, records, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void cleanup() {
            if (this.cleaned) return;
            this.cleaned = true;
            NeoForge.EVENT_BUS.unregister(this.before);
            NeoForge.EVENT_BUS.unregister(this.after);
            this.villagers.forEach(Villager::discard);
            this.players.forEach(ServerPlayer::discard);
            var level = this.helper.getLevel();
            this.forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            VillageLifeService.clear(level.getServer());
            VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.set(this.previousLife);
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(this.previousFamilies);
            this.previousSettings.forEach(VillagerRetaliationConfig.ConfigValue::set);
            level.setDayTime(this.previousDay);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(this.previousDaylight, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(this.previousMobs, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(this.previousWeather, level.getServer());
            level.setWeatherParameters(10_000, 0, false, false);
        }
    }
}
