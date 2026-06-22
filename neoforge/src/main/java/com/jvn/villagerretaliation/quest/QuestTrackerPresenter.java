package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class QuestTrackerPresenter {
    private QuestTrackerPresenter() {
    }

    public static QuestTrackerSyncPayload.Entry entry(EntryInput input) {
        QuestDefinition definition = input.definition();
        QuestDefinition.Step step = input.step();
        return new QuestTrackerSyncPayload.Entry(
                definition.id().toString(),
                resolveText(input.player(), input.title(), input.replacements()),
                resolveText(input.player(), new QuestDefinition.SelectedText(step.text(), step.textKey()), input.replacements()),
                resolveText(input.player(), new QuestDefinition.SelectedText(definition.description(), definition.descriptionKey()), input.replacements()),
                metadataText(input.player(), step.metadata(), input.replacements(), input.status(), input.issuer()),
                Mth.clamp(input.progress(), 0.0F, 1.0F),
                input.showProgress(),
                input.state().name().toLowerCase(Locale.ROOT),
                input.status(),
                input.issuer(),
                input.issuerLocation(),
                input.questItems(),
                false,
                false);
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
                    .append(item.count()).append(';');
        }
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
                    .append(item.count()).append(';');
        }
        return builder.toString();
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
            case "inactive", "abandoned", "abandoned_cooldown", "expired", "branch_locked", "consumed", "not_started" -> 0.0F;
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
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) {
            return List.of();
        }
        Map<String, QuestTrackerSyncPayload.QuestItem> items = new LinkedHashMap<>();
        addQuestItem(items, definition.target().proofItem(), 1, itemLabeler);
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK || objective.item() == null) {
                continue;
            }
            addQuestItem(items, objective.item(), objective.count(), itemLabeler);
        }
        return List.copyOf(items.values());
    }

    private static void addQuestItem(
            Map<String, QuestTrackerSyncPayload.QuestItem> items,
            ResourceLocation itemId,
            int count,
            Function<ResourceLocation, String> itemLabeler) {
        if (itemId == null) {
            return;
        }
        String key = itemId.toString();
        QuestTrackerSyncPayload.QuestItem existing = items.get(key);
        if (existing == null || count > existing.count()) {
            items.put(key, new QuestTrackerSyncPayload.QuestItem(key, itemLabeler.apply(itemId), count));
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
            float progress,
            boolean showProgress,
            VillagerQuestSavedData.QuestState state
    ) {
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
            state = state == null ? VillagerQuestSavedData.QuestState.NOT_STARTED : state;
        }
    }
}
