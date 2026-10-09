package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignScenarioPack.require;
import static com.jvn.villagerretaliation.campaign.content.CampaignScenario.*;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.CampaignQuestConfirmationRequestPayload;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;

final class CampaignScenarioExecution {
    final CampaignScenarioFixture fixture;
    final Map<String, UUID> sessions = new LinkedHashMap<>();
    final Map<String, Submitted> submitted = new LinkedHashMap<>();
    final Map<String, Prompt> prompts = new LinkedHashMap<>();
    final Map<String, Captured> checkpoints = new LinkedHashMap<>();
    String lastDiagnostic = "";

    CampaignScenarioExecution(CampaignScenarioFixture fixture) { this.fixture = fixture; }

    void execute(Step step) throws IOException {
        lastDiagnostic = "";
        switch (step) {
            case Player value -> {
                if (value.action() == PlayerAction.CONNECT) fixture.connect(value.player());
                else fixture.disconnect(value.player());
                ConversationTestStorage.maintain(fixture.server);
            }
            case Party value -> {
                var leader = fixture.player(value.leader());
                var member = fixture.player(value.member());
                if (PartyService.getPartyForPlayer(leader.serverLevel(), leader.getUUID()).isEmpty())
                    require(PartyService.createParty(leader).success(), "Could not create scenario party");
                var invitation = PartyService.sendInvitation(leader, member);
                require(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "Could not join scenario party");
                require(PartyService.setAdminPrivileges(leader, member.getUUID(), value.admin()).success(), "Could not set party authority");
            }
            case Privilege value -> {
                require(PartyService.setAdminPrivileges(fixture.player(value.leader()), fixture.playerId(value.member()), value.enabled()).success(), "Could not change party authority");
                ConversationTestStorage.maintain(fixture.server);
            }
            case QuestPrompt value -> prompts.put(value.id(), prompt(value));
            case QuestSelect value -> select(value.id(), prompts.get(value.prompt()), value.response(), value.confirm());
            case QuestResponse value -> select(value.id(), prompt(new QuestPrompt(value.id(), value.player(), value.actor(),
                    value.quest(), value.stage(), value.slot(), value.pointer())), value.response(), value.confirm());
            case QuestConfirmation value -> confirm(value);
            case StartConversation value -> start(value);
            case ConversationIntent value -> intent(value);
            case Replay value -> expect(submitted.get(value.request()).run(), value.accepted());
            case Interrupt value -> {
                if (value.active()) require(VillagerConversationService.startForced(fixture.player(value.player()), fixture.actors.get(value.actor())), "Could not start interruption");
                else VillagerConversationService.endForPlayer(fixture.player(value.player()), false);
            }
            case Fixture value -> {
                var request = new CampaignFixtureService.Request(fixture.scenario.campaign(), value.entry(), fixture.playerId(value.player()),
                        fixture.campaigns().fixtureToken(fixture.content.definition()), UUID.randomUUID(), fixture.playerId(value.player()), "authored scenario " + fixture.scenario.id());
                submit(value.id(), () -> {
                    var result = CampaignFixtureService.enter(fixture.server, request);
                    lastDiagnostic = result.diagnostic();
                    return result.ready();
                }, value.accepted());
            }
            case WorldEvent value -> {
                var end = fixture.server.getLevel(Level.END);
                end.setDragonFight(new EndDragonFight(end, 0L, new EndDragonFight.Data(false, false, true, false,
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty())));
                CampaignWorldEventService.reconcile(fixture.campaigns(), fixture.content, end, fixture.helper.getLevel().getGameTime());
            }
            case Storage value -> {
                if (value.store() == StorageKind.CAMPAIGN) fixture.campaignStorage(value.available());
                else fixture.conversationStorage(value.available());
            }
            case Reload value -> fixture.reload();
            case Wait value -> throw new IllegalArgumentException("Wait steps must be scheduled by the scenario runner");
            case Checkpoint value -> checkpoints.put(value.id(), new Captured(value.scope(), value.target(), snapshot(value.scope(), value.target())));
            case AssertSame value -> {
                var captured = checkpoints.get(value.checkpoint());
                require(captured.state().equals(snapshot(captured.scope(), captured.target())), "State changed since checkpoint " + value.checkpoint());
            }
            case AssertOutcome value -> {
                var outcome = fixture.instance().outcomes().get(value.milestone());
                require(outcome != null && outcome.outcome().equals(value.outcome()), "Expected world outcome " + value.milestone() + " = " + value.outcome());
                if (!value.origin().isBlank()) require(outcome.origin().name().equalsIgnoreCase(value.origin()), "World outcome has another origin: " + outcome.origin());
            }
            case AssertPlayer value -> {
                var player = fixture.lastPlayers.get(value.player());
                if (value.experience() >= 0) require(player != null && player.totalExperience == value.experience(),
                        "Expected " + value.player() + " experience " + value.experience() + ", got " + (player == null ? "never connected" : player.totalExperience));
                var personal = fixture.instance().players().get(fixture.playerId(value.player()));
                if (value.participation() != null) require((personal == null ? Set.of() : personal.participation()).equals(Set.copyOf(value.participation())),
                        "Personal participation differs for " + value.player());
            }
            case AssertQuest value -> {
                var progress = VillagerQuestSavedData.get(fixture.helper.getLevel()).get(fixture.playerId(value.player()), value.quest());
                require(progress != null, "Quest progress is missing: " + value.quest());
                if (!value.stage().isBlank()) require(progress.currentStage().equals(value.stage()), "Expected quest stage " + value.stage() + ", got " + progress.currentStage());
                if (value.completions() >= 0) require(progress.completionCount() == value.completions(), "Quest completion count differs");
            }
            case AssertConversation value -> {
                var state = session(value.session());
                if (!value.node().isBlank()) require(state.node().equals(value.node()), "Expected conversation node " + value.node() + ", got " + state.node());
                if (!value.phase().isBlank()) require(state.phase().name().equalsIgnoreCase(value.phase()), "Expected conversation phase " + value.phase() + ", got " + state.phase());
                if (!value.controller().isBlank()) require(fixture.playerId(value.controller()).equals(state.controller()), "Conversation has another controller");
                if (!value.outcome().isBlank()) require(state.outcome().equals(value.outcome()), "Conversation outcome differs");
                if (value.choices() >= 0) require(state.choices().size() == value.choices(), "Conversation choice count differs");
                if (value.checks() >= 0) require(state.checks().size() == value.checks(), "Conversation check count differs");
                if (value.receipts() >= 0) require(state.receipts().size() == value.receipts(), "Conversation effect count differs");
            }
            case AssertCheck value -> {
                var check = session(value.session()).checks().get(value.check());
                require(check != null, "Resolved check is missing: " + value.check());
                if (value.die() >= 0) require(check.die() == value.die(), "Expected die " + value.die() + ", got " + check.die());
                if (value.success() != null) require(check.success() == value.success(), "Check result differs");
            }
            case AssertStanding value -> {
                var reading = CampaignStandingService.read(fixture.server, fixture.scenario.campaign(), fixture.playerId(value.player()), value.scope(), value.subject());
                require(reading.diagnostic().isBlank(), reading.diagnostic());
                require(reading.value() == value.value(), "Expected personal standing " + value.value() + ", got " + reading.value());
                if (!value.reaction().isBlank()) require(reading.reaction().id().equals(value.reaction()), "Standing reaction differs");
            }
            case AssertCampaign value -> {
                if (value.outcomes() >= 0) require(fixture.instance().outcomes().size() == value.outcomes(), "World outcome count differs");
                if (value.audits() >= 0) require(fixture.instance().fixtureEntries().size() == value.audits(), "Fixture audit count differs");
                if (value.rewards() >= 0) require(CampaignRewardSavedData.get(fixture.helper.getLevel()).receipts().size() == value.rewards(), "Campaign reward count differs");
            }
        }
    }

