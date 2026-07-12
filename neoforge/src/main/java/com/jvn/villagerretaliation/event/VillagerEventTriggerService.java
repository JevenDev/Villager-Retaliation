package com.jvn.villagerretaliation.event;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionExecutor;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.scene.SceneLaunchService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerEventTriggerService {
    private static final Set<String> IN_FLIGHT_KEYS = ConcurrentHashMap.newKeySet();

    private VillagerEventTriggerService() {
    }

    public static void warm(net.minecraft.server.MinecraftServer server) {
        VillagerEventTriggerResources.warm(server);
    }

    public static void clearCache() {
        VillagerEventTriggerResources.clearCache();
    }

    public static void clearRuntimeState() {
        IN_FLIGHT_KEYS.clear();
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
        DialogueContext context = villager == null || player == null
                ? null
                : VillagerInteractionService.createDialogueContext(level, player, villager);
        if (!definition.conditions().isEmpty()) {
            if (context == null) {
                return;
            }
            if (!DialogueCondition.matchesAll(context, definition.conditions())) {
                return;
            }
        }

        String key = cooldownKey(level, definition, event, villager, player).serialized();
        VillagerEventTriggerSavedData data = VillagerEventTriggerSavedData.get(level);
        long lastRunTime = data.lastRunGameTime(key);
        long gameTime = level.getGameTime();
        if (!definition.repeatable() && lastRunTime > 0L) {
            return;
        }
        if (definition.cooldownTicks() > 0L && lastRunTime > 0L && gameTime - lastRunTime < definition.cooldownTicks()) {
            return;
        }

        if (!IN_FLIGHT_KEYS.add(key)) {
            return;
        }
        try {
            Map<String, String> replacements = eventReplacements(context, level, event, villager, player);
            boolean ran;
            if (context != null) {
                ran = runContextActions(context, definition, replacements);
            } else {
                ran = runFallbackActions(level, event, definition, villager, player, replacements);
            }
            if (ran) {
                data.markRun(key, gameTime);
            }
        } finally {
            IN_FLIGHT_KEYS.remove(key);
        }
    }

    private static Map<String, String> eventReplacements(
            DialogueContext context,
            ServerLevel level,
            VillageEventMemory.MemoryEvent event,
            Villager villager,
            ServerPlayer player) {
        Map<String, String> replacements = context == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(DialoguePlaceholders.base(context));
        replacements.put("memory", event.tagId().toString());
        replacements.put("memory_tag", event.tagId().toString());
        replacements.put("event", event.tagId().toString());
        replacements.put("event_x", Integer.toString(event.pos().getX()));
        replacements.put("event_y", Integer.toString(event.pos().getY()));
        replacements.put("event_z", Integer.toString(event.pos().getZ()));
        replacements.put("event_dimension", level.dimension().location().toString());
        if (villager != null) {
            replacements.put("villager", VillagerPresetNameRegistry.resolveDisplayName(villager).getString());
            replacements.put("villager_profession", VillagerInteractionTextUtil.professionName(villager.getVillagerData().getProfession(), "villager"));
        }
        if (player != null) {
            replacements.put("player", player.getGameProfile().getName());
        }
        return replacements;
    }

    private static boolean runContextActions(
            DialogueContext context,
            VillagerEventTriggerDefinition definition,
            Map<String, String> replacements) {
        boolean ran = false;
        for (int index = 0; index < definition.actions().size(); index++) {
            VillagerActionDefinition action = definition.actions().get(index);
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene()) {
                SceneLaunchService.LaunchResult launch = SceneLaunchService.launch(context, action);
                boolean suspended = false;
                if (launch.accepted()) {
                    SceneSavedData data = SceneSavedData.get(context.level());
                    var scene = data.get(launch.instanceId()).orElse(null);
                    if (scene != null) {
                        data.suspendContinuation(scene, context.player().getUUID(), context.villager().getUUID(),
                                "quest_trigger/" + index + "/" + action.sceneOperationId(),
                                definition.actions(), index + 1, replacements);
                        suspended = true;
                        ran = true;
                    }
                }
                if (suspended || action.required()) break;
                continue;
            }
            VillagerActionResult result = VillagerActionExecutor.execute(context, action, replacements);
            replacements.putAll(result.replacements());
            if (result.flashTracker()) {
                VillagerQuestService.flashTracker(context.player(), true);
            }
            ran |= result.ran();
        }
        return ran;
    }

    private static boolean runFallbackActions(
            ServerLevel level,
            VillageEventMemory.MemoryEvent event,
            VillagerEventTriggerDefinition definition,
            Villager villager,
            ServerPlayer player,
            Map<String, String> replacements) {
        boolean ran = false;
        for (VillagerActionDefinition action : definition.actions()) {
            switch (action.kind()) {
                case NOTIFICATION -> {
                    if (player != null) {
                        String fallback = action.text().isBlank() ? "Village memory updated: {memory}" : action.text();
                        VillagerReputationNetworking.sendNotice(
                                player,
                                VillagerDialogueResources.resolveTemplate(fallback, replacements),
                                VillagerReputationNoticeKind.QUEST);
                        ran = true;
                    }
                }
                case TRACKER -> {
                    if (player != null && action.flashTracker()) {
                        VillagerQuestService.flashTracker(player, true);
                        ran = true;
                    }
                }
                case EXPERIENCE -> {
                    if (player != null && action.amount() > 0) {
                        player.giveExperiencePoints(action.amount());
                        ran = true;
                    }
                }
                case MEMORY -> {
                    if (action.memoryTag() != null) {
                        VillageEventMemory.remember(level, action.memoryTag(), event.pos(), villager, player);
                        ran = true;
                    }
                }
                case FORCED_DIALOGUE, QUEST, QUEST_TRANSITION, REPUTATION, GOSSIP, LOOT, SET_TAG, CLEAR_TAG, SET_VARIABLE, COUNTER, NONE -> {
                }
            }
        }
        return ran;
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
            case PLAYER -> "player:" + (player != null ? player.getUUID() : event.playerId() == null ? "unknown" : event.playerId());
            case SOURCE_VILLAGER -> "villager:" + (villager != null ? villager.getUUID() : event.sourceId() == null ? "unknown" : event.sourceId());
            case VILLAGE -> villageScopeKey(level, villager, event);
        };
        return new CooldownKey(definition.id(), scopeKey);
    }

    private static String villageScopeKey(ServerLevel level, Villager villager, VillageEventMemory.MemoryEvent event) {
        return VillageScopeKeys.forResolvedVillageOrPosition(level, villager, event.pos());
    }

    private record CooldownKey(ResourceLocation triggerId, String scopeKey) {
        private String serialized() {
            return this.triggerId + "|" + this.scopeKey;
        }
    }
}
