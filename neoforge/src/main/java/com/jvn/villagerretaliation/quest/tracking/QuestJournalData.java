package com.jvn.villagerretaliation.quest.tracking;

import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import java.util.List;
import java.util.Objects;

public final class QuestJournalData {
    private QuestJournalData() { }

    private static String text(String value) { return Objects.requireNonNullElse(value, ""); }
    private static <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    public record Entry(
            String questId,
            String title,
            String objective,
            String description,
            String metadata,
            float progress,
            boolean showProgress,
            String state,
            String status,
            String issuer,
            String issuerLocation,
            List<QuestItem> questItems,
            List<RewardPreview> rewardPreviews,
            List<Prerequisite> prerequisites,
            List<ObjectiveStep> objectiveSteps,
            boolean questUpdate,
            boolean questAvailable,
            Journal journal) {
        public Entry {
            questId = text(questId);
            title = text(title);
            objective = text(objective);
            description = text(description);
            metadata = text(metadata);
            state = text(state);
            status = text(status);
            issuer = text(issuer);
            issuerLocation = text(issuerLocation);
            questItems = values(questItems);
            rewardPreviews = values(rewardPreviews);
            prerequisites = values(prerequisites);
            objectiveSteps = values(objectiveSteps);
            progress = Float.isFinite(progress) ? Math.max(0.0F, Math.min(1.0F, progress)) : 0.0F;
            journal = journal == null ? Journal.EMPTY : journal;
        }

        public Entry withQuestUpdate(boolean questUpdate) {
            return new Entry(
                    this.questId,
                    this.title,
                    this.objective,
                    this.description,
                    this.metadata,
                    this.progress,
                    this.showProgress,
                    this.state,
                    this.status,
                    this.issuer,
                    this.issuerLocation,
                    this.questItems,
                    this.rewardPreviews,
                    this.prerequisites,
                    this.objectiveSteps,
                    questUpdate,
                    this.questAvailable,
                    this.journal);
        }

        public Entry withJournal(Journal journal) {
            return new Entry(
                    this.questId,
                    this.title,
                    this.objective,
                    this.description,
                    this.metadata,
                    this.progress,
                    this.showProgress,
                    this.state,
                    this.status,
                    this.issuer,
                    this.issuerLocation,
                    this.questItems,
                    this.rewardPreviews,
                    this.prerequisites,
                    this.objectiveSteps,
                    this.questUpdate,
                    this.questAvailable,
                    journal);
        }

        public boolean trackable() {
            return switch (this.state.toLowerCase(java.util.Locale.ROOT)) {
                case "active", "abandoned", "expired" -> true;
                default -> false;
            };
        }
    }

    public record Journal(
            String questline,
            List<String> tags,
            String icon,
            String color,
            String outlineColor,
            int priority,
            boolean hidden,
            long expiresAtGameTime,
            long completedGameTime,
            Waypoint waypoint,
            String blocker,
            int questlineCompleted,
            int questlineTotal) {
        public static final Journal EMPTY = new Journal("", List.of(), "", "", "", 0, false, -1L, -1L, Waypoint.NONE, "", 0, 0);

        public Journal {
            questline = text(questline);
            tags = values(tags);
            icon = text(icon);
            color = text(color);
            outlineColor = text(outlineColor);
            blocker = text(blocker);
            tags = tags.stream().map(String::trim).filter(tag -> !tag.isBlank()).distinct().toList();
            waypoint = waypoint == null ? Waypoint.NONE : waypoint;
            questlineTotal = Math.max(0, questlineTotal);
            questlineCompleted = Math.max(0, Math.min(questlineCompleted, questlineTotal));
        }
    }

    public record Waypoint(
            String dimension,
            int x,
            int y,
            int z) {
        public static final Waypoint NONE = new Waypoint("", 0, 0, 0);

        public Waypoint {
            dimension = text(dimension);
        }

        public boolean present() { return !this.dimension.isBlank(); }
    }

    public record QuestlineNode(
            String questId,
            String title,
            String description,
            String questline,
            List<String> parentQuestIds,
            String icon,
            String color,
            String state) {
        public QuestlineNode {
            questId = text(questId);
            title = text(title);
            description = text(description);
            questline = text(questline);
            parentQuestIds = values(parentQuestIds);
            icon = text(icon);
            color = text(color);
            state = text(state);
            parentQuestIds = parentQuestIds.stream().filter(parent -> !parent.isBlank()).distinct().toList();
        }
    }

    public record RewardPreview(
            String kind,
            String label,
            int amount,
            String itemId) {
        public RewardPreview {
            kind = text(kind);
            label = text(label);
            itemId = text(itemId);
        }
    }

    public record Prerequisite(
            String questId,
            String label,
            boolean met) {
        public Prerequisite {
            questId = text(questId);
            label = text(label);
        }
    }

    public record ObjectiveStep(
            String label,
            boolean completed) {
        public ObjectiveStep {
            label = text(label);
        }
    }

    public record QuestItem(
            String itemId,
            String label,
            int count,
            int currentCount,
            ItemStackPredicate stackPredicate,
            List<QuestItemEnchantment> enchantments,
            int minDurabilityPercent,
            int maxDurabilityPercent) {
        public QuestItem {
            itemId = text(itemId);
            label = text(label);
            enchantments = values(enchantments);
            count = Math.max(1, count);
            currentCount = Math.max(0, currentCount);
            stackPredicate = stackPredicate == null ? ItemStackPredicate.ANY : stackPredicate;
            minDurabilityPercent = Math.max(-1, Math.min(100, minDurabilityPercent));
            maxDurabilityPercent = Math.max(-1, Math.min(100, maxDurabilityPercent));
        }
    }

    public record QuestItemEnchantment(
            String id,
            int minLevel,
            int maxLevel) {
        public QuestItemEnchantment {
            id = text(id);
            minLevel = Math.max(-1, minLevel);
            maxLevel = Math.max(-1, maxLevel);
        }
    }
}
