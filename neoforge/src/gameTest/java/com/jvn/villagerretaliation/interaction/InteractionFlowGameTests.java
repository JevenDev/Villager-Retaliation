package com.jvn.villagerretaliation.interaction;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.NarrativeActions;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.client.interaction.InteractionClientSession;
import com.jvn.villagerretaliation.client.quest.QuestJournalModel;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.provider.QuestProviderSnapshot;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import com.jvn.villagerretaliation.reputation.runtime.EntityReputationSavedData;
import com.jvn.villagerretaliation.reputation.runtime.ReputationProfile;
import com.jvn.villagerretaliation.reputation.runtime.ReputationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_interaction")
@PrefixGameTestTemplate(false)
public final class InteractionFlowGameTests {
    private static final ResourceLocation COW = ResourceLocation.parse("minecraft:cow");
    private static final ResourceLocation TREE = ResourceLocation.parse("interaction_fixture:loop");
    private static final ResourceLocation QUEST = ResourceLocation.parse("interaction_fixture:trail");

    private InteractionFlowGameTests() { }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void menusBindChoicesToTheirPlayerActorSessionAndRevision(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var other = helper.spawn(EntityType.COW, 2, 1, 1);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var adapter = new Adapter();
        try (var registration = InteractionActors.register(COW, adapter)) {
            var menu = InteractionService.open(player, cow).orElseThrow();
            var client = adapter.clients.get(player.getUUID());
            helper.assertTrue(client.select("disabled").isEmpty(), "disabled choices cannot produce client requests");
            var selection = client.select("help").orElseThrow();
            helper.assertTrue(client.select("help").isEmpty(), "one client view submits each choice at most once");
            var forged = new InteractionMenu.Selection(selection.session(), selection.menuId(), selection.revision(), selection.choiceId(), UUID.randomUUID());
            helper.assertFalse(InteractionService.select(player, forged), "a forged token cannot execute a choice");
            helper.assertFalse(InteractionService.select(stranger, selection), "another player cannot reuse an owner's published token");
            var wrongActor = new InteractionMenu.Selection(new InteractionMenu.SessionRef(selection.session().id(), other.getUUID(), other.getId()),
                    selection.menuId(), selection.revision(), selection.choiceId(), selection.token());
            helper.assertFalse(InteractionService.select(player, wrongActor), "a valid token cannot be redirected to another actor");
            helper.assertTrue(InteractionService.select(player, selection), "the owner can execute its published choice");
            helper.assertValueEqual(adapter.effects, 1, "the authorized choice executes once");
            helper.assertFalse(InteractionService.select(player, selection), "a consumed request cannot replay effects");
            helper.assertFalse(client.accept(menu), "a stale menu revision cannot restore old choice tokens");
            var replacement = InteractionService.open(player, cow).orElseThrow();
            helper.assertFalse(InteractionService.close(player, menu.session()), "an old close request cannot end a reopened session");
            helper.assertFalse(InteractionService.activity(player, menu.session()), "old activity cannot extend a reopened session");
            helper.assertTrue(InteractionService.activity(player, replacement.session()), "activity uses the new session identity");
            helper.assertFalse(client.close(menu.session()), "late client close messages leave the replacement view open");
            adapter.canLeave = false;
            helper.assertFalse(InteractionService.close(player, replacement.session()), "the actor adapter can keep an interaction open");
            helper.assertTrue(InteractionService.activity(player, replacement.session()), "a refused close retains the live session");
            adapter.canLeave = true;
            helper.assertTrue(InteractionService.close(player, replacement.session()), "the owner can close its current interaction");
            helper.assertFalse(InteractionSessions.isConversing(cow), "closing releases the actor");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            InteractionSessions.endForPlayer(stranger, false, false);
            cow.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void changedEligibilityAndRegistrationNeverReuseStaleAuthority(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var adapter = new Adapter();
        try (var original = InteractionActors.register(COW, adapter)) {
            var menu = InteractionService.open(player, cow).orElseThrow();
            var selection = adapter.clients.get(player.getUUID()).select("help").orElseThrow();
            adapter.choiceAllowed = false;
            helper.assertFalse(InteractionService.select(player, selection), "fresh conditions can revoke a published choice");
            helper.assertValueEqual(adapter.effects, 0, "revoked choices do not execute effects");
            helper.assertFalse(adapter.menus.get(player.getUUID()).choices().getFirst().available(), "revoked choices refresh as unavailable");
            adapter.choiceAllowed = true;
            helper.assertTrue(InteractionService.refresh(adapter.context(player, cow)), "the adapter can refresh changed conditions");
            helper.assertTrue(InteractionService.select(player, adapter.clients.get(player.getUUID()).select("help").orElseThrow()),
                    "a fresh choice uses a new token");
            original.close();
            helper.assertFalse(InteractionSessions.isConversing(player), "removing an adapter releases its interaction session");
            helper.assertFalse(NarrativeActors.isRegistered(cow), "removing an interaction adapter retires its narrative factory");
            helper.assertFalse(InteractionService.select(player, selection), "removed adapters cannot accept old requests");
            try (var restored = InteractionActors.register(COW, adapter)) {
                original.close();
                helper.assertTrue(NarrativeActors.isRegistered(cow), "closing an old registration cannot remove a replacement");
                var replacement = InteractionService.open(player, cow).orElseThrow();
                helper.assertFalse(menu.session().id().equals(replacement.session().id()), "replacement registrations create fresh sessions");
                helper.assertTrue(InteractionService.close(player, replacement.session()), "the replacement owns its own session");
            }
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void failedCallbacksRetireSessionsAndConsumedRequests(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var adapter = new Adapter();
        try (var registration = InteractionActors.register(COW, adapter)) {
            InteractionService.open(player, cow).orElseThrow();
            var selection = adapter.clients.get(player.getUUID()).select("help").orElseThrow();
            adapter.failSelection = true;
            boolean failed = false;
            try { InteractionService.select(player, selection); }
            catch (IllegalStateException expected) { failed = expected.getMessage().equals("selection fixture"); }
            helper.assertTrue(failed, "selection errors propagate to the caller");
            helper.assertValueEqual(adapter.effects, 1, "a callback can fail after an effect");
            helper.assertFalse(InteractionSessions.isConversing(player), "a failed callback cannot leave a live interaction behind");
            helper.assertFalse(InteractionService.select(player, selection), "a failed callback never reissues its consumed request");
            adapter.failSelection = false;
            adapter.failShow = true;
            failed = false;
            try { InteractionService.open(player, cow); }
            catch (IllegalStateException expected) { failed = expected.getMessage().equals("presentation fixture"); }
            helper.assertTrue(failed, "presentation errors propagate to the caller");
            helper.assertFalse(InteractionSessions.isConversing(cow), "failed publication releases the actor");
            helper.assertTrue(adapter.clients.get(player.getUUID()).menu().isEmpty(), "a partially published client view is closed");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void vrChoicesRetainTheirProtocolAndBindToTheCurrentSession(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());
        villager.setNoAi(true);
        var option = new DialogueTreeDefinition.Entry("talk", "Talk", DialogueEntryMetadata.EMPTY, "start", DialogueRequestType.STORY,
                true, true, Set.of(), Set.of(), List.of(), false, 0).toOption(TREE);
        try {
            helper.assertTrue(VillagerConversationService.start(player, villager), "VR starts its ordinary interaction");
            var old = DialogueChoiceRequests.publish(player, villager.getId(), List.of(option)).getFirst();
            var reference = InteractionMenu.SessionRef.of(InteractionSessions.current(player).orElseThrow());
            helper.assertFalse(InteractionService.close(player, reference) || InteractionService.activity(player, reference),
                    "generic interaction requests cannot act on the VR transport's session");
            helper.assertTrue(VillagerConversationService.start(player, villager), "VR can reopen the same actor");
            helper.assertFalse(DialogueChoiceRequests.consume(player, villager.getId(), old.id(), old.selectionToken()),
                    "an earlier VR session's menu cannot authorize the reopened session");
            var current = DialogueChoiceRequests.publish(player, villager.getId(), List.of(option)).getFirst();
            helper.assertTrue(DialogueChoiceRequests.consume(player, villager.getId(), current.id(), current.selectionToken()),
                    "VR keeps its existing request payload fields");
            helper.assertFalse(DialogueChoiceRequests.consume(player, villager.getId(), current.id(), current.selectionToken()),
                    "VR choices remain single use");
        } finally {
            VillagerConversationService.endForPlayer(player, false);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void resettingSessionsRetiresPublishedViewsBeforeTheyCanRefresh(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var adapter = new Adapter();
        try (var registration = InteractionActors.register(COW, adapter)) {
            InteractionService.open(player, cow).orElseThrow();
            var selection = adapter.clients.get(player.getUUID()).select("help").orElseThrow();
            InteractionSessions.clear(player.server);
            helper.assertFalse(InteractionService.refresh(adapter.context(player, cow)), "a retired session cannot publish another view");
            helper.assertTrue(adapter.clients.get(player.getUUID()).menu().isEmpty(), "session reset retires the published client view");
            helper.assertFalse(InteractionService.select(player, selection), "retired views cannot authorize actions after reset");
            helper.assertTrue(InteractionService.open(player, cow).isPresent(), "reset leaves no stale actor ownership behind");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_flow")
    public static void vrControlsCannotCloseOrRefreshAnotherConsumersSession(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var vrPlayer = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var villager = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        vrPlayer.teleportTo(villager.getX(), villager.getY(), villager.getZ());
        villager.setNoAi(true);
        var adapter = new Adapter();
        try (var registration = InteractionActors.register(COW, adapter)) {
            var menu = InteractionService.open(player, cow).orElseThrow();
            helper.assertTrue(VillagerConversationService.start(vrPlayer, villager), "the VR consumer opens a separate session");
            var session = InteractionSessions.current(player).orElseThrow();
            long previousActivity = helper.getLevel().getGameTime() - 5;
            session.touch(previousActivity);
            VillagerConversationService.recordActivity(player, cow.getId());
            helper.assertValueEqual(session.lastInteractionGameTime(), previousActivity, "VR activity cannot refresh a foreign consumer's session");
            VillagerInteractionService.handleConversationEndRequest(player, villager.getId());
            helper.assertTrue(session.active(), "a delayed VR close request cannot terminate the player's new actor interaction");
            helper.assertFalse(VillagerConversationService.isConversing(player), "the VR facade reports only VR-owned sessions");
            VillagerConversationService.clearRuntimeState();
            helper.assertFalse(VillagerConversationService.isConversing(vrPlayer), "VR reset retires its own sessions");
            helper.assertTrue(InteractionService.activity(player, menu.session()), "VR reset preserves another consumer's active session");
            helper.assertTrue(InteractionService.select(player, adapter.clients.get(player.getUUID()).select("help").orElseThrow()),
                    "the foreign consumer retains its published choices");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            InteractionSessions.endForPlayer(vrPlayer, false, false);
            cow.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_loop")
    public static void registeredMobRunsInteractionDialogueQuestReputationAndJournalLoop(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        cow.setNoAi(true);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var adapter = new CoreAdapter();
        var raw = JsonParser.parseString("""
                {"id":"interaction_fixture:loop","entries":[
                  {"id":"talk","label":"Talk","start":"welcome"},
                  {"id":"trusted","label":"Trusted work","start":"done","conditions":[{"type":"reputation_tier","tier":"interaction_fixture:ally"}]}],
                 "nodes":{
                  "welcome":{"lines":["Will you help?"],"responses":[{"id":"accept","label":"Accept","next":"accepted","actions":[
                    {"type":"quest","quest":"interaction_fixture:trail","action":"start","required":true},{"type":"reputation","amount":5}]}]},
                  "accepted":{"lines":["Follow the trail."],"responses":[{"id":"finish","label":"Finish","next":"done","actions":[
                    {"type":"quest","quest":"interaction_fixture:trail","action":"turn_in","required":true},{"type":"reputation","amount":7},{"type":"experience","amount":3}]}]},
                  "done":{"lines":["Thank you."],"end":true}}}
                """).getAsJsonObject();
        var tree = DialogueTreeResources.readGeneratedTree(TREE, raw, TREE);
        helper.assertTrue(tree != null, "the non-villager dialogue fixture loads");
        try (var registration = InteractionActors.register(COW, adapter)) {
            DialogueTreeResources.installTestTrees(helper.getLevel().getServer(), "en_us", List.of(tree), Set.of());
            var click = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, cow);
            com.jvn.villagerretaliation.event.VillagerRetaliationEvents.onEntityInteract(click);
            helper.assertTrue(click.isCanceled() && InteractionSessions.isConversing(cow), "registered mobs enter through the real interaction event");
            helper.assertFalse(adapter.menus.get(player.getUUID()).choices().stream().anyMatch(choice -> choice.label().equals("Trusted work")),
                    "the initial reputation hides the trusted dialogue entry");
            choose(helper, adapter, player, "Talk");
            helper.assertTrue(adapter.menus.get(player.getUUID()).text().contains("Will you help"), "dialogue reaches the adapter's presentation");
            var accept = request(adapter, player, "Accept");
            helper.assertTrue(InteractionService.select(player, accept), "dialogue accepts a quest through the consumer action adapter");
            var progress = adapter.quests.get(player.getUUID());
            helper.assertValueEqual(progress.status(), QuestProgressTypes.QuestState.ACTIVE, "the quest uses shared progress");
            helper.assertValueEqual(progress.providerId(), cow.getUUID(), "the quest retains the actual mob provider");
            helper.assertValueEqual(adapter.reputation.score(cow.getUUID(), player.getUUID()), 5, "acceptance changes the configured reputation");
            helper.assertValueEqual(adapter.journal.entries().getFirst().state(), "active", "the shared journal receives active quest data");
            helper.assertFalse(InteractionService.select(player, accept), "replayed acceptance cannot restart the quest or repeat reputation");
            int experience = player.totalExperience;
            var finish = request(adapter, player, "Finish");
            helper.assertTrue(InteractionService.select(player, finish), "dialogue completes the shared quest");
            helper.assertValueEqual(progress.status(), QuestProgressTypes.QuestState.COMPLETED, "the quest reaches completion");
            helper.assertValueEqual(adapter.reputation.score(cow.getUUID(), player.getUUID()), 12, "completion changes reputation exactly once");
            helper.assertValueEqual(player.totalExperience, experience + 3, "completion grants the common reward once");
            helper.assertValueEqual(adapter.journal.entries().getFirst().state(), "completed", "the shared journal receives the completed quest");
            helper.assertTrue(adapter.menus.get(player.getUUID()).choices().stream().anyMatch(choice -> choice.label().equals("Trusted work")),
                    "the changed reputation unlocks another dialogue entry in the same interaction");
            helper.assertFalse(InteractionService.select(player, finish), "completed choices cannot replay rewards");
            helper.assertTrue(VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), QUEST) == null,
                    "the consumer loop creates no VR quest record");
            var reference = adapter.menus.get(player.getUUID()).session();
            helper.assertTrue(InteractionService.close(player, reference), "the consumer closes through the shared lifecycle");
            adapter.reputation.set(cow.getUUID(), player.getUUID(), -20, helper.getLevel().getGameTime(), null);
            helper.assertTrue(InteractionService.open(player, cow).isEmpty(), "the adapter can use reputation to refuse a later interaction");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            DialogueTreeResources.clearCache();
            cow.discard();
        }
        helper.succeed();
    }

    private static InteractionMenu.Selection request(Adapter adapter, ServerPlayer player, String label) {
        var menu = adapter.menus.get(player.getUUID());
        var choice = menu.choices().stream().filter(value -> value.label().equals(label)).findFirst().orElseThrow();
        return adapter.clients.get(player.getUUID()).select(choice.id()).orElseThrow();
    }

    private static void choose(GameTestHelper helper, Adapter adapter, ServerPlayer player, String label) {
        helper.assertTrue(InteractionService.select(player, request(adapter, player, label)), "the published choice executes: " + label);
    }

    private static class Adapter implements InteractionActorAdapter {
        private boolean choiceAllowed = true;
        private boolean failSelection;
        private boolean failShow;
        private boolean canLeave = true;
        private int effects;
        final Map<UUID, InteractionMenu> menus = new HashMap<>();
        final Map<UUID, InteractionClientSession> clients = new HashMap<>();

        @Override public boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) {
            return player.distanceToSqr(actor) <= 64;
        }
        @Override public boolean valid(ServerPlayer player, LivingEntity actor, InteractionSession session) { return player.distanceToSqr(actor) <= 64; }
        @Override public long idleTimeout(ServerPlayer player, LivingEntity actor, InteractionSession session) { return 2400; }
        @Override public InteractionMenu.Content content(DialogueRuntimeContext context) {
            if (!dialogueTrees(context).isEmpty()) return InteractionActorAdapter.super.content(context);
            return new InteractionMenu.Content("Scout", List.of(
                    new InteractionMenu.Choice("help", "Help", this.choiceAllowed, this.choiceAllowed ? "" : "Unavailable"),
                    new InteractionMenu.Choice("disabled", "Disabled", false, "Unavailable")));
        }
        @Override public boolean select(DialogueRuntimeContext context, String choice) {
            if (!dialogueTrees(context).isEmpty()) return InteractionActorAdapter.super.select(context, choice);
            this.effects++;
            if (this.failSelection) throw new IllegalStateException("selection fixture");
            return true;
        }
        @Override public boolean leave(ActorInteractionContext context) { return this.canLeave; }
        @Override public void show(ActorInteractionContext context, InteractionMenu menu) {
            this.menus.put(context.player().getUUID(), menu);
            this.clients.computeIfAbsent(context.player().getUUID(), ignored -> new InteractionClientSession()).accept(menu);
            if (this.failShow) throw new IllegalStateException("presentation fixture");
        }
        @Override public void closed(ServerPlayer player, InteractionMenu.SessionRef session, boolean notifyClient, boolean finishExchanges) {
            var client = this.clients.get(player.getUUID());
            if (client != null) client.close(session);
            var menu = this.menus.get(player.getUUID());
            if (menu != null && menu.session().equals(session)) this.menus.remove(player.getUUID());
        }
    }

    private static final class CoreAdapter extends Adapter {
        private final ReputationService<String> reputation = new ReputationService<>(new EntityReputationSavedData(),
                new ReputationProfile<>("stranger", List.of(new ReputationProfile.Tier<>("ally", 10, true))));
        private final Map<UUID, QuestProgressModel> quests = new HashMap<>();
        private final QuestJournalModel journal = new QuestJournalModel();

        @Override public boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) {
            return super.canStart(player, actor, mode) && this.reputation.score(actor.getUUID(), player.getUUID()) >= 0;
        }
        @Override public Set<ResourceLocation> dialogueTrees(DialogueRuntimeContext context) { return Set.of(TREE); }
        @Override public NarrativeActions actions(ServerPlayer player, LivingEntity actor) {
            return NarrativeActions.common().withReputation(this.reputation, tier -> ResourceLocation.parse("interaction_fixture:" + tier))
                    .with(VillagerActionDefinition.Kind.QUEST, (context, action, replacements) -> {
                        var progress = this.quests.computeIfAbsent(context.player().getUUID(), ignored -> new QuestProgressModel());
                        var provider = new QuestProviderSnapshot(ResourceLocation.parse("interaction_fixture:actor"), actor.getUUID(),
                                actor.getName().getString(), context.level().dimension(), actor.blockPosition(), "", true);
                        var event = switch (action.questAction()) {
                            case START -> QuestLifecycleService.start(QUEST, progress, provider, null, context.level().getGameTime(),
                                    context.player().getUUID(), null, false);
                            case TURN_IN -> QuestLifecycleService.complete(QUEST, progress, context.level().getGameTime(), false);
                            default -> null;
                        };
                        return event != null && (event.type() == QuestLifecycleService.LifecycleEventType.STARTED
                                || event.type() == QuestLifecycleService.LifecycleEventType.COMPLETED)
                                ? ActionResult.success(VillagerActionResult.success(), Set.of())
                                : ActionResult.unavailable("Quest action unavailable", Set.of(), VillagerActionResult.EMPTY);
                    });
        }
        @Override public void show(ActorInteractionContext context, InteractionMenu menu) {
            super.show(context, menu);
            var progress = this.quests.get(context.player().getUUID());
            if (progress == null) return;
            var entry = new QuestJournalData.Entry(QUEST.toString(), "Trail", "Follow the trail", "", "", 1.0F, true,
                    progress.status().name().toLowerCase(java.util.Locale.ROOT), "", context.actor().getName().getString(), "",
                    List.of(), List.of(), List.of(), List.of(), true, false, null);
            this.journal.accept(List.of(entry), List.of(QUEST.toString()), List.of(), Map.of());
        }
    }
}
