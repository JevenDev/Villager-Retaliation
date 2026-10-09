package com.jvn.villagerretaliation.campaign.content;

import static com.jvn.villagerretaliation.campaign.content.CampaignScenario.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class CampaignScenarioParser {
    private CampaignScenarioParser() { }

    public static Result parse(ResourceLocation source, JsonElement input) {
        var issues = new ArrayList<QuestDiagnostic>();
        QuestJsonSchema.validate(input, CampaignScenarioSchema.export()).forEach(issue ->
                issues.add(CampaignSchema.error(source, issue.pointer(), issue.message())));
        if (!issues.isEmpty()) return new Result(null, issues);
        var root = input.getAsJsonObject();
        var actors = new ArrayList<Actor>();
        for (var value : array(root, "actors")) {
            var actor = value.getAsJsonObject();
            actors.add(new Actor(text(actor, "id"), text(actor, "role"), position(actor)));
        }
        var locations = new ArrayList<Location>();
        for (var value : array(root, "locations")) {
            var location = value.getAsJsonObject();
            locations.add(new Location(text(location, "alias"), position(location)));
        }
        var steps = new ArrayList<Step>();
        for (var value : root.getAsJsonArray("steps")) {
            var step = value.getAsJsonObject();
            String id = text(step, "id"), at = "/steps/" + steps.size(), player = text(step, "player"), session = text(step, "session");
            steps.add(switch (text(step, "type")) {
                case "player" -> new Player(id, player, kind(PlayerAction.class, step, "action"), at);
                case "party" -> new Party(id, text(step, "leader"), text(step, "member"), bool(step, "admin", false), at);
                case "privilege" -> new Privilege(id, text(step, "leader"), text(step, "member"), bool(step, "enabled", false), at);
                case "quest_prompt" -> new QuestPrompt(id, player, text(step, "actor"), resource(step, "quest"), text(step, "stage"), text(step, "slot"), at);
                case "quest_select" -> new QuestSelect(id, text(step, "prompt"), text(step, "response"), bool(step, "confirm", true), at);
                case "quest_response" -> new QuestResponse(id, player, text(step, "actor"), resource(step, "quest"), text(step, "stage"),
                        text(step, "slot"), text(step, "response"), bool(step, "confirm", true), at);
                case "quest_confirmation" -> new QuestConfirmation(id, player, bool(step, "accept", false), at);
                case "conversation_start" -> new StartConversation(id, player, session, resource(step, "conversation"), text(step, "topic"), bool(step, "accepted", true), at);
                case "conversation_intent" -> new ConversationIntent(id, player, session, kind(Intent.class, step, "intent"), text(step, "argument"),
                        text(step, "revision_from"), step.has("seed") ? step.get("seed").getAsLong() : null, bool(step, "accepted", true), at);
                case "replay" -> new Replay(id, text(step, "request"), bool(step, "accepted", true), at);
                case "interrupt" -> new Interrupt(id, player, text(step, "actor"), bool(step, "active", false), at);
                case "fixture_entry" -> new Fixture(id, player, text(step, "entry"), bool(step, "accepted", true), at);
                case "world_event" -> new WorldEvent(id, resource(step, "event"), at);
                case "storage" -> new Storage(id, kind(StorageKind.class, step, "store"), bool(step, "available", false), at);
                case "reload" -> new Reload(id, at);
                case "wait" -> new Wait(id, integer(step, "ticks"), at);
                case "checkpoint" -> new Checkpoint(id, kind(Scope.class, step, "scope"), text(step, "target"), at);
                case "assert_same" -> new AssertSame(id, text(step, "checkpoint"), at);
                case "assert_outcome" -> new AssertOutcome(id, text(step, "milestone"), text(step, "outcome"), text(step, "origin"), at);
                case "assert_player" -> new AssertPlayer(id, player, integer(step, "experience"), step.has("participation") ? strings(array(step, "participation")) : null, at);
                case "assert_quest" -> new AssertQuest(id, player, resource(step, "quest"), text(step, "stage"), integer(step, "completions"), at);
                case "assert_conversation" -> new AssertConversation(id, session, text(step, "node"), text(step, "phase"), text(step, "controller"),
                        text(step, "outcome"), integer(step, "choices"), integer(step, "checks"), integer(step, "receipts"), at);
                case "assert_check" -> new AssertCheck(id, session, text(step, "check"), integer(step, "die"), step.has("success") ? step.get("success").getAsBoolean() : null, at);
                case "assert_campaign" -> new AssertCampaign(id, integer(step, "outcomes"), integer(step, "audits"), integer(step, "rewards"), at);
                case "assert_standing" -> new AssertStanding(id, player, kind(CampaignStandingDefinition.Scope.class, step, "scope"),
                        text(step, "subject"), integer(step, "value"), text(step, "reaction"), at);
                default -> throw new IllegalArgumentException("Unknown validated scenario step");
            });
        }
        var scenario = new CampaignScenario(resource(root, "id"), resource(root, "campaign"), strings(array(root, "players")), actors, locations, steps, source);
        validateSequence(scenario, issues);
        return new Result(issues.isEmpty() ? scenario : null, issues);
    }

    private static void validateSequence(CampaignScenario scenario, List<QuestDiagnostic> issues) {
        var players = new HashSet<String>();
        for (int index = 0; index < scenario.players().size(); index++) if (!players.add(scenario.players().get(index)))
            issue(scenario, issues, "/players/" + index, "duplicate scenario player");
        var actors = new HashSet<String>();
        var roles = new HashSet<String>();
        for (int index = 0; index < scenario.actors().size(); index++) {
            var actor = scenario.actors().get(index);
            if (!actors.add(actor.id())) issue(scenario, issues, "/actors/" + index + "/id", "duplicate scenario actor");
            if (!actor.role().isBlank() && !roles.add(actor.role())) issue(scenario, issues, "/actors/" + index + "/role", "duplicate scenario character role");
        }
        var locations = new HashSet<String>();
        for (int index = 0; index < scenario.locations().size(); index++) if (!locations.add(scenario.locations().get(index).alias()))
            issue(scenario, issues, "/locations/" + index + "/alias", "duplicate scenario location");
        var prior = new LinkedHashMap<String, Step>();
        var sessions = new HashSet<String>();
        int ticks = 0;
        for (var step : scenario.steps()) {
            String at = step.pointer();
            for (var player : playerReferences(step).entrySet()) if (!players.contains(player.getValue()))
                issue(scenario, issues, at + "/" + player.getKey(), "unknown scenario player " + player.getValue());
            String actor = switch (step) { case QuestResponse value -> value.actor(); case QuestPrompt value -> value.actor(); case Interrupt value -> value.actor(); default -> ""; };
            if (!actor.isBlank() && !actors.contains(actor)) issue(scenario, issues, at + "/actor", "unknown scenario actor " + actor);
            String session = sessionReference(step);
            if (!session.isBlank() && !sessions.contains(session)) issue(scenario, issues, at + "/session", "session must be started earlier in the scenario");
            if (step instanceof StartConversation start && !sessions.add(start.session())) issue(scenario, issues, at + "/session", "duplicate scenario session");
            if (step instanceof QuestSelect select && !(prior.get(select.prompt()) instanceof QuestPrompt))
                issue(scenario, issues, at + "/prompt", "quest selection requires an earlier prompt");
            if (step instanceof Replay replay && !(prior.get(replay.request()) instanceof QuestResponse || prior.get(replay.request()) instanceof QuestSelect
                    || prior.get(replay.request()) instanceof QuestConfirmation || prior.get(replay.request()) instanceof StartConversation
                    || prior.get(replay.request()) instanceof ConversationIntent || prior.get(replay.request()) instanceof Fixture))
                issue(scenario, issues, at + "/request", "replay requires an earlier submitted request");
            if (step instanceof Replay replay && !replay.accepted() && (prior.get(replay.request()) instanceof QuestResponse
                    || prior.get(replay.request()) instanceof QuestSelect || prior.get(replay.request()) instanceof QuestConfirmation))
                issue(scenario, issues, at + "/accepted", "ordinary quest requests have no acceptance response, assert their resulting state instead");
            if (step instanceof AssertSame same && !(prior.get(same.checkpoint()) instanceof Checkpoint))
                issue(scenario, issues, at + "/checkpoint", "assert_same requires an earlier checkpoint");
            if (step instanceof Checkpoint checkpoint) {
                boolean valid = switch (checkpoint.scope()) {
                    case CAMPAIGN -> checkpoint.target().isBlank();
                    case PLAYER -> players.contains(checkpoint.target());
                    case CONVERSATION -> sessions.contains(checkpoint.target());
                };
                if (!valid) issue(scenario, issues, at + "/target", "checkpoint target does not match its scope");
            }
            if (step instanceof ConversationIntent intent) {
                if (!intent.revisionFrom().isBlank() && (!(prior.get(intent.revisionFrom()) instanceof Checkpoint checkpoint)
                        || checkpoint.scope() != Scope.CONVERSATION || !checkpoint.target().equals(intent.session())))
                    issue(scenario, issues, at + "/revision_from", "revision checkpoint must name this conversation");
                if (intent.seed() != null && intent.intent() != Intent.ROLL) issue(scenario, issues, at + "/seed", "random seed is only valid for a roll request");
                boolean argument = intent.intent() == Intent.CHOOSE || intent.intent() == Intent.HAND_OVER;
                if (argument == intent.argument().isBlank()) issue(scenario, issues, at + "/argument", "intent argument is missing or unsupported");
            }
            if (step instanceof AssertCampaign assertion && assertion.outcomes() < 0 && assertion.audits() < 0 && assertion.rewards() < 0)
                issue(scenario, issues, at, "campaign assertion must check at least one count");
            if (step instanceof Wait wait) ticks += wait.ticks();
            if (prior.putIfAbsent(step.id(), step) != null) issue(scenario, issues, at + "/id", "duplicate scenario step " + step.id());
        }
        if (ticks > 6000) issue(scenario, issues, "/steps", "scenario waits exceed 6000 ticks");
    }

    static Map<String, String> playerReferences(Step step) {
        return switch (step) {
            case Player value -> Map.of("player", value.player());
            case Party value -> Map.of("leader", value.leader(), "member", value.member());
            case Privilege value -> Map.of("leader", value.leader(), "member", value.member());
            case QuestPrompt value -> Map.of("player", value.player());
            case QuestResponse value -> Map.of("player", value.player());
            case QuestConfirmation value -> Map.of("player", value.player());
            case StartConversation value -> Map.of("player", value.player());
            case ConversationIntent value -> value.intent() == Intent.HAND_OVER
                    ? Map.of("player", value.player(), "argument", value.argument()) : Map.of("player", value.player());
            case Interrupt value -> Map.of("player", value.player());
            case Fixture value -> Map.of("player", value.player());
            case AssertPlayer value -> Map.of("player", value.player());
            case AssertStanding value -> Map.of("player", value.player());
            case AssertQuest value -> Map.of("player", value.player());
            case AssertConversation value -> value.controller().isBlank() ? Map.of() : Map.of("controller", value.controller());
            default -> Map.of();
        };
    }

    public static String sessionReference(Step step) {
        return switch (step) {
            case ConversationIntent value -> value.session();
            case AssertConversation value -> value.session();
            case AssertCheck value -> value.session();
            default -> "";
        };
    }

    public static List<QuestDiagnostic> validateContent(CampaignScenario scenario, CampaignContentCatalog catalog) {
        return CampaignScenarioReferences.validate(scenario, catalog);
    }

    static void issue(CampaignScenario scenario, List<QuestDiagnostic> issues, String pointer, String message) {
        issues.add(CampaignSchema.error(scenario.source(), pointer, message));
    }
    private static BlockPos position(JsonObject root) {
        var value = root.getAsJsonObject("position");
        return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
    }
    private static JsonArray array(JsonObject root, String field) { return root.has(field) ? root.getAsJsonArray(field) : new JsonArray(); }
    private static List<String> strings(JsonArray array) { var result = new ArrayList<String>(); array.forEach(value -> result.add(value.getAsString())); return result; }
    private static String text(JsonObject root, String field) { return root.has(field) ? root.get(field).getAsString() : ""; }
    private static ResourceLocation resource(JsonObject root, String field) { return ResourceLocation.parse(text(root, field)); }
    private static boolean bool(JsonObject root, String field, boolean fallback) { return root.has(field) ? root.get(field).getAsBoolean() : fallback; }
    private static int integer(JsonObject root, String field) { return root.has(field) ? root.get(field).getAsInt() : -1; }
    private static <T extends Enum<T>> T kind(Class<T> type, JsonObject root, String field) { return Enum.valueOf(type, text(root, field).toUpperCase(Locale.ROOT)); }

    public record Result(CampaignScenario scenario, List<QuestDiagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return scenario != null && diagnostics.isEmpty(); }
    }
}
