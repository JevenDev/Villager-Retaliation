package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.scene.encounter.EncounterActorSpawning;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterActorRecoveryGameTests {
    private CampaignEncounterActorRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void lostAcknowledgementRecoversTheSameActorOutsideTheSearchRadius(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            interruptAfterMemberJoins(fixture);
            var member = member(fixture);
            var id = member.getUUID();
            helper.assertTrue(EncounterActorSpawning.blocked(member), "unacknowledged actors cannot tick or take damage");
            var death = new LivingDeathEvent((LivingEntity) member, helper.getLevel().damageSources().generic());
            VillagerRetaliationEvents.onLivingDeath(death);
            helper.assertTrue(death.isCanceled(), "unacknowledged actors cannot publish death effects");
            member.setPos(fixture.anchor.getX() + 7.5, fixture.anchor.getY(), fixture.anchor.getZ() + .5);
            fixture.restore(); fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().spawned(), Set.of(id), "recovery resolves the saved identity beyond the old bounded search");
            helper.assertValueEqual(fixture.entities().size(), 2, "recovery never creates another enemy or ally");
            helper.assertTrue(!EncounterActorSpawning.blocked(member), "committed actor resumes gameplay");
            helper.assertValueEqual(fixture.disk().encounter(fixture.encounter().id()).orElseThrow().spawned(),
                    Set.of(id), "membership reaches disk with the acknowledgement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void nativeBirthProofPreventsRespawningAnAbsentUnacknowledgedActor(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            interruptAfterMemberJoins(fixture);
            var actor = member(fixture);
            var id = actor.getUUID();
            actor.discard();
            fixture.restore(); fixture.reload(); fixture.run();
            helper.assertTrue(helper.getLevel().getEntity(id) == null, "a native birth proof cannot replay a missing actor");
            helper.assertValueEqual(fixture.entities().size(), 1, "the absent enemy is never replaced by another identity");
            helper.assertValueEqual(fixture.scene().currentStep(), "start", "unresolved permanent loss keeps the scene suspended");
            var result = EncounterService.refresh(fixture.base.base.server, fixture.data(), fixture.encounter());
            helper.assertTrue(result.diagnostic().contains("native birth receipt"), "recovery explains why it cannot safely spawn");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void cancelledJoinRetriesTheReservedIdentityAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var attempted = new java.util.LinkedHashSet<java.util.UUID>();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (!event.getEntity().getPersistentData().hasUUID("VillagerRetaliationEncounter")) return;
                attempted.add(event.getEntity().getUUID());
                event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(attempted.size(), 1, "cancelled joins reuse one reserved UUID");
            helper.assertValueEqual(fixture.entities().size(), 1, "cancelled enemy leaves its already committed ally");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().spawned(), attempted, "disk recovery inserts the original reservation");
            helper.assertValueEqual(fixture.entities().size(), 2, "exactly one enemy is inserted after recovery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void nativeReservationSurvivesALostSceneAcknowledgement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var interrupted = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (!births(event.getData()).isEmpty() && interrupted.compareAndSet(false, true)) {
                    try { fixture.block(); }
                    catch (IOException exception) { throw new AssertionError(exception); }
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(interrupted.get(), "fixture interrupted a native actor reservation");
            helper.assertTrue(fixture.entities().isEmpty(), "scene acknowledgement is required before insertion");
            fixture.restore();
            var before = fixture.disk().encounter(fixture.encounter().id()).orElseThrow().save()
                    .getCompound("ActorSpawns").getList("Records", Tag.TAG_COMPOUND);
            var identity = before.getCompound(0).getUUID("Entity");
            helper.assertValueEqual(before.getCompound(0).getString("State"), "READY", "initialized snapshot survives the interruption");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().allies().get("guard").entityId(), identity, "native reservation resumes its original ally");
            helper.assertValueEqual(fixture.entities().size(), 2, "repair resumes subsequent spawns");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void missingNativeCompletionPreventsActorAcknowledgement(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var rejected = new AtomicBoolean();
            Consumer<ChunkDataEvent.Save> listener = event -> {
                if (births(event.getData()).stream().anyMatch(raw -> ((CompoundTag) raw).getBoolean("Born"))) {
                    rejected.set(true);
                    event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).remove("villagerretaliation:encounter_actor_births");
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertTrue(rejected.get(), "native completion serialization was checked");
            helper.assertValueEqual(fixture.entities().size(), 1, "unacknowledged ally prevents later spawns");
            helper.assertTrue(fixture.encounter().allies().isEmpty(), "unsaved birth cannot publish ally membership");
            var actor = fixture.entities().getFirst();
            helper.assertTrue(EncounterActorSpawning.blocked(actor), "native save failure freezes the prepared ally");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(fixture.encounter().allies().get("guard").entityId(), actor.getUUID(), "repair acknowledges the same native entity");
            helper.assertValueEqual(fixture.entities().size(), 2, "repair resumes the encounter once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void malformedActorHistoryIsPreservedAndPausesExecution(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.block(); fixture.run(); fixture.restore();
            var saved = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            saved.getList("Encounters", Tag.TAG_COMPOUND).getCompound(0).putString("ActorSpawns", "unreadable fixture");
            install(fixture, saved);
            fixture.run();
            helper.assertTrue(fixture.entities().isEmpty(), "unreadable actor ownership cannot initialize entities");
            helper.assertValueEqual(fixture.encounter().save().getString("ActorSpawns"), "unreadable fixture", "invalid actor history is not discarded");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void legacyPendingSpawnsRequireRecoveryWithoutInventingActors(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.block(); fixture.run(); fixture.restore();
            var saved = fixture.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            saved.putInt("DataVersion", 19);
            saved.getList("Encounters", Tag.TAG_COMPOUND).getCompound(0).remove("ActorSpawns");
            install(fixture, saved);
            fixture.run();
            helper.assertTrue(fixture.entities().isEmpty(), "legacy pending actors have no identity safe to replay");
            helper.assertTrue(!fixture.encounter().save().contains("ActorSpawns"), "migration does not fabricate birth evidence");
            var result = EncounterService.refresh(fixture.base.base.server, fixture.data(), fixture.encounter());
            helper.assertTrue(result.diagnostic().contains("predates actor birth"), "legacy recovery has a specific diagnostic");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void cleanupRetiresAnUnacknowledgedBirth(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            interruptAfterMemberJoins(fixture);
            var actor = member(fixture);
            var snapshot = new CompoundTag();
            helper.assertTrue(actor.save(snapshot), "fixture captures the pending actor");
            fixture.restore(); fixture.reload();
            var result = EncounterService.cleanup(fixture.base.base.server, fixture.data(), fixture.encounter(), true);
            helper.assertValueEqual(result.status(), EncounterService.Status.COMPLETED, "pending birth can be retired durably");
            helper.assertTrue(fixture.entities().isEmpty(), "cleanup removes unacknowledged actors as well as published members");
            fixture.reload();
            var returning = EntityType.loadEntityRecursive(snapshot, helper.getLevel(), value -> value);
            helper.assertTrue(!helper.getLevel().addFreshEntity(returning), "a retired actor cannot return from stale native entity data");
            helper.assertTrue(fixture.entities().isEmpty(), "retired birth stays absent after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void encounterRetrySavesRetirementBeforeRemovingThePreviousGeneration(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            fixture.run();
            var old = member(fixture);
            var ally = fixture.encounter().allies().get("guard").entityId();
            fixture.encounter().scheduleFailure(com.jvn.villagerretaliation.scene.encounter.EncounterTemplate.FailureAction.RESTART_ENCOUNTER,
                    0L, "fixture retry", "");
            fixture.data().persistCampaignHandoff(fixture.base.base.server.overworld());
            fixture.block();
            var result = EncounterService.refresh(fixture.base.base.server, fixture.data(), fixture.encounter());
            helper.assertValueEqual(result.status(), EncounterService.Status.WAITING, "failed retry save suspends the reset");
            helper.assertTrue(!old.isRemoved(), "old actors survive until retirement is durable");
            fixture.restore(); fixture.reload(); fixture.run();
            var replacement = member(fixture);
            helper.assertTrue(!replacement.getUUID().equals(old.getUUID()) && old.isRemoved(), "retry creates one new generation after retiring the old one");
            helper.assertValueEqual(fixture.encounter().allies().get("guard").entityId(), ally, "retry retains its existing ally");
            var replacementId = replacement.getUUID();
            fixture.reload(); fixture.run();
            helper.assertValueEqual(member(fixture).getUUID(), replacementId, "reloading a retry cannot advance its generation again");
            helper.assertValueEqual(fixture.entities().size(), 2, "only the current enemy and ally remain");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void interruptedAllyRevivalKeepsOneReplacementIdentity(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, """
                {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                 "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow"}],
                 "allies":[{"id":"guard","entity":"minecraft:iron_golem","cleanup_policy":"remove","revivable":true,"revive_delay_ticks":1}]}
                """)) {
            fixture.run();
            var prior = fixture.encounter().allies().get("guard").entityId();
            var old = (LivingEntity) helper.getLevel().getEntity(prior);
            var oldSnapshot = new CompoundTag();
            helper.assertTrue(old.save(oldSnapshot), "fixture captures the former ally");
            EncounterService.onDeath(old, fixture.data());
            fixture.encounter().replaceAlly(fixture.encounter().allies().get("guard").dead(0L));
            old.discard();
            var attempts = new java.util.LinkedHashSet<java.util.UUID>();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (!event.getEntity().getPersistentData().hasUUID("VillagerRetaliationEncounterAlly")) return;
                attempts.add(event.getEntity().getUUID());
                event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(attempts.size(), 1, "cancelled revival retains one birth identity");
            fixture.reload(); fixture.run();
            var restored = fixture.encounter().allies().get("guard");
            helper.assertTrue(attempts.contains(restored.entityId()) && !restored.entityId().equals(prior), "revival recovers its original replacement");
            helper.assertValueEqual(restored.generation(), 2, "revival advances the ally generation once");
            var returning = EntityType.loadEntityRecursive(oldSnapshot, helper.getLevel(), value -> value);
            helper.assertTrue(!helper.getLevel().addFreshEntity(returning), "the replaced ally cannot return from stale native data");
            helper.assertValueEqual(fixture.entities().size(), 2, "revival does not duplicate the enemy or ally");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void laterWavesRetainTheirOwnBirthReceiptsAcrossReloads(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper, """
                {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                 "spawn_mode":"raid_waves","spawn_radius":0,"waves":[
                  {"id":"first","members":[{"entity":"minecraft:cow"}]},
                  {"id":"second","trigger":"all_defeated","members":[{"entity":"minecraft:cow"}]}]}
                """)) {
            fixture.run();
            var first = (LivingEntity) member(fixture);
            EncounterService.onDeath(first, fixture.data());
            first.discard();
            fixture.run();
            var second = member(fixture);
            helper.assertTrue(!first.getUUID().equals(second.getUUID()), "next wave has a separate reserved identity");
            helper.assertValueEqual(fixture.encounter().currentWaveId(), "second", "encounter advances to the next authored wave");
            fixture.reload(); fixture.run();
            helper.assertValueEqual(member(fixture).getUUID(), second.getUUID(), "reload retains the current wave identity");
            helper.assertValueEqual(fixture.entities().size(), 1, "defeated wave never spawns again");
        }
        helper.succeed();
    }

    private static void interruptAfterMemberJoins(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        var blocked = new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (!event.getEntity().getPersistentData().hasUUID("VillagerRetaliationEncounter") || !blocked.compareAndSet(false, true)) return;
            try { fixture.block(); }
            catch (IOException exception) { throw new AssertionError(exception); }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try { fixture.run(); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        fixture.helper.assertTrue(blocked.get(), "fixture interrupts the member acknowledgement");
    }

    private static Entity member(CampaignEncounterPersistenceGameTests.Fixture fixture) {
        return fixture.entities().stream().filter(entity -> entity.getPersistentData().hasUUID("VillagerRetaliationEncounter")).findFirst().orElseThrow();
    }

    private static ListTag births(CompoundTag chunk) {
        return chunk.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .getCompound("villagerretaliation:encounter_actor_births").getList("Records", Tag.TAG_COMPOUND);
    }

    private static void install(CampaignEncounterPersistenceGameTests.Fixture fixture, CompoundTag saved) {
        fixture.base.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", SceneSavedData.load(saved, fixture.helper.getLevel().registryAccess()));
        SceneStepEngine.clearRuntimeState();
    }
}
