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
        var level = helper.getLevel();
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        try {
            var wall = wall(fixture);
            var town = fixture.town();
            town.removeDuty(fixture.builder().getUUID());
            var cells = SettlementWallTerrain.preview(level, wall, town::contains).cells().stream()
                    .filter(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE
                            && !cell.target().getCollisionShape(level, cell.position()).isEmpty()).toList();
            var target = cells.getFirst();
            pig.setPos(target.position().getBottomCenter());
            pig.setNoAi(true);
            helper.assertTrue(level.addFreshEntity(pig), "occupant is present in the world");
            var session = new SettlementWallSession();
            var id = UUID.randomUUID();
            var preview = session.request(helper.getLevel(), id, true, town::contains,
                    new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.PREVIEW, id, wall), town.id(), fixture.leader());
            helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "the occupied wall preview is valid");
            pig.setPos(cells.getLast().position().getBottomCenter());
            var confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null);
            helper.assertValueEqual(session.request(helper.getLevel(), id, false, town::contains, confirm, town.id(), fixture.leader()).status(),
                    SettlementWallResultPayload.Status.REJECTED, "revoked permission cannot queue a wall");
            var accepted = session.request(helper.getLevel(), id, true, town::contains, confirm, town.id(), fixture.leader());
            helper.assertValueEqual(accepted.status(), SettlementWallResultPayload.Status.APPLIED, "moving entities do not prevent queuing: " + accepted.message());
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
            pig.setPos(target.position().getBottomCenter());
            var check = com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.canPlaceAt(
                    level, fixture.builder(), target.position(), target.target());
            helper.assertValueEqual(check.statusKey(), "interaction.work.builder.blocked_entity", "native builders wait for occupants");
            var placement = new SettlementConstructionPlacement(level, List.of(new SettlementConstructionPlacement.Part(
                    target.position(), target.before(), target.target(), null)));
            helper.assertFalse(placement.reconcile(level, project.id()), "pending world placement also waits for occupants");
            helper.assertValueEqual(level.getBlockState(target.position()), target.before(), "occupied blocks remain untouched");
            pig.discard();
            helper.assertTrue(placement.reconcile(level, project.id()), "placement resumes when the entity leaves");
            helper.assertValueEqual(level.getBlockState(target.position()), target.target(), "the cleared block is placed");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), town, project.id()), "pending wall can be cancelled");
            helper.assertFalse(SettlementWallsSavedData.get(helper.getLevel()).hasGroup(project.id()), "an unbuilt wall has no completed wall ownership");
        } finally { pig.discard(); fixture.builder().discard(); }
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_camp")
    public static void campBuildsOneBasicPalisadeAtATime(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        try {
            for (var pos : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max()))
                helper.getLevel().setBlock(pos, pos.getY() < fixture.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            var town = fixture.town();
            helper.assertValueEqual(town.stage(), SettlementStage.CAMP, "the settlement is still a Camp");
            town.removeDuty(fixture.builder().getUUID());
            helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), fixture.leader(), town.id(), fixture.builder(), fixture.area().id()),
                    "Camp residents can be assigned as builders through the staffing service");
            var wall = wall(fixture);
            var id = UUID.randomUUID();
            String failure = SettlementWallConstruction.commission(fixture.leader(), town, id, wall,
                    SettlementWallTerrain.preview(helper.getLevel(), wall, town::contains));
            helper.assertTrue(failure.isEmpty(), "Camp can commission a basic palisade: " + failure);
            var project = town.projects().get(id);
            SettlementConstructionGameTests.fundAndDispatch(helper, town, project.wallConstruction().plan(id), fixture.origin(), id);
            helper.assertTrue(project.active(), "a funded Camp palisade starts construction");
            var nextWall = new SettlementWall(SettlementWall.Type.PALISADE, wall.nodes().stream().map(pos -> pos.south(5)).toList(), false, 0);
            var nextId = UUID.randomUUID();
            failure = SettlementWallConstruction.commission(fixture.leader(), town, nextId, nextWall,
                    SettlementWallTerrain.preview(helper.getLevel(), nextWall, town::contains));
            helper.assertTrue(failure.isEmpty(), "another palisade can wait in the same queue: " + failure);
            helper.assertTrue(town.projects().get(nextId).pending(), "Camp has only one active project slot");
            helper.assertFalse(town.activateProject(town.projects().get(nextId), UUID.randomUUID()), "direct activation also enforces the Camp cap");
            for (var type : SettlementWall.Type.values()) {
                if (type == SettlementWall.Type.PALISADE) continue;
                var advanced = new SettlementWall(type, wall.nodes(), false, 0);
                failure = SettlementWallConstruction.commission(fixture.leader(), town, UUID.randomUUID(), advanced,
                        syntheticPreview(advanced));
                helper.assertTrue(failure.contains("Hamlet"), "advanced walls still require Hamlet: " + type);
            }
            var reinforced = new SettlementWall(SettlementWall.Type.PALISADE, wall.nodes(), false, 0,
                    List.of(SettlementWall.Tower.LOW_STONE, SettlementWall.Tower.DEFAULT, SettlementWall.Tower.DEFAULT));
            helper.assertFalse(SettlementWallConstruction.create(reinforced, syntheticPreview(reinforced)).campAvailable(),
                    "stone tower overrides cannot bypass the Camp restriction");
            var building = SettlementProject.planned(town, fixture.area(), fixture.builder().getUUID(), fixture.plan(),
                    fixture.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
            building.queue(fixture.leader().getUUID());
            helper.assertValueEqual(building.capacity(town), 0, "ordinary buildings still require Hamlet");
            helper.assertFalse(town.queueProject(fixture.leader().getUUID(), building), "Camp cannot queue an ordinary building");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertTrue(restored.projects().get(id).active() && restored.projects().get(nextId).pending(),
                    "Camp construction and its queue survive reload");
            helper.assertTrue(SettlementConstructionService.cancel(fixture.leader(), town, id), "the first palisade can be cancelled");
            SettlementConstructionGameTests.fundAndDispatch(helper, town, town.projects().get(nextId).wallConstruction().plan(nextId), fixture.origin(), nextId);
            helper.assertTrue(town.projects().get(nextId).active(), "the next palisade uses the released slot");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wall_limits")
    public static void largeWallUsesItsRouteAndSurvivesReload(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(12, 2, 12));
        var isolatedOrigin = new BlockPos(origin.getX() * 16, origin.getY(), origin.getZ() * 16);
        var fixture = fixture(helper, isolatedOrigin);
        try {
            var start = fixture.origin().offset(8, 0, -256);
            var wall = new SettlementWall(SettlementWall.Type.LOW_STONE,
                    List.of(start, start.east(128), start.east(128).south(96), start.south(96)), true, 0);
            var preview = syntheticPreview(wall);
            var construction = SettlementWallConstruction.create(wall, preview);
            var id = UUID.randomUUID();
            var plan = construction.plan(id);
            helper.assertTrue(plan.blocks().size() > 4096, "the wall exceeds the ordinary building budget");
            helper.assertValueEqual(plan.blockLimit(), 16_384, "walls use the editor's block budget");
            helper.assertValueEqual(fixture.plan().blockLimit(),
                    Math.max(128, com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get()),
                    "ordinary buildings retain their configured budget");
            var area = construction.workplace(helper.getLevel(), id);
            helper.assertTrue(area.max().getX() - area.min().getX() + 1 > 128, "the wall exceeds ordinary area width");
            helper.assertTrue((long) (area.max().getX() - area.min().getX() + 1) * (area.max().getY() - area.min().getY() + 1)
                    * (area.max().getZ() - area.min().getZ() + 1) > SettlementArea.MAX_VOLUME, "the enclosure exceeds ordinary area volume");
            helper.assertFalse(construction.surveyOrigins().stream().anyMatch(pos -> pos.getX() >> 4 == start.east(64).getX() >> 4
                    && pos.getZ() >> 4 == start.south(48).getZ() >> 4), "empty interior sections are never surveyed");
            for (var node : SettlementWallGeometry.pieces(wall)) {
                var piece = SettlementWallGeometry.build(node);
                for (var cell : piece.cells()) {
                    var pos = cell.position();
                    SettlementTestPlots.claim(helper.getLevel(), fixture.town(), pos, pos);
                }
            }
            for (var cell : preview.cells()) helper.getLevel().setBlock(cell.position(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertFalse(fixture.town().contains(start.east(64).south(48)), "the fixture owns the wall route without its empty interior");
            String failure = SettlementWallConstruction.commission(fixture.leader(), fixture.town(), id, wall, preview);
            helper.assertTrue(failure.isEmpty(), "large walls queue without a rectangular territory or volume requirement: " + failure);
            var project = fixture.town().projects().get(id);
            helper.assertTrue(project != null && fixture.town().owns(project), "the entire drawing is one owned project");
            helper.assertTrue(fixture.town().activateProject(project, fixture.builder().getUUID()), "the large wall can activate");
            SettlementConstructionSiteService.observeArea(helper.getLevel(), fixture.town(), project.workplace(), null);
            helper.assertTrue(project.site().sections().size() <= 2, "wall surveying captures at most two sections per pass");
            var supplied = helper.getLevel().getChunkAt(start);
            int before = project.site().sections().size();
            SettlementConstructionSiteService.observeArea(helper.getLevel(), fixture.town(), project.workplace(), supplied);
            helper.assertTrue(project.site().sections().size() <= before + 2, "chunk callbacks retain the survey budget");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var saved = restored.projects().get(id);
            helper.assertTrue(saved.workplace().wall() && restored.owns(saved) && restored.owns(saved.workplace()), "large wall bounds remain valid after reload");
            helper.assertValueEqual(BuilderStructureScanner.fingerprint(saved.wallConstruction().plan(id)),
                    BuilderStructureScanner.fingerprint(plan), "the large wall retains its exact construction plan");
            helper.assertFalse(fixture.town().putArea(fixture.leader().getUUID(), area), "ordinary areas cannot opt into wall bounds");
            boolean rejected = false;
            try { new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Ordinary building", area.min(), area.max(), 5, true); }
            catch (IllegalArgumentException invalid) { rejected = true; }
            helper.assertTrue(rejected, "ordinary worksite bounds remain enforced");
        } finally { fixture.builder().discard(); }
        helper.succeed();
    }

    private static SettlementWallTerrain.Preview syntheticPreview(SettlementWall wall) {
        var geometry = SettlementWallGeometry.build(wall);
        if (!geometry.error().isEmpty()) throw new IllegalArgumentException(geometry.error());
        return new SettlementWallTerrain.Preview(geometry.cells().stream().filter(cell -> !cell.state().isAir())
                .map(cell -> new SettlementWallTerrain.Cell(cell.position(), cell.state(), Blocks.AIR.defaultBlockState(),
                        Blocks.DIRT.defaultBlockState(), SettlementWallTerrain.Kind.PLACE)).toList(), "");
    }

    private static boolean atNode(BlockPos pos, BlockPos first, BlockPos second) {
        return pos.getX() == first.getX() && pos.getZ() == first.getZ() || pos.getX() == second.getX() && pos.getZ() == second.getZ();
    }

    private static SettlementWall wall(SettlementConstructionGameTests.Fixture fixture) {
        var start = fixture.origin().offset(2, 0, 6);
        return new SettlementWall(SettlementWall.Type.PALISADE, List.of(start, start.east(8), start.east(16)), false, 0);
    }

    private static SettlementConstructionGameTests.Fixture fixture(GameTestHelper helper) {
        return fixture(helper, helper.absolutePos(new BlockPos(12, 2, 12)));
    }

    private static SettlementConstructionGameTests.Fixture fixture(GameTestHelper helper, BlockPos origin) {
        var fixture = SettlementConstructionGameTests.fixture(helper, origin);
        fixture.town().advance();
        for (var pos : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max()))
            helper.getLevel().setBlock(pos, pos.getY() < fixture.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return fixture;
    }
}
