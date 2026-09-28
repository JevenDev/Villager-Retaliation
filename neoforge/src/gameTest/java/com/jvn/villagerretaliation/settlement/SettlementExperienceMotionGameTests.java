package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementExperienceMotionGameTests {
    private SettlementExperienceMotionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void unloadedIdleResidentMendsThroughActualOrbContact(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 3, 20);
        var state = experience(helper, fixture, resident, 2, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "both finite orbs reach the nearby idle resident");
        helper.assertValueEqual(damage(fixture.town().residents().get(resident.getUUID()).context(helper.getLevel())), 8,
                "native Mending applies exactly the two orb values");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "contact does not force the town to load");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void solidWallBlocksBackgroundMending(GameTestHelper helper) {
        var fixture = fixture(helper, true, true);
        var resident = resident(helper, fixture, 3, 20);
        var state = experience(helper, fixture, resident, 2, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertFalse(state.removed(), "orbs cannot cross the recorded wall");
        helper.assertValueEqual(state.count(), 2, "blocked contact consumes no experience");
        helper.assertValueEqual(damage(fixture.town().residents().get(resident.getUUID()).context(helper.getLevel())), 20,
                "an item route cannot bypass the physical wall");
        helper.assertTrue(state.context(helper.getLevel()).getX() < fixture.town().center().getX() + 1.0D,
                "native collision shapes stop the approaching orb");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void unknownTerrainCannotGrantRemoteRepairs(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        var resident = resident(helper, fixture, 3, 20);
        var position = fixture.town().center().getBottomCenter();
        var state = experience(helper, fixture, resident, 2, position);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertValueEqual(state.context(helper.getLevel()).position(), position, "unknown terrain holds the pending position");
        helper.assertValueEqual(state.count(), 2, "unknown terrain does not fabricate a pickup");
        helper.assertValueEqual((int) state.save().getCompound("Entity").getShort("Age"), 200, "running lifetime still advances");
        helper.assertValueEqual(damage(fixture.town().residents().get(resident.getUUID()).context(helper.getLevel())), 20,
                "the unobserved approach cannot mend equipment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void partialMendingAndMotionResumeAcrossSaves(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 0, 40);
        var oldBody = resident.saveWithoutId(new CompoundTag());
        var state = experience(helper, fixture, resident, 3, resident.position());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 2L);
        helper.assertValueEqual(state.count(), 2, "one native contact consumes one count");
        var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), () -> {});
        var care = restored.residents().get(resident.getUUID());
        resident.load(oldBody);
        helper.assertTrue(care.restore(resident), "pending idle repair reaches an older native body");
        helper.assertValueEqual(damage(resident), 34, "the older body receives only its actual repair");
        resident.getMainHandItem().setDamageValue(35);
        helper.assertFalse(care.restore(resident), "an acknowledged repair cannot replay after fresh tool wear");
        helper.assertValueEqual(damage(resident), 35, "new native wear remains intact");
        var replay = SettlementGroundExperience.load(state.save());
        fixture.town().removeGroundExperience(state.id());
        fixture.town().recordGroundExperience(replay);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 4L);
        helper.assertTrue(replay.removed(), "the persisted target resumes its remaining two contacts");
        helper.assertValueEqual(damage(fixture.town().residents().get(resident.getUUID()).context(helper.getLevel())), 22,
                "all three contacts conserve the original finite reward");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void passiveWorkerMendingDoesNotSpendProductionProgress(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 3, 20);
        fixture.town().assignDuty(resident.getUUID(), fixture.area().id());
        var worker = SettlementWorkerState.capture(helper.getLevel(), resident,
                new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        worker.work(200, 1.0D);
        fixture.town().recordWorker(worker);
        var state = experience(helper, fixture, resident, 2, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "the civic worker receives passive owned experience");
        helper.assertValueEqual(worker.progress(), 200.0D, "Mending does not spend a work interval");
        helper.assertValueEqual(worker.items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 8,
                "the authoritative job inventory contains the repair");
        worker.restore(resident);
        helper.assertValueEqual(damage(resident), 8, "the native worker receives the repaired inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void nearestEligibleVillagerKeepsNativeTargetingPolicy(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var owner = resident(helper, fixture, 4, 20);
        var nearest = resident(helper, fixture, 1, 20);
        var state = experience(helper, fixture, owner, 1, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "the native nearest eligible target receives the orb");
        helper.assertValueEqual(damage(fixture.town().residents().get(nearest.getUUID()).context(helper.getLevel())), 14,
                "background targeting shares the native nearest-villager rule");
        helper.assertValueEqual(damage(fixture.town().residents().get(owner.getUUID()).context(helper.getLevel())), 20,
                "the farther owner cannot receive an additional repair");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void fullMendingEquipmentCanConsumeItsOwnedExperience(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 1, 0);
        var state = experience(helper, fixture, resident, 2, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "owned work experience is consumed even after its owner finishes repairing");
        helper.assertValueEqual(damage(fixture.town().residents().get(resident.getUUID()).context(helper.getLevel())), 0,
                "native undamaged equipment remains undamaged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void unownedExperienceDoesNotDisappearIntoUndamagedEquipment(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 1, 0);
        var state = experience(helper, fixture, null, 2, fixture.town().center().getBottomCenter());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertFalse(state.removed(), "ordinary orbs require an actual native Mending need");
        helper.assertValueEqual(state.count(), 2, "undamaged equipment cannot silently consume unowned experience");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void movementRetainsNativeStoragePositionAndMomentum(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var orb = new ExperienceOrb(helper.getLevel(), fixture.town().center().getX() + 0.5D, 86.0D,
                fixture.town().center().getZ() + 0.5D, 3);
        orb.setNoGravity(true);
        orb.setDeltaMovement(0.6D, 0.0D, 0.0D);
        var source = orb.blockPosition();
        var state = SettlementGroundExperience.capture(orb, 0L);
        fixture.town().recordGroundExperience(state);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 30L);
        helper.assertTrue(ChunkPos.asLong(state.position()) != ChunkPos.asLong(source), "background motion crosses a chunk boundary");
        helper.assertValueEqual(state.nativePosition(), source, "absence checks still use the original stored body location");
        var restored = SettlementGroundExperience.load(state.save());
        helper.assertValueEqual(restored.nativePosition(), source, "the storage anchor survives a save");
        var before = restored.context(helper.getLevel());
        fixture.town().removeGroundExperience(state.id());
        fixture.town().recordGroundExperience(restored);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 31L);
        helper.assertTrue(restored.context(helper.getLevel()).getX() > before.getX(), "saved momentum continues instead of restarting the approach");
        helper.assertTrue(restored.nativeChunks().contains(ChunkPos.asLong(source)), "the original native receipt anchor is retained");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void laterArmorRepairCannotReplayAnAcknowledgedWeaponRepair(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var resident = resident(helper, fixture, 0, 20);
        var helmet = new ItemStack(Items.IRON_HELMET);
        helmet.setDamageValue(50);
        resident.setItemSlot(EquipmentSlot.HEAD, helmet);
        var care = fixture.town().residents().get(resident.getUUID());
        care.remember(resident);
        var before = new java.util.EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        before.put(EquipmentSlot.MAINHAND, resident.getMainHandItem().copy());
        resident.getMainHandItem().setDamageValue(14);
        care.mend(resident, before);
        var nativeBody = care.context(helper.getLevel());
        nativeBody.getMainHandItem().setDamageValue(15);
        before.clear();
        before.put(EquipmentSlot.HEAD, resident.getItemBySlot(EquipmentSlot.HEAD).copy());
        resident.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(44);
        care.mend(resident, before);
        var restored = SettlementResidentState.load(care.save());
        restored.restore(nativeBody);
        helper.assertValueEqual(nativeBody.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(), 44, "the new armor repair reaches native equipment");
        helper.assertValueEqual(damage(nativeBody), 15, "an earlier acknowledged weapon repair cannot erase fresh wear");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void ordinaryGroundMotionMatchesNativeOrbTicks(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        for (var floor : BlockPos.betweenClosed(position.offset(-3, -1, -3), position.offset(5, -1, 3))) {
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        var nativeOrb = new ExperienceOrb(helper.getLevel(), position.getX() + 0.5D, position.getY() + 0.1D, position.getZ() + 0.5D, 3);
        nativeOrb.setDeltaMovement(0.05D, 0.02D, 0.0D);
        var projected = new ExperienceOrb(helper.getLevel(), 0, 0, 0, 1);
        projected.load(nativeOrb.saveWithoutId(new CompoundTag()));
        new SettlementExperienceSimulation(helper.getLevel(), fixture.town()).advance(projected, 20);
        for (int tick = 0; tick < 20; tick++) {
            nativeOrb.tickCount++;
            nativeOrb.tick();
        }
        helper.assertTrue(projected.position().distanceToSqr(nativeOrb.position()) < 1.0E-12D,
                "native collision, gravity and ground friction produce the same position");
        helper.assertTrue(projected.getDeltaMovement().distanceToSqr(nativeOrb.getDeltaMovement()) < 1.0E-12D,
                "native motion persists the same remaining velocity");
        helper.assertValueEqual((int) projected.saveWithoutId(new CompoundTag()).getShort("Age"), 20,
                "one physical simulation step equals one native age tick");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void backgroundPlayerContactUsesTheNativeCancelablePickup(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        var position = helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter();
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(position);
        var orb = new ExperienceOrb(helper.getLevel(), position.x, position.y, position.z, 3);
        orb.setNoGravity(true);
        orb.setDeltaMovement(Vec3.ZERO);
        var tag = orb.saveWithoutId(new CompoundTag());
        tag.putInt("Count", 2);
        orb.load(tag);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp> cancel = event -> {
            if (event.getOrb() == orb) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, cancel);
        int before = player.totalExperience;
        try {
            new SettlementExperienceSimulation(helper.getLevel(), fixture.town()).advance(orb, 1);
            helper.assertValueEqual(player.totalExperience, before, "native cancellation prevents the background pickup");
            helper.assertValueEqual(orb.saveWithoutId(new CompoundTag()).getInt("Count"), 2, "canceled pickup keeps both finite counts");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        try {
            new SettlementExperienceSimulation(helper.getLevel(), fixture.town()).advance(orb, 1);
            helper.assertValueEqual(player.totalExperience - before, 3, "the native player receives exactly one approved value");
            helper.assertValueEqual(orb.saveWithoutId(new CompoundTag()).getInt("Count"), 1, "native pickup leaves the unconsumed count");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_experience_motion_handoff")
    public static void loadedOrbMovementReconcilesAcrossNativeEntitySections(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var level = helper.getLevel();
        var source = new ChunkPos(fixture.town().center());
        var destination = new ChunkPos(source.x + 1, source.z);
        for (var chunk : java.util.List.of(source, destination)) {
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
            level.getChunk(chunk.x, chunk.z);
        }
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(source.toLong()) && level.areEntitiesLoaded(destination.toLong()), "both native entity sections are ready");
        }).thenExecute(() -> {
            var orb = new ExperienceOrb(level, (source.x << 4) + 15.5D, 86.0D, fixture.town().center().getZ() + 0.5D, 3);
            orb.setNoGravity(true);
            orb.setDeltaMovement(0.6D, 0.0D, 0.0D);
            try {
                helper.assertFalse(level.isPositionEntityTicking(orb.blockPosition()), "the native body is visible but not ticking");
                helper.assertTrue(level.addFreshEntity(orb), "the native orb enters its original section");
                var oldBody = orb.saveWithoutId(new CompoundTag());
                var state = fixture.town().groundExperience().get(orb.getUUID());
                long now = state.checkedAt() + 5L;
                SettlementGroundExperienceService.update(level, fixture.town(), now);
                helper.assertValueEqual(new ChunkPos(orb.blockPosition()), destination, "pending movement relocates the actual native body");
                state = fixture.town().groundExperience().get(orb.getUUID());
                helper.assertTrue(state.nativeChunks().contains(source.toLong()) && state.nativeChunks().contains(destination.toLong()),
                        "both native storage locations retain replay receipts");
                helper.assertValueEqual(state.nativePosition(), orb.blockPosition(), "handoff acknowledges the new native location");
                var moved = orb.position();
                SettlementGroundExperienceService.update(level, fixture.town(), now);
                helper.assertValueEqual(orb.position(), moved, "section callbacks cannot repeat the same movement");
                orb.discard();
                var replay = new ExperienceOrb(level, 0, 0, 0, 1);
                replay.load(oldBody);
                helper.assertFalse(level.addFreshEntity(replay), "the original section receipt rejects the consumed older body");
            } finally {
                orb.discard();
                for (var chunk : java.util.List.of(source, destination)) {
                    level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
                }
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void removedExperienceAcknowledgesHistoricalChunksIndependently(GameTestHelper helper) {
        var fixture = fixture(helper, true, false);
        var state = experience(helper, fixture, null, 1, fixture.town().center().getBottomCenter());
        long first = ChunkPos.asLong(state.nativePosition());
        state.nativePosition(state.nativePosition().east(32));
        long second = ChunkPos.asLong(state.nativePosition());
        state.remove();
        state.acknowledge(first);
        var restored = SettlementGroundExperience.load(state.save());
        helper.assertFalse(restored.acknowledged(), "the unloaded historical chunk remains pending until its receipt is written");
        restored.acknowledge(second);
        helper.assertTrue(restored.acknowledged(), "historical chunks can acknowledge removal on separate visits");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience_motion")
    public static void specialBlockInteractionsWaitForNativeHandling(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(position, Blocks.COBWEB.defaultBlockState());
        var orb = new ExperienceOrb(helper.getLevel(), position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 3);
        orb.setDeltaMovement(0.2D, 0.0D, 0.0D);
        var before = orb.position();
        new SettlementExperienceSimulation(helper.getLevel(), fixture.town()).advance(orb, 20);
        helper.assertValueEqual(orb.position(), before, "unsupported block effects cannot be crossed as ordinary air");
        helper.assertValueEqual((int) orb.saveWithoutId(new CompoundTag()).getShort("Age"), 20,
                "deferring a special environment does not freeze the running lifetime");
        helper.assertTrue(orb.isAlive(), "native handling retains the finite reward");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean terrain, boolean wall) {
        var level = helper.getLevel();
        var test = helper.absolutePos(new BlockPos(2, 2, 2));
        var origin = new BlockPos(14_000_000 + test.getX() * 4, test.getY(), 14_000_000 + test.getZ() * 4);
        var center = new BlockPos((origin.getX() & ~15) + 8, 85, (origin.getZ() & ~15) + 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen",
                center.offset(-10, -2, -10), center.offset(30, 5, 10), 5, true);
        SettlementTestPlots.claim(level, town, area.min(), area.max());
        town.putArea(town.leader(), area);
        if (terrain) {
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                var section = new BlockPos((center.getX() & ~15) + x * 16, 80, (center.getZ() & ~15) + z * 16);
                var tag = new CompoundTag();
                tag.putLong("Origin", section.asLong());
                var palette = new ListTag();
                palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
                palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
                tag.put("Palette", palette);
                var blocks = new byte[8192];
                for (var position : BlockPos.betweenClosed(section, section.offset(15, 15, 15))) {
                    if (position.getY() < center.getY() || wall && position.getX() == center.getX() + 1) {
                        int index = (((position.getY() & 15) << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15)) * 2;
                        blocks[index + 1] = 1;
                    }
                }
                tag.putByteArray("Blocks", blocks);
                town.recordNavigation(SettlementNavigationSection.load(tag));
            }
        }
        return new Fixture(town, area);
    }

    private static Villager resident(GameTestHelper helper, Fixture fixture, int distance, int damage) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.town().center().east(distance).getBottomCenter());
        villager.setNoAi(true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(damage);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, sword);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        fixture.town().recordResident(SettlementResidentState.capture(villager, fixture.town(), 0L));
        return villager;
    }

    private static SettlementGroundExperience experience(GameTestHelper helper, Fixture fixture, Villager owner, int count, Vec3 position) {
        var orb = new ExperienceOrb(helper.getLevel(), position.x, position.y, position.z, 3);
        orb.setDeltaMovement(Vec3.ZERO);
        var tag = orb.saveWithoutId(new CompoundTag());
        tag.putInt("Count", count);
        orb.load(tag);
        if (owner != null) orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", owner.getUUID());
        var state = SettlementGroundExperience.capture(orb, 0L);
        fixture.town().recordGroundExperience(state);
        return state;
    }

    private static int damage(Villager villager) {
        return villager.getMainHandItem().getDamageValue();
    }

    private record Fixture(SettlementRecord town, SettlementArea area) {
    }
}
