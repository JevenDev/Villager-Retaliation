package com.jvn.villagerretaliation.progress;

import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class PlayerHistoryService {
    public static final ResourceLocation PLAYER_DIED = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "player_died");

    private PlayerHistoryService() { }

    public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PlayerHistorySavedData.get(player.serverLevel()).visitDimension(player.getUUID(), event.getTo().location());
    }

    public static void advancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PlayerHistorySavedData.get(player.serverLevel()).obtainWorldAdvancement(event.getAdvancement().id());
    }

    public static void died(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        var killer = player.getKillCredit();
        var death = new PlayerHistorySavedData.Death(
                source.typeHolder().unwrapKey().orElseThrow().location(),
                killer == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()),
                killer instanceof ServerPlayer ? killer.getUUID() : null,
                killer == null ? "" : killer.getName().getString(),
                player.serverLevel().dimension().location(), player.blockPosition(),
                player.serverLevel().getGameTime(), player.getCombatTracker().getDeathMessage().getString());
        PlayerHistorySavedData.get(player.serverLevel()).recordDeath(player.getUUID(), death);
    }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive()) return;
        var data = PlayerHistorySavedData.get(player.serverLevel());
        player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .ifPresent(key -> data.visitBiome(player.getUUID(), key.location()));
        var death = data.takePendingDeath(player.getUUID());
        if (death != null) VillagerQuestService.onCriterion(player.serverLevel(), player, PLAYER_DIED, death.payload());
    }

    public static Map<String, String> replacements(ServerPlayer player) {
        var death = PlayerHistorySavedData.get(player.serverLevel()).latestDeath(player.getUUID());
        return Map.of("last_death_message", death == null ? "" : death.message(),
                "last_death_killer", death == null ? "" : death.killerName(),
                "last_death_dimension", death == null ? "" : death.dimension().toString());
    }
}
