package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementLeadershipGameTests {
    private SettlementLeadershipGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leadershipSupportPausesWhileUnloadedAndPersists(GameTestHelper helper) {
        var data = new SettlementLeadershipSavedData();
        var id = VillageAllegianceId.random();
        data.observe(id, 0L, 5, 5, 100L, true, true);
        data.observe(id, 200L, 5, 5, 100L, true, true);
        data.observe(id, 400L, 0, 0, 0L, false, false);
        data.observe(id, 24_000L, 0, 0, 0L, false, false);
        helper.assertValueEqual(data.observation(id).sustainedTicks(), 200L, "unloaded independent villages do not gain support time");
        helper.assertValueEqual(data.observation(id).housing(), 5, "the last known housing observation is retained");
        var restored = SettlementLeadershipSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.observation(id), data.observation(id), "leadership observations survive reload");
        restored.observe(id, 24_200L, 5, 5, 100L, true, true);
        helper.assertValueEqual(restored.observation(id).sustainedTicks(), 200L, "reloading does not add unseen support time");
        restored.observe(id, 24_400L, 5, 4, 100L, true, false);
        helper.assertValueEqual(restored.observation(id).sustainedTicks(), 0L, "an observed shortage interrupts sustained support");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void independentLeadershipNeedsMajorityTrustAndPreservesResidentsAndContracts(GameTestHelper helper) {
        prepareVillage(helper, fixture -> {
            var level = helper.getLevel();
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var data = SettlementLeadershipSavedData.get(level);
            SettlementLeadershipService.observe(level, registry.record(fixture.id()).orElseThrow());
            var observation = data.observation(fixture.id());
            helper.assertTrue(observation.fullyLoaded(), "the village is fully observed");
            helper.assertValueEqual(observation.housing(), 3, "existing usable beds provide the required housing");
            for (long tick = 200L; tick <= SettlementProgressionService.requiredTicks(); tick += 200L) {
                data.observe(fixture.id(), level.getGameTime() + tick, 3, 3, 240L, true, true);
            }
            VillagerReputationManager.setReputation(level, fixture.residents().get(0), fixture.leader().getUUID(),
                    VillagerRetaliationConfig.REVERED_THRESHOLD.get());
            helper.assertFalse(SettlementLeadershipService.status(fixture.leader(), fixture.id()).orElseThrow().eligible(),
                    "one of three adults is not a majority");
            VillagerReputationManager.setReputation(level, fixture.residents().get(1), fixture.leader().getUUID(),
                    VillagerRetaliationConfig.REVERED_THRESHOLD.get());
            helper.assertTrue(SettlementLeadershipService.status(fixture.leader(), fixture.id()).orElseThrow().eligible(),
                    "two of three adults at Revered satisfy the majority requirement");
            helper.assertFalse(registry.isManaged(fixture.id()), "eligibility does not automatically transfer leadership");
            var hired = fixture.residents().get(2);
            HireContractStore.save(hired, HireContract.regular(UUID.randomUUID(), level.getGameTime(),
                    3, 4, 12, HiredVillagerRole.FARMING));
            CompoundTag paid = hired.getPersistentData().getCompound("VillagerRetaliationHireContract").copy();
            String name = registry.record(fixture.id()).orElseThrow().displayName();
            var town = SettlementLeadershipService.accept(fixture.leader(), fixture.id(), fixture.table()).orElseThrow();
            helper.assertValueEqual(town.id(), fixture.id(), "leadership retains the original village identity");
            helper.assertValueEqual(registry.record(town.id()).orElseThrow().displayName(), name, "the village name is preserved");
            helper.assertValueEqual(registry.residentRoster(town.id(), level.getGameTime()).size(), 3, "the existing census is preserved");
            helper.assertValueEqual(hired.getPersistentData().getCompound("VillagerRetaliationHireContract"), paid,
                    "leadership does not rewrite paid contracts or refund data");
            helper.assertTrue(town.duties().isEmpty(), "leadership does not automatically displace residents' current commitments");
            helper.assertValueEqual(fixture.residents().getFirst().getCustomName().getString(), "Resident 0",
                    "resident names are preserved");
            fixture.residents().forEach(Villager::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leadershipAcceptanceRechecksActualFoodAndBeds(GameTestHelper helper) {
        prepareVillage(helper, fixture -> {
            var level = helper.getLevel();
            var data = SettlementLeadershipSavedData.get(level);
            for (var resident : fixture.residents()) {
                VillagerReputationManager.setReputation(level, resident, fixture.leader().getUUID(),
                        VillagerRetaliationConfig.REVERED_THRESHOLD.get());
            }
            for (long tick = 0L; tick <= SettlementProgressionService.requiredTicks(); tick += 200L) {
                data.observe(fixture.id(), tick, 3, 3, 240L, true, true);
            }
            fixture.residents().forEach(resident -> resident.getInventory().clearContent());
            helper.assertTrue(SettlementLeadershipService.accept(fixture.leader(), fixture.id(), fixture.table()).isEmpty(),
                    "an old favorable food observation cannot approve an empty village");
            helper.assertFalse(VillageAllegianceRegistrySavedData.get(level).isManaged(fixture.id()),
                    "failed acceptance preserves independent leadership");
            helper.assertValueEqual(data.observation(fixture.id()).sustainedTicks(), 0L, "the observed shortage resets support");
            fixture.residents().forEach(Villager::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void managedTownRejectsAutomaticArrivalsButChildrenKeepTheirFamilyHome(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        Villager outsider = EntityType.VILLAGER.create(level);
        outsider.setPos(center.getCenter());
        outsider.setNoAi(true);
        outsider.finalizeSpawn(level, level.getCurrentDifficultyAt(center), MobSpawnType.NATURAL, null);
        level.addFreshEntity(outsider);
        helper.assertTrue(VillageAllegianceApi.canonicalPrimary(level, outsider).isEmpty(),
                "spawning inside a managed town does not bypass approval");
        helper.assertFalse(VillageAllegianceService.updateWanderingResidency(level, outsider),
                "waiting inside the town cannot bypass approval");
        helper.assertFalse(VillageAllegianceService.reassignToCurrentVillage(level, outsider),
                "the legacy reassignment helper cannot bypass staffing authority");
        Villager parent = EntityType.VILLAGER.create(level);
        parent.setPos(center.getCenter());
        parent.setNoAi(true);
        level.addFreshEntity(parent);
        VillageAllegianceApi.assignKnown(level, parent, id, AllegianceAssignmentSource.EXPLICIT_API);
        Villager child = EntityType.VILLAGER.create(level);
        child.setBaby(true);
        child.setPos(center.getCenter());
        VillageAllegianceService.assignBirthAllegiance(level, child, parent, parent);
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(level, child).orElseThrow(), town.id(),
                "children of residents can inherit their existing family home");
        Villager visitorChild = EntityType.VILLAGER.create(level);
        visitorChild.setBaby(true);
        visitorChild.setPos(center.getCenter());
        VillageAllegianceService.assignBirthAllegiance(level, visitorChild, outsider, outsider);
        helper.assertTrue(VillageAllegianceApi.canonicalPrimary(level, visitorChild).isEmpty(),
                "a birth among visitors does not silently enroll a new family");
        outsider.discard();
        parent.discard();
        child.discard();
        visitorChild.discard();
        helper.succeed();
    }

    private static void prepareVillage(GameTestHelper helper, java.util.function.Consumer<Fixture> test) {
        var level = helper.getLevel();
        List<BlockPos> beds = List.of(helper.absolutePos(new BlockPos(2, 2, 2)),
                helper.absolutePos(new BlockPos(4, 2, 2)), helper.absolutePos(new BlockPos(2, 2, 4)));
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)),
                helper.absolutePos(new BlockPos(6, 1, 6)))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos head : beds) {
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
        }
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(beds.getFirst()), beds.getFirst(), 1).orElseThrow();
        BlockPos table = helper.absolutePos(new BlockPos(6, 2, 1));
        level.setBlockAndUpdate(table, VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var id = registry.discoverAt(level, table).orElseThrow();
            for (long section : registry.record(id).orElseThrow().footprintSections()) {
                level.getChunk(SectionPos.x(section), SectionPos.z(section));
            }
            ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VillageLeader"));
            leader.setPos(table.getCenter());
            List<Villager> residents = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                Villager resident = EntityType.VILLAGER.create(level);
                resident.setPos(beds.get(index).getCenter());
                resident.setNoAi(true);
                resident.setCustomName(Component.literal("Resident " + index));
                resident.getInventory().addItem(new ItemStack(Items.BREAD, 16));
                level.addFreshEntity(resident);
                VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
                residents.add(resident);
            }
            try {
                test.accept(new Fixture(id, table, leader, List.copyOf(residents)));
            } finally {
                level.getPoiManager().release(beds.getFirst());
            }
        });
    }

    private record Fixture(VillageAllegianceId id, BlockPos table, ServerPlayer leader, List<Villager> residents) {
    }
}