    ConversationSession.Snapshot session(String alias) {
        var state = ConversationSavedData.get(fixture.helper.getLevel()).snapshots().get(sessions.get(alias));
        require(state != null, "Conversation session is unavailable: " + alias);
        return state;
    }

    private Prompt prompt(QuestPrompt value) {
        var player = fixture.player(value.player());
        var actor = fixture.actors.get(value.actor());
        VillagerConversationService.endForPlayer(player, false);
        player.setPos(actor.getX() + 1, actor.getY(), actor.getZ());
        require(VillagerConversationService.start(player, actor), "Ordinary conversation is unavailable");
        var context = VillagerInteractionService.createDialogueContext(player.serverLevel(), player, actor);
        var catalog = QuestContentCatalogs.current(fixture.server);
        var binding = catalog.dialogueCatalog().bindings(value.quest()).stream()
                .filter(candidate -> candidate.stageId().equals(value.stage()) && candidate.slot().equals(value.slot())).findFirst().orElseThrow();
        require(DialogueTreeService.startEntry(context, binding.treeId(), binding.entryId()).isPresent(), "Authored quest dialogue is unavailable");
        var choices = new LinkedHashMap<String, String>();
        var tree = catalog.dialogueCatalog().trees().get(binding.treeId());
        var node = tree.nodes().get(tree.entry(binding.entryId()).orElseThrow().start());
        var available = DialogueTreeService.activeOptions(context).orElseThrow();
        for (var response : node.responses()) {
            String option = DialogueTreeService.responseOptionId(tree.id(), response.id());
            if (available.stream().anyMatch(candidate -> candidate.id().equals(option))) choices.put(response.id(), option);
        }
        return new Prompt(value.player(), value.actor(), Map.copyOf(choices));
    }

