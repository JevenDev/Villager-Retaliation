package com.jvn.villagerretaliation.campaign.content;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record CampaignScenario(ResourceLocation id, ResourceLocation campaign, List<String> players, List<Actor> actors,
        List<Location> locations, List<Step> steps, ResourceLocation source) {
    public CampaignScenario {
        players = List.copyOf(players);
        actors = List.copyOf(actors);
        locations = List.copyOf(locations);
        steps = List.copyOf(steps);
    }

    public record Actor(String id, String role, BlockPos position) { }
    public record Location(String alias, BlockPos position) { }
    public enum PlayerAction { CONNECT, DISCONNECT }
    public enum Intent { ADVANCE, CHOOSE, ROLL, RESUME, JOIN, TAKE_CONTROL, HAND_OVER, RELEASE_CONTROL, CONFIRM, DECLINE }
    public enum Scope { CAMPAIGN, CONVERSATION, PLAYER }
    public enum StorageKind { CAMPAIGN, CONVERSATION }
    public enum TransactionState { UNARRANGED, NEGOTIATED, PAYMENT_PENDING, PAID }

    public sealed interface Step permits Player, Party, Privilege, QuestPrompt, QuestSelect, QuestResponse, QuestConfirmation, StartConversation,
            ConversationIntent, Replay, Interrupt, Fixture, WorldEvent, Storage, Reload, Wait, Checkpoint, AssertSame,
            AssertOutcome, AssertPlayer, AssertQuest, AssertConversation, AssertCheck, AssertCampaign, AssertStanding,
            GiveItem, AssertItemCount, AssertTransaction, SpawnEvidence, Investigate, AssertEvidence {
        String id();
        String pointer();
    }

    public record Player(String id, String player, PlayerAction action, String pointer) implements Step { }
    public record SpawnEvidence(String id, String evidence, BlockPos position, String pointer) implements Step { }
    public record Investigate(String id, String player, String source, boolean approach, boolean matched,
            boolean applied, boolean unavailable, String pointer) implements Step { }
    public record AssertEvidence(String id, String player, String evidence, boolean discovered, int receipts, String pointer) implements Step { }
    public record GiveItem(String id, String player, ResourceLocation item, int count, String pointer) implements Step { }
    public record AssertItemCount(String id, String player, ResourceLocation item, int count, String pointer) implements Step { }
    public record AssertTransaction(String id, String player, ResourceLocation quest, String transaction,
            TransactionState state, int price, String pointer) implements Step { }
    public record Party(String id, String leader, String member, boolean admin, String pointer) implements Step { }
    public record Privilege(String id, String leader, String member, boolean enabled, String pointer) implements Step { }
    public record QuestPrompt(String id, String player, String actor, ResourceLocation quest, String stage, String slot, String pointer) implements Step { }
    public record QuestSelect(String id, String prompt, String response, boolean confirm, String pointer) implements Step { }
    public record QuestResponse(String id, String player, String actor, ResourceLocation quest, String stage, String slot,
            String response, boolean confirm, String pointer) implements Step { }
    public record QuestConfirmation(String id, String player, boolean accept, String pointer) implements Step { }
    public record StartConversation(String id, String player, String session, ResourceLocation conversation, String topic,
            boolean accepted, String pointer) implements Step { }
    public record ConversationIntent(String id, String player, String session, Intent intent, String argument,
            String revisionFrom, Long seed, boolean accepted, String pointer) implements Step { }
    public record Replay(String id, String request, boolean accepted, String pointer) implements Step { }
    public record Interrupt(String id, String player, String actor, boolean active, String pointer) implements Step { }
    public record Fixture(String id, String player, String entry, boolean accepted, String pointer) implements Step { }
    public record WorldEvent(String id, ResourceLocation event, String pointer) implements Step { }
    public record Storage(String id, StorageKind store, boolean available, String pointer) implements Step { }
    public record Reload(String id, String pointer) implements Step { }
    public record Wait(String id, int ticks, String pointer) implements Step { }
    public record Checkpoint(String id, Scope scope, String target, String pointer) implements Step { }
    public record AssertSame(String id, String checkpoint, String pointer) implements Step { }
    public record AssertOutcome(String id, String milestone, String outcome, String origin, String pointer) implements Step { }
    public record AssertPlayer(String id, String player, int experience, List<String> participation, String pointer) implements Step {
        public AssertPlayer { participation = participation == null ? null : List.copyOf(participation); }
    }
    public record AssertQuest(String id, String player, ResourceLocation quest, String stage, int completions, String pointer) implements Step { }
    public record AssertConversation(String id, String session, String node, String phase, String controller,
            String outcome, int choices, int checks, int receipts, String pointer) implements Step { }
    public record AssertCheck(String id, String session, String check, int die, Boolean success, String pointer) implements Step { }
    public record AssertCampaign(String id, int outcomes, int audits, int rewards, String pointer) implements Step { }
    public record AssertStanding(String id, String player, CampaignStandingDefinition.Scope scope, String subject,
            int value, String reaction, String pointer) implements Step { }
}
