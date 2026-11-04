package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementDistrictRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload.CommissionBuilding;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("vr_district")
@PrefixGameTestTemplate(false)
public final class SettlementDistrictGameTests {
    private SettlementDistrictGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void combinedBuildingAndPathQuoteQueuesSeparateProjectsExactlyOnce(GameTestHelper helper) {
        var f = SettlementPathConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
            var building = building(f, anchor);
            var start = f.origin().offset(0, -1, 12); var end = start.offset(8, 0, 0);
            for (int x = 0; x <= 8; x++) helper.getLevel().setBlock(start.offset(x, 0, 0), Blocks.DIRT.defaultBlockState(), 2);
            var path = new SettlementPath(List.of(node(start), node(end)), 1, SettlementPath.Style.CLEAN, SettlementPath.Material.COBBLESTONE, 1L, false, false);
            var request = new SettlementDistrictRequestPayload(f.town().id().value(), UUID.randomUUID(), false, "", List.of(building),
                    List.of(new SettlementDistrictRequestPayload.PlannedPath(UUID.randomUUID(), path)));
            var quote = SettlementDistrictService.evaluate(f.leader(), request);
            helper.assertTrue(quote.failure().isEmpty(), "combined district is valid: " + quote.failure());
            helper.assertTrue(quote.lines().stream().anyMatch(line -> line.startsWith("Projects: 2 / planned beds:")), "combined review reports housing capacity");
            helper.assertTrue(f.town().projects().isEmpty(), "review creates no projects");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementDistrictRequestPayload.STREAM_CODEC.encode(buffer, request);
                helper.assertValueEqual(SettlementDistrictRequestPayload.STREAM_CODEC.decode(buffer), request, "district buildings and curved-path settings round trip");
            } finally { buffer.release(); }
            var confirm = new SettlementDistrictRequestPayload(request.town(), request.request(), true, quote.fingerprint(), request.buildings(), request.paths());
            var accepted = SettlementDistrictService.evaluate(f.leader(), confirm);
            helper.assertTrue(accepted.queued(), "district commissions: " + accepted.failure());
            helper.assertValueEqual(f.town().projects().size(), 2, "building and path remain separate projects");
            var house = f.town().project(building.request());
            helper.assertValueEqual(house.prerequisites(), List.of(request.paths().getFirst().id()), "paths-first review persists the building dependency");
            helper.assertValueEqual(house.blocker(), "prerequisite", "buildings wait for access preparation");
            helper.assertFalse(SettlementConstructionMaterials.reserved(house), "waiting for paths reserves no building materials");
            var balance = f.town().economy().balance();
            helper.assertTrue(SettlementDistrictService.evaluate(f.leader(), confirm).queued(), "district confirmation is idempotent");
            helper.assertValueEqual(f.town().economy().balance(), balance, "replay cannot reserve payment twice");
            for (var project : f.town().projects().values()) {
                var restored = SettlementProject.load(project.save());
                helper.assertValueEqual(restored.district(), request.request(), "district identity survives reload");
                helper.assertValueEqual(restored.districtFingerprint(), quote.fingerprint(), "approved district quote survives reload");
                helper.assertValueEqual(restored.prerequisites(), project.prerequisites(), "district prerequisites survive reload");
            }
            f.town().project(request.paths().getFirst().id()).finish(SettlementProject.Status.CANCELLED);
            helper.assertValueEqual(SettlementConstructionService.prerequisiteFailure(f.town(), house), "prerequisite_cancelled", "cancelled access work cannot silently start the house");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void overlappingDistrictIsRejectedWithoutPartialQueueOrPayment(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
            var request = new SettlementDistrictRequestPayload(f.town().id().value(), UUID.randomUUID(), false, "", List.of(building(f, anchor), building(f, anchor)), List.of());
            var balance = f.town().economy().balance();
            var reply = SettlementDistrictService.evaluate(f.leader(), request);
            helper.assertTrue(reply.failure().contains("overlap"), "overlapping building footprints reject the district");
            helper.assertTrue(f.town().projects().isEmpty(), "rejected districts queue nothing");
            helper.assertValueEqual(f.town().economy().balance(), balance, "rejected districts consume no funds");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void changedTerrainAndPermissionsRequireAValidNewReview(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
            var request = new SettlementDistrictRequestPayload(f.town().id().value(), UUID.randomUUID(), false, "", List.of(building(f, anchor)), List.of());
            var quote = SettlementDistrictService.evaluate(f.leader(), request);
            helper.assertTrue(quote.failure().isEmpty(), "initial review is valid");
            var block = f.plan().blocks().getFirst(); helper.getLevel().setBlock(f.origin().offset(block.localPos()), Blocks.CHEST.defaultBlockState(), 2);
            var reply = SettlementDistrictService.evaluate(f.leader(), new SettlementDistrictRequestPayload(request.town(), request.request(), true, quote.fingerprint(), request.buildings(), request.paths()));
            helper.assertFalse(reply.queued(), "new containers invalidate the reviewed site");
            helper.assertTrue(f.town().projects().isEmpty(), "changed terrain cannot partially commission");
            var stranger = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DistrictStranger"));
            helper.assertFalse(SettlementDistrictService.evaluate(stranger, request).failure().isEmpty(), "district review enforces settlement permission");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    private static CommissionBuilding building(SettlementConstructionGameTests.Fixture f, BlockPos anchor) {
        return new CommissionBuilding(UUID.randomUUID(), f.plan().entry().id(), anchor, Rotation.NONE, SettlementProject.AUTOMATIC_BUILDER);
    }
    private static SettlementPath.Node node(BlockPos pos) { return new SettlementPath.Node(Vec3.atCenterOf(pos), Vec3.ZERO, Vec3.ZERO); }
}
