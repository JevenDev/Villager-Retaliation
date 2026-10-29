package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_wall_construction")
@PrefixGameTestTemplate(false)
public final class SettlementWallConstructionGameTests {
    private SettlementWallConstructionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_construction")
    public static void confirmationQueuesOnceAndWaitsForABuilder(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var wall = wall(fixture);
            var town = fixture.town();
            town.removeDuty(fixture.builder().getUUID());
            var session = new SettlementWallSession();
            var id = UUID.randomUUID();
            var preview = session.request(helper.getLevel(), id, true, town::contains,
                    new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.PREVIEW, id, wall), town.id(), fixture.leader());
            helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "the wall preview is valid");
            var confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null);
            helper.assertValueEqual(session.request(helper.getLevel(), id, false, town::contains, confirm, town.id(), fixture.leader()).status(),
                    SettlementWallResultPayload.Status.REJECTED, "revoked permission cannot queue a wall");
            var accepted = session.request(helper.getLevel(), id, true, town::contains, confirm, town.id(), fixture.leader());
            helper.assertValueEqual(accepted.status(), SettlementWallResultPayload.Status.APPLIED, "confirmation accepts a construction order: " + accepted.message());
            var project = town.projects().get(preview.token());
            helper.assertTrue(project != null && project.pending(), "a wall waits in the construction queue");
            helper.assertValueEqual(project.blocker(), "builder", "the project reports its missing builder");
            helper.assertValueEqual(session.request(helper.getLevel(), id, true, town::contains, confirm, town.id(), fixture.leader()), accepted,
                    "a repeated confirmation returns the same receipt");
            helper.assertValueEqual(town.projects().size(), 1, "retries cannot duplicate projects");
            var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            for (var block : plan.blocks()) helper.assertTrue(helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)).isAir(),
                    "confirmation never places wall blocks");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var savedProject = restored.projects().get(project.id());
            helper.assertTrue(savedProject.pending() && savedProject.wallConstruction() != null, "queued wall survives reload without a builder");
            helper.assertValueEqual(BuilderStructureScanner.fingerprint(SettlementConstructionService.scan(helper.getLevel(), savedProject)),
                    BuilderStructureScanner.fingerprint(plan), "the exact confirmed plan survives reload");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), town, project.id()), "pending wall can be cancelled");
            helper.assertFalse(SettlementWallsSavedData.get(helper.getLevel()).hasGroup(project.id()), "an unbuilt wall has no completed wall ownership");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_construction")
    public static void crewBuildsNodesThenSpansAndPersistsSharedProgress(GameTestHelper helper) {
        var fixture = fixture(helper);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        try {
            var town = fixture.town();
            SettlementConstructionCrewGameTests.adopt(helper, fixture);
            var wall = wall(fixture);
            var preview = SettlementWallTerrain.preview(helper.getLevel(), wall, town::contains);
            var id = UUID.randomUUID();
            String failure = SettlementWallConstruction.commission(fixture.leader(), town, id, wall, preview);
            helper.assertTrue(failure.isEmpty(), "wall queued: " + failure);
            var project = town.projects().get(id);
            var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            SettlementConstructionGameTests.fundAndDispatch(helper, town, plan, fixture.origin(), id);
            helper.assertTrue(project.crew() != null, "wall ordering also applies to a single builder");
            helper.assertTrue(SettlementConstructionService.configureCrew(fixture.leader(), town, id, project.crewRevision(), 5, List.of(assistant.getUUID())),
                    "a second builder can be assigned through the existing project controls");
            int first = SettlementConstructionCoordination.select(town, project, fixture.builder().getUUID(), plan, helper.getLevel(), 0L);
            int second = SettlementConstructionCoordination.select(town, project, assistant.getUUID(), plan, helper.getLevel(), 0L);
            helper.assertTrue(first >= 0 && second >= 0 && first != second, "both builders claim distinct work in the same step");
            var nodes = SettlementWallGeometry.towers(wall);
            helper.assertTrue(atNode(plan.worldPos(project.origin(), plan.blocks().get(first)), nodes.get(0), nodes.get(1))
                    && atNode(plan.worldPos(project.origin(), plan.blocks().get(second)), nodes.get(0), nodes.get(1)),
                    "the crew starts with the first two nodes");
            int firstStage = project.wallConstruction().save().getList("Blocks", Tag.TAG_COMPOUND).getCompound(first).getInt("Stage");
            helper.assertValueEqual(firstStage, 0, "the first two nodes share the initial step");
            var completed = new HashSet<Integer>();
            for (int index : List.of(first, second)) {
                var block = plan.blocks().get(index);
                helper.getLevel().setBlock(plan.worldPos(project.origin(), block), block.state(), 2);
                completed.add(index);
            }
            SettlementConstructionCoordination.refresh(town, project, plan, helper.getLevel());
            helper.assertValueEqual(project.placed(), 2, "two builders advance two blocks during one round");
            var restored = SettlementProject.load(project.save());
            helper.assertValueEqual(restored.assistants(), List.of(assistant.getUUID()), "crew assignment survives reload");
            helper.assertValueEqual(restored.placed(), 2, "shared progress survives reload");
            helper.assertTrue(restored.crew().claim(assistant.getUUID(), 0L) == null, "claims are safely reacquired after reload");
            var saved = project.wallConstruction().save().getList("Blocks", Tag.TAG_COMPOUND);
            int lastStage = 0;
            for (int round = 0; round < plan.blocks().size() && project.placed() < project.total(); round++) {
                int index = SettlementConstructionCoordination.select(town, project, fixture.builder().getUUID(), plan, helper.getLevel(), round + 1L);
                helper.assertTrue(index >= 0 && index < project.total(), "the next wall placement is reachable in dependency order");
                int stage = saved.getCompound(index).getInt("Stage");
                helper.assertTrue(stage >= lastStage, "construction cannot start a later node or span early");
                for (int previous = 0; previous < plan.blocks().size(); previous++)
                    if (saved.getCompound(previous).getInt("Stage") < stage) helper.assertTrue(completed.contains(previous), "the previous step is complete");
                var block = plan.blocks().get(index);
                var pos = plan.worldPos(project.origin(), block);
                for (int below = 0; below < plan.blocks().size(); below++)
                    if (plan.worldPos(project.origin(), plan.blocks().get(below)).equals(pos.below()))
                        helper.assertTrue(completed.contains(below), "foundations and lower courses precede upper blocks");
                helper.getLevel().setBlock(pos, block.state(), 2);
                completed.add(index);
                lastStage = stage;
                project.crew().release(fixture.builder().getUUID());
                SettlementConstructionCoordination.refresh(town, project, plan, helper.getLevel());
            }
            helper.assertValueEqual(project.placed(), project.total(), "the ordered crew completes every block");
            helper.assertTrue(lastStage >= 3, "construction reaches the second connecting span");
            town.finishProject(project.id(), SettlementProject.Status.COMPLETED);
            project.wallConstruction().record(helper.getLevel(), town, project);
            var records = SettlementWallsSavedData.get(helper.getLevel());
            helper.assertTrue(records.hasGroup(project.id()), "completed walls support later selection and upgrades");
            var before = records.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
            project.wallConstruction().record(helper.getLevel(), town, project);
            helper.assertValueEqual(records.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()), before,
                    "completion replay does not duplicate wall ownership");
        } finally { assistant.discard(); fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_construction")
    public static void cancellationRefundsReservedWallSupplies(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var wall = wall(fixture);
            var id = UUID.randomUUID();
            String failure = SettlementWallConstruction.commission(fixture.leader(), fixture.town(), id, wall,
                    SettlementWallTerrain.preview(helper.getLevel(), wall, fixture.town()::contains));
            helper.assertTrue(failure.isEmpty(), "wall queued: " + failure);
            var project = fixture.town().projects().get(id);
            var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            SettlementConstructionGameTests.fundAndDispatch(helper, fixture.town(), plan, fixture.origin(), id);
            helper.assertTrue(SettlementConstructionMaterials.reserved(project), "the wall quote is held in project escrow");
            var pickup = SettlementConstructionMaterials.collectionTarget(project);
            SettlementConstructionMaterials.collect(fixture.town(), project, fixture.builder().getUUID(), pickup);
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "wall cancellation succeeds");
            int expected = project.wallConstruction().save().getInt("Logs");
            int returned = fixture.town().depots().values().stream().mapToInt(depot -> depot.count(SettlementWallMaterials::logs)).sum();
            helper.assertValueEqual(returned, expected, "all unspent wall logs return to storage");
            helper.assertFalse(SettlementConstructionMaterials.refundPending(project), "the refund is settled");
            helper.assertFalse(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "repeated cancellation cannot refund twice");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000, batch = "wall_native")
    public static void nativeBuilderPlacesWallFromProjectEscrow(GameTestHelper helper) {
        var fixture = fixture(helper);
        var wall = wall(fixture);
        var id = UUID.randomUUID();
        String failure = SettlementWallConstruction.commission(fixture.leader(), fixture.town(), id, wall,
                SettlementWallTerrain.preview(helper.getLevel(), wall, fixture.town()::contains));
        helper.assertTrue(failure.isEmpty(), "wall queued: " + failure);
        var project = fixture.town().projects().get(id);
        var plan = SettlementConstructionService.scan(helper.getLevel(), project);
        SettlementConstructionGameTests.fundAndDispatch(helper, fixture.town(), plan, fixture.origin(), id);
        var pickup = SettlementConstructionMaterials.collectionTarget(project);
        SettlementConstructionMaterials.collect(fixture.town(), project, fixture.builder().getUUID(), pickup);
        var builder = new BuilderWorker();
        helper.startSequence().thenWaitUntil(() -> {
            SettlementConstructionService.reconcile(fixture.builder());
            int index = SettlementConstructionCoordination.select(fixture.town(), project, fixture.builder().getUUID(), plan,
                    helper.getLevel(), helper.getLevel().getGameTime());
            if (index >= 0 && index < project.total()) {
                var target = plan.worldPos(project.origin(), plan.blocks().get(index));
                fixture.builder().setPos(target.north(2).getBottomCenter());
            }
            var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), fixture.builder(),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
            builder.tick(helper.getLevel(), fixture.builder(), null, session.context());
            SettlementConstructionService.observe(fixture.builder(), HiredWorkStateStore.state(fixture.builder()));
            helper.assertTrue(project.placed() >= 2, "the actual worker incrementally places paid wall blocks: " + project.blocker());
        }).thenExecute(() -> {
            helper.assertTrue(project.placed() < project.total(), "the wall is not instantly completed");
            int spent = project.materialEscrow().getList("Quote", Tag.TAG_COMPOUND).getCompound(0).getInt("Spent");
            helper.assertTrue(spent > 0, "real placements spend the simplified wall quote");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), fixture.town(), id), "partially built wall can be cancelled");
            int returned = fixture.town().depots().values().stream().mapToInt(depot -> depot.count(SettlementWallMaterials::logs)).sum();
            helper.assertValueEqual(returned + spent, project.wallConstruction().save().getInt("Logs"), "spent plus refunded logs equal the original quote");
            long placed = plan.blocks().stream().filter(block -> BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)), block.state())).count();
            helper.assertTrue(placed >= 2, "cancellation leaves completed wall blocks in place");
            fixture.builder().discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 2000, batch = "wall_complete")
    public static void nativeCrewCompletesAndRecordsWall(GameTestHelper helper) {
        var fixture = fixture(helper);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        SettlementConstructionCrewGameTests.adopt(helper, fixture);
        var wall = wall(fixture);
        var id = UUID.randomUUID();
        String failure = SettlementWallConstruction.commission(fixture.leader(), fixture.town(), id, wall,
                SettlementWallTerrain.preview(helper.getLevel(), wall, fixture.town()::contains));
        helper.assertTrue(failure.isEmpty(), "wall queued: " + failure);
        var project = fixture.town().projects().get(id);
        var plan = SettlementConstructionService.scan(helper.getLevel(), project);
        SettlementConstructionGameTests.fundAndDispatch(helper, fixture.town(), plan, fixture.origin(), id);
        helper.assertTrue(SettlementConstructionService.configureCrew(fixture.leader(), fixture.town(), id, project.crewRevision(), 5,
                List.of(assistant.getUUID())), "the actual wall project accepts another builder");
        SettlementConstructionMaterials.collect(fixture.town(), project, fixture.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
        var builder = new BuilderWorker();
        var participants = new HashSet<UUID>();
        helper.startSequence().thenWaitUntil(() -> {
            for (var member : List.of(fixture.builder(), assistant)) {
                if (!project.active()) break;
                SettlementConstructionService.reconcile(member);
                int index = SettlementConstructionCoordination.select(fixture.town(), project, member.getUUID(), plan,
                        helper.getLevel(), helper.getLevel().getGameTime());
                if (index >= 0 && index < project.total()) {
                    var target = plan.worldPos(project.origin(), plan.blocks().get(index));
                    member.setPos(target.north(2).getBottomCenter());
                }
                int before = project.placed();
                var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), member,
                        com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
                builder.tick(helper.getLevel(), member, null, session.context());
                SettlementConstructionService.observe(member, HiredWorkStateStore.state(member));
                if (project.placed() > before) participants.add(member.getUUID());
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "the native crew completes the entire wall: " + project.blocker());
        }).thenExecute(() -> {
            helper.assertValueEqual(participants.size(), 2, "both assigned builders contributed actual wall blocks");
            helper.assertValueEqual(project.placed(), plan.blocks().size(), "all wall blocks are completed once");
            for (var block : plan.blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)), block.state()), "the completed wall matches its frozen preview");
            var quote = project.materialEscrow().getList("Quote", Tag.TAG_COMPOUND).getCompound(0);
            helper.assertValueEqual(quote.getInt("Spent"), quote.getInt("Count"), "completion spends exactly the original wall quote");
            SettlementConstructionService.tickQueue(helper.getLevel(), fixture.town());
            var records = SettlementWallsSavedData.get(helper.getLevel());
            helper.assertTrue(records.hasGroup(id), "the completed wall is registered for editing");
            for (var block : plan.blocks()) {
                var entry = records.at(helper.getLevel(), fixture.town().id(), plan.worldPos(project.origin(), block));
                if (entry != null) records.remove(entry.id());
            }
            SettlementConstructionService.tickQueue(helper.getLevel(), fixture.town());
            helper.assertFalse(records.hasGroup(id), "later demolition cannot resurrect completed wall records");
            fixture.builder().discard();
            assistant.discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_plans")
    public static void allWallTypesRetainAutomaticNodesClippingAndMaterialQuotes(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var start = fixture.origin().offset(2, 0, 6);
            for (var type : SettlementWall.Type.values()) {
                var wall = new SettlementWall(type, List.of(start, start.east(16)), false, 8);
                var preview = SettlementWallTerrain.preview(helper.getLevel(), wall, fixture.town()::contains);
                helper.assertTrue(preview.error().isEmpty(), type + " has a valid preview: " + preview.error());
                var construction = SettlementWallConstruction.create(wall, preview);
                var id = UUID.randomUUID();
                var plan = construction.plan(id);
                helper.assertValueEqual(plan.blocks().size(), (int) preview.cells().stream()
                        .filter(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE && !cell.target().isAir()).count(),
                        "every approved block and foundation is included");
                helper.assertValueEqual(BuilderStructureScanner.fingerprint(SettlementWallConstruction.load(construction.save()).plan(id)),
                        BuilderStructureScanner.fingerprint(plan), "each wall type retains its exact authored states and quote");
                var nodes = SettlementWallGeometry.towers(wall);
                helper.assertTrue(nodes.size() > wall.nodes().size(), "automatic nodes participate in the plan");
                var saved = construction.save();
                var malformed = saved.copy();
                malformed.getList("Blocks", Tag.TAG_COMPOUND).getCompound(0).putInt("Stage", -1);
                boolean rejected = false;
                try { SettlementWallConstruction.load(malformed); }
                catch (IllegalArgumentException invalid) { rejected = true; }
                helper.assertTrue(rejected, "invalid construction stages are rejected");
            }
            helper.getLevel().setBlock(start, Blocks.STONE.defaultBlockState(), 2);
            var clipped = SettlementWallTerrain.preview(helper.getLevel(), wall(fixture), fixture.town()::contains);
            var construction = SettlementWallConstruction.create(wall(fixture), clipped);
            helper.assertFalse(construction.plan(UUID.randomUUID()).blocks().stream()
                    .anyMatch(block -> construction.origin().offset(block.localPos()).equals(start)), "skipped terrain is excluded from builder work");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    private static boolean atNode(BlockPos pos, BlockPos first, BlockPos second) {
        return pos.getX() == first.getX() && pos.getZ() == first.getZ() || pos.getX() == second.getX() && pos.getZ() == second.getZ();
    }

    private static SettlementWall wall(SettlementConstructionGameTests.Fixture fixture) {
        var start = fixture.origin().offset(2, 0, 6);
        return new SettlementWall(SettlementWall.Type.PALISADE, List.of(start, start.east(8), start.east(16)), false, 0);
    }

    private static SettlementConstructionGameTests.Fixture fixture(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        fixture.town().advance();
        for (var pos : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max()))
            helper.getLevel().setBlock(pos, pos.getY() < fixture.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return fixture;
    }
}
