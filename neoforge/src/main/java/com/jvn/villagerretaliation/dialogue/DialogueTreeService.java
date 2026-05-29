package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class DialogueTreeService {
    private static final String ENTRY_OPTION_PREFIX = "dt:";
    private static final String RESPONSE_OPTION_PREFIX = "dr:";
    private static final Map<SessionKey, Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogueTreeService() {
    }

    public static String entryOptionId(ResourceLocation treeId, String entryId) {
        return ENTRY_OPTION_PREFIX + treeId + ":" + entryId;
    }

    public static String responseOptionId(ResourceLocation treeId, String responseId) {
        return RESPONSE_OPTION_PREFIX + treeId + ":" + responseId;
    }

    public static Optional<List<DialogueOptionDefinition>> activeOptions(DialogueContext context) {
        Session session = SESSIONS.get(key(context));
        if (session == null) {
            return Optional.empty();
        }

        DialogueTreeDefinition tree = DialogueTreeResources
                .tree(context.level().getServer(), context.locale(), session.treeId())
                .orElse(null);
        if (tree == null) {
            SESSIONS.remove(key(context));
            return Optional.empty();
        }

        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null || node.end()) {
            SESSIONS.remove(key(context));
            return Optional.empty();
        }

        List<DialogueOptionDefinition> options = node.responses().stream()
                .filter(response -> response.matches(context))
                .sorted(Comparator.comparingInt(DialogueTreeDefinition.Response::order)
                        .thenComparing(DialogueTreeDefinition.Response::id))
                .map(response -> response.toOption(tree.id()))
                .toList();
        if (options.isEmpty()) {
            SESSIONS.remove(key(context));
            return Optional.empty();
        }
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

    private static Optional<VillagerDialogueService.DialogueResult> startEntry(
            DialogueContext context,
            ResourceLocation treeId,
            String entryId) {
        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null) {
            return Optional.empty();
        }
        DialogueTreeDefinition.Entry entry = tree.entry(entryId).orElse(null);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(enterNode(context, tree, entry.start(), ""));
    }

    private static Optional<VillagerDialogueService.DialogueResult> selectResponse(
            DialogueContext context,
            ResourceLocation treeId,
            String responseId) {
        Session session = SESSIONS.get(key(context));
        if (session == null || !treeId.equals(session.treeId())) {
            return Optional.empty();
        }

        DialogueTreeDefinition tree = DialogueTreeResources.tree(context.level().getServer(), context.locale(), treeId).orElse(null);
        if (tree == null) {
            SESSIONS.remove(key(context));
            return Optional.empty();
        }
        DialogueTreeDefinition.Node node = tree.node(session.nodeId()).orElse(null);
        if (node == null) {
            SESSIONS.remove(key(context));
            return Optional.empty();
        }
        DialogueTreeDefinition.Response response = node.responses().stream()
                .filter(candidate -> candidate.id().equals(responseId) && candidate.matches(context))
                .findFirst()
                .orElse(null);
        if (response == null) {
            return Optional.empty();
        }

        ActionText responseActionText = executeActions(context, response.actions());
        String responseLine = resolve(response.selectLine(context.random()), context, responseActionText.replacements());
        String leadingText = firstNonBlank(responseActionText.text(), responseLine);
        if (!response.next().isBlank()) {
            return Optional.of(enterNode(context, tree, response.next(), leadingText));
        }

        SESSIONS.remove(key(context));
        return Optional.of(result(
                lineId(tree.id(), node.id(), response.id()),
                firstNonBlank(leadingText, "")
        ));
    }

    private static VillagerDialogueService.DialogueResult enterNode(
            DialogueContext context,
            DialogueTreeDefinition tree,
            String nodeId,
            String leadingText) {
        DialogueTreeDefinition.Node node = tree.node(nodeId).orElse(null);
        if (node == null) {
            SESSIONS.remove(key(context));
            return result(lineId(tree.id(), nodeId, "missing"), leadingText);
        }

        ActionText actionText = executeActions(context, node.actions());
        String nodeLine = resolve(node.selectLine(context.random()), context, actionText.replacements());
        String text = firstNonBlank(leadingText, actionText.text(), nodeLine);
        if (!node.end() && node.responses().stream().anyMatch(response -> response.matches(context))) {
            SESSIONS.put(key(context), new Session(tree.id(), node.id()));
        } else {
            SESSIONS.remove(key(context));
        }
        String resultLineId = actionText.lineId().isBlank() ? lineId(tree.id(), node.id(), "") : actionText.lineId();
        return result(resultLineId, text);
    }

    private static ActionText executeActions(DialogueContext context, List<DialogueActionDefinition> actions) {
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        List<String> texts = new ArrayList<>();
        String lineId = "";
        for (DialogueActionDefinition action : actions) {
            ActionText result = executeAction(context, action, replacements);
            replacements.putAll(result.replacements());
            if (!result.text().isBlank()) {
                texts.add(result.text());
            }
            if (!result.lineId().isBlank()) {
                lineId = result.lineId();
            }
        }
        return new ActionText(lineId, String.join(" ", texts), Map.copyOf(replacements));
    }

    private static ActionText executeAction(
            DialogueContext context,
            DialogueActionDefinition action,
            Map<String, String> inheritedReplacements) {
        return switch (action.kind()) {
            case QUEST -> executeQuestAction(context, action, inheritedReplacements);
            case EXPERIENCE -> {
                if (action.amount() > 0) {
                    context.player().giveExperiencePoints(action.amount());
                }
                yield ActionText.empty();
            }
            case REPUTATION -> {
                if (action.amount() != 0) {
                    VillagerReputationManager.addDialogueReputation(
                            context.level(),
                            context.villager(),
                            context.player(),
                            action.amount());
                }
                yield ActionText.empty();
            }
            case GOSSIP -> {
                if (action.amount() != 0) {
                    VillagerGossipHooks.spreadReputation(
                            context.level(),
                            context.villager(),
                            context.player().getUUID(),
                            action.amount());
                }
                yield ActionText.empty();
            }
            case MEMORY -> {
                if (action.memoryEvent() != null) {
                    VillageEventMemory.remember(
                            context.level(),
                            action.memoryEvent(),
                            context.villager().blockPosition(),
                            context.villager(),
                            context.player());
                }
                yield ActionText.empty();
            }
            case LOOT -> {
                awardLoot(context, action.lootTable());
                yield ActionText.empty();
            }
            case NONE -> ActionText.empty();
        };
    }

    private static ActionText executeQuestAction(
            DialogueContext context,
            DialogueActionDefinition action,
            Map<String, String> inheritedReplacements) {
        Optional<VillagerQuestService.QuestActionOutcome> outcome = VillagerQuestService.performAction(
                context,
                action.questId(),
                action.questAction());
        if (outcome.isEmpty()) {
            return ActionText.empty();
        }

        VillagerQuestService.QuestActionOutcome questOutcome = outcome.get();
        Map<String, String> replacements = new LinkedHashMap<>(inheritedReplacements);
        replacements.putAll(questOutcome.replacements());
        List<String> overrideLines = action.linesForStatus(questOutcome.status());
        String text = overrideLines.isEmpty()
                ? questOutcome.text()
                : VillagerDialogueResources.resolveTemplate(
                        overrideLines.get(context.random().nextInt(overrideLines.size())),
                        replacements);
        return new ActionText(questOutcome.lineId(), text, Map.copyOf(replacements));
    }

    private static void awardLoot(DialogueContext context, ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return;
        }
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable table = context.level().getServer().reloadableRegistries().getLootTable(lootTableKey);
        if (table == LootTable.EMPTY) {
            return;
        }

        LootParams params = new LootParams.Builder(context.level())
                .withLuck(context.player().getLuck())
                .create(LootContextParamSets.EMPTY);
        for (ItemStack stack : table.getRandomItems(params, context.random())) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack reward = stack.copy();
            ItemStack noticeStack = reward.copy();
            if (!context.player().addItem(reward) && !reward.isEmpty()) {
                context.player().drop(reward, false);
            }
            VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), noticeStack);
        }
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

    private record Session(ResourceLocation treeId, String nodeId) {
    }

    private record ActionText(String lineId, String text, Map<String, String> replacements) {
        private static ActionText empty() {
            return new ActionText("", "", Map.of());
        }

        private ActionText {
            lineId = lineId == null ? "" : lineId;
            text = text == null ? "" : text;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }
}
