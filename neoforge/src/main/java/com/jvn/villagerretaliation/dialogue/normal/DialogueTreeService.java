package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.SceneContinuationService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionScreenOpener;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
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
        if (SceneContinuationService.hasPendingDialogue(context)) return Optional.of(List.of());
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
                .sorted(Comparator.comparingInt(DialogueTreeDefinition.Response::priority).reversed()
                        .thenComparingInt(DialogueTreeDefinition.Response::order)
                        .thenComparing(DialogueTreeDefinition.Response::id))
                .map(response -> response.toOption(context, tree.id(), tree.metadata()))
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
        if (SceneContinuationService.hasPendingDialogue(context)) return Optional.empty();
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

        if (SceneContinuationService.hasPendingDialogue(context)) return Optional.empty();
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

        String responseTemplate = response.selectLine(context);
        ActionText responseActionText = executeActions(context, response.actions(), session.replacements(),
                tree.id() + "/" + node.id() + "/response/" + response.id(),
                new SceneContinuation.DialogueResume(tree.id(), response.end() ? "" : response.next(),
                        true, responseTemplate));
        String responseLine = resolve(responseTemplate, context, responseActionText.replacements());
        String leadingText = firstNonBlank(responseLine, responseActionText.text());
        if (!responseActionText.outcome().success()) {
            SESSIONS.remove(key);
            return Optional.of(result(lineId(tree.id(), node.id(), response.id()),
                    responseActionText.outcome().suspended() ? leadingText : responseActionText.text()));
        }
        if (!response.end() && !response.next().isBlank()) {
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

        ActionText actionText = executeActions(context, node.actions(), inheritedReplacements,
                tree.id() + "/node/" + node.id(),
                new SceneContinuation.DialogueResume(tree.id(), node.id(), false, leadingText));
        if (!actionText.outcome().success()) {
            SESSIONS.remove(key(context));
            return result(actionText.lineId(), firstNonBlank(actionText.text(), leadingText));
        }
        return presentNode(context, tree, node, firstNonBlank(leadingText, actionText.text()),
                actionText.replacements(), actionText.lineId());
    }

    private static VillagerDialogueService.DialogueResult presentNode(DialogueContext context,
            DialogueTreeDefinition tree, DialogueTreeDefinition.Node node, String leadingText,
            Map<String, String> replacements, String actionLineId) {
        String nodeLine = resolve(node.selectLine(context), context, replacements);
        String text = firstNonBlank(leadingText, nodeLine);
        if (!node.end() && node.responses().stream().anyMatch(response -> response.matches(context))) {
            SESSIONS.put(key(context), new Session(tree.id(), node.id(), context.level().getGameTime(), replacements));
        } else {
            SESSIONS.remove(key(context));
        }
        String resultLineId = actionLineId.isBlank() ? lineId(tree.id(), node.id(), "") : actionLineId;
        return result(resultLineId, text);
    }

    public static void resumeAfterActions(DialogueContext context, SceneContinuation.DialogueResume resume,
            Map<String, String> replacements) {
        DialogueTreeDefinition tree = DialogueTreeResources.tree(
                context.level().getServer(), context.locale(), resume.treeId()).orElse(null);
        if (tree == null || !tree.matches(context) || resume.nodeId().isBlank()) return;
        DialogueTreeDefinition.Node node = tree.node(resume.nodeId()).orElse(null);
        if (node == null || !node.matches(context)) return;
        String leading = resolve(resume.leadingText(), context, replacements);
        VillagerDialogueService.DialogueResult result = resume.runActions()
                ? enterNode(context, tree, node.id(), leading, replacements)
                : presentNode(context, tree, node, leading, replacements, "");
        if (VillagerConversationService.validate(context.player(), context.villager())
                && !VillagerConversationService.isForced(context.player(), context.villager())) {
            VillagerInteractionScreenOpener.refreshNormal(context.player(), context.villager());
            VillagerInteractionService.sendPersonalVillagerChat(
                    context.player(), context.villager(), result.text(), result.textSegments());
        }
    }

    private static ActionText executeActions(DialogueContext context, List<VillagerActionDefinition> actions,
            Map<String, String> inheritedReplacements, String sourcePointer,
            SceneContinuation.DialogueResume resume) {
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        if (inheritedReplacements != null) replacements.putAll(inheritedReplacements);
        ActionResult outcome = SceneContinuationService.run(context, actions, replacements,
                () -> VillagerQuestService.flashTracker(context.player(), true), sourcePointer, resume).outcome();
        VillagerActionResult result = outcome.legacyResult();
        return new ActionText(result.lineId(), result.text(), result.replacements(), outcome);
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

    private record ActionText(String lineId, String text, Map<String, String> replacements, ActionResult outcome) {
        private ActionText {
            lineId = lineId == null ? "" : lineId;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }
}
