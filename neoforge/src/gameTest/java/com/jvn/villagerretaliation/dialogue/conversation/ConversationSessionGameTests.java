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
            session.decisionParticipants = new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants(identity.id(), choice,
                    identity.campaign(), identity.campaignInstance(), identity.owner(), Set.of(identity.owner()));
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
            helper.assertValueEqual(restored.decisionParticipants, session.decisionParticipants, "decision evidence survives reload");
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
                    tag -> first(tag).remove("DecisionParticipants"),
                    tag -> first(tag).getCompound("DecisionParticipants").putInt("Version", 99),
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

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void legacySessionsMigrateWithoutInventingConsent(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var original = session(UUID.randomUUID());
            disk.open().write(null, 0, original);
            var saved = disk.read();
            saved.putInt("DataVersion", 1);
            first(saved).remove("Consent");
            first(saved).remove("DecisionParticipants");
            var restored = ConversationSavedData.load(saved, disk.directory);
            helper.assertTrue(restored.diagnostic().isBlank(), "version one remains readable");
            helper.assertValueEqual(restored.session(original.identity.id()).snapshot(), original.snapshot(), "migration preserves existing history");
            helper.assertTrue(restored.session(original.identity.id()).consent.isEmpty(), "migration grants no approval");
            helper.assertTrue(restored.session(original.identity.id()).decisionParticipants == null, "version one grants no listener credit");
            var versionTwo = disk.read();
            versionTwo.putInt("DataVersion", 2);
            first(versionTwo).remove("DecisionParticipants");
            var migratedTwo = ConversationSavedData.load(versionTwo, disk.directory);
            helper.assertTrue(migratedTwo.diagnostic().isBlank() && migratedTwo.session(original.identity.id()).decisionParticipants == null,
                    "version two migrates without inventing listener credit");
            var versionThree = disk.read();
            versionThree.putInt("DataVersion", 3);
            var migratedThree = ConversationSavedData.load(versionThree, disk.directory);
            helper.assertTrue(migratedThree.diagnostic().isBlank() && migratedThree.session(original.identity.id()).nestedDecision == null,
                    "version three migrates without inventing a pending decision");
            helper.assertValueEqual(restored.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"), 5, "next save supports saved nested participation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void legacyPendingChoicesRetainTheirRecordedWitnesses(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var original = session(UUID.randomUUID());
            UUID listener = UUID.randomUUID(); UUID request = UUID.randomUUID();
            original.participants.add(listener);
            original.response = "appeal";
            original.phase = ConversationSession.Phase.RESPONSE_EFFECTS;
            original.revision = 2;
            original.requests.put(request, new ConversationSession.Request(original.actingPlayer, 1, ConversationSession.Intent.CHOOSE, "appeal"));
            original.choices.add(new ConversationSession.Choice(request, original.node, original.visit, "appeal", original.actingPlayer,
                    Set.of(original.actingPlayer, listener), 0));
            var saved = new CompoundTag();
            saved.putInt("DataVersion", 2);
            var sessions = new net.minecraft.nbt.ListTag();
            var entry = ConversationSessionCodec.save(original);
            entry.remove("DecisionParticipants");
            sessions.add(entry);
            saved.put("Sessions", sessions);
            var migrated = ConversationSavedData.load(saved, disk.directory);
            helper.assertTrue(migrated.diagnostic().isBlank(), "pending legacy choice remains readable");
            var decision = migrated.session(original.identity.id()).decisionParticipants;
            helper.assertTrue(decision != null, "migration uses recorded choice evidence");
            helper.assertValueEqual(decision.participants(), Set.of(original.actingPlayer, listener), "migration retains the actual witnesses");
            helper.assertValueEqual(decision.decision(), request, "migration keeps the original decision identity");
            var current = migrated.save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertTrue(ConversationSavedData.load(current, disk.directory).diagnostic().isBlank(), "migrated evidence passes current validation");
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> tag.putUUID("Conversation", UUID.randomUUID()),
                    tag -> tag.putUUID("Instance", UUID.randomUUID()),
                    tag -> tag.putUUID("Decision", UUID.randomUUID()),
                    tag -> tag.getList("Participants", 10).add(tag.getList("Participants", 10).get(0).copy()))) {
                var invalid = current.copy();
                corrupt.accept(first(invalid).getCompound("DecisionParticipants"));
                var dormant = ConversationSavedData.load(invalid, disk.directory);
                helper.assertFalse(dormant.diagnostic().isBlank(), "invalid decision provenance cannot execute");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), invalid, "invalid evidence remains repairable");
            }
            first(saved).putString("Phase", "NODE_EFFECTS");
            var ambiguous = ConversationSavedData.load(saved, disk.directory);
            helper.assertTrue(ambiguous.session(original.identity.id()).decisionParticipants == null, "later nodes cannot borrow witnesses from an older choice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void malformedConfirmationsStayDormantWithRecoverableData(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var session = session(UUID.randomUUID());
            UUID request = UUID.randomUUID();
            var quest = ResourceLocation.parse("campaign_fixture:characters");
            var acceptance = new com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance(UUID.randomUUID(), session.identity.campaignInstance(),
                    "campaign-v1", 0, 0, Map.of());
            var consent = new com.jvn.villagerretaliation.campaign.CampaignQuestConsent(session.actingPlayer, session.identity.character(),
                    Map.of(quest, new com.jvn.villagerretaliation.campaign.CampaignQuestConsent.Entry(acceptance, new CompoundTag())));
            session.confirmation = new com.jvn.villagerretaliation.campaign.CampaignConfirmation(acceptance.requestId(), 0, consent,
                    Map.of(quest, new CompoundTag()), "Review the shared consequences.");
            session.pendingChoice = request;
            session.response = "accept";
            session.phase = ConversationSession.Phase.CONFIRMATION;
            session.requests.put(request, new ConversationSession.Request(session.actingPlayer, 1, ConversationSession.Intent.CHOOSE, "accept"));
            var data = disk.open();
            var opening = new ConversationSession(session);
            opening.confirmation = null; opening.pendingChoice = null; opening.requests.clear(); opening.phase = ConversationSession.Phase.RESPONSES;
            data.write(null, 0, opening);
            session.revision = 2;
            data.write(null, 1, session);
            helper.assertValueEqual(disk.reload().session(session.identity.id()).confirmation, session.confirmation, "valid pending confirmation survives reload");
            var original = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> first(tag).remove("Consent"),
                    tag -> first(tag).remove("Confirmation"),
                    tag -> first(tag).putUUID("PendingChoice", UUID.randomUUID()),
                    tag -> first(tag).getCompound("Confirmation").putInt("Version", 99),
                    tag -> first(tag).getCompound("Confirmation").getList("Snapshots", 10).clear(),
                    tag -> first(tag).getCompound("Confirmation").getCompound("Consent").putUUID("Player", UUID.randomUUID()),
                    tag -> first(tag).put("Consent", consent.save()))) {
                var broken = original.copy(); corrupt.accept(broken);
                var dormant = ConversationSavedData.load(broken, disk.directory);
                helper.assertFalse(dormant.diagnostic().isBlank(), "inconsistent confirmation cannot execute");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "repair retains every raw record");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_sessions")
    public static void nestedDecisionRecordsRequireTheirParentAndResolutionEvidence(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var session = session(UUID.randomUUID());
            var data = disk.open();
            data.write(null, 0, session);
            var quest = ResourceLocation.parse("campaign_fixture:characters");
            var acceptance = new com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance(UUID.randomUUID(),
                    session.identity.campaignInstance(), "campaign-v1", 0, 0, Map.of());
            var consent = new com.jvn.villagerretaliation.campaign.CampaignQuestConsent(session.actingPlayer, session.identity.character(),
                    Map.of(quest, new com.jvn.villagerretaliation.campaign.CampaignQuestConsent.Entry(acceptance, new CompoundTag())));
            var confirmation = new com.jvn.villagerretaliation.campaign.CampaignConfirmation(acceptance.requestId(), 0, consent,
                    Map.of(quest, new CompoundTag()), "Review the shared consequences.");
            var parent = VillagerActionDefinition.readListOrInline(quest, "parent",
                    JsonParser.parseString("{\"type\":\"start_scene\",\"scene_id\":\"campaign_fixture:character_greeting\",\"operation_id\":\"nested_parent\",\"wait_for_result\":true}").getAsJsonObject()).getFirst();
            var action = VillagerActionDefinition.readListOrInline(quest, "nested",
                    JsonParser.parseString("{\"type\":\"quest\",\"action\":\"start\",\"quest\":\"campaign_fixture:characters\"}").getAsJsonObject()).getFirst();
            UUID scene = UUID.randomUUID();
            String effect = "1/node/scene";
            session.receipts.put(effect, new ConversationSession.Receipt(effect, session.actingPlayer, null, parent,
                    ConversationSession.ReceiptState.RESOLVED, ActionResult.suspended(scene, VillagerActionRegistry.capabilities(parent))));
            session.nestedDecision = new ConversationNestedDecision(new ConversationNestedDecision.Origin(ConversationNestedDecision.Kind.SCENE,
                    scene, effect, "decide", "accept", -1, "scene-v1", action), ConversationSession.Phase.NODE_EFFECTS, confirmation,
                    ConversationNestedDecision.State.PENDING, null, 0, "");
            session.phase = ConversationSession.Phase.CONFIRMATION;
            session.revision++;
            data.write(null, 1, session);
            helper.assertValueEqual(disk.reload().session(session.identity.id()).nestedDecision, session.nestedDecision,
                    "pending nested decision retains its exact action and parent");
            var pending = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> first(tag).remove("NestedDecision"),
                    tag -> first(tag).getCompound("NestedDecision").putInt("Version", 99),
                    tag -> first(tag).getCompound("NestedDecision").getCompound("Origin").putString("Effect", "another/effect"),
                    tag -> first(tag).getCompound("NestedDecision").getCompound("Origin").putInt("Index", 1),
                    tag -> first(tag).getCompound("NestedDecision").getCompound("Origin").remove("Action"),
                    tag -> first(tag).getCompound("NestedDecision").getCompound("Confirmation").getCompound("Consent").putUUID("Player", UUID.randomUUID()),
                    tag -> first(tag).putString("Phase", "RESPONSES"),
                    tag -> first(tag).getList("Receipts", 10).clear())) {
                var broken = pending.copy(); corrupt.accept(broken);
                var dormant = ConversationSavedData.load(broken, disk.directory);
                helper.assertFalse(dormant.diagnostic().isBlank(), "altered nested decision cannot execute");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), broken,
                        "invalid decision retains its readable raw data for repair");
            }
            session.nestedDecision = session.nestedDecision.resolved(ConversationNestedDecision.State.APPROVED, session.actingPlayer, 1, "");
            session.phase = ConversationSession.Phase.NODE_EFFECTS;
            session.nestedParticipation.put(confirmation.id(), new ConversationParticipationEvidence(effect,
                    new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants(session.identity.id(), confirmation.id(),
                            session.identity.campaign(), session.identity.campaignInstance(), session.actingPlayer, Set.of(session.actingPlayer)),
                    session.actingPlayer));
            session.requests.put(UUID.randomUUID(), new ConversationSession.Request(session.actingPlayer, 2, ConversationSession.Intent.CONFIRM,
                    confirmation.id().toString()));
            session.revision++;
            data.write(null, 2, session);
            helper.assertValueEqual(disk.reload().session(session.identity.id()).nestedDecision, session.nestedDecision,
                    "resolved decision retains its confirming request across reloads");
            var approved = disk.read();
            for (Consumer<CompoundTag> corrupt : List.<Consumer<CompoundTag>>of(
                    tag -> first(tag).getList("Requests", 10).clear(),
                    tag -> first(tag).getList("NestedParticipation", 10).clear(),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).putInt("Version", 99),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).putString("Effect", "another/effect"),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).putUUID("Controller", UUID.randomUUID()),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).getCompound("Participants").putUUID("Instance", UUID.randomUUID()),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).getCompound("Participants").putUUID("Decision", UUID.randomUUID()),
                    tag -> first(tag).getList("NestedParticipation", 10).getCompound(0).getCompound("Participants").getList("Participants", 10)
                            .getCompound(0).putUUID("Player", UUID.randomUUID()),
                    tag -> first(tag).getList("NestedParticipation", 10).add(first(tag).getList("NestedParticipation", 10).get(0).copy()))) {
                var unproven = approved.copy(); corrupt.accept(unproven);
                var dormant = ConversationSavedData.load(unproven, disk.directory);
                helper.assertFalse(dormant.diagnostic().isBlank(), "approval without matching participation evidence cannot execute");
                helper.assertValueEqual(dormant.save(new CompoundTag(), helper.getLevel().registryAccess()), unproven,
                        "invalid participation evidence remains repairable");
            }
            var legacy = approved.copy();
            legacy.putInt("DataVersion", 4);
            first(legacy).remove("NestedParticipation");
            var migrated = ConversationSavedData.load(legacy, disk.directory);
            helper.assertTrue(migrated.diagnostic().isBlank(), "version four approval remains readable: " + migrated.diagnostic());
            helper.assertValueEqual(migrated.session(session.identity.id()).nestedParticipation.get(confirmation.id()).participants().participants(),
                    Set.of(session.actingPlayer), "legacy node approval retains only its original actor");

            UUID witness = UUID.randomUUID(); UUID successor = UUID.randomUUID(); UUID decision = UUID.randomUUID();
            session.participants.add(witness); session.participants.add(successor);
            session.decisionParticipants = new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants(session.identity.id(),
                    decision, session.identity.campaign(), session.identity.campaignInstance(), session.actingPlayer, Set.of(session.actingPlayer, witness));
            session.requests.clear();
            session.requests.put(UUID.randomUUID(), new ConversationSession.Request(session.actingPlayer, 1, ConversationSession.Intent.CONFIRM,
                    decision.toString()));
            session.requests.put(UUID.randomUUID(), new ConversationSession.Request(successor, 2, ConversationSession.Intent.CONFIRM,
                    confirmation.id().toString()));
            session.nestedDecision = new ConversationNestedDecision(session.nestedDecision.origin(), session.nestedDecision.resumePhase(),
                    confirmation, ConversationNestedDecision.State.APPROVED, successor, 1, "");
            var legacyEntry = ConversationSessionCodec.save(session);
            legacyEntry.remove("NestedParticipation");
            legacy.getList("Sessions", 10).set(0, legacyEntry);
            migrated = ConversationSavedData.load(legacy, disk.directory);
            helper.assertTrue(migrated.diagnostic().isBlank(), "legacy handover approval remains readable: " + migrated.diagnostic());
            helper.assertValueEqual(migrated.session(session.identity.id()).nestedParticipation.get(confirmation.id()).participants().participants(),
                    Set.of(session.actingPlayer, witness), "migration keeps recorded witnesses without inventing successor participation");
            var current = migrated.save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertTrue(ConversationSavedData.load(current, disk.directory).diagnostic().isBlank(), "migrated witness evidence passes current validation");
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
