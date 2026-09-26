package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementBackgroundConstructionGameTests {
    private SettlementBackgroundConstructionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void anUnloadedBuilderCompletesARealHouseFromFiniteDepotMaterials(GameTestHelper helper) {
        fixture(helper, false, null, fixture -> {
            int slot = 0;
            for (var material : fixture.plan().materials()) {
                int remaining = material.count();
                while (remaining > 0) {
                    int count = Math.min(64, remaining);
                    fixture.depot().setItem(slot++, material.item().copyWithCount(count));
                    remaining -= count;
                }
            }
            fixture.depot().setItem(slot++, new ItemStack(Items.IRON_AXE));
            fixture.depot().setItem(slot++, new ItemStack(Items.IRON_SHOVEL));
            fixture.depot().setItem(slot, new ItemStack(Items.IRON_HOE));
            work(helper, fixture, 1000);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED,
                    "the real house completes using background work: " + fixture.worker().status() + " / " + fixture.project().blocker());
            for (var block : fixture.plan().blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    fixture.site().getBlockState(fixture.plan().worldPos(fixture.origin(), block)), block.state()), "every real blueprint block is recorded");
            for (var material : fixture.plan().materials()) helper.assertValueEqual(count(helper, fixture, material.item().getItem()), 0,
                    "each blueprint material is consumed exactly once");
            var saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(restored.projects().get(fixture.project().id()).site().pending(), "the completed house retains its pending world placements");
            int pending = fixture.site().placements().size();
            work(helper, fixture, 10);
            helper.assertValueEqual(fixture.site().placements().size(), pending, "completion cannot enqueue another house");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "building never forces the remote site to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void missingMaterialsPreserveTheSiteAndExposeTheMissingItem(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            work(helper, fixture, 10);
            helper.assertTrue(fixture.site().getBlockState(fixture.origin()).isAir(), "missing inputs cannot create a block");
            helper.assertValueEqual(fixture.worker().status(), "missing_construction_materials", "the worker reports the actual supply blocker");
            helper.assertFalse(fixture.project().missingMaterials().isBlank(), "the project identifies the missing material");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void disabledDepotAccessCannotConsumeConstructionMaterials(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", false);
            work(helper, fixture, 10);
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 2, "disabled depot access preserves the real stack");
            fixture.worker().settings().putBoolean("UseAssignedStorageForSupplies", true);
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "restoring access resumes the same project");
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 1, "one block consumes one permitted plank");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void linkedDoorHalvesConsumeOneDoorAndRemainOnePlacement(GameTestHelper helper) {
        fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
                BlockPos.ZERO.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_DOOR));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "both door halves finish");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_DOOR), 0, "one real door pays for both halves");
            helper.assertValueEqual(fixture.site().placements().size(), 1, "linked halves share one pending operation");
            helper.assertValueEqual(fixture.site().placements().values().iterator().next().parts().size(), 2, "the operation preserves both halves");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void linkedBedPartsConsumeOneBed(GameTestHelper helper) {
        var foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
        fixture(helper, false, Map.of(BlockPos.ZERO, foot, BlockPos.ZERO.east(), foot.setValue(BedBlock.PART, BedPart.HEAD)), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.RED_BED));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "both bed parts finish");
            helper.assertValueEqual(count(helper, fixture, Items.RED_BED), 0, "the bed is not charged twice");
            helper.assertValueEqual(fixture.site().placements().size(), 1, "the bed remains a single world operation");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void strippedLogsReuseTheExistingLogAndWearTheRequiredAxe(GameTestHelper helper) {
        fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.STRIPPED_OAK_LOG.defaultBlockState()), fixture -> {
            scene(fixture, Map.of(fixture.origin(), Blocks.OAK_LOG.defaultBlockState()));
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_AXE));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "the observed log can be stripped in place");
            helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 1,
                    "stripping wears the actual axe once");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_LOG), 0, "transforming an existing log does not invent or require another log");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void unsupportedBlocksWaitWithoutSpendingTheirMaterial(GameTestHelper helper) {
        fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.TORCH.defaultBlockState()), fixture -> {
            scene(fixture, Map.of(fixture.origin().below(), Blocks.AIR.defaultBlockState()));
            fixture.depot().setItem(0, new ItemStack(Items.TORCH));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.worker().status(), "construction_blocked_support", "native survival rules require support");
            helper.assertValueEqual(count(helper, fixture, Items.TORCH), 1, "support failure preserves the material");
            helper.assertFalse(fixture.site().pending(), "an unsupported block cannot enter the world queue");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void unsafeRoutesCannotSpendConstructionMaterials(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            scene(fixture, Map.of(fixture.worker().position(), Blocks.LAVA.defaultBlockState()));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.worker().status(), "construction_unreachable", "the builder requires an observed safe route");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "unsafe access preserves real supplies");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void clearingUsesARealToolAndRetainsTheMinedOutput(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            scene(fixture, Map.of(fixture.origin(), Blocks.STONE.defaultBlockState()));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "the cleared site receives its planned block");
            helper.assertValueEqual(count(helper, fixture, Items.COBBLESTONE), 1, "the removed stone produces one real output");
            helper.assertValueEqual(fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 1,
                    "clearing damages the actual tool once");
            helper.assertValueEqual(fixture.site().placements().size(), 2, "clearing and building remain ordered world operations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void existingBlockEntitiesPreventClearingOrMaterialConsumption(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            scene(fixture, Map.of(fixture.origin(), Blocks.CHEST.defaultBlockState()));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.worker().status(), "construction_blocked_existing", "existing container contents remain protected");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "the blocked project preserves its input");
            helper.assertFalse(fixture.site().pending(), "a protected container cannot be queued for replacement");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void constructionReceiptsPreventOldJournalsFromReplacingLaterPlayerEdits(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            var placement = placement(fixture, Blocks.OAK_PLANKS.defaultBlockState(), null);
            var saved = placement.save();
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "the initial placement is delivered");
            level.setBlock(fixture.origin(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            helper.assertTrue(SettlementConstructionPlacement.load(saved).reconcile(level, fixture.project().id()), "an older journal recognizes the receipt");
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.DIAMOND_BLOCK), "replayed delivery preserves the player's later block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void terrainConflictsSurviveSaveAndResumeOnlyWhenTheOriginalSiteIsRestored(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            var placement = placement(fixture, Blocks.OAK_PLANKS.defaultBlockState(), null);
            fixture.site().queue(placement);
            SettlementConstructionSiteService.refresh(level, fixture.site());
            helper.assertTrue(fixture.site().conflict(), "a changed site blocks pending delivery");
            var restored = SettlementConstructionSite.load(fixture.project().id(), fixture.site().save());
            helper.assertTrue(restored.pending() && restored.conflict(), "the changed baseline and pending operation both survive saving");
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.DIAMOND_BLOCK), "the conflicting build remains untouched");
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            SettlementConstructionSiteService.refresh(level, restored);
            helper.assertFalse(restored.pending(), "restoring the original site resumes the saved operation");
            helper.assertTrue(level.getBlockState(fixture.origin()).is(Blocks.OAK_PLANKS), "the original planned material becomes the real block");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void occupyingEntitiesDelayWorldPlacementWithoutCreatingAConflict(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            var animal = EntityType.COW.create(level);
            animal.setNoAi(true);
            animal.setPos(fixture.origin().getBottomCenter());
            level.addFreshEntity(animal);
            try {
                var placement = placement(fixture, Blocks.OAK_PLANKS.defaultBlockState(), null);
                helper.assertFalse(placement.reconcile(level, fixture.project().id()), "an occupied block cannot materialize inside an entity");
                helper.assertFalse(placement.conflict(), "temporary occupancy is not a terrain conflict");
                animal.discard();
                helper.assertTrue(placement.reconcile(level, fixture.project().id()), "delivery resumes once the entity leaves");
            } finally {
                animal.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void linkedPlacementsWaitForEveryChunkBeforeChangingEitherPart(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            var remote = fixture.origin().offset(8_000_000, 0, 8_000_000);
            var placement = new SettlementConstructionPlacement(List.of(
                    new SettlementConstructionPlacement.Part(fixture.origin(), Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), null),
                    new SettlementConstructionPlacement.Part(remote, Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), null)));
            helper.assertFalse(placement.reconcile(level, fixture.project().id()), "a missing partner chunk delays the operation");
            helper.assertTrue(level.getBlockState(fixture.origin()).isAir(), "the ready half remains unchanged");
            helper.assertFalse(level.hasChunkAt(remote), "delivery does not force its missing chunk");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void newBlockEntitiesRetainTheirConfigurationWithoutBlueprintLoot(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            level.setBlock(fixture.origin(), Blocks.AIR.defaultBlockState(), 2);
            var tag = new CompoundTag();
            tag.putString("CustomName", "\"Workshop\"");
            tag.putString("LootTable", "minecraft:chests/simple_dungeon");
            tag.putLong("LootTableSeed", 7L);
            var placement = placement(fixture, Blocks.CHEST.defaultBlockState(), tag);
            helper.assertTrue(placement.reconcile(level, fixture.project().id()), "a configured block entity materializes");
            var saved = level.getBlockEntity(fixture.origin()).saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(saved.contains("CustomName"), "the blueprint's explicit configuration survives");
            helper.assertFalse(saved.contains("LootTable") || saved.contains("LootTableSeed"), "construction follows the native loot sanitization rules");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void cancelingUnloadedWorkRetainsCompletedPlacementsAndCannotRefundTheirMaterials(GameTestHelper helper) {
        fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().placed(), 1, "one supplied block completes before the shortage");
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.CANCELLED);
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.site().placements().size(), 1, "canceling retains only already performed world work");
            SettlementBackgroundService.onActive(fixture.builder());
            SettlementBackgroundService.onActive(fixture.builder());
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(fixture.builder())), "the old live task cannot resume after cancellation");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 0, "canceling cannot refund a material already used in a pending block");
            var replacement = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), fixture.plan(),
                    fixture.origin(), UUID.randomUUID(), 0L);
            helper.assertFalse(fixture.town().startProject(fixture.town().leader(), replacement), "a new project cannot overwrite pending work at the same footprint");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void aReturningBuilderKeepsBackgroundProgressAndSpentInputs(GameTestHelper helper) {
        fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            work(helper, fixture, 20);
            SettlementBackgroundService.onActive(fixture.builder());
            helper.assertValueEqual(BuilderTaskState.placedIndex(HiredWorkStateStore.state(fixture.builder())), 1,
                    "the returning live task resumes after the background placement");
            SettlementBackgroundService.onUnloaded(fixture.builder());
            helper.assertValueEqual(fixture.project().placed(), 1, "observing the old entity cannot rewind project progress");
            helper.assertTrue(HiredJobInventory.getJobInventory(fixture.builder()).findSupply(stack -> stack.is(Items.OAK_PLANKS)).isEmpty(),
                    "live reconciliation preserves spent inputs");
            helper.assertFalse(SettlementConstructionService.prepare(fixture.builder(), HiredWorkStateStore.state(fixture.builder())),
                    "native building waits for pending world work to reconcile");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void activeNativeBuildersAreExcludedFromBackgroundExecution(GameTestHelper helper) {
        fixture(helper, true, planks(), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            helper.getLevel().addFreshEntity(fixture.builder());
            SettlementBackgroundService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 200L);
            helper.assertValueEqual(fixture.project().placed(), 0, "active builders remain under native control");
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 1, "the same interval cannot spend background materials");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void olderProjectsLoadWithoutConstructionObservations(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            var saved = fixture.project().save();
            saved.remove("Site");
            var restored = SettlementProject.load(saved);
            helper.assertTrue(restored.site() == null && restored.active(), "older projects retain their active task without fabricated observations");
            helper.assertValueEqual(restored.structure(), fixture.project().structure(), "the original blueprint identity survives");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void alreadyBuiltBlocksDoNotConsumeMoreMaterials(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            scene(fixture, Map.of(fixture.origin(), Blocks.OAK_PLANKS.defaultBlockState()));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            work(helper, fixture, 10);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "an already matching site completes");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "matching blocks do not consume another material");
            helper.assertFalse(fixture.site().pending(), "matching blocks create no pending operation");
        });
    }

    private static SettlementConstructionPlacement placement(Fixture fixture, BlockState target, CompoundTag blockEntity) {
        return new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(
                fixture.origin(), Blocks.AIR.defaultBlockState(), target, blockEntity)));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void disablingTheConstructionAreaStopsUnloadedWork(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(fixture.town().putArea(fixture.town().leader(), new SettlementArea(fixture.area().id(),
                    SettlementAreaType.CONSTRUCTION, fixture.area().name(), fixture.area().min(), fixture.area().max(), 5, false)),
                    "the construction area can be disabled");
            work(helper, fixture, 20);
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "disabled work preserves supplies");
            helper.assertFalse(fixture.site().pending(), "disabled work cannot queue more blocks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void aChangedWorldBorderStopsConstructionBeforeSpendingMaterials(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            var border = helper.getLevel().getWorldBorder();
            double size = border.getSize();
            double x = border.getCenterX();
            double z = border.getCenterZ();
            try {
                border.setCenter(0.0D, 0.0D);
                border.setSize(16.0D);
                fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
                work(helper, fixture, 20);
                helper.assertValueEqual(fixture.worker().status(), "construction_outside_world", "the changed border is rechecked");
                helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "out-of-bounds work preserves supplies");
                helper.assertFalse(fixture.site().pending(), "out-of-bounds work creates no pending block");
            } finally {
                border.setCenter(x, z);
                border.setSize(size);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void clearingCannotReachThroughAProtectedEnclosure(GameTestHelper helper) {
        fixture(helper, false, planks(), fixture -> {
            var blocks = new LinkedHashMap<BlockPos, BlockState>();
            blocks.put(fixture.origin(), Blocks.STONE.defaultBlockState());
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    for (int y = 0; y < 4; y++) blocks.put(fixture.origin().offset(x, y, z), Blocks.BEDROCK.defaultBlockState());
                }
            }
            scene(fixture, blocks);
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            var items = fixture.worker().items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), items);
            work(helper, fixture, 10);
            helper.assertValueEqual(fixture.worker().status(), "construction_unreachable", "clearing requires a reachable visible block face");
            helper.assertFalse(fixture.site().pending(), "the enclosure prevents remote excavation through its walls");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 1, "unreachable clearing preserves placement inputs");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_background_construction")
    public static void constructionFetchesTheNativeMaterialBatchInOneTrip(GameTestHelper helper) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < 8; x++) blocks.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
        fixture(helper, false, blocks, fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            fixture.worker().work(1000, 1.0D);
            work(helper, fixture, 1);
            helper.assertTrue(fixture.depot().getItem(0).isEmpty(), "one trip collects the batch that fits the native inventory");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 8, "the fetched materials are carried rather than consumed early");
            helper.assertFalse(fixture.site().pending(), "collecting a batch does not place blocks");
            work(helper, fixture, 20);
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED, "the carried batch supplies the remaining work");
            helper.assertValueEqual(count(helper, fixture, Items.OAK_PLANKS), 0, "eight completed blocks consume all eight materials");
        });
    }

    private static Map<BlockPos, BlockState> planks() {
        return Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState());
    }

    private static void work(GameTestHelper helper, Fixture fixture, int intervals) {
        for (int index = 0; index < intervals; index++) SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private static int count(GameTestHelper helper, Fixture fixture, Item item) {
        return fixture.depot().count(stack -> stack.is(item)) + fixture.worker().items(helper.getLevel().registryAccess()).stream()
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void fixture(GameTestHelper helper, boolean loaded, Map<BlockPos, BlockState> blocks, Consumer<Fixture> check) {
        var level = helper.getLevel();
        var id = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
        var template = level.getStructureManager().get(id).orElseThrow();
        var original = template.save(new CompoundTag());
        if (blocks != null) {
            template.load(BuiltInRegistries.BLOCK.asLookup(), template(blocks));
            BuilderStructureScanner.clearCache();
        }
        Villager builder = null;
        try {
            var entry = BuilderStructureCatalog.byId(level.getServer(), id).orElseThrow();
            var plan = BuilderStructureScanner.scan(level, entry, Rotation.NONE).orElseThrow();
            var base = helper.absolutePos(new BlockPos(2, 2, 2));
            var origin = loaded ? base : base.offset(8_000_000, 0, 8_000_000).atY(80);
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var townId = registry.create(0L, level.dimension().location(), origin, "");
            var town = SettlementSavedData.get(level).establish(registry, townId, UUID.randomUUID(), origin, 0L).orElseThrow();
            town.advance();
            var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Construction",
                    origin.offset(-4, -1, -4), origin.offset(plan.localMax()).offset(4, 4, 4), 5, true);
            town.putArea(town.leader(), area);
            var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Materials",
                    origin.east(plan.localMax().getX() + 7), origin.offset(plan.localMax().getX() + 9, 2, 2), 5, true);
            town.putArea(town.leader(), storage);
            var depot = town.registerDepot(town.leader(), storage.id(), storage.min(), SettlementDepotPurpose.MATERIALS);
            depot.observeRules(List.of());
            builder = EntityType.VILLAGER.create(level);
            builder.setPos(origin.offset(-2, 0, 0).getBottomCenter());
            builder.setOnGround(true);
            builder.setNoAi(true);
            builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
            VillageAllegianceApi.assignKnown(level, builder, townId, AllegianceAssignmentSource.EXPLICIT_API);
            town.assignDuty(builder.getUUID(), area.id());
            SettlementWorkforceService.reconcile(level, builder);
            var project = SettlementProject.create(town, area.id(), builder.getUUID(), plan, origin, UUID.randomUUID(), 0L);
            helper.assertTrue(town.startProject(town.leader(), project), "the fixture has an authorized construction project");
            project.restore(HiredWorkStateStore.state(builder));
            var worker = SettlementWorkerState.capture(level, builder, SettlementWorkforceService.duty(builder).orElseThrow(), 0L);
            town.recordWorker(worker);
            town.recordResident(SettlementResidentState.capture(builder, town, 0L));
            project.observeSite(area);
            var fixture = new Fixture(town, area, depot, builder, worker, project, project.site(), origin, plan);
            scene(fixture, Map.of());
            check.accept(fixture);
        } finally {
            if (builder != null && level.getEntity(builder.getUUID()) != null) builder.discard();
            if (blocks != null) {
                template.load(BuiltInRegistries.BLOCK.asLookup(), original);
                BuilderStructureScanner.clearCache();
            }
        }
        helper.succeed();
    }

    private static CompoundTag template(Map<BlockPos, BlockState> blocks) {
        var tag = new CompoundTag();
        var palette = new ListTag();
        var entries = new ListTag();
        int x = 1;
        int y = 1;
        int z = 1;
        for (var block : blocks.entrySet()) {
            var entry = new CompoundTag();
            entry.put("pos", ints(block.getKey().getX(), block.getKey().getY(), block.getKey().getZ()));
            entry.putInt("state", palette.size());
            palette.add(NbtUtils.writeBlockState(block.getValue()));
            entries.add(entry);
            x = Math.max(x, block.getKey().getX() + 1);
            y = Math.max(y, block.getKey().getY() + 1);
            z = Math.max(z, block.getKey().getZ() + 1);
        }
        tag.put("size", ints(x, y, z));
        tag.put("palette", palette);
        tag.put("blocks", entries);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag ints(int x, int y, int z) {
        var result = new ListTag();
        result.add(IntTag.valueOf(x));
        result.add(IntTag.valueOf(y));
        result.add(IntTag.valueOf(z));
        return result;
    }

    private static void scene(Fixture fixture, Map<BlockPos, BlockState> overrides) {
        var site = fixture.site();
        for (int x = site.min().getX() >> 4; x <= site.max().getX() >> 4; x++) {
            for (int y = site.min().getY() >> 4; y <= site.max().getY() >> 4; y++) {
                for (int z = site.min().getZ() >> 4; z <= site.max().getZ() >> 4; z++) {
                    var min = new BlockPos(Math.max(x << 4, site.min().getX()), Math.max(y << 4, site.min().getY()), Math.max(z << 4, site.min().getZ()));
                    var max = new BlockPos(Math.min((x << 4) + 15, site.max().getX()), Math.min((y << 4) + 15, site.max().getY()), Math.min((z << 4) + 15, site.max().getZ()));
                    var palette = new ArrayList<BlockState>();
                    int[] cells = new int[(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1)];
                    int index = 0;
                    for (int by = min.getY(); by <= max.getY(); by++) {
                        for (int bz = min.getZ(); bz <= max.getZ(); bz++) {
                            for (int bx = min.getX(); bx <= max.getX(); bx++) {
                                var pos = new BlockPos(bx, by, bz);
                                var state = overrides.getOrDefault(pos, by < fixture.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
                                if (!palette.contains(state)) palette.add(state);
                                cells[index++] = palette.indexOf(state);
                            }
                        }
                    }
                    var tag = new CompoundTag();
                    tag.putUUID("Id", UUID.randomUUID());
                    tag.putLong("Min", min.asLong());
                    tag.putLong("Max", max.asLong());
                    var states = new ListTag();
                    palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
                    tag.put("Palette", states);
                    tag.putIntArray("Baseline", cells);
                    site.record(SettlementTerrainSection.load("ConstructionSurvey", fixture.project().id(), tag));
                }
            }
        }
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementDepot depot, Villager builder,
                           SettlementWorkerState worker, SettlementProject project, SettlementConstructionSite site,
                           BlockPos origin, BuilderStructureScanner.StructurePlan plan) {
    }
}
