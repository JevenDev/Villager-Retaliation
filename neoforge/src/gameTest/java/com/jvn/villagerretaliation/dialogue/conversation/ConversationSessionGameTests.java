package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class ConversationSessionGameTests {
    private ConversationSessionGameTests() { }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void savedHistoryRetainsSpeakersRollsAndEffectReceipts(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            var session = session(UUID.randomUUID());
            data.write(null, 0, session);
            var identity = session.identity;
            session.revision++;
            session.phase = ConversationSession.Phase.ROLLED;
            session.response = "appeal";
            session.check = "convince";
            UUID choice = UUID.randomUUID();
            UUID roll = UUID.randomUUID();
            session.requests.put(choice, new ConversationSession.Request(identity.owner(), 1, ConversationSession.Intent.CHOOSE, "appeal"));
            session.requests.put(roll, new ConversationSession.Request(identity.owner(), 1, ConversationSession.Intent.ROLL, ""));
            session.choices.add(new ConversationSession.Choice(choice, "welcome", 1, "appeal", identity.owner(), Set.of(identity.owner()), 4));
            var check = new ConversationDefinition.Check("convince", 10, "yes", "no", "");
            session.checks.put("convince", ConversationSession.Check.roll(roll, check, 13, identity.owner(), 5));
            session.transcript.add(new ConversationSession.Line("welcome", 1, "greet", ConversationDefinition.SpeakerKind.ROLE,
                    "mentor", identity.character(), "Mira", "fixture.welcome", "Welcome back."));
            var action = action();
            var result = ActionResult.suspended(UUID.randomUUID(), VillagerActionRegistry.capabilities(action));
            session.receipts.put("1/node/reward", new ConversationSession.Receipt("1/node/reward", identity.owner(), null, action,
                    ConversationSession.ReceiptState.RESOLVED, result));
            session.replacements.put("reward", "seven");
            data.write(null, 1, session);
            var restored = disk.reload().session(identity.id());
            helper.assertValueEqual(restored.snapshot(), session.snapshot(), "durable record matches acknowledged presentation");
            helper.assertValueEqual(restored.requests, session.requests, "request identities survive reload");
            helper.assertValueEqual(restored.replacements, session.replacements, "effect replacements survive reload");
            restored.controller = null;
            helper.assertValueEqual(data.session(identity.id()).controller, identity.owner(), "returned copies cannot mutate saved sessions");
            helper.assertValueEqual(disk.reload().activeCharacter(identity.character()), identity.id(), "character lock survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void checksFollowD20RulesAndRejectAlteredBreakdowns(GameTestHelper helper) {
        for (int difficulty : List.of(0, 10, 20, 25)) for (int die = 1; die <= 20; die++) {
            var definition = new ConversationDefinition.Check("test", difficulty, "passed", "failed", "");
            var result = ConversationSession.Check.roll(UUID.randomUUID(), definition, die, UUID.randomUUID(), 0);
            boolean expected = die == 20 || die != 1 && die >= difficulty;
            helper.assertValueEqual(result.success(), expected, "natural rolls and difficulty " + difficulty + "/" + die);
            helper.assertValueEqual(result.destination(), expected ? "passed" : "failed", "saved branch follows the saved roll");
        }
        try {
            new ConversationSession.Check(UUID.randomUUID(), "test", 1, 0, 0, 1, true, "passed", UUID.randomUUID(), 0);
            helper.fail("altered success must be rejected");
        } catch (IllegalArgumentException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void malformedConversationDataStaysDormantWithoutLosingHistory(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            data.write(null, 0, session(UUID.randomUUID()));
            var original = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> tag.putInt("DataVersion", 99),
                    tag -> first(tag).putString("Phase", "UNKNOWN"),
                    tag -> first(tag).putLong("Revision", 0),
                    tag -> first(tag).putUUID("Controller", UUID.randomUUID()),
                    tag -> first(tag).remove("Participants"),
                    tag -> first(tag).putString("Phase", "ROLLED"),
                    tag -> first(tag).putString("Phase", "COMPLETE"),
                    tag -> tag.getList("Sessions", 10).add(first(tag).copy()))) {
                var broken = original.copy();
                corrupt.accept(broken);
                var dormant = ConversationSavedData.load(broken, disk.directory);
                helper.assertFalse(dormant.diagnostic().isBlank(), "malformed record has repair diagnostics");
                helper.assertTrue(dormant.snapshots().isEmpty(), "malformed records cannot execute");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "raw state remains recoverable");
                helper.assertValueEqual(disk.read(), original, "inspection does not overwrite the file");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void characterReservationsAndRevisionsRejectCompetingWrites(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            UUID character = UUID.randomUUID();
            var first = session(character);
            var second = session(character);
            data.write(null, 0, first);
            try { data.write(null, 0, second); helper.fail("second session must not claim the same actor"); }
            catch (ConversationSavedData.Conflict expected) { }
            first.revision++;
            first.phase = ConversationSession.Phase.COMPLETE;
            first.outcome = "finished";
            data.write(null, 1, first);
            try { data.write(null, 1, first); helper.fail("stale write must not replace the outcome"); }
            catch (ConversationSavedData.Conflict expected) { }
            data.write(null, 0, second);
            helper.assertValueEqual(disk.reload().activeCharacter(character), second.identity.id(), "completion releases the character");
            helper.assertValueEqual(data.maintenanceBatch(), List.of(second.identity.id()), "completed history is excluded from maintenance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void failedRollWritesStayHiddenAndRetryTheSameCandidate(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            var session = session(UUID.randomUUID());
            data.write(null, 0, session);
            Files.delete(disk.file());
            Files.createDirectory(disk.file());
            session.phase = ConversationSession.Phase.ROLLED;
            session.check = "convince";
            session.revision++;
            UUID request = UUID.randomUUID();
            session.requests.put(request, new ConversationSession.Request(session.controller, 1, ConversationSession.Intent.ROLL, ""));
            session.checks.put("convince", ConversationSession.Check.roll(request,
                    new ConversationDefinition.Check("convince", 10, "yes", "no", ""), 17, session.controller, 10));
            try { data.write(null, 1, session); helper.fail("blocked file must fail"); } catch (IOException expected) { }
            helper.assertTrue(data.session(session.identity.id()).checks.isEmpty(), "unsaved roll is not displayed");
            helper.assertFalse(data.issue(session.identity.id()).isBlank(), "unacknowledged session is paused");
            var replacement = data.session(session.identity.id());
            replacement.revision++;
            try { data.write(null, 1, replacement); helper.fail("pending result cannot be replaced"); } catch (IOException expected) { }
            Files.delete(disk.file());
            data.retryPending(null);
            helper.assertTrue(data.issue(session.identity.id()).isBlank(), "storage recovery clears the pause");
            helper.assertValueEqual(disk.reload().session(session.identity.id()).checks.get("convince").die(), 17, "retry persists the original roll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void failedStartsReserveEveryActorAndMaintenanceIsBounded(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var data = disk.open();
            Files.createDirectory(disk.file());
            var pending = session(UUID.randomUUID());
            try { data.write(null, 0, pending); helper.fail("blocked start must fail"); } catch (IOException expected) { }
            helper.assertValueEqual(data.activeCharacter(pending.identity.character()), pending.identity.id(), "failed start reserves its actor");
            try { data.write(null, 0, session(pending.identity.character())); helper.fail("another start cannot steal the reservation"); }
            catch (ConversationSavedData.Conflict expected) { }
            Files.delete(disk.file());
            data.retryPending(null);
            for (int count = 0; count < 17; count++) data.write(null, 0, session(UUID.randomUUID()));
            var first = data.maintenanceBatch();
            var second = data.maintenanceBatch();
            helper.assertValueEqual(first.size(), 16, "maintenance has a fixed batch limit");
            var covered = new java.util.HashSet<>(first);
            covered.addAll(second);
            helper.assertValueEqual(covered.size(), 18, "round robin does not starve later sessions");
        }
        helper.succeed();
    }

    private static CompoundTag first(CompoundTag tag) { return tag.getList("Sessions", 10).getCompound(0); }

    private static ConversationSession session(UUID character) {
        UUID player = UUID.randomUUID();
        var id = ResourceLocation.parse("campaign_fixture:mentor_conversation");
        var identity = new ConversationSession.Identity(UUID.randomUUID(), id, 1, "graph-v1", ResourceLocation.parse("campaign_fixture:characters"),
                UUID.randomUUID(), "campaign-v1", null, "mentor", character, ResourceLocation.parse("minecraft:overworld"), player, null, "A conversation", "guidance", 0);
        return new ConversationSession(identity, player, null, "welcome", new ConversationSession.Speaker("mentor", character, identity.dimension(), "Mira"));
    }

    private static VillagerActionDefinition action() {
        return VillagerActionDefinition.readListOrInline(ResourceLocation.parse("campaign_fixture:test"), "test",
                JsonParser.parseString("{\"type\":\"experience\",\"amount\":7}").getAsJsonObject()).getFirst();
    }

    private static final class Disk implements AutoCloseable {
        final Path directory = Files.createTempDirectory("conversation-state-");
        Disk() throws IOException { }
        Path file() { return directory.resolve(ConversationSavedData.DATA_NAME + ".dat"); }
        ConversationSavedData open() { return ConversationSavedData.load(new CompoundTag(), directory); }
        CompoundTag read() throws IOException { return NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"); }
        ConversationSavedData reload() throws IOException { return ConversationSavedData.load(read(), directory); }
        @Override public void close() throws IOException { Files.deleteIfExists(file()); Files.delete(directory); }
    }
}
