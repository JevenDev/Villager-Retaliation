package com.jvn.villagerretaliation.quest.follower;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
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

public final class QuestFollowerSavedData extends SavedData {
    final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static QuestFollowerSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(QuestFollowerSavedData::new, QuestFollowerSavedData::load), "villagerretaliation_followers");
    }

    public static QuestFollowerSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new QuestFollowerSavedData();
        var entries = root.getList("Followers", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var tag = entries.getCompound(i);
            var quest = ResourceLocation.tryParse(tag.getString("Quest"));
            if (quest == null || !tag.hasUUID("Actor") || !tag.hasUUID("Player") || !tag.hasUUID("Run")) continue;
            try {
                var directive = QuestFollowerDirective.parse(quest, JsonParser.parseString(tag.getString("Directive")).getAsJsonObject());
                var entry = new Entry(tag.getUUID("Player"), quest, tag.getUUID("Run"), tag.getUUID("Actor"),
                        tag.hasUUID("Target") ? tag.getUUID("Target") : tag.getUUID("Player"), directive, tag.getString("Stage"),
                        tag.getString("Command"), BlockPos.of(tag.getLong("Anchor")), tag.getString("Dimension"));
                if (tag.contains("Runtime")) {
                    if (!tag.contains("Runtime", Tag.TAG_STRING) || ResourceLocation.tryParse(tag.getString("Runtime")) == null)
                        throw new IllegalArgumentException("Invalid quest follower runtime identity");
                    entry.runtime = ResourceLocation.parse(tag.getString("Runtime"));
                }
                entry.diagnostic = tag.getString("Diagnostic");
                entry.dead = tag.getBoolean("Dead");
                entry.missingSince = tag.contains("MissingSince") ? tag.getLong("MissingSince") : -1;
                data.entries.put(entry.actor, entry);
            } catch (com.google.gson.JsonParseException | IllegalStateException | IllegalArgumentException exception) {
                com.mojang.logging.LogUtils.getLogger().error("Could not load quest follower {}", quest, exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var tags = new ListTag();
        for (var entry : entries.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Player", entry.player);
            tag.putUUID("Run", entry.run);
            tag.putUUID("Actor", entry.actor);
            tag.putUUID("Target", entry.target);
            tag.putString("Quest", entry.quest.toString());
            if (!entry.runtime.equals(com.jvn.villagerretaliation.quest.runtime.QuestRuntimes.defaultId()))
                tag.putString("Runtime", entry.runtime.toString());
            tag.putString("Directive", entry.directive.data().toString());
            tag.putString("Stage", entry.stage);
            tag.putString("Command", entry.command);
            tag.putLong("Anchor", entry.anchor.asLong());
            tag.putString("Dimension", entry.dimension);
            tag.putString("Diagnostic", entry.diagnostic);
            tag.putBoolean("Dead", entry.dead);
            tag.putLong("MissingSince", entry.missingSince);
            tags.add(tag);
        }
        root.put("Followers", tags);
        return root;
    }

    static final class Entry {
        final UUID player;
        final ResourceLocation quest;
        ResourceLocation runtime = com.jvn.villagerretaliation.quest.runtime.QuestRuntimes.defaultId();
        final UUID run;
        final UUID actor;
        UUID target;
        final QuestFollowerDirective directive;
        final String stage;
        String command;
        BlockPos anchor;
        String dimension;
        String diagnostic = "";
        boolean dead;
        long missingSince = -1;

        Entry(UUID player, ResourceLocation quest, UUID run, UUID actor, UUID target, QuestFollowerDirective directive,
                String stage, String command, BlockPos anchor, String dimension) {
            this.player = player;
            this.quest = quest;
            this.run = run;
            this.actor = actor;
            this.target = target;
            this.directive = directive;
            this.stage = stage;
            this.command = command;
            this.anchor = anchor.immutable();
            this.dimension = dimension;
        }
    }
}
