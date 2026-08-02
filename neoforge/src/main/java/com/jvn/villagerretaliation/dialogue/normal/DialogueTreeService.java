package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class DialogueTreeService {
    private static final String ENTRY_OPTION_PREFIX = "dt:";
    private static final String RESPONSE_OPTION_PREFIX = "dr:";
    public static final String LEAVE_OPTION_ID = "dt:leave";
    private static final long SESSION_TTL_TICKS = 20L * 60L * 5L;
    private static final Map<SessionKey, Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogueTreeService() {
    }

    public static void clearRuntimeState() {
        SESSIONS.clear();
    }

    public static String entryOptionId(ResourceLocation treeId, String entryId) {
        return ENTRY_OPTION_PREFIX + treeId + ":" + entryId;
    }

    public static String responseOptionId(ResourceLocation treeId, String responseId) {
        return RESPONSE_OPTION_PREFIX + treeId + ":" + responseId;
    }

    public static Optional<List<DialogueOptionDefinition>> activeOptions(DialogueContext context) {
        SessionKey key = key(context);
        Session session = SESSIONS.get(key);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired(context.level().getGameTime())) {
            SESSIONS.remove(key);
            return Optional.empty();
        }

        DialogueTreeDefinition tree = DialogueTreeResources
                .tree(context.level().getServer(), context.locale(), session.treeId())
                .orElse(null);
        if (tree == null || !tree.matches(context)) {
            SESSIONS.remove(key);
            return Optional.empty();
        }

        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null || node.end() || !node.matches(context)) {
            SESSIONS.remove(key);
            return Optional.empty();
        }

        List<DialogueOptionDefinition> options = node.responses().stream()
                .filter(response -> response.matches(context))
                .sorted(Comparator.comparingInt(DialogueTreeDefinition.Response::order)
                        .thenComparing(DialogueTreeDefinition.Response::id))
                .map(response -> response.toOption(tree.id()))
                .toList();
        if (options.isEmpty()) {
            SESSIONS.remove(key);
            return Optional.empty();
        }
        SESSIONS.put(key, session.touch(context.level().getGameTime()));
        return Optional.of(options);
    }

    public static Optional<VillagerDialogueService.DialogueResult> handleDialogueOption(
            DialogueContext context,
            DialogueOptionDefinition option) {
        DialogueTreeReference reference = option.treeReference();
        if (reference.isEmpty()) {
            return Optional.empty();
        }

        if (reference.isEntry()) {
            return startEntry(context, reference.treeId(), reference.entryId());
        }
        if (reference.isResponse()) {
            return selectResponse(context, reference.treeId(), reference.responseId());
        }
        return Optional.empty();
    }

    public static boolean leaveActiveSession(DialogueContext context) {
        return SESSIONS.remove(key(context)) != null;
    }

    public static Optional<VillagerDialogueService.DialogueResult> startEntry(
            DialogueContext context,
            ResourceLocation treeId,
            String entryId) {
        return startEntry(context, treeId, entryId, Map.of());
    }

    public static Optional<VillagerDialogueService.DialogueResult> startEntry(
            DialogueContext context,
            ResourceLocation treeId,
            String entryId,
            Map<String, String> inheritedReplacements) {
        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null || !tree.matches(context)) {
            return Optional.empty();
        }
        DialogueTreeDefinition.Entry entry = tree.entry(entryId).orElse(null);
        if (entry == null || !entry.matches(context, VillagerDialogueService.moodFor(context))) {
            return Optional.empty();
        }
        return Optional.of(enterNode(context, tree, entry.start(), "", inheritedReplacements));
    }

    private static Optional<VillagerDialogueService.DialogueResult> selectResponse(
            DialogueContext context,
            ResourceLocation treeId,
            String responseId) {
        SessionKey key = key(context);
        Session session = SESSIONS.get(key);
        if (session == null || !treeId.equals(session.treeId())) {
            return Optional.empty();
        }
        if (session.expired(context.level().getGameTime())) {
            SESSIONS.remove(key);
            return Optional.empty();
        }

        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null || !tree.matches(context)) {
            SESSIONS.remove(key);
            return Optional.empty();
        }
        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) {
            SESSIONS.remove(key);
            return Optional.empty();
        }
        DialogueTreeDefinition.Response response = node.responses().stream()
                .filter(candidate -> candidate.id().equals(responseId) && candidate.matches(context))
                .findFirst()
                .orElse(null);
        if (response == null) {
            return Optional.empty();
        }

        ActionText responseActionText = executeActions(context, response.actions(), session.replacements());
        String responseLine = resolve(response.selectLine(context.random()), context, responseActionText.replacements());
        String leadingText = firstNonBlank(responseLine, responseActionText.text());
        if (!response.next().isBlank()) {
            return Optional.of(enterNode(context, tree, response.next(), leadingText, responseActionText.replacements()));
        }

        SESSIONS.remove(key);
        return Optional.of(result(
                lineId(tree.id(), node.id(), response.id()),
                firstNonBlank(leadingText, "")
        ));
    }

    private static VillagerDialogueService.DialogueResult enterNode(
            DialogueContext context,
            DialogueTreeDefinition tree,
            String nodeId,
            String leadingText,
            Map<String, String> inheritedReplacements) {
        DialogueTreeDefinition.Node node = tree.node(nodeId).orElse(null);
        if (node == null || !node.matches(context)) {
            SESSIONS.remove(key(context));
            return result(lineId(tree.id(), nodeId, "missing"), leadingText);
        }

        ActionText actionText = executeActions(context, node.actions(), inheritedReplacements);
        String nodeLine = resolve(node.selectLine(context.random()), context, actionText.replacements());
        String text = firstNonBlank(leadingText, actionText.text(), nodeLine);
        if (!node.end() && node.responses().stream().anyMatch(response -> response.matches(context))) {
            SESSIONS.put(key(context), new Session(
                    tree.id(),
                    node.id(),
                    context.level().getGameTime(),
                    actionText.replacements()));
        } else {
            SESSIONS.remove(key(context));
        }
        String resultLineId = actionText.lineId().isBlank() ? lineId(tree.id(), node.id(), "") : actionText.lineId();
        return result(resultLineId, text);
    }

    private static ActionText executeActions(
            DialogueContext context,
            List<VillagerActionDefinition> actions,
            Map<String, String> inheritedReplacements) {
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        if (inheritedReplacements != null) {
            replacements.putAll(inheritedReplacements);
        }
        List<String> texts = new ArrayList<>();
        String lineId = "";
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene()) {
                SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
                boolean suspended = false;
                if (launch.accepted()) {
                    SceneSavedData data = SceneSavedData.get(context.level());
                    var scene = data.get(launch.instanceId()).orElse(null);
                    if (scene != null) {
                        data.suspendContinuation(scene, context.player().getUUID(), context.villager().getUUID(),
                                "dialogue_response/" + index + "/" + action.sceneOperationId(),
                                actions, index + 1, replacements);
                        suspended = true;
                    }
                }
                if (suspended || action.required()) break;
                continue;
            }
            ActionResult actionResult = VillagerActionRegistry.execute(context, action, replacements);
            VillagerActionResult result = actionResult.legacyResult();
            replacements.putAll(result.replacements());
            if (result.flashTracker()) {
                VillagerQuestService.flashTracker(context.player(), true);
            }
            if (!result.text().isBlank()) {
                texts.add(result.text());
            }
            if (!result.lineId().isBlank()) {
                lineId = result.lineId();
            }
            if (action.required() && !actionResult.success()) {
                break;
            }
        }
        return new ActionText(lineId, String.join(" ", texts), Map.copyOf(replacements));
    }

    private static String resolve(String template, DialogueContext context, Map<String, String> replacements) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return VillagerDialogueResources.resolveTemplate(
                template,
                DialoguePlaceholders.merge(DialoguePlaceholders.base(context), replacements)
        );
    }

    private static VillagerDialogueService.DialogueResult result(String lineId, String text) {
        return new VillagerDialogueService.DialogueResult(lineId, text == null ? "" : text);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String lineId(ResourceLocation treeId, String nodeId, String responseId) {
        StringBuilder builder = new StringBuilder("tree_")
                .append(treeId.toString().replace(':', '_').replace('/', '_'))
                .append("_")
                .append(nodeId == null ? "missing" : nodeId);
        if (responseId != null && !responseId.isBlank()) {
            builder.append("_").append(responseId);
        }
        return builder.toString();
    }

    private static SessionKey key(DialogueContext context) {
        return new SessionKey(context.player().getUUID(), context.villager().getUUID());
    }

    private record SessionKey(UUID playerId, UUID villagerId) {
    }

    private record Session(
            ResourceLocation treeId,
            String nodeId,
            long lastTouchedGameTime,
            Map<String, String> replacements) {
        private Session {
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }

        private boolean expired(long gameTime) {
            return gameTime - this.lastTouchedGameTime > SESSION_TTL_TICKS;
        }

        private Session touch(long gameTime) {
            return new Session(this.treeId, this.nodeId, gameTime, this.replacements);
        }
    }

    private record ActionText(String lineId, String text, Map<String, String> replacements) {
        private ActionText {
            lineId = lineId == null ? "" : lineId;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }
}
