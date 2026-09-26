package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementPastureGameTests {
    private SettlementPastureGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void unloadedSheepRegrowsWoolByConsumingObservedGrass(GameTestHelper helper) {
        fixture(helper, fixture -> {
            run(helper, fixture, 200, 2_200);
            helper.assertFalse(sheep(helper, fixture).isSheared(), "grazing regrows the recorded fleece");
            helper.assertTrue(fixture.plot().soil().is(Blocks.DIRT), "the recorded grass becomes dirt");
            helper.assertTrue(fixture.plot().pending(), "grazing leaves a pending world change");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.plot().position()).is(Blocks.GRASS_BLOCK),
                    "the absent native chunk waits for reconciliation");
            fixture.animal().shear();
            run(helper, fixture, 2_400, 6_600);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "bare dirt cannot supply another fleece");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.plot().position()), "grazing never loads the pen");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void grazingConsumesShortGrassBeforeTheGrassBlock(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var plot = plot(helper, fixture, fixture.plot().position(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.SHORT_GRASS.defaultBlockState(), 15);
            run(helper, fixture, 200, 2_200);
            helper.assertTrue(plot.crop().isAir() && plot.soil().is(Blocks.GRASS_BLOCK),
                    "the first meal removes only short grass");
            fixture.animal().shear();
            run(helper, fixture, 2_400, 4_400);
            helper.assertFalse(sheep(helper, fixture).isSheared(), "the second meal regrows another fleece");
            helper.assertTrue(plot.soil().is(Blocks.DIRT), "the second meal consumes the grass block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void sheepShareTheSameFinitePastureBlock(GameTestHelper helper) {
        fixture(helper, fixture -> {
            Sheep second = EntityType.SHEEP.create(helper.getLevel());
            second.setPos(fixture.animal().position().getBottomCenter());
            second.setSheared(true);
            var state = SettlementLivestock.capture(second, fixture.area().id(), 0L);
            fixture.town().recordLivestock(state);
            run(helper, fixture, 200, 2_200);
            int grown = (sheep(helper, fixture).isSheared() ? 0 : 1)
                    + (((Sheep) state.context(helper.getLevel())).isSheared() ? 0 : 1);
            helper.assertValueEqual(grown, 1, "one grass block can regrow only one shared fleece");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void lambGrazingUsesNativeMaturation(GameTestHelper helper) {
        fixture(helper, fixture -> {
            Sheep lamb = sheep(helper, fixture);
            lamb.setAge(-5_000);
            fixture.animal().observe(lamb, 0L);
            run(helper, fixture, 200, 200);
            helper.assertValueEqual(sheep(helper, fixture).getAge(), -3_600,
                    "one interval gives 200 normal age ticks and the native 1200-tick grass bonus");
            helper.assertTrue(fixture.plot().soil().is(Blocks.DIRT), "the faster lamb still consumes real pasture");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void disabledAnimalAiAndUnsafeAnimalsCannotGraze(GameTestHelper helper) {
        fixture(helper, fixture -> {
            Sheep animal = sheep(helper, fixture);
            animal.setNoAi(true);
            fixture.animal().observe(animal, 0L);
            run(helper, fixture, 200, 2_400);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "disabled native AI also disables background grazing");
            animal.setNoAi(false);
            animal.setRemainingFireTicks(200);
            fixture.animal().observe(animal, 2_400L);
            run(helper, fixture, 2_600, 5_000);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "an animal in danger cannot gain grazing output");
            helper.assertTrue(fixture.plot().soil().is(Blocks.GRASS_BLOCK), "paused animals preserve pasture");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void disabledAndUnobservedPasturesCannotSupplyWool(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var area = fixture.area();
            fixture.town().putArea(fixture.town().leader(), new SettlementArea(area.id(), area.type(), area.name(),
                    area.min(), area.max(), area.priority(), false));
            run(helper, fixture, 200, 2_400);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "a disabled designation cannot run background grazing");
            fixture.town().putArea(fixture.town().leader(), area);
            fixture.town().removeField(fixture.plot().position());
            run(helper, fixture, 2_600, 5_000);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "an unobserved surface cannot be assumed to contain grass");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void mobGriefingRulePreservesGrassWhileAllowingNativeWoolRegrowth(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            boolean previous = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            try {
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
                run(helper, fixture, 200, 2_200);
                helper.assertFalse(sheep(helper, fixture).isSheared(), "native sheep still regrow wool when block griefing is disabled");
                helper.assertTrue(fixture.plot().soil().is(Blocks.GRASS_BLOCK) && !fixture.plot().pending(),
                        "the griefing rule prevents terrain consumption");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void grazingCreditSurvivesReloadWithoutReplayingTime(GameTestHelper helper) {
        fixture(helper, fixture -> {
            run(helper, fixture, 200, 1_000);
            var level = helper.getLevel();
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var animal = restored.livestock().get(fixture.animal().id());
            double progress = animal.save().getDouble("Grazing");
            helper.assertTrue(progress > 0.0D, "unfinished grazing is saved");
            SettlementLivestockService.update(level, restored, 1_000L);
            helper.assertValueEqual(animal.save().getDouble("Grazing"), progress, "repeated checkpoints neither gain nor erase grazing time");
            for (long now = 1_200L; now <= 2_200L; now += 200L) SettlementLivestockService.update(level, restored, now);
            helper.assertFalse(((Sheep) animal.context(level)).isSheared(), "the restored animal completes its existing grazing cycle");
            var old = animal.save();
            old.remove("Grazing");
            helper.assertValueEqual(SettlementLivestock.load(old).save().getDouble("Grazing"), 0.0D,
                    "older livestock saves begin without invented grazing credit");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void nativeReturnClearsGrazingCreditAndAppliesTheFleeceOnce(GameTestHelper helper) {
        fixture(helper, fixture -> {
            run(helper, fixture, 200, 1_000);
            Sheep animal = sheep(helper, fixture);
            SettlementLivestockService.onActive(animal);
            helper.assertValueEqual(fixture.animal().save().getDouble("Grazing"), 0.0D, "live AI takes over without background grazing credit");
            run(helper, fixture, 1_200, 3_400);
            SettlementLivestockService.onActive(animal);
            helper.assertFalse(animal.isSheared(), "return applies completed wool regrowth");
            animal.setSheared(true);
            SettlementLivestockService.onActive(animal);
            helper.assertTrue(animal.isSheared(), "another native shearing is not reversed by the acknowledged journal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void pendingGrazingReconcilesThroughChunkEventsWithoutReplay(GameTestHelper helper) {
        fixture(helper, fixture -> {
            run(helper, fixture, 200, 2_200);
            var pending = fixture.plot().save();
            var level = helper.getLevel();
            SettlementFieldService.onChunkLoad(new ChunkEvent.Load(fixture.chunk(), false));
            helper.assertTrue(fixture.chunk().getBlockState(fixture.plot().position()).is(Blocks.DIRT),
                    "chunk return applies the consumed pasture before live work");
            fixture.chunk().setBlockState(fixture.plot().position(), Blocks.GRASS_BLOCK.defaultBlockState(), false);
            var stale = SettlementFieldPlot.load(pending);
            helper.assertTrue(stale.reconcile(fixture.chunk()), "the chunk recognizes the applied grazing receipt");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.plot().position()).is(Blocks.GRASS_BLOCK),
                    "a stale journal cannot consume later native regrowth");
            helper.assertFalse(level.hasChunkAt(fixture.plot().position()), "chunk handoff does not request additional chunks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void changedPastureBlocksArePreservedAndStopFurtherGrazing(GameTestHelper helper) {
        fixture(helper, fixture -> {
            run(helper, fixture, 200, 2_200);
            fixture.chunk().setBlockState(fixture.plot().position(), Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
            helper.assertFalse(fixture.plot().reconcile(fixture.chunk()), "conflicting player terrain is not overwritten");
            helper.assertTrue(fixture.plot().conflict(), "the unresolved terrain change remains visible");
            fixture.animal().shear();
            run(helper, fixture, 2_400, 6_600);
            helper.assertTrue(sheep(helper, fixture).isSheared(), "conflicted pasture cannot supply more wool");
            helper.assertTrue(fixture.chunk().getBlockState(fixture.plot().position()).is(Blocks.DIAMOND_BLOCK),
                    "the player block survives background work");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void grassSpreadsToObservedDirtAtNativeVerticalOffsets(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var target = plot(helper, fixture, fixture.plot().position().offset(1, -2, 0),
                    Blocks.DIRT.defaultBlockState(), Blocks.SNOW.defaultBlockState(), 15);
            spread(helper, fixture, fixture.plot(), 200);
            helper.assertTrue(target.soil().is(Blocks.GRASS_BLOCK), "nearby grass spreads downward through the native candidate range");
            helper.assertTrue(target.soil().getValue(SnowyDirtBlock.SNOWY), "the spread records the native snowy property");
            helper.assertFalse(helper.getLevel().hasChunkAt(target.position()), "grass spreading never loads terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void darknessWaterAndOpaqueCoverPreventGrassSpreading(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var source = plot(helper, fixture, fixture.plot().position(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), 0);
            var target = plot(helper, fixture, source.position().east(), Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), 15);
            spread(helper, fixture, source, 200);
            helper.assertTrue(target.soil().is(Blocks.DIRT), "dark source grass cannot spread");
            source = plot(helper, fixture, source.position(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), 15);
            target = plot(helper, fixture, target.position(), Blocks.DIRT.defaultBlockState(), Blocks.WATER.defaultBlockState(), 15);
            spread(helper, fixture, source, 200);
            helper.assertTrue(target.soil().is(Blocks.DIRT), "water prevents grass propagation");
            target = plot(helper, fixture, target.position(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), 15);
            spread(helper, fixture, source, 200);
            helper.assertTrue(target.soil().is(Blocks.DIRT), "opaque cover prevents grass propagation");
            target = plot(helper, fixture, target.position(), Blocks.DIRT.defaultBlockState(),
                    Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2), 15);
            spread(helper, fixture, source, 200);
            helper.assertTrue(target.soil().is(Blocks.DIRT), "thick snow blocks grass while a single layer is supported");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void coveredGrassDecaysAndDirtDoesNotRegrowWithoutASource(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var covered = plot(helper, fixture, fixture.plot().position(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), 15);
            spread(helper, fixture, covered, 1);
            helper.assertTrue(covered.soil().is(Blocks.DIRT), "a native random tick removes covered grass");
            covered.change(covered.soil(), Blocks.AIR.defaultBlockState());
            spread(helper, fixture, covered, 200);
            helper.assertTrue(covered.soil().is(Blocks.DIRT), "isolated dirt cannot invent a spreading grass source");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void pastureSurveyAndGrowthRespectRandomTicksAndServerTime(GameTestHelper helper) {
        fixture(helper, fixture -> {
            SettlementFieldService.observeArea(helper.getLevel(), fixture.town(), fixture.area(), fixture.chunk());
            helper.assertTrue(fixture.town().fields().containsKey(fixture.plot().position()), "ordinary pen surveys capture pasture");
            var plot = fixture.town().fields().get(fixture.plot().position());
            var level = helper.getLevel();
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
                plot.advance(level, fixture.town(), 200L);
                helper.assertValueEqual(plot.save().getDouble("Growth"), 0.0D, "disabled random ticks stop grass growth");
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3, level.getServer());
                plot.advance(level, fixture.town(), 1_000_000L);
                helper.assertTrue(plot.save().getDouble("Growth") <= 200.0D * 3 / 4096,
                        "elapsed grass growth is capped to running server time");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void regrowingPastureSupportsRepeatedUnloadedShearing(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var level = helper.getLevel();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) plot(helper, fixture, fixture.plot().position().offset(x, 0, z),
                            Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), 15);
                }
            }
            var villager = EntityType.VILLAGER.create(level);
            villager.setPos(fixture.animal().position().getBottomCenter());
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, fixture.town().id(),
                    com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            fixture.town().assignDuty(villager.getUUID(), fixture.area().id());
            SettlementWorkforceService.reconcile(level, villager);
            com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
            var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
            inventory.setItem(com.jvn.villagerretaliation.inventory.HiredJobInventory.MAINHAND_SLOT,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS));
            var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
            fixture.town().recordWorker(worker);
            fixture.town().recordResident(SettlementResidentState.capture(villager, fixture.town(), 0L));
            fixture.animal().access(worker.id(), worker.position(), 0);
            long day = level.getDayTime();
            int previous = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            try {
                level.setDayTime(6_000L);
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(4096, level.getServer());
                for (long now = 200L; now <= 14_000L; now += 200L) SettlementBackgroundService.update(level, fixture.town(), now);
                var items = worker.items(level.registryAccess());
                helper.assertTrue(items.get(com.jvn.villagerretaliation.inventory.HiredJobInventory.MAINHAND_SLOT).getDamageValue() >= 3,
                        "one sheep is grazed and sheared through at least three real work cycles");
                helper.assertTrue(items.stream().filter(stack -> stack.is(net.minecraft.world.item.Items.WHITE_WOOL))
                        .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum() >= 3,
                        "repeated grazing yields actual wool in the worker inventory");
                helper.assertFalse(level.hasChunkAt(fixture.plot().position()), "the complete renewable production loop stays unloaded");
            } finally {
                level.setDayTime(day);
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previous, level.getServer());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pasture")
    public static void shrinkingAPenStopsSpreadIntoItsFormerArea(GameTestHelper helper) {
        fixture(helper, fixture -> {
            var source = fixture.plot();
            var target = plot(helper, fixture, source.position().east(),
                    Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), 15);
            var old = fixture.area();
            var smaller = new SettlementArea(old.id(), old.type(), old.name(), source.position(), source.position().above(), 5, true);
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), smaller), "the pen can be resized");
            spread(helper, fixture, source, 200);
            helper.assertTrue(target.soil().is(Blocks.DIRT), "an obsolete observation outside the pen cannot receive new grass");
        });
    }

    private static void spread(GameTestHelper helper, Fixture fixture, SettlementFieldPlot source, int ticks) {
        var random = RandomSource.create(1731L);
        for (int step = 0; step < ticks; step++) SettlementPasture.advance(helper.getLevel(), fixture.town(), source, 1.0D, random);
    }

    private static void run(GameTestHelper helper, Fixture fixture, int from, int until) {
        for (int now = from; now <= until; now += 200) SettlementLivestockService.update(helper.getLevel(), fixture.town(), now);
    }

    private static Sheep sheep(GameTestHelper helper, Fixture fixture) {
        return (Sheep) fixture.animal().context(helper.getLevel());
    }

    private static SettlementFieldPlot plot(GameTestHelper helper, Fixture fixture, BlockPos position,
                                            BlockState soil, BlockState cover, int light) {
        setBlock(fixture.chunk(), position, soil);
        setBlock(fixture.chunk(), position.above(), cover);
        var plot = SettlementFieldPlot.capture(helper.getLevel(), fixture.area().id(), position, fixture.chunk(), 0L);
        var tag = plot.save();
        tag.putInt("BlockLight", light);
        tag.putInt("SkyLight", 0);
        plot = SettlementFieldPlot.load(tag);
        fixture.town().recordField(plot);
        return plot;
    }

    private static void setBlock(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15,
                position.getY() & 15, position.getZ() & 15, state);
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var chunk = new LevelChunk(level, new ChunkPos(650_000 + origin.getX() * 4, 650_000 + origin.getZ() * 4));
        BlockPos center = new BlockPos(chunk.getPos().getMinBlockX() + 8, 64, chunk.getPos().getMinBlockZ() + 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pasture",
                center.offset(-3, -3, -3), center.offset(3, 3, 3), 5, true);
        town.putArea(town.leader(), area);
        setBlock(chunk, center, Blocks.GRASS_BLOCK.defaultBlockState());
        var plot = SettlementFieldPlot.capture(level, area.id(), center, chunk, 0L);
        var tag = plot.save();
        tag.putInt("BlockLight", 15);
        plot = SettlementFieldPlot.load(tag);
        town.recordField(plot);
        Sheep sheep = EntityType.SHEEP.create(level);
        sheep.setPos(center.above().getBottomCenter());
        sheep.setSheared(true);
        var animal = SettlementLivestock.capture(sheep, area.id(), 0L);
        town.recordLivestock(animal);
        helper.assertFalse(level.hasChunkAt(center), "the native pasture chunk stays unloaded");
        check.accept(new Fixture(town, area, chunk, plot, animal));
        helper.succeed();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, LevelChunk chunk,
                           SettlementFieldPlot plot, SettlementLivestock animal) {
    }
}
