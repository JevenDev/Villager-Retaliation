package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.content.bundle.LocalizedReference;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleFingerprints;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class ConversationCompiler {
    private final QuestBundleTransactions.EffectiveBundle bundle;
    private final QuestBundleTransactions.DefinitionSource source;
    private final List<QuestDiagnostic> diagnostics = new ArrayList<>();
    private final Map<String, JsonElement> english;
    private ResourceLocation quest;

    private ConversationCompiler(QuestBundleTransactions.EffectiveBundle bundle, ResourceLocation id) {
        this.bundle = bundle;
        this.source = bundle.source(QuestBundlePath.Kind.CONVERSATION, id);
        this.english = bundle.locales().messages(QuestLocaleCatalog.ENGLISH);
    }

    public static Result compile(QuestBundleTransactions.EffectiveBundle bundle, ResourceLocation id) {
        return new ConversationCompiler(bundle, id).compile(id);
    }

    private Result compile(ResourceLocation id) {
        JsonObject root = bundle.definitions().getOrDefault(QuestBundlePath.Kind.CONVERSATION, Map.of()).get(id);
        for (var issue : ConversationSchema.validate(root)) error(issue.pointer(), issue.message());
        if (!diagnostics.isEmpty()) return new Result(null, diagnostics);
        if (!id.toString().equals(string(root, "id"))) error("/id", "conversation id does not match its bundle identity");
        quest = root.has("quest") ? ResourceLocation.parse(string(root, "quest")) : bundle.questId();
        if (!bundle.owner().shared() && !bundle.questId().equals(quest)) error("/quest", "private conversation belongs to quest " + bundle.questId());
        validateText(root, "");
        if (!diagnostics.isEmpty()) return new Result(null, diagnostics);
        var materialized = QuestBundleRuntimeMaterializer.materializeDefinition(bundle, QuestBundlePath.Kind.CONVERSATION, id);
        for (String issue : materialized.errors()) error("", issue);
        if (!diagnostics.isEmpty()) return new Result(null, diagnostics);
        JsonObject runtime = materialized.definition();
        Map<String, ConversationDefinition.Topic> topics = new LinkedHashMap<>();
        Map<String, ConversationDefinition.Node> nodes = new LinkedHashMap<>();
        Map<String, ConversationDefinition.Check> checks = new LinkedHashMap<>();
        Map<String, ConversationDefinition.Outcome> outcomes = new LinkedHashMap<>();
        JsonArray entries = array(root, "topics");
        for (int index = 0; index < entries.size(); index++) {
            JsonObject value = entries.get(index).getAsJsonObject();
            String pointer = "/topics/" + index;
            String key = string(value, "id");
            put(topics, key, new ConversationDefinition.Topic(key, text(value, "label", pointer), string(value, "start"),
                    conditions(value, pointer), pointer), pointer);
        }
        entries = array(root, "checks");
        for (int index = 0; index < entries.size(); index++) {
            JsonObject value = entries.get(index).getAsJsonObject();
            String pointer = "/checks/" + index;
            String key = string(value, "id");
            put(checks, key, new ConversationDefinition.Check(key,
                    value.has("difficulty") ? value.get("difficulty").getAsInt() : ConversationSchema.DEFAULT_DIFFICULTY,
                    string(value, "success"), string(value, "failure"), social(value), modifiers(value, pointer), pointer, string(value, "transaction")), pointer);
        }
        entries = array(root, "outcomes");
        for (int index = 0; index < entries.size(); index++) {
            JsonObject value = entries.get(index).getAsJsonObject();
            String pointer = "/outcomes/" + index;
            String key = string(value, "id");
            put(outcomes, key, new ConversationDefinition.Outcome(key, text(value, "label", pointer), pointer), pointer);
        }
        entries = array(root, "nodes");
        for (int index = 0; index < entries.size(); index++) {
            JsonObject value = entries.get(index).getAsJsonObject();
            JsonObject resolved = runtime.getAsJsonArray("nodes").get(index).getAsJsonObject();
            String pointer = "/nodes/" + index;
            String key = string(value, "id");
            Map<String, ConversationDefinition.Exchange> exchanges = new LinkedHashMap<>();
            JsonArray lines = array(value, "exchanges");
            for (int line = 0; line < lines.size(); line++) {
                JsonObject exchange = lines.get(line).getAsJsonObject();
                JsonObject speaker = exchange.getAsJsonObject("speaker");
                String location = pointer + "/exchanges/" + line;
                String exchangeId = string(exchange, "id");
                put(exchanges, exchangeId, new ConversationDefinition.Exchange(exchangeId,
                        new ConversationDefinition.Speaker(ConversationDefinition.SpeakerKind.valueOf(string(speaker, "type").toUpperCase(Locale.ROOT)),
                                string(speaker, "role"), speaker.has("player") ? ConversationDefinition.PlayerActor.valueOf(
                                        string(speaker, "player").toUpperCase(Locale.ROOT)) : ConversationDefinition.PlayerActor.INITIATOR), text(exchange, "text", location), conditions(exchange, location),
                        ConversationLinePresentation.read(exchange), location), location);
            }
            Map<String, ConversationDefinition.Response> responses = new LinkedHashMap<>();
            JsonArray choices = array(value, "responses");
            for (int choice = 0; choice < choices.size(); choice++) {
                JsonObject response = choices.get(choice).getAsJsonObject();
                JsonObject resolvedResponse = resolved.getAsJsonArray("responses").get(choice).getAsJsonObject();
                String location = pointer + "/responses/" + choice;
                String responseId = string(response, "id");
                put(responses, responseId, new ConversationDefinition.Response(responseId, text(response, "label", location),
                        conditions(response, location), effects(resolvedResponse, location),
                        string(response, "next"), string(response, "check"), location,
                        response.has("unavailable") ? text(response, "unavailable", location) : ""), location);
            }
            put(nodes, key, new ConversationDefinition.Node(key, new ArrayList<>(exchanges.values()), effects(resolved, pointer),
                    responses, string(value, "next"), string(value, "outcome"), pointer), pointer);
        }
        Map<String, ConversationDefinition.Cast> characters = new LinkedHashMap<>();
        if (root.has("characters")) for (var entry : root.getAsJsonObject("characters").entrySet()) {
            var cast = entry.getValue().getAsJsonObject();
            characters.put(entry.getKey(), new ConversationDefinition.Cast(string(cast, "tag"),
                    cast.has("radius") ? cast.get("radius").getAsInt() : 16));
        }
        var definition = new ConversationDefinition(id, root.get("revision").getAsInt(), QuestBundleFingerprints.definition(root),
                root.has("campaign") ? ResourceLocation.parse(string(root, "campaign")) : null, string(root, "character"), quest, text(root, "title", ""),
                topics, nodes, checks, outcomes, bundle.owner(), source, compatibilityFingerprint(root), characters);
        if (characters.containsKey("provider")) error("/characters/provider", "the provider role is already bound");
        if (definition.campaign() != null && !characters.isEmpty()) error("/characters", "campaign conversations use campaign character bindings");
        validateGraph(definition);
        if (definition.campaign() == null) {
            if (quest == null) error("/quest", "ordinary conversations require a quest provider");
            if (!definition.character().equals("provider")) error("/character", "ordinary conversations use the provider character");
            for (var node : definition.nodes().values()) {
                for (var exchange : node.exchanges()) if (exchange.speaker().kind() == ConversationDefinition.SpeakerKind.ROLE
                        && !exchange.speaker().role().equals("provider") && !characters.containsKey(exchange.speaker().role()))
                    error(exchange.pointer() + "/speaker/role", "unknown ordinary conversation character");
                var effects = new ArrayList<>(node.effects());
                node.responses().values().forEach(response -> effects.addAll(response.effects()));
                for (var effect : effects) if (effect.action().kind().name().startsWith("CAMPAIGN_"))
                    error(effect.pointer() + "/action", "campaign actions require a campaign conversation");
            }
            for (var check : definition.checks().values()) if (!check.transaction().isEmpty())
                error(check.pointer() + "/transaction", "campaign bargaining requires a campaign conversation");
        }
        return new Result(diagnostics.isEmpty() ? definition : null, diagnostics);
    }

    static String compatibilityFingerprint(JsonObject definition) {
        var logic = definition.deepCopy();
        logic.remove("revision");
        logic.remove("title");
        for (var entry : array(logic, "topics")) entry.getAsJsonObject().remove("label");
        for (var entry : array(logic, "outcomes")) entry.getAsJsonObject().remove("label");
        for (var entry : array(logic, "checks")) for (var modifier : array(entry.getAsJsonObject(), "modifiers"))
            modifier.getAsJsonObject().remove("label");
        for (var entry : array(logic, "nodes")) {
            var node = entry.getAsJsonObject();
            for (var line : array(node, "exchanges")) {
                var value = line.getAsJsonObject();
                for (String field : List.of("text", "camera", "voices")) value.remove(field);
            }
            for (var response : array(node, "responses")) {
                response.getAsJsonObject().remove("label");
                response.getAsJsonObject().remove("unavailable");
            }
        }
        return QuestBundleFingerprints.definition(logic);
    }

    private List<ConversationDefinition.Modifier> modifiers(JsonObject check, String pointer) {
        Map<String, ConversationDefinition.Modifier> result = new LinkedHashMap<>();
        JsonArray entries = array(check, "modifiers");
        for (int index = 0; index < entries.size(); index++) {
            var value = entries.get(index).getAsJsonObject();
            String location = pointer + "/modifiers/" + index;
            String id = string(value, "id");
            put(result, id, new ConversationDefinition.Modifier(id, text(value, "label", location),
                    value.get("amount").getAsInt(), conditions(value, location), location), location);
        }
        return List.copyOf(result.values());
    }

    private static ConversationDefinition.Social social(JsonObject check) {
        if (!check.has("social")) return null;
        var social = check.getAsJsonObject("social");
        return new ConversationDefinition.Social(ResourceLocation.parse(string(social, "progression")),
                com.jvn.villagerretaliation.skill.player.PlayerSocialSkill.parse(string(social, "skill")),
                social.has("experience") ? social.get("experience").getAsInt() : 0);
    }

    private List<DialogueCondition> conditions(JsonObject value, String pointer) {
        var conditions = DialogueCondition.readList(source.resource(), pointer, value, quest);
        for (int index = 0; index < conditions.size(); index++) validateCondition(conditions.get(index), pointer + "/conditions/" + index);
        return conditions;
    }

    private void validateCondition(DialogueCondition condition, String pointer) {
        if (condition instanceof DialogueCondition.Invalid invalid) error(pointer, invalid.reason());
        if (condition instanceof DialogueCondition.Not not) validateCondition(not.condition(), pointer + "/condition");
        List<DialogueCondition> children = condition instanceof DialogueCondition.AllOf all ? all.conditions()
                : condition instanceof DialogueCondition.AnyOf any ? any.conditions() : List.of();
        for (int index = 0; index < children.size(); index++) validateCondition(children.get(index), pointer + "/conditions/" + index);
    }

    private List<ConversationDefinition.Effect> effects(JsonObject value, String pointer) {
        Map<String, ConversationDefinition.Effect> effects = new LinkedHashMap<>();
        JsonArray entries = array(value, "effects");
        for (int index = 0; index < entries.size(); index++) {
            JsonObject effect = entries.get(index).getAsJsonObject();
            String location = pointer + "/effects/" + index;
            var actions = VillagerActionDefinition.readObjects(source.resource(), location + "/action", List.of(effect.getAsJsonObject("action")), quest);
            if (actions.size() != 1) {
                error(location + "/action", "action could not be compiled with this conversation's quest context");
                continue;
            }
            String id = string(effect, "id");
            put(effects, id, new ConversationDefinition.Effect(id, actions.getFirst(), location), location);
        }
        return List.copyOf(effects.values());
    }

    private String text(JsonObject value, String field, String pointer) {
        var reference = LocalizedReference.read(value.get(field)).orElse(null);
        if (reference == null) {
            error(pointer + "/" + field, "a nonblank localized reference is required");
            return "";
        }
        String key;
        try {
            key = reference.expand(bundle.localizationPrefix());
        } catch (IllegalArgumentException exception) {
            error(pointer + "/" + field, exception.getMessage());
            return "";
        }
        JsonElement payload = english.get(key);
        if (payload != null && payload.isJsonObject()) payload = payload.getAsJsonObject().get("text");
        if (payload == null || !payload.isJsonPrimitive() || !payload.getAsJsonPrimitive().isString() || payload.getAsString().isBlank()) {
            error(pointer + "/" + field, "English localization must contain one nonblank text value for " + key);
        }
        return key;
    }

    private void validateText(JsonElement value, String pointer) {
        if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++) validateText(value.getAsJsonArray().get(index), pointer + "/" + index);
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (Set.of("title", "label", "text", "unavailable").contains(entry.getKey())) text(value.getAsJsonObject(), entry.getKey(), pointer);
                else validateText(entry.getValue(), pointer + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"));
            }
        }
    }

    private <T> void put(Map<String, T> values, String id, T value, String pointer) {
        if (values.putIfAbsent(id, value) != null) error(pointer + "/id", "duplicate id " + id);
    }

    private void validateGraph(ConversationDefinition definition) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        Map<String, Set<String>> reverse = new LinkedHashMap<>();
        Set<String> entries = new LinkedHashSet<>();
        Set<String> terminals = new LinkedHashSet<>();
        definition.topics().values().forEach(topic -> {
            require(definition.nodes(), topic.start(), topic.pointer() + "/start", "node");
            entries.add(topic.start());
        });
        definition.checks().values().forEach(check -> {
            require(definition.nodes(), check.success(), check.pointer() + "/success", "node");
            require(definition.nodes(), check.failure(), check.pointer() + "/failure", "node");
        });
        for (var node : definition.nodes().values()) {
            Set<String> next = new LinkedHashSet<>();
            if (!node.next().isBlank()) {
                require(definition.nodes(), node.next(), node.pointer() + "/next", "node");
                next.add(node.next());
            }
            if (!node.outcome().isBlank()) {
                require(definition.outcomes(), node.outcome(), node.pointer() + "/outcome", "outcome");
                terminals.add(node.id());
            }
            for (var response : node.responses().values()) {
                if (!response.next().isBlank()) {
                    require(definition.nodes(), response.next(), response.pointer() + "/next", "node");
                    next.add(response.next());
                } else {
                    require(definition.checks(), response.check(), response.pointer() + "/check", "check");
                    var check = definition.checks().get(response.check());
                    if (check != null) { next.add(check.success()); next.add(check.failure()); }
                }
            }
            edges.put(node.id(), next);
            next.forEach(target -> reverse.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(node.id()));
        }
        Set<String> reachable = reachable(entries, edges);
        Set<String> finishes = reachable(terminals, reverse);
        for (var node : definition.nodes().values()) {
            if (!reachable.contains(node.id())) error(node.pointer() + "/id", "node is unreachable from every topic");
            if (!finishes.contains(node.id())) error(node.pointer(), "node has no path to a named outcome");
        }
    }

    private void require(Map<String, ?> values, String id, String pointer, String type) {
        if (!values.containsKey(id)) error(pointer, "unknown " + type + " " + id);
    }

    private static Set<String> reachable(Set<String> start, Map<String, Set<String>> edges) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(start);
        while (!pending.isEmpty()) {
            String node = pending.removeFirst();
            if (visited.add(node)) pending.addAll(edges.getOrDefault(node, Set.of()));
        }
        return visited;
    }

    public static List<QuestDiagnostic> validateCampaign(ConversationDefinition conversation, CampaignDefinition campaign) {
        List<QuestDiagnostic> issues = new ArrayList<>();
        if (!campaign.id().equals(conversation.campaign())) {
            issues.add(error(conversation.source(), "/campaign", "conversation belongs to another campaign"));
            return List.copyOf(issues);
        }
        if (conversation.quest() != null && !campaign.questBundles().contains(conversation.quest())) {
            issues.add(error(conversation.source(), "/quest", "conversation quest is not declared by its campaign"));
        }
        if (!campaign.characters().containsKey(conversation.character())) {
            issues.add(error(conversation.source(), "/character", "unknown campaign character " + conversation.character()));
        }
        for (var topic : conversation.topics().values())
            campaignConditions(conversation, campaign, topic.conditions(), topic.pointer() + "/conditions", issues);
        for (var node : conversation.nodes().values()) for (var response : node.responses().values())
            campaignConditions(conversation, campaign, response.conditions(), response.pointer() + "/conditions", issues);
        for (var check : conversation.checks().values()) for (var modifier : check.modifiers())
            campaignConditions(conversation, campaign, modifier.conditions(), modifier.pointer() + "/conditions", issues);
        Set<String> transactions = new HashSet<>();
        for (var check : conversation.checks().values()) if (!check.transaction().isEmpty()) {
            var transaction = campaign.transactions().get(check.transaction());
            String pointer = check.pointer() + "/transaction";
            if (transaction == null) issues.add(error(conversation.source(), pointer, "unknown campaign transaction " + check.transaction()));
            else {
                if (!transaction.quest().equals(conversation.quest())) issues.add(error(conversation.source(), pointer, "transaction belongs to another quest"));
                if (!transaction.character().equals(conversation.character())) issues.add(error(conversation.source(), pointer, "transaction belongs to another campaign character"));
                if (transaction.bargainedCount() == null) issues.add(error(conversation.source(), pointer, "transaction does not declare a bargained price"));
            }
            if (!transactions.add(check.transaction())) issues.add(error(conversation.source(), pointer, "transaction already has a bargaining check in this conversation"));
        }
        for (var node : conversation.nodes().values()) {
            var effects = new ArrayList<ConversationDefinition.Effect>(node.effects());
            node.responses().values().forEach(response -> effects.addAll(response.effects()));
            for (var effect : effects) if (effect.action().campaignPayment() != null) {
                var action = effect.action();
                var transaction = campaign.transactions().get(action.campaignPayment().transaction());
                String pointer = effect.pointer() + "/action/transaction";
                if (action.questId() == null || !campaign.questBundles().contains(action.questId()))
                    issues.add(error(conversation.source(), effect.pointer() + "/action/quest", "transaction requires a quest declared by its campaign"));
                else if (transaction == null) issues.add(error(conversation.source(), pointer, "unknown campaign transaction " + action.campaignPayment().transaction()));
                else if (!transaction.quest().equals(action.questId())) issues.add(error(conversation.source(), pointer, "transaction belongs to another quest"));
                else if (!transaction.character().equals(conversation.character())) issues.add(error(conversation.source(), pointer, "transaction belongs to another campaign character"));
            }
        }
        for (var node : conversation.nodes().values()) for (var exchange : node.exchanges()) {
            campaignConditions(conversation, campaign, exchange.conditions(), exchange.pointer() + "/conditions", issues);
            if (exchange.speaker().kind() == ConversationDefinition.SpeakerKind.ROLE && !campaign.characters().containsKey(exchange.speaker().role())) {
                issues.add(error(conversation.source(), exchange.pointer() + "/speaker/role", "unknown campaign character " + exchange.speaker().role()));
            }
        }
        return List.copyOf(issues);
    }

    private static void campaignConditions(ConversationDefinition conversation, CampaignDefinition campaign,
            List<DialogueCondition> conditions, String pointer, List<QuestDiagnostic> issues) {
        for (int index = 0; index < conditions.size(); index++)
            campaignCondition(conversation, campaign, conditions.get(index), pointer + "/" + index, issues);
    }

    private static void campaignCondition(ConversationDefinition conversation, CampaignDefinition campaign,
            DialogueCondition condition, String pointer, List<QuestDiagnostic> issues) {
        if (condition instanceof DialogueCondition.AllOf all) campaignConditions(conversation, campaign, all.conditions(), pointer + "/conditions", issues);
        else if (condition instanceof DialogueCondition.AnyOf any) campaignConditions(conversation, campaign, any.conditions(), pointer + "/conditions", issues);
        else if (condition instanceof DialogueCondition.Not not) campaignCondition(conversation, campaign, not.condition(), pointer + "/condition", issues);
        else if (condition instanceof DialogueCondition.CampaignCompanion companion && companion.reference().campaign().equals(campaign.id())) {
            if (!campaign.characters().containsKey(companion.reference().role()))
                issues.add(error(conversation.source(), pointer + "/role", "unknown campaign companion " + companion.reference().role()));
        } else if (condition instanceof DialogueCondition.CampaignTransaction query && query.reference().campaign().equals(campaign.id())) {
            var value = query.reference();
            var transaction = campaign.transactions().get(value.transaction());
            if (!campaign.questBundles().contains(value.quest())) issues.add(error(conversation.source(), pointer + "/quest", "transaction requires a quest declared by its campaign"));
            else if (transaction == null) issues.add(error(conversation.source(), pointer + "/transaction", "unknown campaign transaction " + value.transaction()));
            else if (!transaction.quest().equals(value.quest())) issues.add(error(conversation.source(), pointer + "/transaction", "transaction belongs to another quest"));
        }
    }

    private void error(String pointer, String message) { diagnostics.add(error(source, pointer, message)); }

    public static QuestDiagnostic error(QuestBundleTransactions.DefinitionSource source, String pointer, String message) {
        return new QuestDiagnostic(QuestDiagnostic.Severity.ERROR, "conversation.invalid", source.resource(), source.pack(),
                source.resource() == null ? "" : source.resource().toString(), pointer, message,
                "Correct the conversation definition at this source location and reload datapacks.", Set.of());
    }

    private static String string(JsonObject value, String key) { return value.has(key) ? value.get(key).getAsString() : ""; }
    private static JsonArray array(JsonObject value, String key) { return value.has(key) ? value.getAsJsonArray(key) : new JsonArray(); }

    public record Result(ConversationDefinition definition, List<QuestDiagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return definition != null && diagnostics.isEmpty(); }
    }
}
