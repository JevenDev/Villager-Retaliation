package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageMembership;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerEventTriggerService {
    private static final Map<CooldownKey, Long> LAST_RUN_TIMES = new ConcurrentHashMap<>();

    private VillagerEventTriggerService() {
    }

    public static void warm(net.minecraft.server.MinecraftServer server) {
        VillagerEventTriggerResources.warm(server);
    }

    public static void clearCache() {
        VillagerEventTriggerResources.clearCache();
    }

    public static void clearRuntimeState() {
        LAST_RUN_TIMES.clear();
    }

    public static void onMemoryWritten(ServerLevel level, VillageEventMemory.MemoryEvent event) {
        if (level == null || event == null || level.getServer() == null) {
            return;
        }
        for (VillagerEventTriggerDefinition definition : VillagerEventTriggerResources.triggers(level.getServer())) {
            if (!definition.listensTo(event)) {
                continue;
            }
            runIfPossible(level, event, definition);
        }
    }

    private static void runIfPossible(
            ServerLevel level,
            VillageEventMemory.MemoryEvent event,
            VillagerEventTriggerDefinition definition) {
        Villager villager = resolveVillager(level, event.sourceId());
        ServerPlayer player = resolvePlayer(level, event.playerId());
        if (villager == null || player == null) {
            return;
        }

        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        for (DialogueCondition condition : definition.conditions()) {
            if (!condition.matches(context)) {
                return;
            }
        }

        CooldownKey key = cooldownKey(level, definition, event, villager, player);
        long lastRunTime = LAST_RUN_TIMES.getOrDefault(key, 0L);
        long gameTime = level.getGameTime();
        if (!definition.repeatable() && lastRunTime > 0L) {
            return;
        }
        if (definition.cooldownTicks() > 0L && lastRunTime > 0L && gameTime - lastRunTime < definition.cooldownTicks()) {
            return;
        }

        boolean ran = false;
        Map<String, String> replacements = new LinkedHashMap<>(DialoguePlaceholders.base(context));
        replacements.put("memory", event.tagId().toString());
        replacements.put("memory_tag", event.tagId().toString());
        replacements.put("event", event.tagId().toString());
        for (com.jvn.villagerretaliation.action.VillagerActionDefinition action : definition.actions()) {
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker()) {
                VillagerQuestService.flashTracker(player, true);
            }
            ran |= result.ran();
        }
        if (ran) {
            LAST_RUN_TIMES.put(key, gameTime);
        }
    }

    private static Villager resolveVillager(ServerLevel level, UUID sourceId) {
        if (sourceId == null) {
            return null;
        }
        Entity entity = level.getEntity(sourceId);
        return entity instanceof Villager villager && villager.isAlive() ? villager : null;
    }

    private static ServerPlayer resolvePlayer(ServerLevel level, UUID playerId) {
        return playerId == null ? null : level.getServer().getPlayerList().getPlayer(playerId);
    }

    private static CooldownKey cooldownKey(
            ServerLevel level,
            VillagerEventTriggerDefinition definition,
            VillageEventMemory.MemoryEvent event,
            Villager villager,
            ServerPlayer player) {
        String scopeKey = switch (definition.scope()) {
            case PLAYER -> "player:" + player.getUUID();
            case SOURCE_VILLAGER -> "villager:" + villager.getUUID();
            case VILLAGE -> villageScopeKey(level, villager, event);
        };
        return new CooldownKey(definition.id(), scopeKey);
    }

    private static String villageScopeKey(ServerLevel level, Villager villager, VillageEventMemory.MemoryEvent event) {
        return VillageMembership.resolve(level, villager)
                .map(area -> "village:" + level.dimension().location() + ":" + posKey(area.centerBlock()))
                .orElseGet(() -> "village:" + level.dimension().location() + ":" + posKey(event.pos()));
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record CooldownKey(ResourceLocation triggerId, String scopeKey) {
    }
}