    private void select(String id, Prompt prompt, String response, boolean confirm) throws IOException {
        String option = prompt.options().get(response);
        require(option != null, "Response was unavailable when the prompt opened: " + response);
        Submitted choice = () -> {
            VillagerDialogueRequestHandler.handle(fixture.player(prompt.player()), fixture.actors.get(prompt.actor()).getId(), option);
            return true;
        };
        choice.run();
        var pending = CampaignQuestAcceptanceService.pending(fixture.player(prompt.player()));
        var approval = confirm && pending != null ? new CampaignQuestConfirmationRequestPayload(pending.id(), pending.revision(), true) : null;
        if (approval != null) CampaignQuestAcceptanceService.handle(fixture.player(prompt.player()), approval);
        submitted.put(id, () -> {
            choice.run();
            if (approval != null) CampaignQuestAcceptanceService.handle(fixture.player(prompt.player()), approval);
            return true;
        });
    }

    private void confirm(QuestConfirmation value) throws IOException {
        var pending = CampaignQuestAcceptanceService.pending(fixture.player(value.player()));
        require(pending != null, "No quest impact confirmation is waiting");
        var payload = new CampaignQuestConfirmationRequestPayload(pending.id(), pending.revision(), value.accept());
        submit(value.id(), () -> { CampaignQuestAcceptanceService.handle(fixture.player(value.player()), payload); return true; }, true);
    }

    private void start(StartConversation value) throws IOException {
        UUID operation = UUID.randomUUID();
        sessions.put(value.session(), operation);
        var definition = QuestContentCatalogs.current(fixture.server).conversations().get(value.conversation());
        var actor = CampaignCharacterService.resolve(fixture.server, fixture.scenario.campaign(), definition.character());
        if (actor.available()) fixture.player(value.player()).setPos(actor.entity().getX() + 1, actor.entity().getY(), actor.entity().getZ());
        submit(value.id(), () -> {
            var result = ConversationService.start(fixture.player(value.player()), value.conversation(), value.topic(), operation);
            lastDiagnostic = result.diagnostic();
            if (result.session() != null) sessions.put(value.session(), result.session().identity().id());
            return result.diagnostic().isBlank() && result.session() != null;
        }, value.accepted());
    }

    private void intent(ConversationIntent value) throws IOException {
        var state = session(value.session());
        long revision = value.revisionFrom().isBlank() ? state.revision()
                : ((ConversationSession.Snapshot) checkpoints.get(value.revisionFrom()).state()).revision();
        String argument = value.argument();
        if (value.intent() == Intent.HAND_OVER) argument = fixture.playerId(value.argument()).toString();
        else if (value.intent() == Intent.CONFIRM || value.intent() == Intent.DECLINE)
            argument = state.confirmation() == null ? UUID.randomUUID().toString() : state.confirmation().id().toString();
        UUID request = UUID.randomUUID(), session = state.identity().id();
        String submittedArgument = argument;
        if (value.seed() != null) fixture.player(value.player()).getRandom().setSeed(value.seed());
        submit(value.id(), () -> {
            var result = ConversationService.request(fixture.player(value.player()), session, revision, request,
                    ConversationSession.Intent.valueOf(value.intent().name()), submittedArgument);
            lastDiagnostic = result.diagnostic();
            return result.diagnostic().isBlank();
        }, value.accepted());
    }

    private void submit(String id, Submitted request, boolean accepted) throws IOException {
        submitted.put(id, request);
        expect(request.run(), accepted);
    }

    private void expect(boolean actual, boolean expected) {
        require(actual == expected, "Expected request " + (expected ? "accepted" : "rejected") + ", got " + (actual ? "accepted" : "rejected") + ": " + lastDiagnostic);
    }

    private Object snapshot(Scope scope, String target) {
        return switch (scope) {
            case CAMPAIGN -> new CampaignState(fixture.instance(), CampaignRewardSavedData.get(fixture.helper.getLevel()).save(new CompoundTag(), fixture.helper.getLevel().registryAccess()),
                    VillagerQuestSavedData.get(fixture.helper.getLevel()).save(new CompoundTag(), fixture.helper.getLevel().registryAccess()));
            case CONVERSATION -> session(target);
            case PLAYER -> {
                var quests = new LinkedHashMap<String, CompoundTag>();
                var saved = VillagerQuestSavedData.get(fixture.helper.getLevel()).save(new CompoundTag(), fixture.helper.getLevel().registryAccess());
                for (var raw : saved.getList("Entries", Tag.TAG_COMPOUND)) {
                    var tag = (CompoundTag) raw;
                    if (tag.getUUID("Player").equals(fixture.playerId(target))) quests.put(tag.getString("Quest"), tag.copy());
                }
                var player = fixture.lastPlayers.get(target);
                yield new PlayerState(fixture.instance().players().get(fixture.playerId(target)), player == null ? 0 : player.totalExperience, Map.copyOf(quests));
            }
        };
    }

    interface Submitted { boolean run() throws IOException; }
    private record Prompt(String player, String actor, Map<String, String> options) { }
    private record Captured(Scope scope, String target, Object state) { }
    private record CampaignState(CampaignInstance campaign, CompoundTag rewards, CompoundTag quests) { }
    private record PlayerState(CampaignInstance.PlayerRecord campaign, int experience, Map<String, CompoundTag> quests) { }
}
