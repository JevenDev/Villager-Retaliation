package com.jvn.villagerretaliation.quest.tracking;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.scene.SceneJournalPresenter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class QuestTrackerPresenter {
    private QuestTrackerPresenter() {
    }

    public static QuestTrackerSyncPayload.Entry entry(EntryInput input) {
        QuestDefinition definition = input.definition();
        QuestDefinition.Step step = input.step();
        String objective = resolveText(
                input.player(),
                new QuestDefinition.SelectedText(step.text(), step.textKey()),
                input.replacements());
        QuestTrackerSyncPayload.Entry entry = new QuestTrackerSyncPayload.Entry(
                definition.id().toString(),
                resolveText(input.player(), input.title(), input.replacements()),
                objective,
                resolveText(input.player(), new QuestDefinition.SelectedText(definition.description(), definition.descriptionKey()), input.replacements()),
                appendSceneStatus(metadataText(input.player(), step.metadata(), input.replacements(), input.status(), input.issuer()),
                        SceneJournalPresenter.status(input.player(), definition.id())),
                Mth.clamp(input.progress(), 0.0F, 1.0F),
                input.showProgress(),
                input.state().name().toLowerCase(Locale.ROOT),
                input.status(),
                input.issuer(),
                input.issuerLocation(),
                input.questItems(),
                input.rewardPreviews(),
                input.prerequisites(),
                input.objectiveSteps(),
                false,
                false);
        QuestDefinition.Tracker tracker = definition.tracker();
        return entry.withJournal(new QuestTrackerSyncPayload.Journal(
                definition.questline(),
                definition.tags().stream().sorted().toList(),
                tracker.icon() == null ? "" : tracker.icon().toString(),
                tracker.color(),
                tracker.outlineColor(),
                tracker.priority(),
                tracker.hidden(),
                -1L,
                -1L,
                QuestTrackerSyncPayload.Waypoint.NONE)
                .withBlocker(blockerText(input, objective)));
    }

    private static String appendSceneStatus(String metadata,String sceneStatus){
        if(sceneStatus==null||sceneStatus.isBlank())return metadata;
        return metadata==null||metadata.isBlank()?sceneStatus:metadata+" • "+sceneStatus;
    }

    public static String syncSignature(
            List<QuestTrackerSyncPayload.Entry> entries,
            ResourceLocation trackedQuestId) {
        return syncSignature(entries, trackedQuestId == null ? List.of() : List.of(trackedQuestId));
    }

    public static String syncSignature(
            List<QuestTrackerSyncPayload.Entry> entries,
            List<ResourceLocation> trackedQuestIds) {
        StringBuilder builder = new StringBuilder();
        if (trackedQuestIds != null) {
            for (ResourceLocation trackedQuestId : trackedQuestIds) {
                if (trackedQuestId != null) {
                    builder.append(trackedQuestId).append(';');
                }
            }
        }
        builder.append('\n');
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            builder.append(entrySignature(entry)).append('\n');
        }
        return builder.toString();
    }

    public static Map<String, String> entrySignatures(List<QuestTrackerSyncPayload.Entry> entries) {
        Map<String, String> signatures = new LinkedHashMap<>();
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        for (QuestTrackerSyncPayload.Entry entry : entries) {
            if (entry != null && !entry.questId().isBlank() && !entry.questAvailable()) {
                signatures.put(entry.questId(), questProgressSignature(entry));
            }
        }
        return Map.copyOf(signatures);
    }

    public static String questProgressSignature(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(entry.questId()).append('|')
                .append(entry.title()).append('|')
                .append(entry.objective()).append('|')
                .append(entry.description()).append('|')
                .append(entry.metadata()).append('|')
                .append(entry.progress()).append('|')
                .append(entry.showProgress()).append('|')
                .append(entry.state()).append('|')
                .append(entry.status()).append('|')
                .append(entry.issuer()).append('|');
        for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
            builder.append(item.itemId()).append(',')
                    .append(item.label()).append(',')
                    .append(item.count()).append(',')
                    .append(item.currentCount()).append(';');
        }
        builder.append('|');
        appendRewardPreviewSignature(builder, entry.rewardPreviews());
        builder.append('|');
        appendPrerequisiteSignature(builder, entry.prerequisites());
        builder.append('|');
        appendObjectiveStepSignature(builder, entry.objectiveSteps());
        appendJournalSignature(builder, entry.journal());
        return builder.toString();
    }

    public static String entrySignature(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(entry.questId()).append('|')
                .append(entry.title()).append('|')
                .append(entry.objective()).append('|')
                .append(entry.description()).append('|')
                .append(entry.metadata()).append('|')
                .append(entry.progress()).append('|')
                .append(entry.showProgress()).append('|')
                .append(entry.state()).append('|')
                .append(entry.status()).append('|')
                .append(entry.issuer()).append('|')
                .append(entry.issuerLocation()).append('|');
        for (QuestTrackerSyncPayload.QuestItem item : entry.questItems()) {
            builder.append(item.itemId()).append(',')
                    .append(item.label()).append(',')
                    .append(item.count()).append(',')
                    .append(item.currentCount()).append(';');
        }
        builder.append('|');
        appendRewardPreviewSignature(builder, entry.rewardPreviews());
        builder.append('|');
        appendPrerequisiteSignature(builder, entry.prerequisites());
        builder.append('|');
        appendObjectiveStepSignature(builder, entry.objectiveSteps());
        appendJournalSignature(builder, entry.journal());
        return builder.toString();
    }

    private static void appendRewardPreviewSignature(
            StringBuilder builder,
            List<QuestTrackerSyncPayload.RewardPreview> rewardPreviews) {
        for (QuestTrackerSyncPayload.RewardPreview reward : rewardPreviews) {
            builder.append(reward.kind()).append(',')
                    .append(reward.label()).append(',')
                    .append(reward.amount()).append(',')
                    .append(reward.itemId()).append(';');
        }
    }

    private static void appendPrerequisiteSignature(
            StringBuilder builder,
            List<QuestTrackerSyncPayload.Prerequisite> prerequisites) {
        for (QuestTrackerSyncPayload.Prerequisite prerequisite : prerequisites) {
            builder.append(prerequisite.questId()).append(',')
                    .append(prerequisite.label()).append(',')
                    .append(prerequisite.met()).append(';');
        }
    }

    private static void appendObjectiveStepSignature(
            StringBuilder builder,
            List<QuestTrackerSyncPayload.ObjectiveStep> objectiveSteps) {
        for (QuestTrackerSyncPayload.ObjectiveStep objectiveStep : objectiveSteps) {
            builder.append(objectiveStep.label()).append(',')
                    .append(objectiveStep.completed()).append(';');
        }
    }

    private static void appendJournalSignature(StringBuilder builder, QuestTrackerSyncPayload.Journal journal) {
        builder.append('|').append(journal.questline()).append('|')
                .append(journal.icon()).append('|').append(journal.color()).append('|')
                .append(journal.outlineColor()).append('|').append(journal.priority()).append('|').append(journal.hidden()).append('|')
                .append(journal.expiresAtGameTime()).append('|').append(journal.completedGameTime()).append('|')
                .append(journal.blocker()).append('|').append(journal.questlineCompleted()).append('|')
                .append(journal.questlineTotal()).append('|');
        for (String tag : journal.tags()) {
            builder.append(tag).append(';');
        }
        QuestTrackerSyncPayload.Waypoint waypoint = journal.waypoint();
        builder.append('|').append(waypoint.dimension()).append(',').append(waypoint.x()).append(',')
                .append(waypoint.y()).append(',').append(waypoint.z());
    }

    public static QuestDefinition.Step fallbackStep(
            String stepKey,
            VillagerQuestSavedData.QuestState state) {
        return new QuestDefinition.Step(
                fallbackText(stepKey),
                fallbackTextKey(stepKey),
                state == VillagerQuestSavedData.QuestState.ACTIVE,
                fallbackProgress(stepKey),
                Map.of());
    }

    public static String fallbackText(String stepKey) {
        return switch (stepKey) {
            case "inactive" -> "Return when this quest's conditions are right again.";
            case "proof" -> "Obtain {proof_item} for this quest.";
            case "hunt" -> "Defeat {objective_count} {objective_entity}.";
            case "break" -> "Break {objective_count} {objective_block}.";
            case "build" -> "Place {objective_count} {objective_block}.";
            case "interact" -> "Use {objective_count} {objective_block}.";
            case "event" -> "Wait for {objective_memory}.";
            case "trade" -> "Complete trades: {objective_progress_count}/{objective_count}.";
            case "gift" -> "Give {objective_count} {objective_item}.";
            case "reputation" -> "Reach {objective_reputation_level} reputation with {issuer}.";
            case "choice" -> "Make a choice for this quest.";
            case "fact" -> "Resolve {objective_fact}.";
            case "return" -> "Return to {issuer}.";
            case "abandoned" -> "Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} to pick this back up.";
            case "abandoned_cooldown" -> "Available later. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z}.";
            case "expired" -> "Expired. Return to {issuer} near {issuer_x}, {issuer_y}, {issuer_z} if this can be restarted.";
            case "failed" -> "Failed. Review the quest details before restarting.";
            case "completed" -> "Completed.";
            case "branch_locked" -> "Closed by another choice.";
            case "consumed" -> "Unavailable.";
            default -> "Reach the center of {target} near {target_x}, {target_z}.";
        };
    }

    public static String fallbackTextKey(String stepKey) {
        String normalized = stepKey == null || stepKey.isBlank() ? "travel" : stepKey.toLowerCase(Locale.ROOT);
        return "quest.tracker.step." + normalized;
    }

    public static float fallbackProgress(String stepKey) {
        return switch (stepKey) {
            case "inactive", "failed", "abandoned", "abandoned_cooldown", "expired", "branch_locked", "consumed", "not_started" -> 0.0F;
            case "proof" -> 0.66F;
            case "return", "completed" -> 1.0F;
            default -> 0.25F;
        };
    }

    public static QuestDefinition.Step objectiveTrackerStep(
            QuestDefinition.Objective objective,
            QuestDefinition.Step fallback,
            boolean complete) {
        QuestDefinition.ObjectiveTracker tracker = objective.tracker();
        QuestDefinition.SelectedText displayText = tracker.displayText(complete);
        String text = displayText.text().isBlank() ? fallback.text() : displayText.text();
        String textKey = displayText.key().isBlank() ? fallback.textKey() : displayText.key();
        float progress = !complete && tracker.progress() >= 0.0F ? tracker.progress() : fallback.progress();
        Map<String, String> metadata = tracker.metadata().isEmpty() ? fallback.metadata() : tracker.metadata();
        return new QuestDefinition.Step(text, textKey, tracker.showProgress(), progress, metadata);
    }

    public static boolean objectiveTrackerHasDisplay(QuestDefinition.Objective objective, boolean complete) {
        return complete ? objective.tracker().hasCompletionDisplay() : objective.tracker().hasActiveDisplay();
    }

    public static String metadataText(
            ServerPlayer player,
            Map<String, String> metadata,
            Map<String, String> replacements,
            String status,
            String issuer) {
        List<String> parts = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            parts.add(status);
        }
        if (issuer != null && !issuer.isBlank()) {
            parts.add(resolveGlobalText(player, "quest.tracker.metadata.issued_by", "Issued by {issuer}", replacements));
        }
        if (metadata == null || metadata.isEmpty()) {
            return String.join(" | ", parts);
        }
        metadata.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> VillagerDialogueResources.resolveTemplate(value, replacements))
                .forEach(parts::add);
        return String.join(" | ", parts);
    }

    public static String resolveText(
            ServerPlayer player,
            QuestDefinition.SelectedText selected,
            Map<String, String> replacements) {
        if (selected == null) {
            return "";
        }
        if (!selected.key().isBlank()) {
            return resolveGlobalText(player, selected.key(), selected.text(), replacements);
        }
        return VillagerDialogueResources.resolveTemplate(selected.text(), replacements);
    }

    public static String resolveGlobalText(
            ServerPlayer player,
            String key,
            String fallback,
            Map<String, String> replacements) {
        if (player != null && player.getServer() != null && key != null && !key.isBlank()) {
            return VillagerDialogueResources
                    .globalMessage(player.getServer(), player.getRandom(), key, VillagerLocale.locale(player), replacements)
                    .orElseGet(() -> VillagerDialogueResources.resolveTemplate(fallback, replacements));
        }
        return VillagerDialogueResources.resolveTemplate(fallback, replacements);
    }

    public static List<QuestTrackerSyncPayload.QuestItem> questItems(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Function<ResourceLocation, String> itemLabeler) {
        return questItems(definition, progress, itemLabeler, itemId -> 0, objective -> 0);
    }

    public static List<QuestTrackerSyncPayload.QuestItem> questItems(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Function<ResourceLocation, String> itemLabeler,
            Function<ResourceLocation, Integer> itemCounter,
            ToIntFunction<QuestDefinition.Objective> objectiveCounter) {
        return questItems(
                definition,
                progress,
                itemLabeler,
                itemCounter,
                objectiveCounter,
                QuestObjectiveQuery.activeObjectives(definition, progress));
    }

    public static List<QuestTrackerSyncPayload.QuestItem> questItems(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Function<ResourceLocation, String> itemLabeler,
            Function<ResourceLocation, Integer> itemCounter,
            ToIntFunction<QuestDefinition.Objective> objectiveCounter,
            List<QuestDefinition.Objective> objectives) {
        return questItems(
                definition, progress, itemLabeler, itemCounter, objectiveCounter,
                QuestDefinition.Objective::item, objectives);
    }

    public static List<QuestTrackerSyncPayload.QuestItem> questItems(
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress,
            Function<ResourceLocation, String> itemLabeler,
            Function<ResourceLocation, Integer> itemCounter,
            ToIntFunction<QuestDefinition.Objective> objectiveCounter,
            Function<QuestDefinition.Objective, ResourceLocation> objectiveItemResolver,
            List<QuestDefinition.Objective> objectives) {
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return List.of();
        }
        Map<String, QuestTrackerSyncPayload.QuestItem> items = new LinkedHashMap<>();
        addQuestItem(
                items,
                definition.target().proofItem(),
                definition.target().proofItemPredicate(),
                List.of(),
                -1,
                -1,
                1,
                itemLabeler,
                currentItemCount(itemCounter, definition.target().proofItem()));
        for (QuestDefinition.Objective objective : objectives == null ? List.<QuestDefinition.Objective>of() : objectives) {
            ResourceLocation itemId = objectiveItemResolver == null ? objective.item() : objectiveItemResolver.apply(objective);
            if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK || itemId == null) {
                continue;
            }
            addQuestItem(
                    items,
                    itemId,
                    objective.itemRequirements().stackPredicate(),
                    objective.itemRequirements().enchantments().stream()
                            .map(requirement -> new QuestTrackerSyncPayload.QuestItemEnchantment(
                                    requirement.id().toString(),
                                    requirement.minLevel().orElse(-1),
                                    requirement.maxLevel().orElse(-1)))
                            .toList(),
                    objective.itemRequirements().minDurabilityPercent().orElse(-1),
                    objective.itemRequirements().maxDurabilityPercent().orElse(-1),
                    objective.count(),
                    itemLabeler,
                    objectiveCounter == null ? 0 : Math.max(0, objectiveCounter.applyAsInt(objective)));
        }
        return List.copyOf(items.values());
    }

    private static int currentItemCount(Function<ResourceLocation, Integer> itemCounter, ResourceLocation itemId) {
        if (itemCounter == null || itemId == null) {
            return 0;
        }
        Integer count = itemCounter.apply(itemId);
        return count == null ? 0 : Math.max(0, count);
    }

    private static String blockerText(EntryInput input, String objective) {
        VillagerQuestSavedData.QuestState state = input.state();
        if (state == VillagerQuestSavedData.QuestState.NOT_STARTED) {
            return input.prerequisites().stream()
                    .filter(prerequisite -> !prerequisite.met())
                    .map(QuestTrackerSyncPayload.Prerequisite::label)
                    .filter(label -> label != null && !label.isBlank())
                    .findFirst()
                    .orElse("");
        }
        if (state == VillagerQuestSavedData.QuestState.COMPLETED
                || state == VillagerQuestSavedData.QuestState.CONSUMED) {
            return "";
        }
        if (state == VillagerQuestSavedData.QuestState.ACTIVE) {
            for (QuestTrackerSyncPayload.QuestItem item : input.questItems()) {
                int remaining = Math.max(0, item.count() - item.currentCount());
                if (remaining <= 0) {
                    continue;
                }
                Map<String, String> values = previewReplacements(input.replacements());
                values.put("remaining", Integer.toString(remaining));
                values.put("item", item.label());
                return resolveGlobalText(
                        input.player(),
                        "quest.tracker.blocker.items",
                        "Collect {remaining} more {item}.",
                        values);
            }
            for (QuestTrackerSyncPayload.ObjectiveStep step : input.objectiveSteps()) {
                if (!step.completed() && !step.label().isBlank()) {
                    return step.label();
                }
            }
        }
        return objective == null ? "" : objective;
    }

    public static Map<String, QuestlineProgress> questlineProgress(
            Collection<QuestDefinition> definitions,
            Function<ResourceLocation, VillagerQuestSavedData.QuestProgress> progressLookup) {
        if (definitions == null || definitions.isEmpty()) {
            return Map.of();
        }
        Map<String, QuestlineProgress> progressByQuestline = new LinkedHashMap<>();
        for (QuestDefinition definition : definitions) {
            if (definition == null || definition.questline().isBlank() || definition.tracker().hidden()) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = progressLookup == null
                    ? null
                    : progressLookup.apply(definition.id());
            if (progress != null
                    && progress.state() == VillagerQuestSavedData.QuestState.CONSUMED
                    && "branch_lock".equals(progress.consumedReason())) {
                continue;
            }
            QuestlineProgress current = progressByQuestline.getOrDefault(
                    definition.questline(), QuestlineProgress.EMPTY);
            boolean completed = progress != null
                    && (progress.completionCount() > 0
                    || progress.state() == VillagerQuestSavedData.QuestState.COMPLETED
                    || progress.completedGameTime() >= 0L);
            progressByQuestline.put(
                    definition.questline(),
                    new QuestlineProgress(current.completed() + (completed ? 1 : 0), current.total() + 1));
        }
        return Map.copyOf(progressByQuestline);
    }

    public static List<QuestTrackerSyncPayload.RewardPreview> rewardPreviews(
            ServerPlayer player,
            QuestDefinition definition,
            Map<String, String> replacements) {
        if (definition == null || definition.rewards() == null) {
            return List.of();
        }
        QuestDefinition.Rewards rewards = definition.rewards();
        List<QuestTrackerSyncPayload.RewardPreview> previews = new ArrayList<>();
        if (player != null && player.getServer() != null && rewards.lootTable() != null) {
            for (QuestRewardPreviewResources.ItemPreview item : QuestRewardPreviewResources.itemPreviews(
                    player.getServer(), rewards.lootTable())) {
                previews.add(new QuestTrackerSyncPayload.RewardPreview(
                        "item",
                        item.countText(),
                        item.minimumCount(),
                        item.itemId()));
            }
        }
        addAmountRewardPreview(
                previews,
                player,
                "experience",
                rewards.experience(),
                "quest.tracker.reward.experience",
                "{amount} XP",
                replacements);
        addAmountRewardPreview(
                previews,
                player,
                "reputation",
                rewards.reputation(),
                "quest.tracker.reward.reputation",
                "Reputation {signed_amount}",
                replacements);
        addAmountRewardPreview(
                previews,
                player,
                "gossip",
                rewards.gossipReputation(),
                "quest.tracker.reward.gossip",
                "Village gossip {signed_amount}",
                replacements);
        if (rewards.memoryEvent() != null) {
            Map<String, String> values = previewReplacements(replacements);
            values.put("memory_event", rewards.memoryEvent().toString());
            previews.add(new QuestTrackerSyncPayload.RewardPreview(
                    "memory",
                    resolveGlobalText(player, "quest.tracker.reward.memory", "Village memory updated", values),
                    0));
        }
        return List.copyOf(previews.stream()
                .filter(reward -> reward != null && !reward.label().isBlank())
                .limit(QuestTrackerSyncPayload.MAX_REWARD_PREVIEWS)
                .toList());
    }

    private static void addAmountRewardPreview(
            List<QuestTrackerSyncPayload.RewardPreview> previews,
            ServerPlayer player,
            String kind,
            int amount,
            String textKey,
            String fallback,
            Map<String, String> replacements) {
        if (amount == 0) {
            return;
        }
        Map<String, String> values = previewReplacements(replacements);
        values.put("amount", Integer.toString(Math.abs(amount)));
        values.put("signed_amount", signedAmount(amount));
        previews.add(new QuestTrackerSyncPayload.RewardPreview(
                kind,
                resolveGlobalText(player, textKey, fallback, values),
                amount));
    }

    private static Map<String, String> previewReplacements(Map<String, String> replacements) {
        Map<String, String> values = new LinkedHashMap<>();
        if (replacements != null) {
            values.putAll(replacements);
        }
        return values;
    }

    private static String signedAmount(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }

    public static List<QuestTrackerSyncPayload.Prerequisite> prerequisites(
            ServerPlayer player,
            QuestDefinition definition,
            Function<ResourceLocation, String> questTitleLabeler,
            Function<ResourceLocation, Boolean> completionLookup) {
        if (definition == null || definition.prerequisites().isEmpty()) {
            return List.of();
        }
        List<QuestTrackerSyncPayload.Prerequisite> presented = new ArrayList<>();
        for (ResourceLocation prerequisite : definition.prerequisites()) {
            String title = questTitleLabeler == null ? prerequisite.toString() : questTitleLabeler.apply(prerequisite);
            if (title == null || title.isBlank()) {
                title = prerequisite.toString();
            }
            Map<String, String> values = new LinkedHashMap<>();
            values.put("parent_quest", title);
            values.put("parent_quest_id", prerequisite.toString());
            boolean met = completionLookup != null && Boolean.TRUE.equals(completionLookup.apply(prerequisite));
            String fallback = met ? "Completed {parent_quest}" : "Complete {parent_quest}";
            String key = met ? "quest.tracker.prerequisite.parent_complete" : "quest.tracker.prerequisite.parent";
            presented.add(new QuestTrackerSyncPayload.Prerequisite(
                    prerequisite.toString(),
                    resolveGlobalText(player, key, fallback, values),
                    met));
        }
        return List.copyOf(presented);
    }

    private static void addQuestItem(
            Map<String, QuestTrackerSyncPayload.QuestItem> items,
            ResourceLocation itemId,
            com.jvn.villagerretaliation.util.item.ItemStackPredicate stackPredicate,
            List<QuestTrackerSyncPayload.QuestItemEnchantment> enchantments,
            int minDurabilityPercent,
            int maxDurabilityPercent,
            int count,
            Function<ResourceLocation, String> itemLabeler,
            int currentCount) {
        if (itemId == null) {
            return;
        }
        com.jvn.villagerretaliation.util.item.ItemStackPredicate predicate = stackPredicate == null
                ? com.jvn.villagerretaliation.util.item.ItemStackPredicate.ANY
                : stackPredicate;
        List<QuestTrackerSyncPayload.QuestItemEnchantment> safeEnchantments =
                enchantments == null ? List.of() : List.copyOf(enchantments);
        String key = itemId + "|" + predicate.hashCode() + "|" + safeEnchantments.hashCode()
                + "|" + minDurabilityPercent + "|" + maxDurabilityPercent;
        QuestTrackerSyncPayload.QuestItem existing = items.get(key);
        if (existing == null || count > existing.count()) {
            items.put(key, new QuestTrackerSyncPayload.QuestItem(
                    itemId.toString(), itemLabeler.apply(itemId), count, currentCount, predicate,
                    safeEnchantments, minDurabilityPercent, maxDurabilityPercent));
        } else if (currentCount > existing.currentCount()) {
            items.put(key, new QuestTrackerSyncPayload.QuestItem(
                    existing.itemId(),
                    existing.label(),
                    existing.count(),
                    currentCount,
                    existing.stackPredicate(),
                    existing.enchantments(),
                    existing.minDurabilityPercent(),
                    existing.maxDurabilityPercent()));
        }
    }

    public record EntryInput(
            ServerPlayer player,
            QuestDefinition definition,
            QuestDefinition.SelectedText title,
            QuestDefinition.Step step,
            Map<String, String> replacements,
            String status,
            String issuer,
            String issuerLocation,
            List<QuestTrackerSyncPayload.QuestItem> questItems,
            List<QuestTrackerSyncPayload.RewardPreview> rewardPreviews,
            List<QuestTrackerSyncPayload.Prerequisite> prerequisites,
            List<QuestTrackerSyncPayload.ObjectiveStep> objectiveSteps,
            float progress,
            boolean showProgress,
            VillagerQuestSavedData.QuestState state
    ) {
        public EntryInput(
                ServerPlayer player,
                QuestDefinition definition,
                QuestDefinition.SelectedText title,
                QuestDefinition.Step step,
                Map<String, String> replacements,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestTrackerSyncPayload.QuestItem> questItems,
                float progress,
                boolean showProgress,
                VillagerQuestSavedData.QuestState state) {
            this(player, definition, title, step, replacements, status, issuer, issuerLocation, questItems, List.of(), List.of(), List.of(), progress, showProgress, state);
        }

        public EntryInput(
                ServerPlayer player,
                QuestDefinition definition,
                QuestDefinition.SelectedText title,
                QuestDefinition.Step step,
                Map<String, String> replacements,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestTrackerSyncPayload.QuestItem> questItems,
                List<QuestTrackerSyncPayload.RewardPreview> rewardPreviews,
                float progress,
                boolean showProgress,
                VillagerQuestSavedData.QuestState state) {
            this(player, definition, title, step, replacements, status, issuer, issuerLocation, questItems, rewardPreviews, List.of(), List.of(), progress, showProgress, state);
        }

        public EntryInput(
                ServerPlayer player,
                QuestDefinition definition,
                QuestDefinition.SelectedText title,
                QuestDefinition.Step step,
                Map<String, String> replacements,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestTrackerSyncPayload.QuestItem> questItems,
                List<QuestTrackerSyncPayload.RewardPreview> rewardPreviews,
                List<QuestTrackerSyncPayload.Prerequisite> prerequisites,
                float progress,
                boolean showProgress,
                VillagerQuestSavedData.QuestState state) {
            this(player, definition, title, step, replacements, status, issuer, issuerLocation, questItems, rewardPreviews, prerequisites, List.of(), progress, showProgress, state);
        }

        public EntryInput {
            if (definition == null) {
                throw new IllegalArgumentException("tracker entry definition must not be null");
            }
            title = title == null
                    ? new QuestDefinition.SelectedText(definition.title(), definition.titleKey())
                    : title;
            step = step == null ? fallbackStep("travel", state) : step;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
            status = status == null ? "" : status;
            issuer = issuer == null ? "" : issuer;
            issuerLocation = issuerLocation == null ? "" : issuerLocation;
            questItems = questItems == null ? List.of() : List.copyOf(questItems);
            rewardPreviews = rewardPreviews == null ? List.of() : List.copyOf(rewardPreviews);
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            objectiveSteps = objectiveSteps == null ? List.of() : List.copyOf(objectiveSteps);
            state = state == null ? VillagerQuestSavedData.QuestState.NOT_STARTED : state;
        }
    }

    public record QuestlineProgress(int completed, int total) {
        private static final QuestlineProgress EMPTY = new QuestlineProgress(0, 0);

        public QuestlineProgress {
            total = Math.max(0, total);
            completed = Math.max(0, Math.min(completed, total));
        }
    }
}
