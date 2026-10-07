package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CampaignCharacterLossService {
    private static final int RETRIES_PER_PASS = 16;
    private static final Map<MinecraftServer, LinkedHashMap<UUID, CampaignCharacterBinding>> PENDING = new WeakHashMap<>();

    private CampaignCharacterLossService() { }

    static void record(Entity entity) {
        if (!(entity instanceof Villager) || !(entity.level() instanceof ServerLevel level)) return;
        if (CampaignOwnedSpawns.rollingBack(entity.getUUID())) return;
        var reason = entity.getRemovalReason();
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) return;
        var data = CampaignCharacterSavedData.get(level);
        var binding = data.forEntity(entity.getUUID());
        if (binding == null || binding.lost()) return;
        var pending = PENDING.computeIfAbsent(level.getServer(), ignored -> new LinkedHashMap<>());
        pending.put(binding.entityId(), binding);
        try {
            if (finish(level.getServer(), data, binding)) pending.remove(binding.entityId());
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not save permanent loss of campaign character {}. Its confirmed loss will retry automatically.", entity.getUUID(), exception);
        }
        if (pending.isEmpty()) PENDING.remove(level.getServer());
    }

    static String diagnostic(MinecraftServer server, CampaignCharacterBinding binding) {
        var pending = PENDING.get(server);
        return binding != null && !binding.lost() && pending != null && binding.equals(pending.get(binding.entityId()))
                ? "Confirmed campaign character loss is waiting to be saved or reconciled with its original identity. Check world storage. Retry is automatic."
                : "";
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 100L == 0L) retry(server);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
    }

    static void retry(MinecraftServer server) {
        var pending = PENDING.get(server);
        if (pending == null || pending.isEmpty()) return;
        var data = CampaignCharacterSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return;
        var batch = pending.values().stream().limit(RETRIES_PER_PASS).toList();
        for (var binding : batch) {
            boolean finished = false;
            try { finished = finish(server, data, binding); }
            catch (IOException exception) {
                if (!diagnostic(server, binding).isBlank()) LogUtils.getLogger().debug("Campaign character {} is still waiting for its loss to be saved", binding.entityId(), exception);
            }
            pending.remove(binding.entityId());
            if (!finished) pending.put(binding.entityId(), binding);
        }
        if (pending.isEmpty()) PENDING.remove(server);
    }

    private static boolean finish(MinecraftServer server, CampaignCharacterSavedData data, CampaignCharacterBinding expected) throws IOException {
        var current = data.forEntity(expected.entityId());
        if (current == null || !expected.equals(current.withLoss(false)) || current.lost()) return true;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(expected.entityId());
            if (entity == null || entity.isRemoved()) continue;
            if (!(entity instanceof Villager villager) || !villager.isAlive()) return false;
            if (expected.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN) {
                var spawns = CampaignSpawnSavedData.get(server.overworld());
                var receipt = spawns.forEntity(expected.entityId());
                if (!spawns.diagnostic().isBlank() || receipt == null || !receipt.binding().equals(expected)
                        || !CampaignOwnedSpawns.owns(receipt, villager)) return false;
            }
            return true;
        }
        data.recordLoss(expected.entityId(), true);
        return true;
    }
}
