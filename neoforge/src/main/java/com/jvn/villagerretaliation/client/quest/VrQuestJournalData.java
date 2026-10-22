package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;

public final class VrQuestJournalData {
    private VrQuestJournalData() { }

    public static QuestJournalData.Entry shared(QuestTrackerSyncPayload.Entry value) {
        if (value == null) return null;
        return new QuestJournalData.Entry(
                value.questId(),
                value.title(),
                value.objective(),
                value.description(),
                value.metadata(),
                value.progress(),
                value.showProgress(),
                value.state(),
                value.status(),
                value.issuer(),
                value.issuerLocation(),
                value.questItems().stream().map(VrQuestJournalData::shared).toList(),
                value.rewardPreviews().stream().map(VrQuestJournalData::shared).toList(),
                value.prerequisites().stream().map(VrQuestJournalData::shared).toList(),
                value.objectiveSteps().stream().map(VrQuestJournalData::shared).toList(),
                value.questUpdate(),
                value.questAvailable(),
                shared(value.journal()));
    }

    public static QuestJournalData.Journal shared(QuestTrackerSyncPayload.Journal value) {
        if (value == null) return null;
        return new QuestJournalData.Journal(
                value.questline(),
                value.tags(),
                value.icon(),
                value.color(),
                value.outlineColor(),
                value.priority(),
                value.hidden(),
                value.expiresAtGameTime(),
                value.completedGameTime(),
                shared(value.waypoint()),
                value.blocker(),
                value.questlineCompleted(),
                value.questlineTotal());
    }

    public static QuestJournalData.Waypoint shared(QuestTrackerSyncPayload.Waypoint value) {
        if (value == null) return null;
        return new QuestJournalData.Waypoint(
                value.dimension(), value.x(), value.y(), value.z(), value.radius());
    }

    public static QuestJournalData.QuestlineNode shared(QuestTrackerSyncPayload.QuestlineNode value) {
        if (value == null) return null;
        return new QuestJournalData.QuestlineNode(
                value.questId(),
                value.title(),
                value.description(),
                value.questline(),
                value.parentQuestIds(),
                value.icon(),
                value.color(),
                value.state());
    }

    public static QuestJournalData.RewardPreview shared(QuestTrackerSyncPayload.RewardPreview value) {
        if (value == null) return null;
        return new QuestJournalData.RewardPreview(
                value.kind(),
                value.label(),
                value.amount(),
                value.itemId());
    }

    public static QuestJournalData.Prerequisite shared(QuestTrackerSyncPayload.Prerequisite value) {
        if (value == null) return null;
        return new QuestJournalData.Prerequisite(
                value.questId(),
                value.label(),
                value.met());
    }

    public static QuestJournalData.ObjectiveStep shared(QuestTrackerSyncPayload.ObjectiveStep value) {
        if (value == null) return null;
        return new QuestJournalData.ObjectiveStep(
                value.label(),
                value.completed());
    }

    public static QuestJournalData.QuestItem shared(QuestTrackerSyncPayload.QuestItem value) {
        if (value == null) return null;
        return new QuestJournalData.QuestItem(
                value.itemId(),
                value.label(),
                value.count(),
                value.currentCount(),
                value.stackPredicate(),
                value.enchantments().stream().map(VrQuestJournalData::shared).toList(),
                value.minDurabilityPercent(),
                value.maxDurabilityPercent());
    }

    public static QuestJournalData.QuestItemEnchantment shared(QuestTrackerSyncPayload.QuestItemEnchantment value) {
        if (value == null) return null;
        return new QuestJournalData.QuestItemEnchantment(
                value.id(),
                value.minLevel(),
                value.maxLevel());
    }

    public static QuestTrackerSyncPayload.Entry legacy(QuestJournalData.Entry value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.Entry(
                value.questId(),
                value.title(),
                value.objective(),
                value.description(),
                value.metadata(),
                value.progress(),
                value.showProgress(),
                value.state(),
                value.status(),
                value.issuer(),
                value.issuerLocation(),
                value.questItems().stream().map(VrQuestJournalData::legacy).toList(),
                value.rewardPreviews().stream().map(VrQuestJournalData::legacy).toList(),
                value.prerequisites().stream().map(VrQuestJournalData::legacy).toList(),
                value.objectiveSteps().stream().map(VrQuestJournalData::legacy).toList(),
                value.questUpdate(),
                value.questAvailable(),
                legacy(value.journal()));
    }

    public static QuestTrackerSyncPayload.Journal legacy(QuestJournalData.Journal value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.Journal(
                value.questline(),
                value.tags(),
                value.icon(),
                value.color(),
                value.outlineColor(),
                value.priority(),
                value.hidden(),
                value.expiresAtGameTime(),
                value.completedGameTime(),
                legacy(value.waypoint()),
                value.blocker(),
                value.questlineCompleted(),
                value.questlineTotal());
    }

    public static QuestTrackerSyncPayload.Waypoint legacy(QuestJournalData.Waypoint value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.Waypoint(
                value.dimension(), value.x(), value.y(), value.z(), value.radius());
    }

    public static QuestTrackerSyncPayload.QuestlineNode legacy(QuestJournalData.QuestlineNode value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.QuestlineNode(
                value.questId(),
                value.title(),
                value.description(),
                value.questline(),
                value.parentQuestIds(),
                value.icon(),
                value.color(),
                value.state());
    }

    public static QuestTrackerSyncPayload.RewardPreview legacy(QuestJournalData.RewardPreview value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.RewardPreview(
                value.kind(),
                value.label(),
                value.amount(),
                value.itemId());
    }

    public static QuestTrackerSyncPayload.Prerequisite legacy(QuestJournalData.Prerequisite value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.Prerequisite(
                value.questId(),
                value.label(),
                value.met());
    }

    public static QuestTrackerSyncPayload.ObjectiveStep legacy(QuestJournalData.ObjectiveStep value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.ObjectiveStep(
                value.label(),
                value.completed());
    }

    public static QuestTrackerSyncPayload.QuestItem legacy(QuestJournalData.QuestItem value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.QuestItem(
                value.itemId(),
                value.label(),
                value.count(),
                value.currentCount(),
                value.stackPredicate(),
                value.enchantments().stream().map(VrQuestJournalData::legacy).toList(),
                value.minDurabilityPercent(),
                value.maxDurabilityPercent());
    }

    public static QuestTrackerSyncPayload.QuestItemEnchantment legacy(QuestJournalData.QuestItemEnchantment value) {
        if (value == null) return null;
        return new QuestTrackerSyncPayload.QuestItemEnchantment(
                value.id(),
                value.minLevel(),
                value.maxLevel());
    }
}
