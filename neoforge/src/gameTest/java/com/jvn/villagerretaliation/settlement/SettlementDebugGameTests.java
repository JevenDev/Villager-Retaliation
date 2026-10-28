package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_debug")
@PrefixGameTestTemplate(false)
public final class SettlementDebugGameTests {
    private SettlementDebugGameTests() {
    }

    @GameTest(template = "empty")
    public static void circularPlotsFollowPlayerPositionAndNegativeCoordinates(GameTestHelper helper) {
        var circle = SettlementDebugService.plots(new Vec3(8, 64, 8), 32);
        helper.assertTrue(circle.contains(ChunkPos.asLong(2, 0)), "east edge is included");
        helper.assertTrue(circle.contains(ChunkPos.asLong(-2, 0)), "west edge is included");
        helper.assertFalse(circle.contains(ChunkPos.asLong(2, 2)), "square corners outside the circle are excluded");
        helper.assertValueEqual(SettlementDebugService.plots(new Vec3(8, 64, 8), 1).size(), 1,
                "small circles do not acquire the normal three-by-three starting plots");
        helper.assertValueEqual(SettlementDebugService.plots(new Vec3(8, 64, 8), 8).size(), 1,
                "tangency alone does not claim neighboring plots");
        helper.assertValueEqual(SettlementDebugService.plots(new Vec3(-0.25, 64, -0.25), 1).size(), 4,
                "the actual player position crosses four plots around a negative-coordinate corner");
        var shifted = SettlementDebugService.plots(new Vec3(-24, -40, -24), 32);
        for (long plot : circle) {
            helper.assertTrue(shifted.contains(ChunkPos.asLong(ChunkPos.getX(plot) - 2, ChunkPos.getZ(plot) - 2)),
                    "negative coordinates preserve the circular footprint");
        }
        helper.assertValueEqual(circle.size(), shifted.size(), "translation preserves plot count");
        helper.assertTrue(SettlementDebugService.plots(new Vec3(0, 0, 0), SettlementDebugService.MAX_RADIUS).size() < 1100,
                "the largest command footprint stays bounded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandCreatesOwnedFreePersistentPlotsAtPlayerPosition(GameTestHelper helper) throws Exception {
        var player = player(helper, 4_000_008, -4_000_008);
        var source = player.createCommandSourceStack().withPermission(2).withPosition(new Vec3(0, 100, 0));
        var level = helper.getLevel();
        var data = SettlementSavedData.get(level);
        int before = data.settlements().size();
        helper.assertValueEqual(level.getServer().getCommands().getDispatcher()
                .execute("vr admin debug village 64", source), 1, "the command creates a village");
        var town = data.at(level.dimension().location(), player.blockPosition()).orElseThrow();
        helper.assertValueEqual(data.settlements().size(), before + 1, "exactly one village is created");
        helper.assertValueEqual(town.center(), player.blockPosition(), "the player anchors the circle, not execute positioned");
        helper.assertValueEqual(town.leader(), player.getUUID(), "the invoking player owns the settlement");
        helper.assertTrue(town.permits(player.getUUID(), SettlementPermission.BUILD), "the owner can test construction");
        helper.assertValueEqual(town.territory().plots(), SettlementDebugService.plots(player.position(), 64),
                "all and only circular plots are claimed");
        helper.assertTrue(town.territory().capacity() >= town.territory().plots().size(), "capacity supports the debug grant");
        helper.assertValueEqual(town.territory().paidPurchases(), 0, "no plots are charged");
        helper.assertTrue(town.economy().constructionFunds().isZero(), "creation needs no treasury funds");
        var registry = VillageAllegianceRegistrySavedData.get(level);
        helper.assertTrue(registry.isManaged(town.id()), "the village registry recognizes the settlement");
        helper.assertValueEqual(registry.record(town.id()).orElseThrow().footprintSections().size(),
                town.territory().plots().size(), "the registry footprint matches the circle");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        var copy = restored.get(town.id()).orElseThrow();
        helper.assertValueEqual(copy.territory().plots(), town.territory().plots(), "plots survive save and reload");
        helper.assertValueEqual(copy.leader(), player.getUUID(), "ownership survives reload");
        for (long plot : town.territory().plots()) {
            helper.assertValueEqual(restored.owner(town.dimension(), ChunkPos.getX(plot), ChunkPos.getZ(plot)).orElseThrow(),
                    town.id(), "reloaded plots are indexed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overlapsAreRejectedAtomicallyIncludingOuterPlots(GameTestHelper helper) {
        var player = player(helper, 4_002_008, -4_002_008);
        var source = player.createCommandSourceStack().withPermission(2);
        helper.assertValueEqual(SettlementDebugService.create(source, 16), 1, "initial village exists");
        var data = SettlementSavedData.get(helper.getLevel());
        var town = data.at(helper.getLevel().dimension().location(), player.blockPosition()).orElseThrow();
        var original = java.util.Set.copyOf(town.territory().plots());
        int count = data.settlements().size();
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        int records = registry.activeRecords(town.dimension()).size();
        player.setPos(player.getX() + 64, player.getY(), player.getZ());
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 64), 0,
                "an overlap at the far edge is rejected even when the center is unclaimed");
        helper.assertValueEqual(data.settlements().size(), count, "no partial settlement remains");
        helper.assertValueEqual(registry.activeRecords(town.dimension()).size(), records, "no orphan registry entry remains");
        helper.assertValueEqual(town.territory().plots(), original, "existing territory is untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandExpandsExistingSettlementWithoutReplacingItsState(GameTestHelper helper) throws Exception {
        var founder = player(helper, 4_006_008, -4_006_008);
        var level = helper.getLevel();
        var data = SettlementSavedData.get(level);
        helper.assertValueEqual(SettlementDebugService.create(founder.createCommandSourceStack().withPermission(2), 64), 1, "source settlement exists");
        var town = data.at(level.dimension().location(), founder.blockPosition()).orElseThrow();
        town.advance();
        town.territory().autonomousCapacity(1);
        int x = new ChunkPos(town.center()).x;
        int z = new ChunkPos(town.center()).z;
        while (town.territory().contains(x, z)) x--;
        town.territory().claim(x, z, false);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        data.claimed(registry, town);
        var original = java.util.Set.copyOf(town.territory().plots());
        var center = town.center();
        var stage = town.stage();
        int free = town.territory().freeClaims(), paid = town.territory().paidPurchases();
        int count = data.settlements().size(), records = registry.activeRecords(town.dimension()).size();
        var operator = player(helper, founder.getX() + 48, founder.getZ());
        var expected = new java.util.HashSet<>(original);
        expected.addAll(SettlementDebugService.plots(operator.position(), 32));
        helper.assertTrue(expected.size() > original.size(), "offset circle adds territory");
        helper.assertValueEqual(level.getServer().getCommands().getDispatcher().execute("vr admin debug village 32",
                operator.createCommandSourceStack().withPermission(2)), 1, "command expands the settlement under another operator");
        helper.assertValueEqual(data.settlements().size(), count, "expansion creates no settlement");
        helper.assertValueEqual(registry.activeRecords(town.dimension()).size(), records, "expansion creates no registry identity");
        helper.assertTrue(data.at(town.dimension(), operator.blockPosition()).orElseThrow() == town, "the same record is expanded");
        helper.assertValueEqual(town.center(), center, "founding center is unchanged");
        helper.assertValueEqual(town.leader(), founder.getUUID(), "expansion does not transfer ownership to the operator");
        helper.assertValueEqual(town.stage(), stage, "stage is preserved");
        helper.assertValueEqual(town.territory().plots(), expected, "circle is added without removing old plots");
        helper.assertValueEqual(town.territory().freeClaims(), free, "free claim rewards are preserved");
        helper.assertValueEqual(town.territory().paidPurchases(), paid, "purchase history is preserved");
        helper.assertTrue(town.economy().constructionFunds().isZero(), "expansion costs nothing");
        long revision = town.territory().revision();
        helper.assertValueEqual(SettlementDebugService.create(operator.createCommandSourceStack().withPermission(2), 1), 1, "smaller repeated circle succeeds");
        helper.assertValueEqual(town.territory().revision(), revision, "a contained circle makes no territory change");
        helper.assertValueEqual(town.territory().plots(), expected, "smaller radius never shrinks existing territory");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        var copy = restored.get(town.id()).orElseThrow();
        helper.assertValueEqual(copy.territory().plots(), expected, "expanded territory survives reload");
        helper.assertValueEqual(copy.territory().paidPurchases(), paid, "purchase history survives reload");
        helper.assertTrue(copy.territory().capacity() >= expected.size(), "expanded territory has sufficient saved capacity");
        helper.assertValueEqual(registry.record(town.id()).orElseThrow().footprintSections().size(), expected.size(), "registry footprint expands");
        for (long plot : expected) helper.assertValueEqual(restored.owner(town.dimension(), ChunkPos.getX(plot), ChunkPos.getZ(plot)).orElseThrow(),
                town.id(), "expanded plots are indexed after reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void autonomousExpansionPreservesVillageStateAndUpdatesSections(GameTestHelper helper) {
        var player = player(helper, 4_010_008, -4_010_008);
        var level = helper.getLevel();
        var data = SettlementSavedData.get(level);
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 16), 1, "source settlement exists");
        var town = data.at(level.dimension().location(), player.blockPosition()).orElseThrow();
        var state = new AutonomousVillageState();
        int y = net.minecraft.core.SectionPos.blockToSectionCoord(town.center().getY());
        for (long plot : town.territory().plots()) for (int dy = -1; dy <= 1; dy++)
            state.sections.add(net.minecraft.core.SectionPos.asLong(ChunkPos.getX(plot), y + dy, ChunkPos.getZ(plot)));
        state.ruler = UUID.randomUUID();
        town.autonomous(state);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        data.claimed(registry, town);
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 96), 1, "autonomous village expands");
        helper.assertTrue(town.autonomous() == state, "autonomous decisions are retained");
        helper.assertTrue(town.contains(town.center().east(64)), "new plots are usable within autonomous vertical bounds");
        helper.assertFalse(town.contains(town.center().east(64).above(96)), "expansion preserves vertical boundary semantics");
        helper.assertValueEqual(registry.record(town.id()).orElseThrow().footprintSections(), state.sections, "registry uses the expanded sections");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.autonomous().sections, state.sections, "expanded autonomous boundary survives reload");
        helper.assertValueEqual(restored.autonomous().ruler(), state.ruler(), "ruler survives expansion and reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expansionRejectsOtherVillagesWithoutPartialClaims(GameTestHelper helper) {
        var player = player(helper, 4_008_008, -4_008_008);
        var level = helper.getLevel();
        var data = SettlementSavedData.get(level);
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 16), 1, "first settlement exists");
        var town = data.at(level.dimension().location(), player.blockPosition()).orElseThrow();
        var neighborPlayer = player(helper, player.getX() + 128, player.getZ());
        helper.assertValueEqual(SettlementDebugService.create(neighborPlayer.createCommandSourceStack().withPermission(2), 16), 1, "neighbor exists");
        var neighbor = data.at(town.dimension(), neighborPlayer.blockPosition()).orElseThrow();
        var original = java.util.Set.copyOf(town.territory().plots());
        var other = java.util.Set.copyOf(neighbor.territory().plots());
        long revision = town.territory().revision();
        int capacity = town.territory().capacity(), count = data.settlements().size();
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 160), 0, "overlapping expansion is rejected");
        helper.assertValueEqual(town.territory().plots(), original, "no partial plots are added");
        helper.assertValueEqual(neighbor.territory().plots(), other, "neighbor retains all plots");
        helper.assertValueEqual(town.territory().revision(), revision, "rejection preserves revision");
        helper.assertValueEqual(town.territory().capacity(), capacity, "rejection preserves capacity");
        helper.assertValueEqual(data.settlements().size(), count, "rejection creates no settlement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandRequiresOperatorPlayerAndBoundedRadius(GameTestHelper helper) throws Exception {
        var player = player(helper, 4_004_008, -4_004_008);
        var source = player.createCommandSourceStack();
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        int before = SettlementSavedData.get(helper.getLevel()).settlements().size();
        helper.assertValueEqual(SettlementDebugService.create(source.withPermission(0), 16), 0, "service rejects non-operators");
        helper.assertValueEqual(SettlementDebugService.create(helper.getLevel().getServer().createCommandSourceStack(), 16),
                0, "a console has no standing position");
        for (String command : java.util.List.of("vr admin debug village 0", "vr admin debug village 257",
                "vr admin debug village -1", "vr admin debug village 1.5")) {
            boolean rejected = false;
            try {
                dispatcher.execute(command, source.withPermission(2));
            } catch (CommandSyntaxException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "invalid radius is rejected by the command tree");
        }
        boolean denied = false;
        try {
            dispatcher.execute("vr admin debug village 16", source.withPermission(0));
        } catch (CommandSyntaxException expected) {
            denied = true;
        }
        helper.assertTrue(denied, "the command tree rejects non-operators");
        helper.assertValueEqual(SettlementDebugService.create(source.withPermission(2), 257), 0, "direct calls also validate radius");
        helper.assertValueEqual(SettlementSavedData.get(helper.getLevel()).settlements().size(), before, "invalid calls create nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldBorderRejectsWholeCircleWithoutCreatingRecords(GameTestHelper helper) {
        var player = player(helper, 29_999_970, 0);
        var level = helper.getLevel();
        var data = SettlementSavedData.get(level);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        int towns = data.settlements().size();
        int records = registry.activeRecords(level.dimension().location()).size();
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 64), 0,
                "a circle crossing the world border is rejected");
        helper.assertValueEqual(data.settlements().size(), towns, "no partial village is created at the border");
        helper.assertValueEqual(registry.activeRecords(level.dimension().location()).size(), records, "no registry entry is leaked");
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 1), 1, "small circle fits inside border");
        var town = data.at(level.dimension().location(), player.blockPosition()).orElseThrow();
        var original = java.util.Set.copyOf(town.territory().plots());
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 64), 0, "expansion crossing border is rejected");
        helper.assertValueEqual(town.territory().plots(), original, "border rejection preserves the settlement");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DebugVillage"));
        player.setPos(x, 64, z);
        return player;
    }
}
