package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionManagementGameTests {
    private SettlementConstructionManagementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_management")
    public static void crewManagementPreservesIdentityAndRejectsConcurrentOrRevokedEdits(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        try {
            fixture.town().advance();
            SettlementConstructionCrewGameTests.adopt(helper, fixture);
            var project = SettlementConstructionCrewGameTests.begin(helper, fixture);
            var action = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.ConfigureProject(project.id(), 0L, 8, List.of(assistant.getUUID())));
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CrewManager"));
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD));
            helper.assertValueEqual(SettlementManagementService.executeFailure(delegate, action), "permission_staff", "the protocol requires staffing authority");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.BUILD, SettlementPermission.STAFF));
            helper.assertTrue(SettlementManagementService.execute(delegate, action), "the authorized command configures the shared crew");
            helper.assertFalse(SettlementManagementService.execute(fixture.leader(), action), "a second editor cannot replay an old revision");
            helper.assertValueEqual(project.assistants(), List.of(assistant.getUUID()), "one command adds the assistant once");
            helper.assertValueEqual(project.builder(), fixture.builder().getUUID(), "crew editing cannot transfer lead payment ownership");
            helper.assertValueEqual(fixture.town().duties().get(assistant.getUUID()), fixture.area().id(), "crew editing cannot transfer the home assignment");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
            helper.assertValueEqual(SettlementManagementService.executeFailure(delegate,
                    new SettlementManagementActionPayload(fixture.town().id().value(), new SettlementManagementActionPayload.ConfigureProject(
                            project.id(), project.crewRevision(), 1, List.of()))), "permission_build", "revocation is enforced at execution");
        } finally {
            assistant.discard();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_management")
    public static void projectSnapshotsAndCrewCommandsRoundTripWithBoundedMembers(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        try {
            fixture.town().advance();
            SettlementConstructionCrewGameTests.adopt(helper, fixture);
            var project = SettlementConstructionCrewGameTests.begin(helper, fixture);
            SettlementConstructionService.configureCrew(fixture.leader(), fixture.town(), project.id(), 0L, 7, List.of(assistant.getUUID()));
            var request = new SettlementManagementRequestPayload(fixture.town().id().value(), SettlementManagementRequestPayload.Page.PROJECTS,
                    0, project.id().toString(), 33, false);
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), request);
            var row = (SettlementManagementPayload.Project) snapshot.entries().getFirst();
            helper.assertValueEqual(row.priority(), 7, "the project reports its real priority");
            helper.assertValueEqual(row.assistants().getFirst().id(), assistant.getUUID(), "crew identity reaches the inspector");
            helper.assertTrue(row.crewAvailable() && !row.builderName().isBlank(), "adoption and the named payment owner reach the editor");
            var action = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.ConfigureProject(project.id(), row.crewRevision(), 6, List.of(assistant.getUUID())));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
                helper.assertValueEqual(SettlementManagementPayload.STREAM_CODEC.decode(buffer), snapshot, "crew snapshots round trip without losing authority or progress");
                SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, action);
                helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), action, "crew revisions and membership survive the wire");
            } finally {
                buffer.release();
            }
            boolean rejected = false;
            try {
                new SettlementManagementActionPayload.ConfigureProject(project.id(), 0L, 5,
                        java.util.stream.IntStream.range(0, 16).mapToObj(ignored -> UUID.randomUUID()).toList());
            } catch (IllegalArgumentException invalid) {
                rejected = true;
            }
            helper.assertTrue(rejected, "the lead and assistants cannot exceed sixteen crew members");
        } finally {
            assistant.discard();
            fixture.builder().discard();
        }
        helper.succeed();
    }
}
