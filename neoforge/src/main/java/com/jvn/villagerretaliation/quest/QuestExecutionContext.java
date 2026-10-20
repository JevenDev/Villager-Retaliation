package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.provider.DiscoveryQuestProviderType;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderType;
import com.jvn.villagerretaliation.quest.provider.VillagerQuestProviderType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public record QuestExecutionContext(
        ServerLevel level,
        ServerPlayer player,
        QuestDefinition quest,
        String event,
        QuestProviderType providerType,
        QuestProviderBinding binding,
        Entity liveProvider,
        String villageKey,
        Set<ResourceLocation> capabilities,
        DialogueContext legacyDialogueContext
) {
    public static final ResourceLocation LIVE_PROVIDER = VillagerRetaliation.id("live_provider");
    public static final ResourceLocation SAVED_PROVIDER = VillagerRetaliation.id("saved_provider");
    public static final ResourceLocation DIALOGUE_CONTEXT = VillagerRetaliation.id("dialogue_context");
    public static final ResourceLocation VILLAGER_PROVIDER = VillagerRetaliation.id("villager_provider");

    public QuestExecutionContext {
        event = event == null ? "" : event;
        villageKey = villageKey == null ? "" : villageKey;
        capabilities = freezeCapabilities(capabilities);
    }

    public static QuestExecutionContext fromDialogueContext(
            DialogueContext context,
            QuestDefinition quest,
            String event) {
        if (context == null) {
            throw new IllegalArgumentException("dialogue context must not be null");
        }
        QuestProviderBinding binding = VillagerQuestProviderType.INSTANCE.bindingFromDialogueContext(context);
        return new QuestExecutionContext(
                context.level(),
                context.player(),
                quest,
                event,
                context.villager() == null ? DiscoveryQuestProviderType.INSTANCE : VillagerQuestProviderType.INSTANCE,
                binding,
                context.villager(),
                binding.villageKey(),
                context.villager() == null ? Set.of() : Set.of(LIVE_PROVIDER, DIALOGUE_CONTEXT, VILLAGER_PROVIDER),
                context);
    }

    public static QuestExecutionContext fromLiveProvider(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition quest,
            String event,
            QuestProviderType providerType,
            QuestProviderBinding binding,
            Entity liveProvider) {
        if (providerType == null || binding == null || liveProvider == null
                || !providerType.id().equals(binding.providerType())
                || !binding.live() || !binding.matchesProviderId(liveProvider.getUUID())
                || liveProvider.isRemoved() || liveProvider.level() != level
                || !level.dimension().equals(binding.dimension())
                || player != null && player.serverLevel() != level) {
            throw new IllegalArgumentException("live quest provider must match its type, binding and level");
        }
        return new QuestExecutionContext(level, player, quest, event, providerType, binding, liveProvider,
                binding.villageKey(), Set.of(LIVE_PROVIDER), null);
    }

    public static QuestExecutionContext fromSavedProvider(
            ServerLevel level,
            ServerPlayer player,
            QuestDefinition quest,
            String event,
            QuestProviderType providerType,
            QuestProviderBinding binding) {
        return new QuestExecutionContext(
                level,
                player,
                quest,
                event,
                providerType,
                binding,
                null,
                binding == null ? "" : binding.villageKey(),
                Set.of(SAVED_PROVIDER),
                null);
    }

    public com.jvn.villagerretaliation.dialogue.conditions.ConditionContext conditions(
            VillagerQuestSavedData.QuestProgress progress) {
        return this.providerType == null
                ? com.jvn.villagerretaliation.dialogue.conditions.EntityConditionAccess.forQuest(this, progress)
                : this.providerType.conditions(this, progress);
    }

    public com.jvn.villagerretaliation.dialogue.conditions.ConditionContext conditions(
            com.jvn.villagerretaliation.quest.runtime.QuestProgressState progress) {
        return this.providerType == null
                ? com.jvn.villagerretaliation.dialogue.conditions.EntityConditionAccess.forQuest(this, progress)
                : this.providerType.conditions(this, progress);
    }

    public Optional<QuestProviderBinding> providerBinding() {
        return Optional.ofNullable(this.binding);
    }

    public Optional<Entity> liveProviderEntity() {
        return Optional.ofNullable(this.liveProvider);
    }

    public Optional<DialogueContext> dialogueContext() {
        return Optional.ofNullable(this.legacyDialogueContext);
    }

    public boolean hasCapability(ResourceLocation capability) {
        return capability != null && this.capabilities.contains(capability);
    }

    private static Set<ResourceLocation> freezeCapabilities(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> copy = new LinkedHashSet<>();
        for (ResourceLocation value : values) {
            if (value != null) {
                copy.add(value);
            }
        }
        return copy.isEmpty() ? Set.of() : Collections.unmodifiableSet(copy);
    }
}
