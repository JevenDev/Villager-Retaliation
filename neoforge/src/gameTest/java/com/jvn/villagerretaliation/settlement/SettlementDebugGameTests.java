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
        helper.assertValueEqual(SettlementDebugService.create(source, 32), 0, "repeating at the same location is rejected");
        player.setPos(player.getX() + 64, player.getY(), player.getZ());
        helper.assertValueEqual(SettlementDebugService.create(player.createCommandSourceStack().withPermission(2), 64), 0,
                "an overlap at the far edge is rejected even when the center is unclaimed");
        helper.assertValueEqual(data.settlements().size(), count, "no partial settlement remains");
        helper.assertValueEqual(registry.activeRecords(town.dimension()).size(), records, "no orphan registry entry remains");
        helper.assertValueEqual(town.territory().plots(), original, "existing territory is untouched");
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
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DebugVillage"));
        player.setPos(x, 64, z);
        return player;
    }
}
