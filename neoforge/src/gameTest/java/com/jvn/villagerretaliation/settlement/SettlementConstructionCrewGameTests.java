package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionCrewGameTests {
    private SettlementConstructionCrewGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_crews")
    public static void crewClaimsProtectEveryPartAndExpireWithoutChangingProgress(GameTestHelper helper) {
        var lead = UUID.randomUUID();
        var assistant = UUID.randomUUID();
        var crew = new SettlementConstructionCrew(lead, 8, 0);
        helper.assertTrue(crew.configure(0L, 7, List.of(assistant)), "crew adoption succeeds once");
        helper.assertFalse(crew.configure(0L, 0, List.of()), "a stale edit cannot remove the crew");
        helper.assertTrue(crew.acquire(lead, 0, List.of(BlockPos.ZERO, BlockPos.ZERO.above()), 10L), "the lead claims both door parts");
        helper.assertFalse(crew.acquire(assistant, 1, List.of(BlockPos.ZERO.above()), 10L), "the second half cannot be claimed separately");
        helper.assertTrue(crew.acquire(assistant, 2, List.of(BlockPos.ZERO.east()), 10L), "an independent placement can run concurrently");
        helper.assertFalse(crew.acquire(UUID.randomUUID(), 3, List.of(BlockPos.ZERO.west()), 10L), "nonmembers cannot reserve placements");
        helper.assertTrue(crew.claim(lead, 209L) != null, "the bounded claim remains live");
        helper.assertTrue(crew.claim(lead, 210L) == null, "abandoned navigation cannot hold a placement forever");
        helper.assertValueEqual(crew.placed(), 0, "claims never count as completed placements");
        helper.assertTrue(crew.acquire(assistant, -1, List.of(BlockPos.ZERO), 210L), "clearing uses the same position exclusion");
        helper.assertTrue(crew.detach(assistant), "reassignment removes the assistant");
        helper.assertTrue(crew.claim(assistant, 210L) == null, "reassignment releases every target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_crews")
    public static void crewProgressPersistsOutOfOrderAndRejectsMalformedRecords(GameTestHelper helper) {
        var lead = UUID.randomUUID();
        var assistant = UUID.randomUUID();
        var crew = new SettlementConstructionCrew(lead, 6, 2);
        crew.configure(0L, 9, List.of(assistant));
        crew.refresh(index -> index < 2 || index == 4);
        crew.acquire(lead, 2, List.of(BlockPos.ZERO), 1L);
        var saved = crew.save();
        var restored = SettlementConstructionCrew.load(saved, lead, 6, 2);
        helper.assertValueEqual(restored.placed(), 3, "out of order work counts exactly once");
        helper.assertValueEqual(restored.next(), 2, "a later placement cannot skip an unfinished dependency");
        helper.assertTrue(restored.claim(lead, 1L) == null, "reload rechecks work against actual world receipts");
        helper.assertValueEqual(restored.assistants(), List.of(assistant), "crew membership survives reload");
        helper.assertValueEqual(restored.priority(), 9, "project priority survives reload");
        var bad = saved.copy();
        bad.getList("Assistants", Tag.TAG_COMPOUND).add(bad.getList("Assistants", Tag.TAG_COMPOUND).getFirst().copy());
        helper.assertTrue(rejects(bad, lead, 6, 2), "duplicate members are rejected");
        bad = saved.copy();
        bad.putLongArray("Completed", new long[] { 1L << 20 });
        helper.assertTrue(rejects(bad, lead, 6, 2), "progress outside the schematic is rejected");
        bad = saved.copy();
        bad.putInt("Version", 2);
        helper.assertTrue(rejects(bad, lead, 6, 2), "unknown crew versions are preserved by recovery");
        helper.assertTrue(rejects(saved, lead, 6, 3), "the task cursor must agree with shared progress");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_crews")
    public static void assistantsShareProgressButNeverReceiveTheLeadFee(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var assistant = assistant(helper, fixture);
        try {
            fixture.town().advance();
            var project = begin(helper, fixture);
            helper.assertFalse(SettlementConstructionService.configureCrew(fixture.leader(), fixture.town(), project.id(), 0L, 7,
                    List.of(assistant.getUUID())), "old worksites need explicit adoption");
            adopt(helper, fixture);
            helper.assertTrue(SettlementConstructionService.configureCrew(fixture.leader(), fixture.town(), project.id(), 0L, 7,
                    List.of(assistant.getUUID())), "an adopted civic worker may assist");
            var state = project.state();
            BuilderTaskState.setPlacedIndex(state, project.total());
            BuilderTaskState.setBlocked(state, "missing_materials");
            project.observe(state);
            helper.assertValueEqual(project.placed(), 0, "an assistant cursor cannot complete the shared project");
            helper.assertValueEqual(project.blocker(), "missing_materials", "worker diagnostics still reach the project");
            project.refreshProgress(index -> index == 2);
            project.observe(state);
            helper.assertValueEqual(project.placed(), 1, "the same observed placement is not counted twice");
            int leadBefore = VillagerWalletService.getCurrentCurrency(fixture.builder());
            int assistantBefore = VillagerWalletService.getCurrentCurrency(assistant);
            SettlementConstructionService.worked(fixture.town(), project, assistant);
            helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(assistant), assistantBefore, "assistant work never receives project payment");
            SettlementConstructionService.synchronizePayments(fixture.town(), fixture.builder());
            SettlementConstructionService.synchronizePayments(fixture.town(), fixture.builder());
            helper.assertValueEqual(VillagerWalletService.getCurrentCurrency(fixture.builder()), leadBefore + project.fee(), "the lead receives the fee exactly once");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.projects().get(project.id()).placed(), 1, "shared progress survives town reload");
            helper.assertValueEqual(restored.duties().get(assistant.getUUID()), fixture.area().id(), "assistance preserves the home assignment");
            fixture.town().removeDuty(assistant.getUUID());
            helper.assertTrue(project.active() && project.assistants().isEmpty(), "reassigning an assistant leaves the lead project active");
        } finally {
            assistant.discard();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_crews")
    public static void projectPriorityAndCrewEditsRespectAuthorityAndRevision(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var assistant = assistant(helper, fixture);
        try {
            fixture.town().advance();
            fixture.town().advance();
            adopt(helper, fixture);
            var first = begin(helper, fixture);
            var secondLead = UUID.randomUUID();
            fixture.town().assignDuty(secondLead, fixture.area().id());
            var second = SettlementProject.create(fixture.town(), fixture.area().id(), secondLead, fixture.plan(),
                    fixture.origin().east(10), UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertTrue(fixture.town().startProject(fixture.leader().getUUID(), second), "a second nonoverlapping project fits the existing stage limit");
            helper.assertTrue(first.configureCrew(0L, 4, List.of(assistant.getUUID())), "first crew configured");
            helper.assertTrue(second.configureCrew(0L, 8, List.of(assistant.getUUID())), "second crew configured");
            helper.assertValueEqual(fixture.town().assignedProject(assistant.getUUID()), second, "shared assistants choose the higher priority project");
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CrewEditor"));
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
            helper.assertFalse(SettlementConstructionService.configureCrew(delegate, fixture.town(), first.id(), 1L, 9,
                    List.of(assistant.getUUID())), "building permission alone cannot allocate staff");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(),
                    Set.of(SettlementPermission.BUILD, SettlementPermission.STAFF));
            helper.assertTrue(SettlementConstructionService.configureCrew(delegate, fixture.town(), first.id(), 1L, 9,
                    List.of(assistant.getUUID())), "an authorized editor can prioritize a project");
            helper.assertValueEqual(fixture.town().assignedProject(assistant.getUUID()), first, "priority changes redirect the shared crew");
            helper.assertFalse(SettlementConstructionService.configureCrew(delegate, fixture.town(), first.id(), 1L, 0, List.of()), "replayed edits cannot overwrite newer priorities");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(SettlementConstructionService.configureCrew(delegate, fixture.town(), first.id(), 2L, 0, List.of()), "revoked authority cannot change the crew");
        } finally {
            assistant.discard();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    static SettlementProject begin(GameTestHelper helper, SettlementConstructionGameTests.Fixture fixture) {
        var project = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), fixture.plan(),
                fixture.origin(), UUID.randomUUID(), helper.getLevel().getGameTime());
        project.reservePayment(fixture.plan().price());
        helper.assertTrue(fixture.town().startProject(fixture.leader().getUUID(), project), "the project starts with one reserved lead fee");
        project.restore(HiredWorkStateStore.state(fixture.builder()));
        return project;
    }

    static void adopt(GameTestHelper helper, SettlementConstructionGameTests.Fixture fixture) {
        helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(),
                SettlementWorksitePlan.defaults(HiredVillagerRole.BUILDER), true), "the home construction worksite is explicitly adopted");
    }

    static Villager assistant(GameTestHelper helper, SettlementConstructionGameTests.Fixture fixture) {
        var worker = EntityType.VILLAGER.create(helper.getLevel());
        worker.setPos(fixture.origin().offset(-3, 0, 3).getBottomCenter());
        worker.setNoAi(true);
        worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(helper.getLevel(), worker, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(helper.getLevel().addFreshEntity(worker), "the assistant is loaded");
        fixture.town().assignDuty(worker.getUUID(), fixture.area().id());
        SettlementWorkforceService.reconcile(helper.getLevel(), worker);
        SettlementCareService.onUnloaded(worker);
        return worker;
    }

    private static boolean rejects(CompoundTag tag, UUID lead, int total, int prefix) {
        try {
            SettlementConstructionCrew.load(tag, lead, total, prefix);
            return false;
        } catch (IllegalArgumentException invalid) {
            return true;
        }
    }
}
