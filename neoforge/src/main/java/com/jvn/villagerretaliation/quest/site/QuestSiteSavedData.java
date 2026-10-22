package com.jvn.villagerretaliation.quest.site;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class QuestSiteSavedData extends SavedData {
    final Map<UUID, Site> sites = new LinkedHashMap<>();
    enum Phase { SEARCHING, CANDIDATE, PREPARING, PLACING, SPAWNING, READY, ACTIVE, RETIRED, UNAVAILABLE }
    public static QuestSiteSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(QuestSiteSavedData::new, QuestSiteSavedData::load), "villagerretaliation_quest_sites");
    }

    public static QuestSiteSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new QuestSiteSavedData();
        var list = root.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            if (!tag.hasUUID("Id") || !tag.hasUUID("Player") || ResourceLocation.tryParse(tag.getString("Quest")) == null) continue;
            try {
                var site = new Site(tag.getUUID("Id"), tag.getUUID("Player"), ResourceLocation.parse(tag.getString("Quest")),
                        tag.hasUUID("Provider") ? tag.getUUID("Provider") : null,
                        ResourceLocation.parse(tag.getString("Dimension")), BlockPos.of(tag.getLong("Home")));
                site.phase = Phase.valueOf(tag.getString("Phase"));
                site.config = tag.getString("Config");
                site.attempt = tag.getInt("Attempt");
                site.accepted = tag.getBoolean("Accepted");
                site.validated = tag.getBoolean("Validated");
                site.requestedAt = tag.getLong("RequestedAt");
                site.position = tag.contains("Position") ? BlockPos.of(tag.getLong("Position")) : null;
                site.actorPosition = tag.contains("ActorPosition") ? BlockPos.of(tag.getLong("ActorPosition")) : site.position;
                site.actorDimension = ResourceLocation.tryParse(tag.getString("ActorDimension"));
                if (site.actorDimension == null) site.actorDimension = site.dimension;
                site.template = ResourceLocation.tryParse(tag.getString("Template"));
                site.run = tag.hasUUID("Run") ? tag.getUUID("Run") : null;
                site.captive = tag.hasUUID("Captive") ? tag.getUUID("Captive") : null;
                site.released = tag.getBoolean("Released");
                site.discovered = tag.getBoolean("Discovered");
                site.rescued = tag.getBoolean("Rescued");
                site.mapIssued = tag.getBoolean("MapIssued");
                var recipients = tag.getList("MapRecipients", Tag.TAG_COMPOUND);
                for (int j = 0; j < recipients.size(); j++) site.mapRecipients.add(recipients.getCompound(j).getUUID("Player"));
                site.dead = tag.getBoolean("Dead");
                site.diagnostic = tag.getString("Diagnostic");
                var guards = tag.getList("Guards", Tag.TAG_COMPOUND);
                for (int j = 0; j < guards.size(); j++) {
                    var guard = guards.getCompound(j);
                    site.guards.add(guard.getUUID("Id"));
                    if (guard.getBoolean("Dead")) site.defeated.add(guard.getUUID("Id"));
                }
                if (site.phase == Phase.PLACING || site.phase == Phase.SPAWNING) {
                    site.phase = Phase.UNAVAILABLE;
                    site.diagnostic = "Quest site preparation was interrupted. The site has been preserved for inspection.";
                }
                data.sites.put(site.id, site);
            } catch (IllegalArgumentException exception) {
                com.mojang.logging.LogUtils.getLogger().error("Could not load quest site {}", tag.getUUID("Id"), exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var site : sites.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Id", site.id);
            tag.putUUID("Player", site.player);
            tag.putString("Quest", site.quest.toString());
            if (site.provider != null) tag.putUUID("Provider", site.provider);
            tag.putString("Dimension", site.dimension.toString());
            tag.putLong("Home", site.home.asLong());
            tag.putString("Phase", site.phase.name());
            tag.putString("Config", site.config);
            tag.putInt("Attempt", site.attempt);
            tag.putBoolean("Accepted", site.accepted);
            tag.putBoolean("Validated", site.validated);
            tag.putLong("RequestedAt", site.requestedAt);
            if (site.position != null) tag.putLong("Position", site.position.asLong());
            if (site.actorPosition != null) tag.putLong("ActorPosition", site.actorPosition.asLong());
            if (site.actorDimension != null) tag.putString("ActorDimension", site.actorDimension.toString());
            if (site.template != null) tag.putString("Template", site.template.toString());
            if (site.run != null) tag.putUUID("Run", site.run);
            if (site.captive != null) tag.putUUID("Captive", site.captive);
            tag.putBoolean("Released", site.released);
            tag.putBoolean("Discovered", site.discovered);
            tag.putBoolean("Rescued", site.rescued);
            tag.putBoolean("MapIssued", site.mapIssued);
            var recipients = new ListTag();
            for (var player : site.mapRecipients) {
                var receipt = new CompoundTag();
                receipt.putUUID("Player", player);
                recipients.add(receipt);
            }
            tag.put("MapRecipients", recipients);
            tag.putBoolean("Dead", site.dead);
            tag.putString("Diagnostic", site.diagnostic);
            var guards = new ListTag();
            for (var id : site.guards) {
                var guard = new CompoundTag();
                guard.putUUID("Id", id);
                guard.putBoolean("Dead", site.defeated.contains(id));
                guards.add(guard);
            }
            tag.put("Guards", guards);
            list.add(tag);
        }
        root.put("Sites", list);
        return root;
    }

    static final class Site {
        final UUID id, player, provider;
        final ResourceLocation quest, dimension;
        final BlockPos home;
        Phase phase = Phase.SEARCHING;
        String config = "", diagnostic = "";
        int attempt;
        BlockPos position, actorPosition;
        ResourceLocation template, actorDimension;
        UUID run, captive;
        final List<UUID> guards = new ArrayList<>(), defeated = new ArrayList<>(), mapRecipients = new ArrayList<>();
        boolean accepted, validated, released, discovered, rescued, mapIssued, dead;
        long requestedAt;
        transient Object pending;
        transient long activatedAt, pendingSince;
        Site(UUID id, UUID player, ResourceLocation quest, UUID provider, ResourceLocation dimension, BlockPos home) {
            this.id = id;
            this.player = player;
            this.quest = quest;
            this.provider = provider;
            this.dimension = dimension;
            this.home = home.immutable();
        }
    }
}
