package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.mixin.EntityStorageAccessor;
import com.jvn.villagerretaliation.mixin.PersistentEntitySectionManagerAccessor;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class EncounterActorTreeGameTests {
    private EncounterActorTreeGameTests() { }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void initializationCapturesNewMountsBeforeMaterializingTheRider(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var rider = fixture.create(EntityType.ZOMBIE);
            rider.setBaby(true);
            var prepared = fixture.prepare(rider);
            var nodes = EncounterActorInitializer.initialize(rider, prepared, actor -> {
                var mount = fixture.create(EntityType.CHICKEN);
                mount.setChickenJockey(true);
                helper.assertTrue(actor.startRiding(mount, true), "fixture rider joins its new mount");
                helper.assertTrue(fixture.level().addFreshEntity(mount), "initializer inserts the mount");
                return "";
            });
            var receipt = fixture.reserve(prepared.ready(nodes));
            helper.assertValueEqual(nodes.size(), 2, "initializer captures the complete mounted group");
            helper.assertTrue(nodes.stream().allMatch(EncounterActorSpawn.Node::owned), "new mount and rider belong to this birth");
            var restored = EncounterActorMaterializer.materialize(fixture.level(), receipt);
            helper.assertValueEqual(restored.getUUID(), rider.getUUID(), "materialization keeps the rider identity");
            helper.assertTrue(restored.getVehicle() != null, "rider keeps its original mount");
            helper.assertTrue(EncounterActorBirths.born(fixture.level(), receipt), "mounted group receives native completion");
            helper.assertTrue(contains(nativeEntities(fixture, restored.getRootVehicle()), rider.getUUID()), "native storage includes the rider");
            helper.assertTrue(contains(nativeEntities(fixture, restored.getRootVehicle()), restored.getVehicle().getUUID()), "native storage includes the mount");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void partialPassengerInsertionResumesWithoutReinitialization(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var spider = fixture.create(EntityType.SPIDER);
            var prepared = fixture.prepare(spider);
            var nodes = EncounterActorInitializer.initialize(spider, prepared, actor -> {
                var passenger = fixture.create(EntityType.SKELETON);
                passenger.setCustomName(Component.literal("Saved passenger"));
                helper.assertTrue(passenger.startRiding(actor, true), "fixture creates a passenger");
                return "";
            });
            var receipt = fixture.reserve(prepared.ready(nodes));
            var passengerId = nodes.get(1).id();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (event.getEntity().getUUID().equals(passengerId)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { expectFailure(helper, () -> EncounterActorMaterializer.materialize(fixture.level(), receipt), "could not enter"); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(!EncounterActorBirths.born(fixture.level(), receipt), "partial group cannot commit");
            var root = fixture.level().getEntity(spider.getUUID());
            helper.assertTrue(root != null, "first entity remains available for recovery");
            var restored = EncounterActorMaterializer.materialize(fixture.level(), EncounterActorSpawn.load(receipt.save()));
            helper.assertTrue(restored == root, "recovery reuses the inserted root");
            var passenger = fixture.level().getEntity(passengerId);
            helper.assertTrue(passenger != null && passenger.getVehicle() == root, "recovery inserts the exact saved passenger");
            helper.assertValueEqual(passenger.getCustomName().getString(), "Saved passenger", "initialized state is not rerolled");
            helper.assertTrue(contains(nativeEntities(fixture, root), passengerId), "native tree is complete before acknowledgement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void existingMountKeepsItsIdentityOwnershipAndPersonalState(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mount = fixture.create(EntityType.CHICKEN);
            mount.setCustomName(Component.literal("Existing chicken"));
            mount.getPersistentData().putString("FixtureOwner", "player");
            helper.assertTrue(fixture.level().addFreshEntity(mount), "fixture inserts an existing mount");
            var rider = fixture.create(EntityType.ZOMBIE);
            rider.setBaby(true);
            var prepared = fixture.prepare(rider);
            var nodes = EncounterActorInitializer.initialize(rider, prepared, actor -> {
                mount.setChickenJockey(true);
                helper.assertTrue(actor.startRiding(mount, true), "initializer adopts the existing mount");
                return "";
            });
            helper.assertTrue(!nodes.getFirst().owned(), "an existing mount is recorded as external");
            var receipt = fixture.reserve(prepared.ready(nodes));
            var restored = EncounterActorMaterializer.materialize(fixture.level(), receipt);
            helper.assertTrue(restored.getVehicle() == mount, "materialization keeps the existing mount object");
            helper.assertValueEqual(mount.getCustomName().getString(), "Existing chicken", "personal state remains intact");
            helper.assertValueEqual(mount.getPersistentData().getString("FixtureOwner"), "player", "external ownership remains intact");
            helper.assertTrue(!mount.getPersistentData().contains(EncounterActorSpawning.OWNER), "existing mount is never claimed by the campaign");
            helper.assertTrue(contains(nativeEntities(fixture, mount), restored.getUUID()), "external root persists its campaign passenger");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void missingExternalMountCannotBeRecreatedFromTheSnapshot(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var mount = fixture.create(EntityType.CHICKEN);
            helper.assertTrue(fixture.level().addFreshEntity(mount), "fixture inserts an existing mount");
            var rider = fixture.create(EntityType.ZOMBIE);
            rider.setBaby(true);
            var prepared = fixture.prepare(rider);
            var receipt = fixture.reserve(prepared.ready(EncounterActorInitializer.initialize(rider, prepared, actor -> {
                actor.startRiding(mount, true);
                return "";
            })));
            rider.stopRiding();
            mount.discard();
            expectFailure(helper, () -> EncounterActorMaterializer.materialize(fixture.level(), receipt), "external mount");
            helper.assertTrue(fixture.level().getEntity(mount.getUUID()) == null, "external mount is never cloned");
            helper.assertTrue(fixture.level().getEntity(rider.getUUID()) == null, "rider waits for its actual original mount");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void nativeCompletionPreventsCloningAMissingPassenger(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var root = fixture.create(EntityType.SPIDER);
            var prepared = fixture.prepare(root);
            var receipt = fixture.reserve(prepared.ready(EncounterActorInitializer.initialize(root, prepared, actor -> {
                fixture.create(EntityType.SKELETON).startRiding(actor, true);
                return "";
            })));
            var actor = EncounterActorMaterializer.materialize(fixture.level(), receipt);
            var passenger = actor.getFirstPassenger();
            var id = passenger.getUUID();
            passenger.discard();
            expectFailure(helper, () -> EncounterActorMaterializer.materialize(fixture.level(), receipt), "native birth receipt");
            helper.assertTrue(fixture.level().getEntity(id) == null, "native completion prevents replaying a missing passenger");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void missingNativeReservationNeverGetsReinvented(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actor = fixture.create(EntityType.COW);
            var prepared = fixture.prepare(actor);
            var receipt = fixture.reserve(prepared.ready(EncounterActorInitializer.initialize(actor, prepared, entity -> "")));
            var chunk = fixture.level().getChunkAt(receipt.anchor());
            chunk.removeData(EncounterActorBirths.BIRTHS);
            expectFailure(helper, () -> EncounterActorMaterializer.materialize(fixture.level(), receipt), "proof is missing");
            expectFailure(helper, () -> EncounterActorBirths.reserve(fixture.level(), receipt), "lost its native proof");
            helper.assertTrue(fixture.level().getEntity(actor.getUUID()) == null, "unproven reservation cannot insert an entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void futureNativeProofsArePreservedAndBlockInsertion(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actor = fixture.create(EntityType.COW);
            var prepared = fixture.prepare(actor);
            var receipt = prepared.ready(EncounterActorInitializer.initialize(actor, prepared, entity -> ""));
            var raw = new CompoundTag();
            raw.putInt("Version", 99);
            raw.putString("FutureEvidence", "retain");
            var ledger = EncounterActorBirths.Ledger.load(raw);
            fixture.level().getChunkAt(receipt.anchor()).setData(EncounterActorBirths.BIRTHS, ledger);
            expectFailure(helper, () -> EncounterActorBirths.reserve(fixture.level(), receipt), "unreadable");
            helper.assertValueEqual(ledger.save(), raw, "unsupported native data round-trips unchanged");
            helper.assertTrue(fixture.level().getEntity(actor.getUUID()) == null, "unreadable native proofs cannot spawn actors");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void malformedAndFutureActorRecordsStayDormant(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actor = fixture.create(EntityType.COW);
            var receipt = fixture.prepare(actor);
            var ledger = new EncounterActorSpawns();
            ledger.record(receipt);
            var raw = (CompoundTag) ledger.save();
            var future = raw.copy();
            future.putInt("Version", 99);
            var restored = EncounterActorSpawns.load(future, fixture.encounter);
            helper.assertValueEqual(restored.save(), future, "future actor history is preserved");
            helper.assertTrue(!restored.diagnostic().isBlank(), "future history is not executable");
            var duplicate = raw.copy();
            var list = duplicate.getList("Records", Tag.TAG_COMPOUND);
            list.add(list.getCompound(0).copy());
            restored = EncounterActorSpawns.load(duplicate, fixture.encounter);
            helper.assertValueEqual(restored.save(), duplicate, "conflicting duplicate records remain readable for repair");
            helper.assertTrue(!restored.diagnostic().isBlank(), "duplicate actor keys cannot execute");
            var legacy = EncounterActorSpawns.load(null, fixture.encounter);
            helper.assertTrue(legacy.legacy() && legacy.save() == null, "missing history retains its legacy identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void changedOwnershipCannotReplaceAReservedActor(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actor = fixture.create(EntityType.COW);
            var prepared = fixture.prepare(actor);
            var receipt = fixture.reserve(prepared.ready(EncounterActorInitializer.initialize(actor, prepared, entity -> "")));
            var loaded = EncounterActorMaterializer.materialize(fixture.level(), receipt);
            loaded.getPersistentData().remove(EncounterActorSpawning.OWNER);
            expectFailure(helper, () -> EncounterActorMaterializer.materialize(fixture.level(), receipt), "conflicting ownership");
            helper.assertTrue(fixture.level().getEntity(actor.getUUID()) == loaded, "conflicting identity is preserved for explicit repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void retiredActorHistorySurvivesSceneCompaction(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var id = net.minecraft.resources.ResourceLocation.parse("campaign_fixture:actor_retention");
            var definition = com.jvn.villagerretaliation.scene.compiler.SceneCompiler.compile(
                    com.jvn.villagerretaliation.scene.compiler.SceneParser.parse(id, com.google.gson.JsonParser.parseString("""
                            {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:actor_retention","ownership":"player",
                             "entry_step":"done","actors":[],"steps":[{"id":"done","type":"villagerretaliation:scene_complete"}]}
                            """).getAsJsonObject()).resource()).scene();
            var errors = new java.util.ArrayList<String>();
            var template = EncounterResources.parse(id, com.google.gson.JsonParser.parseString("""
                    {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:actor_retention","members":[{"entity":"minecraft:cow"}]}
                    """).getAsJsonObject(), errors);
            helper.assertTrue(definition != null && template != null && errors.isEmpty(), "retention fixture compiles");
            var data = new com.jvn.villagerretaliation.scene.persistence.SceneSavedData();
            var player = UUID.randomUUID();
            var owner = new com.jvn.villagerretaliation.scene.runtime.SceneOwner(
                    com.jvn.villagerretaliation.scene.model.SceneResource.OwnershipMode.PLAYER, player, null, null, "");
            for (int index = 0; index < 2; index++) {
                var scene = data.start(definition, "retention/" + index, owner, null, java.util.Set.of(player), java.util.Map.of(), 0L, id).instance();
                var encounter = data.startEncounter(template, scene, "actors", fixture.level().dimension().location(), fixture.origin, "normal").encounter();
                if (index == 0) {
                    var receipt = EncounterActorSpawn.prepare("member/0/0", encounter.id(), UUID.randomUUID(), fixture.level().dimension().location(), fixture.origin);
                    encounter.actorSpawns().record(receipt.retired(true));
                }
                encounter.cleaned();
                com.jvn.villagerretaliation.scene.runtime.SceneTransitionService.complete(data, scene, 1L);
                scene.cleanupStatus(com.jvn.villagerretaliation.scene.runtime.SceneInstance.CleanupStatus.COMPLETE);
            }
            long later = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.TERMINAL_RETENTION_TICKS + 2L;
            helper.assertValueEqual(data.compactTerminalHistory(later, 8), 1, "only ordinary settled history is compacted");
            var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var reloaded = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(saved, helper.getLevel().registryAccess());
            helper.assertValueEqual(reloaded.compactTerminalHistory(later + 1, 8), 0, "actor retirement evidence survives reload and later compaction");
            helper.assertValueEqual(reloaded.encounters().size(), 1, "unloaded actors retain their cleanup authority");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_actor_trees")
    public static void initializationDoesNotClaimAnotherBirthsEntity(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var actor = fixture.create(EntityType.COW);
            var prepared = fixture.prepare(actor);
            var other = fixture.create(EntityType.CHICKEN);
            var otherReceipt = EncounterActorSpawn.prepare("member/0/0", UUID.randomUUID(), other.getUUID(), fixture.level().dimension().location(), fixture.origin);
            EncounterActorSpawning.stamp(other, otherReceipt);
            var nodes = EncounterActorInitializer.initialize(actor, prepared, entity -> {
                helper.assertTrue(fixture.level().addFreshEntity(other), "another birth joins during initialization");
                return "";
            });
            helper.assertValueEqual(nodes.size(), 1, "another operation's actor stays outside this birth");
            helper.assertTrue(EncounterActorSpawning.owns(other.getPersistentData(), otherReceipt), "existing birth ownership is never overwritten");
        }
        helper.succeed();
    }

    private static void expectFailure(GameTestHelper helper, Checked work, String message) throws IOException {
        try { work.run(); }
        catch (IOException exception) {
            helper.assertTrue(exception.getMessage().contains(message), "expected diagnostic " + message + ", got " + exception.getMessage());
            return;
        }
        helper.fail("unsafe actor operation should have been rejected");
    }

    private static CompoundTag nativeEntities(Fixture fixture, Entity root) {
        var manager = ((ServerLevelEntityManagerAccessor) fixture.level()).villagerretaliation$entityManager();
        var storage = (EntityStorage) ((PersistentEntitySectionManagerAccessor) manager).villagerretaliation$permanentStorage();
        return ((EntityStorageAccessor) storage).villagerretaliation$regionStorage().read(new ChunkPos(root.blockPosition())).join().orElseThrow();
    }

    private static boolean contains(CompoundTag tag, UUID id) {
        if (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(id)) return true;
        for (String key : List.of("Entities", "Passengers"))
            for (var child : tag.getList(key, Tag.TAG_COMPOUND))
                if (contains((CompoundTag) child, id)) return true;
        return false;
    }

    private interface Checked { void run() throws IOException; }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final UUID encounter = UUID.randomUUID();
        final EncounterActorTestStorage storage;
        final java.util.Set<Entity> created = new java.util.LinkedHashSet<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            origin = helper.absolutePos(new BlockPos(5, 2, 5));
            storage = new EncounterActorTestStorage(level(), origin, 4);
        }
        net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
        <T extends Entity> T create(EntityType<T> type) {
            T entity = type.create(level());
            entity.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
            created.add(entity);
            return entity;
        }
        EncounterActorSpawn prepare(Entity actor) {
            var receipt = EncounterActorSpawn.prepare("member/0/0", encounter, actor.getUUID(), level().dimension().location(), origin);
            EncounterActorSpawning.stamp(actor, receipt);
            return receipt;
        }
        EncounterActorSpawn reserve(EncounterActorSpawn ready) throws IOException {
            EncounterActorBirths.reserve(level(), ready);
            return ready.reserved();
        }
        @Override public void close() {
            var owned = new java.util.ArrayList<Entity>();
            for (var entity : level().getAllEntities())
                if (entity.getPersistentData().getCompound(EncounterActorSpawning.OWNER).hasUUID("Encounter")
                        && entity.getPersistentData().getCompound(EncounterActorSpawning.OWNER).getUUID("Encounter").equals(encounter)) owned.add(entity);
            owned.forEach(Entity::discard);
            created.forEach(Entity::discard);
            storage.close();
        }
    }
}
