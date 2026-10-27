package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementBoundsPayload;
import com.jvn.villagerretaliation.network.SettlementBoundsRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_bounds")
@PrefixGameTestTemplate(false)
public final class SettlementBoundsGameTests {
    private SettlementBoundsGameTests() { }

    @GameTest(template = "empty")
    public static void nearbyBoundsContainOnlyOwnedPlotsAndRoundTrip(GameTestHelper helper) {
        var fixture = fixture(helper);
        var snapshot = SettlementBoundsService.snapshot(fixture.player(), fixture.town().id().value(), 4);
        helper.assertTrue(snapshot.allowed(), "the leader can inspect territory");
        helper.assertValueEqual(snapshot.plots().size(), fixture.town().territory().plots().size(),
                "all nearby owned plots are included");
        helper.assertTrue(snapshot.plots().stream().allMatch(plot -> fixture.town().territory().contains(plot.x, plot.z)),
                "the overlay never marks unowned land as owned");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementBoundsPayload.STREAM_CODEC.encode(buffer, snapshot);
            helper.assertValueEqual(SettlementBoundsPayload.STREAM_CODEC.decode(buffer), snapshot, "bounds survive network encoding");
            var request = new SettlementBoundsRequestPayload(fixture.town().id().value(), 5);
            SettlementBoundsRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementBoundsRequestPayload.STREAM_CODEC.decode(buffer), request, "requests retain their correlation id");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void permissionChangesAndOtherDimensionsHideBounds(GameTestHelper helper) {
        var fixture = fixture(helper);
        var visitor = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BoundsVisitor"));
        helper.assertFalse(SettlementBoundsService.snapshot(visitor, fixture.town().id().value(), 0).allowed(),
                "unrelated players cannot inspect managed territory");
        fixture.town().setPermissions(fixture.player().getUUID(), visitor.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertTrue(SettlementBoundsService.snapshot(visitor, fixture.town().id().value(), 1).allowed(),
                "delegated view permission is sufficient");
        fixture.town().setPermissions(fixture.player().getUUID(), visitor.getUUID(), Set.of());
        var denied = SettlementBoundsService.snapshot(visitor, fixture.town().id().value(), 2);
        helper.assertFalse(denied.allowed(), "revoked permission disables the overlay");
        helper.assertTrue(denied.plots().isEmpty(), "denied replies contain no territory");
        var nether = FakePlayerFactory.get(helper.getLevel().getServer().getLevel(Level.NETHER),
                new GameProfile(fixture.player().getUUID(), "BoundsSteward"));
        helper.assertFalse(SettlementBoundsService.snapshot(nether, fixture.town().id().value(), 3).allowed(),
                "territory cannot be inspected across dimensions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void distantBoundsDoNotLoadChunks(GameTestHelper helper) {
        var fixture = fixture(helper);
        var distant = fixture.town().center().offset(16_000, 0, 16_000);
        fixture.player().setPos(distant.getX(), distant.getY(), distant.getZ());
        helper.assertFalse(helper.getLevel().hasChunkAt(distant), "the distant player position starts unloaded");
        var snapshot = SettlementBoundsService.snapshot(fixture.player(), fixture.town().id().value(), 0);
        helper.assertTrue(snapshot.allowed() && snapshot.plots().isEmpty(), "only nearby owned plots are sent");
        helper.assertFalse(helper.getLevel().hasChunkAt(distant), "bounds inspection never loads the distant position");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidAndOversizedBoundsAreRejected(GameTestHelper helper) {
        var dimension = helper.getLevel().dimension().location();
        var town = UUID.randomUUID();
        for (var plots : List.of(List.of(new ChunkPos(0, 0), new ChunkPos(11, 0)),
                List.of(new ChunkPos(0, 0), new ChunkPos(0, 0)))) {
            boolean rejected = false;
            try {
                new SettlementBoundsPayload(town, dimension, 0, true, plots);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "the bounded renderer rejects distant or duplicate plots");
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BoundsSteward"));
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        return new Fixture(town, player);
    }

    private record Fixture(SettlementRecord town, ServerPlayer player) { }
}
