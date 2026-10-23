package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestPerformance;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerService;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;

public final class QuestPerformanceService {
    private static com.jvn.villagerretaliation.quest.content.QuestContentCatalog trackedCatalog;
    private static java.util.Map<net.minecraft.resources.ResourceLocation, QuestDefinition> trackedDefinitions = java.util.Map.of();

    private QuestPerformanceService() { }

    public static void tick(MinecraftServer server) {
        var catalog = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(server);
        if (catalog != trackedCatalog) {
            var definitions = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, QuestDefinition>();
            catalog.questDefinitions().stream().filter(quest -> !quest.rewards().performance().measurements().isEmpty())
                    .forEach(quest -> definitions.put(quest.id(), quest));
            trackedDefinitions = java.util.Map.copyOf(definitions);
            trackedCatalog = catalog;
        }
        if (trackedDefinitions.isEmpty()) return;
        var data = VillagerQuestSavedData.get(server.overworld());
        Set<QuestPerformanceState> updated = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : data.activeEntries(trackedDefinitions.keySet())) {
            var definition = trackedDefinitions.get(entry.questId());
            if (definition == null || definition.rewards().performance().measurements().isEmpty()) continue;
            var state = state(server.overworld(), entry.playerId(), definition, entry.progress());
            if (!state.frozen() && updated.add(state)) update(server.overworld(), entry.playerId(), definition, entry.progress());
        }
    }

    private static QuestPerformanceState state(ServerLevel level, UUID player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        PartyQuestService.sharedForPlayer(level, player, definition.id()).ifPresent(shared -> {
            if (shared.performance() == null) shared.performance(progress.performance());
            progress.performance(shared.performance());
        });
        return progress.performance();
    }

    public static void update(ServerLevel level, UUID playerId, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        update(level, playerId, definition, progress, true);
    }

    private static void update(ServerLevel level, UUID playerId, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress, boolean evaluateCompletion) {
        if (definition.rewards().performance().measurements().isEmpty()) return;
        var state = state(level, playerId, definition, progress);
        if (state.frozen()) return;
        var shared = PartyQuestService.sharedForPlayer(level, playerId, definition.id()).orElse(null);
        boolean online = false;
        boolean active = false;
        var onlinePlayers = new java.util.ArrayList<UUID>();
        var data = VillagerQuestSavedData.get(level);
        var players = shared == null ? Set.of(playerId) : shared.enrollments().keySet();
        for (UUID id : players) {
            if (shared != null && !shared.linked(id)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            var personal = data.get(id, definition.id());
            if (personal == null) continue;
            if (shared != null) {
                PartyQuestService.mergePersonalProgress(shared, definition, personal);
                PartyQuestService.syncPersonalProgress(shared, definition, progress);
            }
            if (player != null) {
                onlinePlayers.add(id);
                online = true;
                active |= VillagerQuestService.performanceActive(player, definition, personal);
            }
        }
        if (!onlinePlayers.isEmpty() && !onlinePlayers.contains(state.scopePlayer())) state.scopePlayer(onlinePlayers.getFirst());
        var scopedProgress = online && state.scopePlayer() != null ? data.get(state.scopePlayer(), definition.id()) : progress;
        if (shared != null) PartyQuestService.syncPersonalProgress(shared, definition, scopedProgress);
        state.update(definition, scopedProgress, level.getServer().overworld().getGameTime(), online, active,
                actor -> QuestFollowerService.healthPercent(level, scopedProgress.questRunId(), actor));
        data.setDirty();
        if (shared != null) PartyService.markChanged(level);
        var stage = definition.stages().get(scopedProgress.currentStage());
        if (!evaluateCompletion || !online || !active || !scopedProgress.pendingStageEntry().isBlank()
                || !scopedProgress.pendingStageTransition().isBlank()
                || stage != null && (!stage.next().isBlank() || !QuestTimers.allowsStage(stage, scopedProgress, level.getGameTime()))) return;
        for (UUID id : players) {
            if (shared != null && !shared.linked(id)) continue;
            var player = level.getServer().getPlayerList().getPlayer(id);
            var personal = data.get(id, definition.id());
            if (player != null && personal != null && VillagerQuestService.conditionReadyForPlayer(player, definition, personal)) {
                state.freeze(definition.rewards());
                break;
            }
        }
    }

    public static QuestDefinition.Rewards freeze(DialogueContext context, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (definition.rewards().performance().measurements().isEmpty()) return definition.rewards();
        update(context.level(), context.player().getUUID(), definition, progress);
        var rewards = progress.performance().freeze(definition.rewards());
        VillagerQuestSavedData.get(context.level()).setDirty();
        PartyQuestService.sharedForPlayer(context.level(), context.player().getUUID(), definition.id())
                .ifPresent(shared -> PartyService.markChanged(context.level()));
        return rewards;
    }

    public static void playerEvent(ServerPlayer player, QuestPerformance.Metric metric, double amount, LivingEntity killed) {
        var level = player.serverLevel();
        var data = VillagerQuestSavedData.get(level);
        Set<QuestPerformanceState> updated = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : data.activeProgress(player.getUUID())) {
            var definition = VillagerQuestResources.quest(player.server, entry.getKey()).orElse(null);
            if (definition == null || definition.rewards().performance().measurements().isEmpty()) continue;
            var progress = entry.getValue();
            update(level, player.getUUID(), definition, progress, false);
            if (!updated.add(progress.performance()) || !performanceEventAllowed(player, definition, progress)) continue;
            progress.performance().event(definition.rewards().performance(), metric, amount, measurement -> killed == null
                    || measurement.entityTypes().isEmpty() && measurement.entityTags().isEmpty()
                    || measurement.entityTypes().contains(BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()))
                    || measurement.entityTags().stream().anyMatch(tag -> killed.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag))));
            data.setDirty();
        }
    }

    private static boolean performanceEventAllowed(ServerPlayer player, QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        return !progress.completionPending() && VillagerQuestService.performanceActive(player, definition, progress);
    }
}
