package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementPlotGameTests {
    private SettlementPlotGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void plotTransactionsRespectPermissionsReservesAndBankedCredits(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        town.advance();
        town.economy().credit(CurrencyAmount.of(100, 1));
        town.economy().configureBudget(CurrencyAmount.of(90, 1), CurrencyAmount.ZERO);
        int x = town.territory().foundingChunk().x + 2, z = town.territory().foundingChunk().z;
        helper.assertValueEqual(claim(fixture.leader(), town, x, z, false), "funds", "reserve prevents spending");
        helper.assertValueEqual(town.economy().balance(), CurrencyAmount.of(100, 1), "failure preserves funds");
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PlotBuilder"));
        town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertValueEqual(claim(delegate, town, x, z, false), "permission", "paid claims require treasury authority");
        helper.assertValueEqual(claim(delegate, town, x, z, true), "", "Build permission can spend settlement credits");
        helper.assertValueEqual(town.territory().freeClaims(), 3, "one free credit is spent");
        helper.assertValueEqual(town.territory().paidPurchases(), 0, "free claims do not increase pricing");
        town.economy().configureBudget(CurrencyAmount.ZERO, CurrencyAmount.ZERO);
        helper.assertValueEqual(claim(fixture.leader(), town, x + 1, z, false), "", "players can pay while saving credits");
        helper.assertValueEqual(town.territory().freeClaims(), 3, "paid claims preserve banked credits");
        helper.assertValueEqual(town.economy().balance(), CurrencyAmount.of(76, 1), "the exact server quote is charged");
        var saved = SettlementSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = SettlementSavedData.load(saved, helper.getLevel().registryAccess());
        var loaded = restored.get(town.id()).orElseThrow();
        helper.assertValueEqual(loaded.territory().save(), town.territory().save(), "territory accounting survives save/load");
        helper.assertValueEqual(restored.at(town.dimension(), new BlockPos(x * 16, 300, z * 16)).orElseThrow().id(), town.id(),
                "the ownership index is rebuilt as full chunk columns");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void competingAndDuplicatePlotPurchasesNeverDoubleCharge(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        var center = town.center().east(64);
        var id = registry.create(1, town.dimension(), center, "Competing plots");
        var other = SettlementSavedData.get(helper.getLevel()).establish(registry, id, town.leader(), center, 1).orElseThrow();
        town.advance();
        other.advance();
        town.economy().credit(CurrencyAmount.of(100, 1));
        other.economy().credit(CurrencyAmount.of(100, 1));
        int x = town.territory().foundingChunk().x + 2, z = town.territory().foundingChunk().z;
        long revision = town.territory().revision();
        long price = SettlementPlotPricing.price(town.territory(), x, z);
        helper.assertValueEqual(claim(fixture.leader(), town, x, z, false), "", "the first request owns the plot");
        helper.assertValueEqual(SettlementPlotService.claim(fixture.leader(), town, x, z, false, revision, price),
                "quote_changed", "a duplicate request is rejected");
        helper.assertValueEqual(claim(fixture.leader(), other, x, z, false), "reserved", "a competing village cannot purchase the same plot");
        helper.assertValueEqual(town.economy().balance(), CurrencyAmount.of(80, 1), "the winner is charged once");
        helper.assertValueEqual(other.economy().balance(), CurrencyAmount.of(100, 1), "the loser loses no funds");
        helper.assertValueEqual(other.territory().freeClaims(), 4, "the loser loses no credits");
        helper.assertTrue(SettlementSavedData.get(helper.getLevel()).owner(net.minecraft.world.level.Level.NETHER.location(), x, z).isEmpty(),
                "the ownership index does not cross dimensions");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_stage_rules")
    public static void stalePricesAndLoweredRulesPreserveEarnedLandAndRewards(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        town.advance();
        town.economy().credit(CurrencyAmount.of(100, 1));
        int x = town.territory().foundingChunk().x + 2, z = town.territory().foundingChunk().z;
        long quote = SettlementPlotPricing.price(town.territory(), x, z);
        var pricing = SettlementPlotPricing.rules();
        var rules = new java.util.EnumMap<SettlementStage, SettlementStageRules.Limits>(SettlementStage.class);
        for (var stage : SettlementStage.values()) rules.put(stage, SettlementStageRules.forStage(stage));
        try {
            SettlementPlotPricing.apply(new SettlementPlotPricing.Rules(17, 2, 4));
            helper.assertValueEqual(SettlementPlotService.claim(fixture.leader(), town, x, z, false, town.territory().revision(), quote),
                    "quote_changed", "stale prices cannot spend funds");
            var lowered = new java.util.EnumMap<>(rules);
            var hamlet = lowered.get(SettlementStage.HAMLET);
            lowered.put(SettlementStage.HAMLET, new SettlementStageRules.Limits(hamlet.radius(), hamlet.projects(), hamlet.depots(),
                    hamlet.marketSupply(), hamlet.marketDemand(), hamlet.services(), 9, 100));
            SettlementStageRules.apply(lowered);
            helper.assertValueEqual(town.territory().capacity(), 25, "earned capacity never shrinks");
            helper.assertValueEqual(town.territory().freeClaims(), 4, "reload does not retroactively award changed rewards");
            helper.assertValueEqual(town.economy().balance(), CurrencyAmount.of(100, 1), "stale quotes lose no funds");
        } finally {
            SettlementPlotPricing.apply(pricing);
            SettlementStageRules.apply(rules);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void olderVillageWinsMigrationAndConflictingFacilitiesKeepInventory(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var leader = UUID.randomUUID();
        var oldId = registry.create(0, dimension, BlockPos.ZERO, "Older");
        var youngId = registry.create(1, dimension, new BlockPos(64, 0, 0), "Younger");
        var old = new SettlementRecord(oldId, dimension, BlockPos.ZERO, leader, 100, () -> {});
        var young = new SettlementRecord(youngId, dimension, new BlockPos(64, 0, 0), leader, 0, () -> {});
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Contested depot",
                new BlockPos(32, 0, 0), new BlockPos(47, 2, 15), 5, true);
        var oldPlots = Set.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0), ChunkPos.asLong(2, 0));
        var youngPlots = Set.of(ChunkPos.asLong(2, 0), ChunkPos.asLong(3, 0), ChunkPos.asLong(4, 0));
        old.migrateTerritory(oldPlots, "");
        young.migrateTerritory(youngPlots, "");
        old.putArea(leader, area);
        young.putArea(leader, area);
        var depot = young.registerDepot(leader, area.id(), area.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var entries = new ListTag();
        for (var town : List.of(young, old)) {
            var tag = town.save(helper.getLevel().registryAccess());
            tag.remove("Territory");
            entries.add(tag);
        }
        var legacy = new CompoundTag();
        legacy.putInt("FormatVersion", 54);
        legacy.put("Settlements", entries);
        var migrated = SettlementSavedData.load(legacy, helper.getLevel().registryAccess());
        migrated.restoreRegistry(registry);
        var older = migrated.get(oldId).orElseThrow();
        var younger = migrated.get(youngId).orElseThrow();
        helper.assertTrue(older.contains(area.min(), area.max()), "registry creation time wins over settlement order and founding time");
        helper.assertFalse(younger.areas().get(area.id()).enabled(), "facilities without their complete footprint are disabled");
        helper.assertValueEqual(younger.depots().get(depot.id()).getItem(0).getCount(), 7, "conflicts preserve inventory");
        helper.assertTrue(SettlementStock.depots(younger).isEmpty(), "conflicting depots cannot provide stock");
        helper.assertFalse(younger.territory().conflict().isEmpty(), "conflicts provide an explicit explanation");
        var before = younger.territory().save();
        migrated.restoreRegistry(registry);
        helper.assertValueEqual(younger.territory().save(), before, "migration cannot repeat");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void boundedPlotSnapshotsAndClaimCommandsRoundTrip(GameTestHelper helper) {
        var fixture = fixture(helper);
        var window = SettlementViewWindow.around(fixture.town(), fixture.town().center().getX(), fixture.town().center().getZ(), 32);
        var snapshot = SettlementPlotService.snapshot(fixture.leader(), fixture.town(), window);
        helper.assertTrue(snapshot.plots().size() <= SettlementViewWindow.MAX_CHUNKS, "plot snapshots stay bounded");
        helper.assertTrue(snapshot.plots().stream().anyMatch(plot -> plot.eligible() && plot.price() == 20), "neighbors carry server quotes");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var payload = new SettlementViewStatePayload(UUID.randomUUID(), fixture.town().id().value(), fixture.town().dimension(),
                    0, window, true, snapshot);
            SettlementViewStatePayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(SettlementViewStatePayload.STREAM_CODEC.decode(buffer), payload, "plot views round trip");
            var command = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.ClaimPlot(-2, -3, false, 7, 24));
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "claims preserve revision, coordinates and payment choice");
            helper.assertValueEqual(buffer.readableBytes(), 0, "packets are consumed exactly");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void naturalColumnsStayReservedUntilTheirVillageIsAdopted(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var naturalCenter = new BlockPos(32, 64, 0);
        var natural = registry.create(0, dimension, naturalCenter, "Natural");
        var tag = registry.save(new CompoundTag(), helper.getLevel().registryAccess());
        ((CompoundTag) tag.getList("Records", net.minecraft.nbt.Tag.TAG_COMPOUND).getFirst()).putLongArray("FootprintSections",
                new long[]{net.minecraft.core.SectionPos.asLong(naturalCenter)});
        registry = VillageAllegianceRegistrySavedData.load(tag, helper.getLevel().registryAccess());
        var data = new SettlementSavedData();
        var leader = UUID.randomUUID();
        var id = registry.create(1, dimension, BlockPos.ZERO, "Neighbor");
        var town = data.establish(registry, id, leader, BlockPos.ZERO, 1).orElseThrow();
        town.advance();
        helper.assertValueEqual(SettlementPlotService.failure(data, registry, town, leader, 2, 0, true), "reserved",
                "unmanaged natural footprints reserve the entire chunk column");
        helper.assertFalse(registry.contains(registry.record(natural).orElseThrow(), naturalCenter.above(160)),
                "ordinary natural village membership keeps its section height");
        var adopted = data.establish(registry, natural, leader, naturalCenter, 2).orElseThrow();
        helper.assertTrue(adopted.contains(naturalCenter.above(160)), "adoption converts the preserved footprint to columns");
        helper.assertValueEqual(adopted.territory().plots().size(), 1, "adoption preserves the recorded footprint");
        helper.assertValueEqual(adopted.territory().freeClaims(), 8, "unused starting entitlement remains banked");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void migrationUsesUuidTieBreakingAndPersistsInactiveConflicts(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var dimension = helper.getLevel().dimension().location();
        var first = new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(new UUID(0, 1));
        var second = new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(new UUID(0, 2));
        var entries = new ListTag();
        for (var id : List.of(second, first)) {
            registry.ensureRecord(id, 0, dimension, BlockPos.ZERO);
            var town = new SettlementRecord(id, dimension, BlockPos.ZERO, UUID.randomUUID(), 0, () -> {});
            var entry = town.save(helper.getLevel().registryAccess());
            entry.remove("Territory");
            entries.add(entry);
        }
        var tag = new CompoundTag();
        tag.putInt("FormatVersion", 54);
        tag.put("Settlements", entries);
        var data = SettlementSavedData.load(tag, helper.getLevel().registryAccess());
        data.restoreRegistry(registry);
        helper.assertTrue(data.get(first).orElseThrow().territory().active(), "the lower UUID wins equal creation times");
        var inactive = data.get(second).orElseThrow();
        helper.assertFalse(inactive.territory().active(), "a completely overlapped legacy settlement stays inactive");
        helper.assertTrue(inactive.territory().conflict().contains("inactive"), "inactivity has an explicit explanation");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        restored.restoreRegistry(registry);
        helper.assertValueEqual(restored.get(second).orElseThrow().territory().save(), inactive.territory().save(),
                "inactive conflicts survive reload without re-running migration");
        helper.succeed();
    }

    private static String claim(ServerPlayer player, SettlementRecord town, int x, int z, boolean free) {
        return SettlementPlotService.claim(player, town, x, z, free, town.territory().revision(), SettlementPlotPricing.price(town.territory(), x, z));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(-8_000_000 + origin.getX() * 32, 80, -8_000_000 + origin.getZ() * 32);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PlotLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0, level.dimension().location(), center, "Plot tests");
        return new Fixture(leader, SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0).orElseThrow());
    }

    private record Fixture(ServerPlayer leader, SettlementRecord town) {
    }
}
